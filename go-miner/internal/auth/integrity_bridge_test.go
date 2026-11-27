package auth

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func TestFetchIntegrityFromBridge(t *testing.T) {
	var gotSecret string
	var gotForce string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotSecret = r.Header.Get("X-Bridge-Secret")
		gotForce = r.URL.Query().Get("force")
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"token":"bridge-tok-123","expiration":9999999999999,"device_id":"dev-abc","client_session_id":"sess-xyz","user_agent":"TestUA"}`))
	}))
	defer ts.Close()

	t.Setenv("TWITCH_INTEGRITY_BRIDGE_URL", ts.URL+"/integrity")
	t.Setenv("TWITCH_INTEGRITY_BRIDGE_SECRET", "s3cr3t")

	a := &Authenticator{
		httpClient: &http.Client{Timeout: 5 * time.Second},
	}

	resp, err := a.fetchIntegrityFromBridge(context.Background(), true)
	if err != nil {
		t.Fatalf("fetchIntegrityFromBridge error: %v", err)
	}
	if gotSecret != "s3cr3t" {
		t.Fatalf("X-Bridge-Secret = %q, want s3cr3t", gotSecret)
	}
	if gotForce != "1" {
		t.Fatalf("force query = %q, want 1", gotForce)
	}
	if resp.Token != "bridge-tok-123" || a.integrityToken != "bridge-tok-123" {
		t.Fatalf("unexpected token: %+v", resp)
	}
	if a.deviceID != "dev-abc" || a.clientSession != "sess-xyz" {
		t.Fatalf("unexpected device/session: %q / %q", a.deviceID, a.clientSession)
	}
}
