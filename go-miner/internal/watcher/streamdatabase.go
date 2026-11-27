package watcher

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	// StreamDatabaseGlobalBadgesURL is the public global badges page indexed
	// for newly deployed Twitch watch-time badge campaigns.
	StreamDatabaseGlobalBadgesURL = "https://www.streamdatabase.com/twitch/global-badges"
	maxStreamDatabaseBytes        = 8 << 20
)

// StreamDatabaseBadge represents a single global badge entry extracted from
// streamdatabase.com/twitch/global-badges (__NEXT_DATA__ JSON).
type StreamDatabaseBadge struct {
	ID          string     `json:"id"`
	SetID       string     `json:"set_id,omitempty"`
	Version     string     `json:"version,omitempty"`
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Cost        string     `json:"cost"`
	Added       bool       `json:"added"`
	Removed     bool       `json:"removed"`
	StartAt     *time.Time `json:"start_at,omitempty"`
	EndAt       *time.Time `json:"end_at,omitempty"`
	ImageURL    string     `json:"image_url,omitempty"`
	Game        string     `json:"game,omitempty"`
	Campaign    string     `json:"campaign,omitempty"`
	DailyCap    bool       `json:"daily_cap,omitempty"`
	Tier        int        `json:"tier,omitempty"`
}

var (
	nextDataScriptRE = regexp.MustCompile(`(?is)<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>`)

	// Strict watch-to-earn positive requirement: must mention watching AND a duration.
	watchVerbRE     = regexp.MustCompile(`(?i)\bwatch(?:ing)?\b`)
	watchDurationRE = regexp.MustCompile(`(?i)\b(\d+)\s*(minutes?|mins?|m|hours?|hrs?|h|days?|d)\b`)
	perDayRE        = regexp.MustCompile(`(?i)(?:/\s*day|\bper\s+day\b|\beach\s+day\b|\bdaily\b|\bfor\s+\d+\s+days?\b)`)

	// Strict non-watch mechanic rejection list (v0.4 critical fix).
	// Rejects evolving badges (e.g. Pokémon First Partners Collection where Pikachu
	// has cost="free" but evolving requires subbing), subscribing, gifting, cheering/bits,
	// purchasing, clipping, chatting, and "securing all" meta-badges.
	nonWatchMechanicRE = regexp.MustCompile(
		`(?i)\b(?:evolv(?:e[ds]?|ing)|subscrib(?:e[ds]?|ing|ers?)|subbing|subs?|subscriptions?|gift(?:s|ed|ing)?|cheer(?:s|ed|ing)?|bits|purchas(?:e[ds]?|ing)|buy(?:s|ing)?|bought|clip(?:s|ped|ping)?|chat(?:s|ted|ting)?|prime\s+(?:gaming|sub)|securing\s+all)\b`,
	)
)

// IsWatchToEarnBadge enforces the strict v0.4 watch-only badge filter:
//  1. Structural checks: cost == "free" (case-insensitive), added == true,
//     removed == false, and now within [start_at, end_at] when present.
//  2. Description checks: explicitly requires watching (watch/watching + duration
//     such as minutes/hours/days) AND explicitly rejects non-watch mechanics
//     (evolving, evolve, subscribing, gifting, subbing, cheering, bits,
//     purchasing, clip, chat, securing all).
func IsWatchToEarnBadge(b StreamDatabaseBadge, now time.Time) bool {
	if !strings.EqualFold(strings.TrimSpace(b.Cost), "free") {
		return false
	}
	if !b.Added || b.Removed {
		return false
	}
	if b.StartAt != nil && !b.StartAt.IsZero() && now.Before(*b.StartAt) {
		return false
	}
	if b.EndAt != nil && !b.EndAt.IsZero() && !b.EndAt.After(now) {
		return false
	}

	desc := strings.TrimSpace(b.Description)
	if desc == "" {
		return false
	}
	if nonWatchMechanicRE.MatchString(desc) {
		return false
	}
	if !watchVerbRE.MatchString(desc) {
		return false
	}
	if !watchDurationRE.MatchString(desc) {
		return false
	}
	return true
}

// ParseWatchDuration extracts the required watch duration from a badge
// description (e.g. "Watch 30 minutes", "Watch for 1 hour", "Watch 60m/day").
// Returns 0 if no valid duration is found.
func ParseWatchDuration(description string) time.Duration {
	m := watchDurationRE.FindStringSubmatch(description)
	if len(m) < 3 {
		return 0
	}
	n, err := strconv.Atoi(m[1])
	if err != nil || n <= 0 {
		return 0
	}
	unit := strings.ToLower(m[2])
	switch {
	case strings.HasPrefix(unit, "m"):
		return time.Duration(n) * time.Minute
	case strings.HasPrefix(unit, "h"):
		return time.Duration(n) * time.Hour
	case strings.HasPrefix(unit, "d"):
		// Multi-day watch badges typically require 60m/day unless a smaller
		// minute/hour duration is also specified in the same description.
		all := watchDurationRE.FindAllStringSubmatch(description, -1)
		for _, sub := range all {
			if len(sub) >= 3 && !strings.HasPrefix(strings.ToLower(sub[2]), "d") {
				if v, err := strconv.Atoi(sub[1]); err == nil && v > 0 {
					if strings.HasPrefix(strings.ToLower(sub[2]), "h") {
						return time.Duration(v) * time.Hour
					}
					return time.Duration(v) * time.Minute
				}
			}
		}
		return time.Duration(n) * 60 * time.Minute
	default:
		return 0
	}
}

// HasDailyWatchCap reports whether a badge description indicates a daily watch cap
// (e.g. "Watch 60 minutes per day", "60m/day").
func HasDailyWatchCap(description string) bool {
	return perDayRE.MatchString(description)
}

// CalculateStepDurations computes per-tier step durations in ascending tier order.
// For cumulative multi-tier campaigns (e.g. 30m, 60m, 90m), it returns the incremental
// step duration for each tier (30m, 30m, 30m). For daily-cap campaigns (e.g. 60m/day),
// each tier keeps its per-day duration.
func CalculateStepDurations(cumulative []time.Duration, dailyCap bool) []time.Duration {
	if len(cumulative) == 0 {
		return nil
	}
	steps := make([]time.Duration, len(cumulative))
	if dailyCap {
		copy(steps, cumulative)
		return steps
	}
	var prev time.Duration
	for i, d := range cumulative {
		if d > prev {
			steps[i] = d - prev
		} else {
			steps[i] = d
		}
		prev = d
	}
	return steps
}

// FetchStreamDatabaseBadges downloads and parses the StreamDatabase global badges page.
func FetchStreamDatabaseBadges(ctx context.Context, client *http.Client, pageURL string) ([]StreamDatabaseBadge, error) {
	if pageURL == "" {
		pageURL = StreamDatabaseGlobalBadgesURL
	}
	if client == nil {
		client = &http.Client{Timeout: 20 * time.Second}
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, pageURL, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
	req.Header.Set("Accept", "text/html,application/xhtml+xml,application/json")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("streamdatabase returned HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxStreamDatabaseBytes+1))
	if err != nil {
		return nil, err
	}
	if len(body) > maxStreamDatabaseBytes {
		return nil, fmt.Errorf("streamdatabase response exceeds %d bytes", maxStreamDatabaseBytes)
	}
	return ParseStreamDatabaseHTML(body)
}

// ParseStreamDatabaseHTML extracts badge entries from the __NEXT_DATA__ script
// tag in a StreamDatabase HTML page (or directly from JSON if given raw JSON).
func ParseStreamDatabaseHTML(html []byte) ([]StreamDatabaseBadge, error) {
	rawJSON := html
	if m := nextDataScriptRE.FindSubmatch(html); len(m) >= 2 {
		rawJSON = m[1]
	}

	var root map[string]any
	if err := json.Unmarshal(rawJSON, &root); err != nil {
		return nil, fmt.Errorf("parse streamdatabase __NEXT_DATA__: %w", err)
	}

	var badges []StreamDatabaseBadge
	collectStreamDatabaseBadges(root, &badges)
	return badges, nil
}

func collectStreamDatabaseBadges(node any, out *[]StreamDatabaseBadge) {
	switch v := node.(type) {
	case []any:
		for _, item := range v {
			collectStreamDatabaseBadges(item, out)
		}
	case map[string]any:
		if b, ok := parseStreamDatabaseBadgeMap(v); ok {
			*out = append(*out, b)
			return
		}
		for _, child := range v {
			collectStreamDatabaseBadges(child, out)
		}
	}
}

func parseStreamDatabaseBadgeMap(m map[string]any) (StreamDatabaseBadge, bool) {
	title, _ := m["title"].(string)
	desc, _ := m["description"].(string)
	cost, hasCost := m["cost"].(string)
	if title == "" || (!hasCost && desc == "") {
		return StreamDatabaseBadge{}, false
	}
	// Require at least one badge-specific field so we don't match unrelated page metadata.
	_, hasAdded := m["added"]
	_, hasRemoved := m["removed"]
	if !hasCost && !hasAdded && !hasRemoved {
		return StreamDatabaseBadge{}, false
	}

	b := StreamDatabaseBadge{
		ID:          stringField(m, "id"),
		SetID:       stringField(m, "set_id", "setId"),
		Version:     stringField(m, "version"),
		Title:       title,
		Description: desc,
		Cost:        cost,
		Added:       boolField(m, "added", true),
		Removed:     boolField(m, "removed", false),
		ImageURL:    stringField(m, "image_url", "imageUrl", "image"),
		Game:        stringField(m, "game", "category"),
		Campaign:    stringField(m, "campaign", "campaign_name"),
		DailyCap:    HasDailyWatchCap(desc),
	}
	if t := parseTimeField(m, "start_at", "startAt", "starts_at"); t != nil {
		b.StartAt = t
	}
	if t := parseTimeField(m, "end_at", "endAt", "ends_at"); t != nil {
		b.EndAt = t
	}
	return b, true
}

func stringField(m map[string]any, keys ...string) string {
	for _, k := range keys {
		if s, ok := m[k].(string); ok && s != "" {
			return s
		}
	}
	return ""
}

func boolField(m map[string]any, key string, def bool) bool {
	if b, ok := m[key].(bool); ok {
		return b
	}
	return def
}

func parseTimeField(m map[string]any, keys ...string) *time.Time {
	for _, k := range keys {
		raw, ok := m[k].(string)
		if !ok || strings.TrimSpace(raw) == "" {
			continue
		}
		for _, layout := range []string{time.RFC3339, time.RFC3339Nano, "2006-01-02T15:04:05Z", "2006-01-02"} {
			if t, err := time.Parse(layout, strings.TrimSpace(raw)); err == nil {
				return &t
			}
		}
	}
	return nil
}

// EligibleBadgeEntry represents a badge in the prioritized Auto/Pinned queue.
type EligibleBadgeEntry struct {
	CampaignID      string
	CampaignName    string
	BadgeTitle      string
	GameName        string
	GameSlug        string
	ImageURL        string
	EndsAt          time.Time
	RequiredMinutes int
	WatchedMinutes  int
	Tier            int
	DailyCap        bool
}

// SortEligibleBadges sorts eligible badges in exact Auto priority order:
//  1. Pinned badge/campaign first (if pinnedKey is non-empty).
//  2. Earliest expiration date first (zero EndsAt sorts last).
//  3. Shortest remaining watch duration (unowned tier) first.
//  4. Ascending tier, then alphabetical title for deterministic tie-breaking.
func SortEligibleBadges(entries []EligibleBadgeEntry, pinnedKey string) {
	pinnedNorm := strings.ToLower(strings.TrimSpace(pinnedKey))
	sort.SliceStable(entries, func(i, j int) bool {
		if pinnedNorm != "" {
			pi := matchesPin(entries[i], pinnedNorm)
			pj := matchesPin(entries[j], pinnedNorm)
			if pi != pj {
				return pi
			}
		}
		ei, ej := entries[i].EndsAt, entries[j].EndsAt
		if !ei.Equal(ej) {
			if ei.IsZero() {
				return false
			}
			if ej.IsZero() {
				return true
			}
			return ei.Before(ej)
		}
		ri := entries[i].RequiredMinutes - entries[i].WatchedMinutes
		rj := entries[j].RequiredMinutes - entries[j].WatchedMinutes
		if ri != rj {
			return ri < rj
		}
		if entries[i].Tier != entries[j].Tier {
			return entries[i].Tier < entries[j].Tier
		}
		return entries[i].BadgeTitle < entries[j].BadgeTitle
	})
}

func matchesPin(e EligibleBadgeEntry, pinnedNorm string) bool {
	return strings.EqualFold(e.CampaignID, pinnedNorm) ||
		strings.EqualFold(e.BadgeTitle, pinnedNorm) ||
		strings.EqualFold(e.CampaignName, pinnedNorm)
}

// DailyCapTracker tracks campaigns that have hit their daily watch cap
// for the current UTC day so mining rotates to the next eligible badge.
type DailyCapTracker struct {
	mu      sync.Mutex
	capped  map[string]string // campaignKey -> "YYYY-MM-DD" UTC
}

// NewDailyCapTracker creates an empty DailyCapTracker.
func NewDailyCapTracker() *DailyCapTracker {
	return &DailyCapTracker{capped: make(map[string]string)}
}

// MarkCapped marks a campaign as having reached its daily cap for the UTC day of now.
func (d *DailyCapTracker) MarkCapped(campaignKey string, now time.Time) {
	d.mu.Lock()
	defer d.mu.Unlock()
	d.capped[strings.ToLower(strings.TrimSpace(campaignKey))] = now.UTC().Format("2006-01-02")
}

// IsCapped reports whether campaignKey is capped for the UTC day of now.
func (d *DailyCapTracker) IsCapped(campaignKey string, now time.Time) bool {
	d.mu.Lock()
	defer d.mu.Unlock()
	day, ok := d.capped[strings.ToLower(strings.TrimSpace(campaignKey))]
	return ok && day == now.UTC().Format("2006-01-02")
}

// MatchStreamDatabaseToInventory matches a StreamDatabase badge against Twitch's
// Drops Inventory campaigns by game and badge reward name.
func MatchStreamDatabaseToInventory(b StreamDatabaseBadge, inventory []badgeInventoryCampaign) (badgeInventoryCampaign, badgeInventoryDrop, bool) {
	for _, camp := range inventory {
		if b.Game != "" && camp.Game.Name != "" &&
			!strings.EqualFold(slugify(b.Game), slugify(camp.Game.Name)) &&
			!strings.EqualFold(slugify(b.Game), camp.Game.Slug) {
			continue
		}
		for _, drop := range camp.Drops {
			for _, edge := range drop.Benefits {
				if isBadgeReward(edge.Benefit.Name, camp.Game.Name, b.Title) {
					return camp, drop, true
				}
			}
			if isBadgeReward(drop.Name, camp.Game.Name, b.Title) {
				return camp, drop, true
			}
		}
	}
	return badgeInventoryCampaign{}, badgeInventoryDrop{}, false
}
