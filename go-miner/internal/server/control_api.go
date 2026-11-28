package server

import (
	"net/http"
	"strings"
)

// ControlHandlers holds optional callbacks invoked by the loopback control API
// (used by the Lootly Android foreground service and UI).
type ControlHandlers struct {
	OnPause         func(reason string)
	OnResume        func()
	OnRefreshBadges func()
	OnPinBadge      func(campaign string)
	OnSetFilters    func(badges, drops bool)
}

// SetControlHandlers registers callbacks for /api/control/* endpoints.
func (s *AnalyticsServer) SetControlHandlers(h ControlHandlers) {
	s.mu.Lock()
	s.control = h
	s.mu.Unlock()
}

func (s *AnalyticsServer) handleControlPause(w http.ResponseWriter, r *http.Request) {
	reason := strings.TrimSpace(r.URL.Query().Get("reason"))
	if reason == "" {
		reason = "user_requested"
	}
	s.mu.RLock()
	fn := s.control.OnPause
	s.mu.RUnlock()
	if fn != nil {
		fn(reason)
	}
	if s.log != nil {
		s.log.Info("Watch heartbeats paused", "reason", reason)
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "paused",
		"reason": reason,
	})
}

func (s *AnalyticsServer) handleControlResume(w http.ResponseWriter, _ *http.Request) {
	s.mu.RLock()
	fn := s.control.OnResume
	s.mu.RUnlock()
	if fn != nil {
		fn()
	}
	if s.log != nil {
		s.log.Info("Watch heartbeats resumed")
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "resumed",
	})
}

func (s *AnalyticsServer) handleControlRefreshBadges(w http.ResponseWriter, _ *http.Request) {
	s.mu.RLock()
	fn := s.control.OnRefreshBadges
	s.mu.RUnlock()
	if fn != nil {
		fn()
	}
	if s.log != nil {
		s.log.Info("Badge catalog refresh requested")
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "refreshing",
	})
}

func (s *AnalyticsServer) handleControlPinBadge(w http.ResponseWriter, r *http.Request) {
	campaign := strings.TrimSpace(r.URL.Query().Get("campaign"))
	s.mu.RLock()
	fn := s.control.OnPinBadge
	s.mu.RUnlock()
	if fn != nil {
		fn(campaign)
	}
	mode := "auto"
	if campaign != "" {
		mode = "pinned"
	}
	if s.log != nil {
		s.log.Info("Badge target updated", "mode", mode, "campaign", campaign)
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status":          "ok",
		"mode":            mode,
		"pinned_campaign": campaign,
	})
}

func (s *AnalyticsServer) handleControlSetFilters(w http.ResponseWriter, r *http.Request) {
	badges := !strings.EqualFold(strings.TrimSpace(r.URL.Query().Get("badges")), "false")
	drops := !strings.EqualFold(strings.TrimSpace(r.URL.Query().Get("drops")), "false")
	s.mu.RLock()
	fn := s.control.OnSetFilters
	refreshFn := s.control.OnRefreshBadges
	s.mu.RUnlock()
	if fn != nil {
		fn(badges, drops)
	}
	if refreshFn != nil {
		refreshFn()
	}
	if s.log != nil {
		s.log.Info("Mining filters updated", "badges", badges, "drops", drops)
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"status": "ok",
		"badges": badges,
		"drops":  drops,
	})
}
