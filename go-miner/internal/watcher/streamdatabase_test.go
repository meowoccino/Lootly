package watcher

import (
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
}
