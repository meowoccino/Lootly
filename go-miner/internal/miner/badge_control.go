package miner

// RefreshBadges triggers an immediate BadgeWatcher re-evaluation.
func (m *Miner) RefreshBadges() {
	if m.badgeWatcher != nil {
		m.badgeWatcher.TriggerRefresh()
	}
}

// PinBadgeCampaign pins a specific badge or linked drop campaign (empty resets to Auto)
// and triggers an immediate re-evaluation.
func (m *Miner) PinBadgeCampaign(campaign string) {
	if m.badgeWatcher != nil {
		m.badgeWatcher.SetPinnedCampaign(campaign)
		m.badgeWatcher.TriggerRefresh()
	}
}

// SetBadgeFilters updates whether global badges and/or in-game drops are enabled
// and triggers an immediate re-evaluation (retiring any filtered-out streamers).
func (m *Miner) SetBadgeFilters(badges, drops bool) {
	if m.badgeWatcher != nil {
		m.badgeWatcher.SetFilters(badges, drops)
		m.badgeWatcher.TriggerRefresh()
	}
}
