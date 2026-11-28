package watcher

import (
	"encoding/json"
	"testing"
	"time"
)

func TestIsWatchToEarnBadge_StrictFilter(t *testing.T) {
	now := time.Date(2026, 5, 10, 12, 0, 0, 0, time.UTC)
	start := now.Add(-24 * time.Hour)
	end := now.Add(72 * time.Hour)
	expired := now.Add(-1 * time.Hour)
	future := now.Add(24 * time.Hour)

	tests := []struct {
		name  string
		badge StreamDatabaseBadge
		want  bool
	}{
		{
			name: "rejects Pokemon Pikachu evolving badge even with cost=free",
			badge: StreamDatabaseBadge{
				ID:          "pokemon-pikachu",
				Title:       "Pikachu",
				Description: "This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects Pokemon Ivysaur paid evolving badge",
			badge: StreamDatabaseBadge{
				ID:          "pokemon-ivysaur",
				Title:       "Ivysaur",
				Description: "This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign.",
				Cost:        "paid",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects Pokemon Venusaur paid evolving badge",
			badge: StreamDatabaseBadge{
				ID:          "pokemon-venusaur",
				Title:       "Venusaur",
				Description: "This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign.",
				Cost:        "paid",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects subscribing badge",
			badge: StreamDatabaseBadge{
				Title:       "Sub Badge",
				Description: "Watch for 30 minutes and subscribe to a participating channel.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects gifting/cheering/bits/clip/chat/securing all",
			badge: StreamDatabaseBadge{
				Title:       "Meta Badge",
				Description: "Earned by securing all badges in the collection.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects clip badge",
			badge: StreamDatabaseBadge{
				Title:       "Clipper",
				Description: "Watch 15 minutes and share a clip to unlock.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects chat badge",
			badge: StreamDatabaseBadge{
				Title:       "Chatter",
				Description: "Watch 10 minutes and send a chat message.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "rejects removed or not added or expired or future",
			badge: StreamDatabaseBadge{
				Title:       "Expired Watch",
				Description: "Watch 30 minutes of any eligible stream.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &expired,
			},
			want: false,
		},
		{
			name: "rejects future badge",
			badge: StreamDatabaseBadge{
				Title:       "Future Watch",
				Description: "Watch 30 minutes of any eligible stream.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &future,
				EndAt:       &end,
			},
			want: false,
		},
		{
			name: "accepts Payday 3 Clover watch badge",
			badge: StreamDatabaseBadge{
				ID:          "payday3-clover",
				Title:       "Clover",
				Description: "Watch 30 minutes of any PAYDAY 3 stream to earn the Clover badge.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: true,
		},
		{
			name: "accepts Payday 3 Pearl watch badge",
			badge: StreamDatabaseBadge{
				ID:          "payday3-pearl",
				Title:       "Pearl",
				Description: "Watch 60 minutes of any PAYDAY 3 stream to earn the Pearl badge.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: true,
		},
		{
			name: "accepts Payday 3 Joy watch badge",
			badge: StreamDatabaseBadge{
				ID:          "payday3-joy",
				Title:       "Joy",
				Description: "Watch 90 minutes of any PAYDAY 3 stream to earn the Joy badge.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: true,
		},
		{
			name: "accepts GTAVI Chains watch badge",
			badge: StreamDatabaseBadge{
				ID:          "gtavi-chains",
				Title:       "GTAVI Chains",
				Description: "Earned by watching 60 minutes per day of Grand Theft Auto VI streams.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: true,
		},
		{
			name: "accepts RuneScape Bones watch badge",
			badge: StreamDatabaseBadge{
				ID:          "runescape-bones",
				Title:       "RuneScape Bones",
				Description: "Watch 1 hour of RuneScape streams to earn the Bones badge.",
				Cost:        "free",
				Added:       true,
				Removed:     false,
				StartAt:     &start,
				EndAt:       &end,
			},
			want: true,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			if got := IsWatchToEarnBadge(tc.badge, now); got != tc.want {
				t.Fatalf("IsWatchToEarnBadge(%q) = %v, want %v", tc.badge.Title, got, tc.want)
			}
		})
	}
}

func TestParseStreamDatabaseHTML(t *testing.T) {
	html := `<html><head><script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"badges":[{"id":"pika","title":"Pikachu","description":"This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign.","cost":"free","added":true,"removed":false},{"id":"clover","title":"Clover","description":"Watch 30 minutes of PAYDAY 3.","cost":"free","added":true,"removed":false}]}}}</script></head></html>`
	badges, err := ParseStreamDatabaseHTML([]byte(html))
	if err != nil {
		t.Fatalf("ParseStreamDatabaseHTML error: %v", err)
	}
	if len(badges) != 2 {
		t.Fatalf("got %d badges, want 2", len(badges))
	}
	now := time.Now()
	var eligible []string
	for _, b := range badges {
		if IsWatchToEarnBadge(b, now) {
			eligible = append(eligible, b.Title)
		}
	}
	if len(eligible) != 1 || eligible[0] != "Clover" {
		t.Fatalf("eligible = %v, want [Clover]", eligible)
	}
}

func TestSortEligibleBadgesAndDailyCap(t *testing.T) {
	now := time.Date(2026, 5, 10, 12, 0, 0, 0, time.UTC)
	entries := []EligibleBadgeEntry{
		{CampaignID: "c2", BadgeTitle: "Later", EndsAt: now.Add(48 * time.Hour), RequiredMinutes: 30},
		{CampaignID: "c1", BadgeTitle: "SoonerLong", EndsAt: now.Add(24 * time.Hour), RequiredMinutes: 60},
		{CampaignID: "c1", BadgeTitle: "SoonerShort", EndsAt: now.Add(24 * time.Hour), RequiredMinutes: 30},
	}
	SortEligibleBadges(entries, "")
	if entries[0].BadgeTitle != "SoonerShort" || entries[1].BadgeTitle != "SoonerLong" || entries[2].BadgeTitle != "Later" {
		t.Fatalf("unexpected auto order: %+v", entries)
	}

	SortEligibleBadges(entries, "Later")
	if entries[0].BadgeTitle != "Later" {
		t.Fatalf("expected pinned badge first, got %s", entries[0].BadgeTitle)
	}

	tracker := NewDailyCapTracker()
	tracker.MarkCapped("gtavi", now)
	if !tracker.IsCapped("gtavi", now.Add(2*time.Hour)) {
		t.Fatal("expected campaign to be capped on same UTC day")
	}
	if tracker.IsCapped("gtavi", now.Add(24*time.Hour)) {
		t.Fatal("expected campaign cap to reset on next UTC day")
	}
}

func TestCalculateStepDurations(t *testing.T) {
	steps := CalculateStepDurations([]time.Duration{30 * time.Minute, 60 * time.Minute, 90 * time.Minute}, false)
	if len(steps) != 3 || steps[0] != 30*time.Minute || steps[1] != 30*time.Minute || steps[2] != 30*time.Minute {
		t.Fatalf("unexpected cumulative steps: %v", steps)
	}
	daily := CalculateStepDurations([]time.Duration{60 * time.Minute, 60 * time.Minute}, true)
	if len(daily) != 2 || daily[0] != 60*time.Minute || daily[1] != 60*time.Minute {
		t.Fatalf("unexpected daily steps: %v", daily)
	}
}

func TestBlastoiseRejectedAcrossAllSources(t *testing.T) {
	now := time.Date(2026, 5, 10, 12, 0, 0, 0, time.UTC)
	start := now.Add(-24 * time.Hour)
	end := now.Add(72 * time.Hour)
	desc := "This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign."

	for _, cost := range []string{"paid", "free"} {
		b := StreamDatabaseBadge{
			ID:          "pokemon-blastoise",
			Title:       "Blastoise",
			Description: desc,
			Cost:        cost,
			Added:       true,
			Removed:     false,
			StartAt:     &start,
			EndAt:       &end,
		}
		if IsWatchToEarnBadge(b, now) {
			t.Fatalf("IsWatchToEarnBadge accepted Blastoise with cost=%q", cost)
		}
	}

	if !badgeRequiresPayment(desc) {
		t.Fatal("badgeRequiresPayment must reject Blastoise evolving description")
	}

	matches, _ := badgeTitlesForWatchReward("Blastoise", "Pokémon", "Pokémon First Partners Collection", []badgeDefinition{
		{Title: "Blastoise", Description: desc},
	})
	if len(matches) != 0 {
		t.Fatalf("badgeTitlesForWatchReward accepted Blastoise: %v", matches)
	}

	// Exact live zarmstrong/twitch-drops.json + twitch-badges.json Pikachu case:
	// Campaign "Pokemon Evolutions" with drop "Pikachu Badge" must NOT match either
	// "Pikachu" (evolving) or "2026 Worlds costumed Pikachu" (World Championships).
	liveBadges := []badgeDefinition{
		{
			Title:       "2026 Worlds costumed Pikachu",
			Description: "This badge was earned during the 2026 Pokémon World Championships.",
		},
		{
			Title:       "Pikachu",
			Description: "This badge was earned by evolving a badge from the Pokémon First Partners Collection campaign.",
		},
	}
	pikaMatches, _ := badgeTitlesForWatchReward("Pikachu Badge", "Pokémon", "Pokemon Evolutions", liveBadges)
	if len(pikaMatches) != 0 {
		t.Fatalf("badgeTitlesForWatchReward accepted live Pikachu entry: %v", pikaMatches)
	}
}

func TestConnectedInGameDropsFilteringAndPriority(t *testing.T) {
	now := time.Date(2026, 5, 10, 12, 0, 0, 0, time.UTC)

	makeInv := func(id, name, status string, connected bool, endOffset time.Duration, reqMin, watchedMin int, claimed bool, img string) badgeInventoryCampaign {
		c := badgeInventoryCampaign{
			ID:     id,
			Name:   name,
			Status: status,
			EndAt:  now.Add(endOffset),
		}
		c.Game.Name = "Apex Legends"
		c.Game.Slug = "apex-legends"
		c.Self = &struct {
			IsAccountConnected bool `json:"isAccountConnected"`
		}{IsAccountConnected: connected}
		d := badgeInventoryDrop{
			Name:            name + " Drop",
			RequiredMinutes: reqMin,
			Self: &struct {
				Minutes  int    `json:"currentMinutesWatched"`
				Claimed  bool   `json:"isClaimed"`
				Instance string `json:"dropInstanceID"`
				Eligible bool   `json:"hasPreconditionsMet"`
			}{Minutes: watchedMin, Claimed: claimed, Eligible: true},
		}
		d.Benefits = []struct {
			Benefit struct {
				Name          string `json:"name"`
				ImageAssetURL string `json:"imageAssetURL"`
			} `json:"benefit"`
		}{{Benefit: struct {
			Name          string `json:"name"`
			ImageAssetURL string `json:"imageAssetURL"`
		}{Name: name + " Reward", ImageAssetURL: img}}}
		c.Drops = []badgeInventoryDrop{d}
		return c
	}

	inventory := []badgeInventoryCampaign{
		makeInv("linked-active", "Apex Weapon Charm", "ACTIVE", true, 24*time.Hour, 60, 15, false, "https://cdn.example/charm.png"),
		makeInv("unlinked-active", "Unlinked Skin", "ACTIVE", false, 12*time.Hour, 30, 0, false, "https://cdn.example/unlinked.png"),
		makeInv("linked-expired-status", "Inactive Drop", "EXPIRED", true, 24*time.Hour, 30, 0, false, "https://cdn.example/inactive.png"),
		makeInv("linked-claimed", "Already Claimed", "ACTIVE", true, 24*time.Hour, 30, 30, true, "https://cdn.example/claimed.png"),
	}

	got := connectedInGameDropCampaigns(inventory, now)
	if len(got) != 1 {
		t.Fatalf("connectedInGameDropCampaigns returned %d campaigns, want 1: %+v", len(got), got)
	}
	if got[0].ID != "linked-active" || !got[0].IsInGameDrop {
		t.Fatalf("unexpected campaign: %+v", got[0])
	}
	if got[0].BadgeImageURL != "https://cdn.example/charm.png" {
		t.Fatalf("BadgeImageURL = %q, want charm.png", got[0].BadgeImageURL)
	}
	if got[0].WatchTimes["Apex Weapon Charm Reward"] != 45*time.Minute {
		t.Fatalf("remaining WatchTime = %v, want 45m", got[0].WatchTimes["Apex Weapon Charm Reward"])
	}

	// Verify ViewerDropsDashboard parsing (active vs connected) and merging with inventory.
	rawDash := []json.RawMessage{
		json.RawMessage(`{"id":"dash-connected","name":"Rust Twitch Drops","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","imageURL":"https://cdn.example/rust.png","game":{"slug":"rust","displayName":"Rust"},"self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"dash-unlinked","name":"Unlinked Game Drops","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","game":{"slug":"other","displayName":"Other"},"self":{"isAccountConnected":false}}`),
	}
	dashCampaigns, activeCount, connectedCount := parseDashboardDropCampaigns(rawDash)
	if activeCount != 2 || connectedCount != 1 {
		t.Fatalf("parseDashboardDropCampaigns = (active=%d, connected=%d), want (2, 1)", activeCount, connectedCount)
	}
	merged := mergeInventoryAndDashboardCampaigns(inventory, dashCampaigns)
	mergedEligible := connectedInGameDropCampaigns(merged, now)
	if len(mergedEligible) != 2 {
		t.Fatalf("merged connectedInGameDropCampaigns = %d, want 2: %+v", len(mergedEligible), mergedEligible)
	}
	allDrops := allActiveInGameDropCampaigns(merged, now)
	if len(allDrops) != 4 {
		t.Fatalf("allActiveInGameDropCampaigns = %d, want 4 (2 linked + 2 unlinked)", len(allDrops))
	}
	var linkedCnt, unlinkedCnt int
	for _, d := range allDrops {
		if d.AccountLinkURL == "" {
			t.Fatalf("expected non-empty AccountLinkURL for drop %q", d.Name)
		}
		if d.IsLinked {
			linkedCnt++
		} else {
			unlinkedCnt++
		}
	}
	if linkedCnt != 2 || unlinkedCnt != 2 {
		t.Fatalf("linked=%d unlinked=%d, want 2 and 2", linkedCnt, unlinkedCnt)
	}

	// Verify sub-only / paid drop campaigns are strictly rejected.
	paidRaw := []json.RawMessage{
		json.RawMessage(`{"id":"sub-1","name":"Sub Drop","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"sub-2","name":"Warrior Jar Alexander","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"sub-3","name":"Horde and Alliance Badges","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"sub-4","name":"DOOM: The Dark Ages Badge","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"sub-5","name":"Gallica Badge","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","self":{"isAccountConnected":true}}`),
		json.RawMessage(`{"id":"free-boxart","name":"Free Watch Drop","status":"ACTIVE","endAt":"2026-05-12T12:00:00Z","game":{"slug":"rust","displayName":"Rust","boxArtURL":"https://cdn.example/box-{width}x{height}.jpg"},"self":{"isAccountConnected":true}}`),
	}
	paidParsed, _, _ := parseDashboardDropCampaigns(paidRaw)
	filtered := allActiveInGameDropCampaigns(paidParsed, now)
	if len(filtered) != 1 || filtered[0].ID != "free-boxart" {
		t.Fatalf("expected only free-boxart, got %+v", filtered)
	}
	if filtered[0].BadgeImageURL != "https://cdn.example/box-144x192.jpg" {
		t.Fatalf("expected boxArtURL fallback, got %q", filtered[0].BadgeImageURL)
	}
}
