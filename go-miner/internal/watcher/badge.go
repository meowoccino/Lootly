package watcher

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/Guliveer/twitch-miner-go/internal/config"
	"github.com/Guliveer/twitch-miner-go/internal/gql"
	"github.com/Guliveer/twitch-miner-go/internal/logger"
	"github.com/Guliveer/twitch-miner-go/internal/model"
)

const (
	badgeCatalogCacheTTL = 15 * time.Minute
	// Twitch's directory query accepts at most 100 streams. AllChannels badge
	// campaigns do not require the Drops Enabled tag, so search the broadest
	// eligible page and prefer its highest-viewer candidate.
	badgeCategoryStreamLimit = 100

	// Twitch uses Special Events as a synthetic category for campaigns whose
	// allow-listed channels may stream in any real category.
	crossCategoryBadgeCampaignSlug = "special-events"
)

type badgeGQL interface {
	GetAvailableBadgeNames(context.Context) (map[string]struct{}, error)
	GetDropsInventory(context.Context) (json.RawMessage, error)
	GetStreamInfo(context.Context, string) (*gql.StreamInfoResponse, error)
	GetUserID(context.Context, string) (string, error)
	GetTopStreamsByCategory(context.Context, string, int, bool) ([]gql.TopStream, error)
	GetAvailableCampaigns(context.Context, string) ([]string, error)
}

type dropsDashboardGQL interface {
	GetDropsDashboard(ctx context.Context, status string) ([]json.RawMessage, error)
}

type dropClaimGQL interface {
	ClaimDropRewards(ctx context.Context, dropInstanceID string) (bool, error)
}

type badgeTracked struct {
	username            string
	added               bool
	lastProgressMinutes int
	stalledTicks        int
}

type badgeCandidate struct {
	stream      *gql.TopStream
	campaignIDs []string
}

type badgeCampaignLookup struct {
	ids []string
	err error
}

// BadgeWatcher discovers active watch-time chat badge Drops and keeps a live,
// eligible channel in the miner until Twitch reports the badge/campaign earned.
type BadgeWatcher struct {
	mu              sync.Mutex
	channelIDsMu    sync.Mutex
	cfg             config.BadgeWatcherConfig
	gql             badgeGQL
	httpClient      *http.Client
	log             *logger.Logger
	blacklist       map[string]bool
	defaults        *model.StreamerSettings
	tracked          map[string]badgeTracked
	lastEligible     map[string]badgeCampaign
	stalledSkipUntil map[string]time.Time
	channelIDs       map[string]string
	campaigns       []badgeCampaign
	catalogLoadedAt time.Time
	pinnedCampaign  string
	badgesEnabled   bool
	dropsEnabled    bool
	refreshCh       chan struct{}
}

func NewBadgeWatcher(cfg config.BadgeWatcherConfig, gqlClient badgeGQL, httpClient *http.Client, log *logger.Logger, blacklist []string, defaults *model.StreamerSettings) *BadgeWatcher {
	b := make(map[string]bool)
	for _, name := range blacklist {
		b[strings.ToLower(name)] = true
	}
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 20 * time.Second}
	}
	return &BadgeWatcher{
		cfg:           cfg,
		gql:           gqlClient,
		httpClient:    httpClient,
		log:           log,
		blacklist:     b,
		defaults:      defaults,
		tracked:          make(map[string]badgeTracked),
		lastEligible:     make(map[string]badgeCampaign),
		stalledSkipUntil: make(map[string]time.Time),
		channelIDs:       make(map[string]string),
		badgesEnabled: true,
		dropsEnabled:  true,
		refreshCh:     make(chan struct{}, 1),
	}
}

// TriggerRefresh wakes the BadgeWatcher loop immediately to re-evaluate
// eligible badges/drops (non-blocking).
func (bw *BadgeWatcher) TriggerRefresh() {
	if bw == nil || bw.refreshCh == nil {
		return
	}
	select {
	case bw.refreshCh <- struct{}{}:
	default:
	}
}

// SetPinnedCampaign pins a specific badge or linked drop campaign for manual
// targeting (empty string resets to Auto priority).
func (bw *BadgeWatcher) SetPinnedCampaign(campaign string) {
	bw.mu.Lock()
	bw.pinnedCampaign = strings.TrimSpace(campaign)
	bw.mu.Unlock()
}

// PinnedCampaign returns the currently pinned campaign name (empty when Auto).
func (bw *BadgeWatcher) PinnedCampaign() string {
	bw.mu.Lock()
	defer bw.mu.Unlock()
	return bw.pinnedCampaign
}

// SetFilters updates whether global badges and/or in-game drops are enabled.
func (bw *BadgeWatcher) SetFilters(badges, drops bool) {
	bw.mu.Lock()
	bw.badgesEnabled = badges
	bw.dropsEnabled = drops
	bw.mu.Unlock()
}

func matchesBadgePin(c badgeCampaign, pinned string) bool {
	if strings.EqualFold(c.ID, pinned) || strings.EqualFold(c.Name, pinned) {
		return true
	}
	for _, b := range c.BadgeNames {
		if strings.EqualFold(b, pinned) {
			return true
		}
	}
	return false
}

func (bw *BadgeWatcher) Run(ctx context.Context, add func(context.Context, *model.Streamer), remove func(string, string), get func() []*model.Streamer) error {
	bw.log.Info("🏅 BadgeWatcher started", "poll_interval", bw.cfg.PollInterval, "streamer_limit", bw.cfg.StreamerLimit)
	bw.evaluate(ctx, add, remove, get)

	interval := bw.cfg.PollInterval
	if interval <= 0 {
		interval = time.Hour
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	progressTicker := time.NewTicker(60 * time.Second)
	defer progressTicker.Stop()

	for {
		select {
		case <-ctx.Done():
			bw.log.Info("🏅 BadgeWatcher stopping")
			bw.cleanup(remove, get)
			return ctx.Err()
		case <-ticker.C:
			bw.evaluate(ctx, add, remove, get)
		case <-progressTicker.C:
			bw.syncTrackedProgress(ctx, remove, get)
		case <-bw.refreshCh:
			bw.evaluate(ctx, add, remove, get)
		}
	}
}

func (bw *BadgeWatcher) syncTrackedProgress(ctx context.Context, remove func(string, string), get func() []*model.Streamer) {
	bw.mu.Lock()
	if len(bw.tracked) == 0 {
		bw.mu.Unlock()
		return
	}
	trackedCopy := make(map[string]badgeTracked, len(bw.tracked))
	for k, v := range bw.tracked {
		trackedCopy[k] = v
	}
	eligibleCopy := make(map[string]badgeCampaign, len(bw.lastEligible))
	for k, v := range bw.lastEligible {
		eligibleCopy[k] = v
	}
	bw.mu.Unlock()

	raw, err := bw.gql.GetDropsInventory(ctx)
	if err != nil {
		return
	}
	var inventory struct {
		Campaigns []badgeInventoryCampaign `json:"dropCampaignsInProgress"`
	}
	if err := json.Unmarshal(raw, &inventory); err != nil {
		return
	}

	streamers := get()
	shouldRefresh := false
	now := time.Now()
	for id, tr := range trackedCopy {
		camp, ok := eligibleCopy[id]
		if !ok {
			continue
		}
		streamer := findStreamer(streamers, tr.username)
		if streamer == nil {
			continue
		}
		prog := campaignDropProgress(camp, inventory.Campaigns)
		if prog == nil || prog.RequiredMinutes <= 0 {
			continue
		}
		streamer.Mu.Lock()
		streamer.ActiveBadgeDrop = prog
		streamer.Mu.Unlock()
		bw.log.Info("Drop progress",
			"reward", prog.Reward,
			"streamer", streamer.Username,
			"progress", fmt.Sprintf("%d%% [%d/%d]", prog.ProgressPercent, prog.WatchedMinutes, prog.RequiredMinutes),
		)
		if prog.IsClaimable || prog.ProgressPercent >= 100 {
			if prog.DropInstanceID != "" {
				if claimGQL, ok := bw.gql.(dropClaimGQL); ok {
					bw.log.Info("Claiming drop from inventory", "drop", prog.Reward, "category", prog.Game)
					if _, err := claimGQL.ClaimDropRewards(ctx, prog.DropInstanceID); err != nil {
						bw.log.Warn("Failed to auto-claim completed drop", "drop", prog.Reward, "error", err)
					}
				}
			}
			shouldRefresh = true
			continue
		}

		// 2-minute stall watchdog: if watched_minutes hasn't increased for 2
		// consecutive 60s ticks, skip this streamer for 30 minutes and switch.
		bw.mu.Lock()
		cur, stillTracked := bw.tracked[id]
		stalled := false
		if stillTracked {
			if prog.WatchedMinutes > cur.lastProgressMinutes {
				cur.lastProgressMinutes = prog.WatchedMinutes
				cur.stalledTicks = 0
			} else {
				cur.stalledTicks++
			}
			if cur.stalledTicks >= 2 {
				stalled = true
				bw.stalledSkipUntil[strings.ToLower(cur.username)] = now.Add(30 * time.Minute)
				delete(bw.tracked, id)
			} else {
				bw.tracked[id] = cur
			}
		}
		bw.mu.Unlock()

		if stalled {
			bw.log.Warn("Stream stalled (no progress for 2m) — switching stream", "streamer", tr.username, "campaign", camp.Name)
			if tr.added {
				remove(tr.username, "badge_stream_stalled")
			} else {
				unmarkBadge(streamers, tr.username)
			}
			shouldRefresh = true
		}
	}
	if shouldRefresh {
		bw.TriggerRefresh()
	}
}

func (bw *BadgeWatcher) evaluate(ctx context.Context, add func(context.Context, *model.Streamer), remove func(string, string), get func() []*model.Streamer) {
	campaigns, err := bw.loadCampaigns(ctx)
	if err != nil {
		bw.log.Warn("Failed to load badge campaign catalog", "error", err)
		return
	}
	owned, err := bw.gql.GetAvailableBadgeNames(ctx)
	if err != nil {
		bw.log.Warn("Failed to load earned Twitch badges", "error", err)
		return
	}
	raw, err := bw.gql.GetDropsInventory(ctx)
	if err != nil {
		bw.log.Warn("Failed to load Drops inventory for badges", "error", err)
		return
	}
	completed, err := completedCampaigns(raw)
	if err != nil {
		bw.log.Warn("Failed to parse Drops inventory for badges", "error", err)
		return
	}
	var inventory struct {
		Campaigns []badgeInventoryCampaign `json:"dropCampaignsInProgress"`
	}
	if err := json.Unmarshal(raw, &inventory); err != nil {
		bw.log.Warn("Failed to parse badge reward progress", "error", err)
		return
	}
	bw.mu.Lock()
	badgesOn := bw.badgesEnabled
	dropsOn := bw.dropsEnabled
	pinned := bw.pinnedCampaign
	bw.mu.Unlock()

	now := time.Now()
	rewards := make(map[string]model.RewardDeadline, len(campaigns))
	eligible := make(map[string]badgeCampaign)
	eligibleOrdered := make([]badgeCampaign, 0, len(campaigns))
	if badgesOn {
		for _, c := range campaigns {
			if c.EndsAt.After(now) && !ownsCampaignBadge(c, owned) && !campaignCompleted(c, completed) {
				eligible[c.ID] = c
				eligibleOrdered = append(eligibleOrdered, c)
				rewards[c.ID] = c.nextReward(owned, inventory.Campaigns, now)
			}
		}
	}
	allInventoryCampaigns := inventory.Campaigns
	var dashActive int
	var (
		unlinkedDrops []badgeCampaign
		linkedCount   int
		unlinkedCount int
	)
	if dropsOn {
		if dashGQL, ok := bw.gql.(dropsDashboardGQL); ok {
			if rawDash, err := dashGQL.GetDropsDashboard(ctx, "ACTIVE"); err != nil {
				bw.log.Warn("Failed to load ViewerDropsDashboard for in-game drops", "error", err)
			} else {
				var dashCampaigns []badgeInventoryCampaign
				dashCampaigns, dashActive, _ = parseDashboardDropCampaigns(rawDash)
				allInventoryCampaigns = mergeInventoryAndDashboardCampaigns(inventory.Campaigns, dashCampaigns)
			}
		}
		for _, c := range allActiveInGameDropCampaigns(allInventoryCampaigns, now) {
			if _, exists := eligible[c.ID]; exists || campaignCompleted(c, completed) {
				continue
			}
			if c.IsLinked {
				eligible[c.ID] = c
				eligibleOrdered = append(eligibleOrdered, c)
				rewards[c.ID] = c.nextReward(nil, allInventoryCampaigns, now)
				linkedCount++
			} else {
				unlinkedDrops = append(unlinkedDrops, c)
				unlinkedCount++
			}
		}
	}
	bw.log.Info("In-game drops scanned", "active", dashActive, "linked", linkedCount, "unlinked", unlinkedCount)

	// Auto-advance: if the pinned/manual campaign completed or is no longer eligible,
	// clear the pin so Auto priority advances to the next earliest-expiring item.
	if pinned != "" {
		pinStillEligible := false
		for _, c := range eligibleOrdered {
			if matchesBadgePin(c, pinned) {
				pinStillEligible = true
				break
			}
		}
		if !pinStillEligible {
			bw.mu.Lock()
			if strings.EqualFold(bw.pinnedCampaign, pinned) {
				bw.pinnedCampaign = ""
			}
			bw.mu.Unlock()
			pinned = ""
			bw.log.Info("Pinned campaign completed or unavailable, returning to Auto")
		}
	}

	sort.SliceStable(eligibleOrdered, func(i, j int) bool {
		if pinned != "" {
			pi := matchesBadgePin(eligibleOrdered[i], pinned)
			pj := matchesBadgePin(eligibleOrdered[j], pinned)
			if pi != pj {
				return pi
			}
		}
		if eligibleOrdered[i].IsInGameDrop != eligibleOrdered[j].IsInGameDrop {
			return !eligibleOrdered[i].IsInGameDrop
		}
		if !eligibleOrdered[i].EndsAt.Equal(eligibleOrdered[j].EndsAt) {
			return eligibleOrdered[i].EndsAt.Before(eligibleOrdered[j].EndsAt)
		}
		ri := rewards[eligibleOrdered[i].ID].Remaining
		rj := rewards[eligibleOrdered[j].ID].Remaining
		if ri != rj {
			if ri == 0 {
				return false
			}
			if rj == 0 {
				return true
			}
			return ri < rj
		}
		return eligibleOrdered[i].Name < eligibleOrdered[j].Name
	})
	sort.SliceStable(unlinkedDrops, func(i, j int) bool {
		if !unlinkedDrops[i].EndsAt.Equal(unlinkedDrops[j].EndsAt) {
			return unlinkedDrops[i].EndsAt.Before(unlinkedDrops[j].EndsAt)
		}
		return unlinkedDrops[i].Name < unlinkedDrops[j].Name
	})

	// Retire completed campaigns, stale channels, or lower-priority targets
	// (e.g. after pinning a different item or returning to Auto) before filling free slots.
	topEligibleID := ""
	if len(eligibleOrdered) > 0 {
		topEligibleID = eligibleOrdered[0].ID
	}
	bw.mu.Lock()
	bw.lastEligible = eligible
	for id, tr := range bw.tracked {
		campaign, eligibleNow := eligible[id]
		streamer := findStreamer(get(), tr.username)
		shouldRetarget := topEligibleID != "" && id != topEligibleID && bw.cfg.StreamerLimit == 1
		valid := eligibleNow && !shouldRetarget && streamer != nil && badgeStreamerValid(streamer, campaign)
		if valid {
			prog := campaignDropProgress(campaign, allInventoryCampaigns)
			streamer.Mu.Lock()
			streamer.BadgeReward = rewards[id]
			streamer.ActiveBadgeDrop = prog
			streamer.Mu.Unlock()
			if prog != nil && prog.RequiredMinutes > 0 {
				bw.log.Info("Drop progress",
					"reward", prog.Reward,
					"streamer", streamer.Username,
					"progress", fmt.Sprintf("%d%% [%d/%d]", prog.ProgressPercent, prog.WatchedMinutes, prog.RequiredMinutes),
				)
			}
			continue
		}
		if tr.added {
			reason := "badge_stream_no_longer_eligible"
			if shouldRetarget {
				reason = "badge_priority_retarget"
			} else if !eligibleNow {
				reason = "badge_campaign_completed_or_expired"
			}
			remove(tr.username, reason)
		} else {
			unmarkBadge(get(), tr.username)
		}
		delete(bw.tracked, id)
	}
	used := len(bw.tracked)
	bw.mu.Unlock()
	limit := bw.cfg.StreamerLimit
	if limit < 1 {
		limit = 1
	} else if limit > 2 {
		limit = 2
	}
	bw.mu.Lock()
	reserved := make(map[string]bool, len(bw.tracked)+len(bw.stalledSkipUntil))
	for _, tr := range bw.tracked {
		reserved[strings.ToLower(tr.username)] = true
	}
	for login, until := range bw.stalledSkipUntil {
		if now.Before(until) {
			reserved[login] = true
		} else {
			delete(bw.stalledSkipUntil, login)
		}
	}
	bw.mu.Unlock()
	emitEligible := func(c badgeCampaign, isLinked bool) {
		if c.IsInGameDrop && len(c.BadgeNames) > 1 {
			for _, tierName := range c.BadgeNames {
				reqMinutes := int(c.WatchTimes[tierName] / time.Minute)
				imgURL := c.BadgeImageURLs[tierName]
				if imgURL == "" {
					imgURL = c.BadgeImageURL
				}
				bw.log.Info("Badge campaign eligible",
					"campaign", c.Name,
					"badge_name", tierName,
					"badges", tierName,
					"game", c.GameName,
					"badge_image_url", imgURL,
					"required_minutes", reqMinutes,
					"ends_at", c.EndsAt.UTC().Format(time.RFC3339),
					"is_drop", true,
					"is_linked", isLinked,
					"account_link_url", c.AccountLinkURL,
				)
			}
			return
		}
		badgeName := c.Name
		if len(c.BadgeNames) > 0 {
			badgeName = c.BadgeNames[0]
		}
		var reqMinutes int
		if rw := rewards[c.ID]; rw.Remaining > 0 {
			reqMinutes = int(rw.Remaining / time.Minute)
		} else if len(c.BadgeNames) > 0 && c.WatchTimes[c.BadgeNames[0]] > 0 {
			reqMinutes = int(c.WatchTimes[c.BadgeNames[0]] / time.Minute)
		}
		bw.log.Info("Badge campaign eligible",
			"campaign", c.Name,
			"badge_name", badgeName,
			"badges", strings.Join(c.BadgeNames, ", "),
			"game", c.GameName,
			"badge_image_url", c.BadgeImageURL,
			"required_minutes", reqMinutes,
			"ends_at", c.EndsAt.UTC().Format(time.RFC3339),
			"is_drop", c.IsInGameDrop,
			"is_linked", isLinked,
			"account_link_url", c.AccountLinkURL,
		)
	}
	for _, c := range eligibleOrdered {
		emitEligible(c, !c.IsInGameDrop || c.IsLinked)
	}
	for _, c := range unlinkedDrops {
		emitEligible(c, false)
	}
	bw.log.Info("🏅 Badge campaigns evaluated", "catalog", len(campaigns), "eligible", len(eligibleOrdered), "active", used, "owned_badges", len(owned), "completed_campaigns", len(completed))
	campaignLookupCache := make(map[string]badgeCampaignLookup)
	for _, c := range eligibleOrdered {
		id := c.ID
		if used >= limit || ctx.Err() != nil {
			break
		}
		bw.mu.Lock()
		_, exists := bw.tracked[id]
		bw.mu.Unlock()
		if exists {
			continue
		}
		candidate, err := bw.findCampaignCandidate(ctx, c, reserved, campaignLookupCache)
		if err != nil {
			bw.log.Warn("Failed to find badge stream", "campaign", c.Name, "category", c.GameSlug, "error", err)
			continue
		}
		if candidate == nil {
			bw.log.Info("No live eligible stream for badge campaign", "campaign", c.Name, "category", c.GameSlug, "all_channels", c.AllChannels)
			continue
		}
		if existing := findStreamer(get(), candidate.stream.Username); existing != nil {
			prog := campaignDropProgress(c, allInventoryCampaigns)
			existing.Mu.Lock()
			existing.IsBadgeWatched = true
			existing.BadgeCampaign = c.Name
			existing.BadgeImageURL = c.BadgeImageURL
			existing.BadgeReward = rewards[id]
			existing.ActiveBadgeDrop = prog
			if len(candidate.campaignIDs) > 0 {
				existing.Stream.CampaignIDs = append([]string(nil), candidate.campaignIDs...)
			}
			existing.Mu.Unlock()
			if prog != nil && prog.RequiredMinutes > 0 {
				bw.log.Info("Drop progress", "reward", prog.Reward, "streamer", existing.Username, "progress", fmt.Sprintf("%d%% [%d/%d]", prog.ProgressPercent, prog.WatchedMinutes, prog.RequiredMinutes))
			}
			initMin := 0
			if prog != nil {
				initMin = prog.WatchedMinutes
			}
			bw.mu.Lock()
			bw.tracked[id] = badgeTracked{username: candidate.stream.Username, added: false, lastProgressMinutes: initMin}
			bw.mu.Unlock()
			reserved[strings.ToLower(candidate.stream.Username)] = true
			used++
			bw.log.Info("🏅 Using existing streamer for badge campaign", "streamer", candidate.stream.Username, "campaign", c.Name, "category", c.GameSlug, "badges", strings.Join(c.BadgeNames, ", "), "badge_image_url", c.BadgeImageURL)
			continue
		}
		s := bw.badgeStreamer(ctx, candidate.stream, c, candidate.campaignIDs)
		s.BadgeReward = rewards[id]
		prog := campaignDropProgress(c, allInventoryCampaigns)
		s.ActiveBadgeDrop = prog
		add(ctx, s)
		if prog != nil && prog.RequiredMinutes > 0 {
			bw.log.Info("Drop progress", "reward", prog.Reward, "streamer", s.Username, "progress", fmt.Sprintf("%d%% [%d/%d]", prog.ProgressPercent, prog.WatchedMinutes, prog.RequiredMinutes))
		}
		initMin := 0
		if prog != nil {
			initMin = prog.WatchedMinutes
		}
		bw.mu.Lock()
		bw.tracked[id] = badgeTracked{username: candidate.stream.Username, added: true, lastProgressMinutes: initMin}
		bw.mu.Unlock()
		reserved[strings.ToLower(candidate.stream.Username)] = true
		used++
		bw.log.Info("🏅 Discovered badge campaign stream", "streamer", candidate.stream.Username, "campaign", c.Name, "category", c.GameSlug, "badges", strings.Join(c.BadgeNames, ", "), "badge_image_url", c.BadgeImageURL)
	}
}

func (bw *BadgeWatcher) badgeStreamer(ctx context.Context, candidate *gql.TopStream, campaign badgeCampaign, campaignIDs []string) *model.Streamer {
	s := model.NewStreamer(candidate.Username)
	s.ChannelID = candidate.ChannelID
	s.DisplayName = candidate.DisplayName
	s.IsOnline = true
	s.OnlineAt = time.Now()
	s.IsBadgeWatched = true
	s.BadgeCampaign = campaign.Name
	s.BadgeImageURL = campaign.BadgeImageURL
	if candidate.GameID != "" || candidate.GameSlug != "" || candidate.GameName != "" {
		gameSlug := candidate.GameSlug
		if gameSlug == "" {
			gameSlug = campaign.GameSlug
		}
		s.Stream.Game = &model.GameInfo{ID: candidate.GameID, Slug: gameSlug, Name: candidate.GameName}
	}
	s.Stream.ViewersCount = candidate.ViewersCount
	if campaignIDs != nil {
		s.Stream.CampaignIDs = append([]string(nil), campaignIDs...)
	} else if ids, err := bw.gql.GetAvailableCampaigns(ctx, candidate.ChannelID); err == nil {
		s.Stream.CampaignIDs = ids
	} else {
		bw.log.Warn("Failed to load campaigns for badge streamer", "streamer", candidate.Username, "error", err)
	}
	settings := *bw.defaults
	if settings.Bet != nil {
		copyBet := *settings.Bet
		settings.Bet = &copyBet
	}
	settings.FollowRaid = false
	settings.ClaimDrops = true
	// A restricted campaign's explicit allow-list is authoritative even when
	// Twitch's generic Drops endpoint does not expose campaign IDs for it.
	settings.DropsOnly = campaign.AllChannels
	settings.Chat = model.ChatNever
	s.Settings = &settings
	return s
}

func (bw *BadgeWatcher) findCampaignCandidate(ctx context.Context, campaign badgeCampaign, reserved map[string]bool, lookupCache map[string]badgeCampaignLookup) (*badgeCandidate, error) {
	if !campaign.AllChannels {
		streams, err := bw.findCampaignStreams(ctx, campaign)
		if err != nil {
			return nil, err
		}
		allowed := make(map[string]bool, len(campaign.Channels))
		for _, name := range campaign.Channels {
			allowed[strings.ToLower(strings.TrimSpace(name))] = true
		}
		candidate := bw.pickCandidate(streams, reserved, allowed, false)
		if candidate == nil {
			return nil, nil
		}
		return &badgeCandidate{stream: candidate}, nil
	}
	lookupFailures := 0
	var lastLookupErr error
	var directoryErrors []error
	for _, dropsOnly := range []bool{true, false} {
		streams, err := bw.gql.GetTopStreamsByCategory(ctx, campaign.GameSlug, badgeCategoryStreamLimit, dropsOnly)
		if err != nil {
			directoryErrors = append(directoryErrors, err)
			continue
		}
		candidate, failures, lookupErr := bw.pickEligibleCampaignCandidate(ctx, campaign, streams, reserved, lookupCache)
		lookupFailures += failures
		if lookupErr != nil {
			lastLookupErr = lookupErr
		}
		if candidate != nil {
			bw.logCampaignCandidateWarnings(campaign, lookupFailures, lastLookupErr, directoryErrors)
			return candidate, nil
		}
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
	}
	bw.logCampaignCandidateWarnings(campaign, lookupFailures, lastLookupErr, directoryErrors)
	if len(directoryErrors) == 2 {
		return nil, fmt.Errorf("both badge category searches failed: %v; %v", directoryErrors[0], directoryErrors[1])
	}
	return nil, nil
}

func (bw *BadgeWatcher) logCampaignCandidateWarnings(campaign badgeCampaign, lookupFailures int, lastLookupErr error, directoryErrors []error) {
	if lookupFailures > 0 {
		bw.log.Warn("Failed to verify some badge campaign candidates", "campaign", campaign.Name, "failures", lookupFailures, "last_error", lastLookupErr)
	}
	if len(directoryErrors) == 1 {
		bw.log.Warn("One badge category search failed", "campaign", campaign.Name, "error", directoryErrors[0])
	}
}

func (bw *BadgeWatcher) pickEligibleCampaignCandidate(ctx context.Context, campaign badgeCampaign, streams []gql.TopStream, reserved map[string]bool, cache map[string]badgeCampaignLookup) (*badgeCandidate, int, error) {
	failures := 0
	var lastErr error
	for i := range streams {
		if ctx.Err() != nil {
			return nil, failures, lastErr
		}
		stream := &streams[i]
		login := strings.ToLower(stream.Username)
		if bw.blacklist[login] || reserved[login] || stream.ChannelID == "" {
			continue
		}
		lookup, found := cache[stream.ChannelID]
		if !found {
			lookup.ids, lookup.err = bw.gql.GetAvailableCampaigns(ctx, stream.ChannelID)
			cache[stream.ChannelID] = lookup
			if lookup.err != nil {
				failures++
				lastErr = lookup.err
			}
		}
		if lookup.err != nil {
			continue
		}
		// For in-game drops, campaign.ID is a real Twitch drop campaign UUID, so
		// require an exact match in lookup.ids so we never watch a streamer who
		// only has a different drop campaign active. For synthetic badge catalog
		// IDs, fall back to verifying that the channel exposes any active Drops.
		matched := len(lookup.ids) > 0
		if campaign.IsInGameDrop && campaign.ID != "" {
			matched = slices.Contains(lookup.ids, campaign.ID)
		}
		if matched {
			return &badgeCandidate{stream: stream, campaignIDs: lookup.ids}, failures, lastErr
		}
	}
	return nil, failures, lastErr
}

func (bw *BadgeWatcher) findCampaignStreams(ctx context.Context, campaign badgeCampaign) ([]gql.TopStream, error) {
	if campaign.AllChannels {
		return bw.gql.GetTopStreamsByCategory(ctx, campaign.GameSlug, badgeCategoryStreamLimit, false)
	}

	streams := make([]gql.TopStream, 0, len(campaign.Channels))
	for _, username := range campaign.Channels {
		displayName := strings.TrimSpace(username)
		login := strings.ToLower(displayName)
		if login == "" || bw.blacklist[login] {
			continue
		}
		stream, err := bw.restrictedCampaignStream(ctx, campaign, login, displayName)
		if err != nil {
			bw.log.Warn("Failed to check restricted badge channel", "campaign", campaign.Name, "streamer", login, "error", err)
			continue
		}
		if stream != nil {
			streams = append(streams, *stream)
		}
	}
	sort.SliceStable(streams, func(i, j int) bool { return streams[i].ViewersCount > streams[j].ViewersCount })
	return streams, nil
}

func (bw *BadgeWatcher) restrictedCampaignStream(ctx context.Context, campaign badgeCampaign, login, displayName string) (*gql.TopStream, error) {
	info, err := bw.gql.GetStreamInfo(ctx, login)
	if err != nil || info == nil {
		return nil, err
	}
	if !badgeCampaignGameMatches(info.Game, campaign) {
		return nil, nil
	}
	channelID, err := bw.channelID(ctx, login)
	if err != nil {
		return nil, err
	}
	stream := &gql.TopStream{Username: login, ChannelID: channelID, DisplayName: displayName, ViewersCount: info.ViewersCount}
	if info.Game != nil {
		stream.GameID = info.Game.ID
		stream.GameName = info.Game.Name
		stream.GameSlug = info.Game.Slug
	}
	return stream, nil
}

func (bw *BadgeWatcher) channelID(ctx context.Context, login string) (string, error) {
	bw.channelIDsMu.Lock()
	channelID := bw.channelIDs[login]
	bw.channelIDsMu.Unlock()
	if channelID != "" {
		return channelID, nil
	}
	channelID, err := bw.gql.GetUserID(ctx, login)
	if err != nil {
		return "", err
	}
	bw.channelIDsMu.Lock()
	bw.channelIDs[login] = channelID
	bw.channelIDsMu.Unlock()
	return channelID, nil
}

func (bw *BadgeWatcher) pickCandidate(streams []gql.TopStream, reserved, allowed map[string]bool, allChannels bool) *gql.TopStream {
	for i := range streams {
		login := strings.ToLower(streams[i].Username)
		if !bw.blacklist[login] && !reserved[login] && (allChannels || allowed[login]) {
			return &streams[i]
		}
	}
	return nil
}

func (bw *BadgeWatcher) loadCampaigns(ctx context.Context) ([]badgeCampaign, error) {
	if len(bw.campaigns) > 0 && time.Since(bw.catalogLoadedAt) < badgeCatalogCacheTTL {
		return bw.campaigns, nil
	}
	campaigns, err := loadBadgeCampaigns(ctx, bw.httpClient, bw.cfg.DropsCatalogURL, bw.cfg.BadgesCatalogURL, time.Now(), func(campaign, reward, reason string, matches []string) {
		fields := []any{"campaign", campaign, "reason", reason}
		if reward != "" {
			fields = append(fields, "reward", reward)
		}
		if len(matches) > 0 {
			fields = append(fields, "badge_matches", strings.Join(matches, ", "))
		}
		bw.log.Warn("Badge catalog watch reward skipped", fields...)
	})
	if err != nil {
		if len(bw.campaigns) > 0 {
			bw.log.Warn("Badge catalogs unavailable; using cached campaigns", "error", err, "cache_age", time.Since(bw.catalogLoadedAt).Round(time.Second))
			return bw.campaigns, nil
		}
		return nil, err
	}
	bw.campaigns, bw.catalogLoadedAt = campaigns, time.Now()
	return campaigns, nil
}

func badgeStreamerValid(s *model.Streamer, campaign badgeCampaign) bool {
	s.Mu.RLock()
	defer s.Mu.RUnlock()
	if !s.IsOnline || s.Stream == nil {
		return false
	}
	if !campaign.AllChannels && !badgeCampaignAllowsChannel(campaign, s.Username) {
		return false
	}
	// CampaignIDs are Twitch UUIDs, whereas campaign.ID comes from the
	// external catalog. An all-channel streamer is valid when Twitch reports
	// at least one active Drops campaign on it.
	if campaign.AllChannels && len(s.Stream.CampaignIDs) == 0 {
		return false
	}
	return badgeCampaignGameMatches(s.Stream.Game, campaign)
}

func badgeCampaignAllowsChannel(campaign badgeCampaign, username string) bool {
	for _, allowed := range campaign.Channels {
		if strings.EqualFold(strings.TrimSpace(allowed), username) {
			return true
		}
	}
	return false
}

func badgeCampaignGameMatches(game *model.GameInfo, campaign badgeCampaign) bool {
	if strings.EqualFold(campaign.GameSlug, crossCategoryBadgeCampaignSlug) {
		return true
	}
	if game == nil {
		return false
	}
	return strings.EqualFold(game.Slug, campaign.GameSlug) || strings.EqualFold(slugify(game.Name), campaign.GameSlug)
}

func findStreamer(streamers []*model.Streamer, username string) *model.Streamer {
	for _, s := range streamers {
		if strings.EqualFold(s.Username, username) {
			return s
		}
	}
	return nil
}
func unmarkBadge(streamers []*model.Streamer, username string) {
	if s := findStreamer(streamers, username); s != nil {
		s.Mu.Lock()
		s.IsBadgeWatched = false
		s.BadgeCampaign = ""
		s.BadgeReward = model.RewardDeadline{}
		s.Mu.Unlock()
	}
}
func (bw *BadgeWatcher) cleanup(remove func(string, string), get func() []*model.Streamer) {
	bw.mu.Lock()
	defer bw.mu.Unlock()
	for id, tr := range bw.tracked {
		if tr.added {
			remove(tr.username, "badge_watcher_shutdown")
		} else {
			unmarkBadge(get(), tr.username)
		}
		delete(bw.tracked, id)
	}
}
