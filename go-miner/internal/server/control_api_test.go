package server

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/Guliveer/twitch-miner-go/internal/logger"
)

func TestControlEndpoints(t *testing.T) {
	log, err := logger.Setup(logger.Config{Level: slog.LevelError})
	if err != nil {
		t.Fatalf("logger setup: %v", err)
	}
	srv := NewAnalyticsServer("127.0.0.1:0", log, nil, "test-secret")

	var (
		pausedReason string
		resumed      bool
		refreshed    bool
		pinned       string
	)
	srv.SetControlHandlers(ControlHandlers{
		OnPause:         func(reason string) { pausedReason = reason },
		OnResume:        func() { resumed = true },
		OnRefreshBadges: func() { refreshed = true },
		OnPinBadge:      func(campaign string) { pinned = campaign },
	})

	doPost := func(path string) *httptest.ResponseRecorder {
		req := httptest.NewRequest(http.MethodPost, path, nil)
		req.Header.Set("X-API-Key", "test-secret")
		rec := httptest.NewRecorder()
		srv.srv.Handler.ServeHTTP(rec, req)
		return rec
	}

	if rec := doPost("/api/control/pause?reason=twitch_app_foreground"); rec.Code != http.StatusOK {
		t.Fatalf("pause status = %d, want 200", rec.Code)
	}
	if pausedReason != "twitch_app_foreground" {
		t.Fatalf("pausedReason = %q, want twitch_app_foreground", pausedReason)
	}

	if rec := doPost("/api/control/resume"); rec.Code != http.StatusOK {
		t.Fatalf("resume status = %d, want 200", rec.Code)
	}
	if !resumed {
		t.Fatal("expected OnResume to be called")
	}

	if rec := doPost("/api/control/refresh-badges"); rec.Code != http.StatusOK {
		t.Fatalf("refresh-badges status = %d, want 200", rec.Code)
	}
	if !refreshed {
		t.Fatal("expected OnRefreshBadges to be called")
	}

	rec := doPost("/api/control/pin-badge?campaign=PAYDAY+3")
	if rec.Code != http.StatusOK {
		t.Fatalf("pin-badge status = %d, want 200", rec.Code)
	}
	if pinned != "PAYDAY 3" {
		t.Fatalf("pinned = %q, want PAYDAY 3", pinned)
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode pin response: %v", err)
	}
	if body["mode"] != "pinned" {
		t.Fatalf("mode = %v, want pinned", body["mode"])
	}

	// Empty campaign resets to auto.
	rec = doPost("/api/control/pin-badge?campaign=")
	if rec.Code != http.StatusOK || pinned != "" {
		t.Fatalf("reset pin failed: code=%d pinned=%q", rec.Code, pinned)
	}
}
