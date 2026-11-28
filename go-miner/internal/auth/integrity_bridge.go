package auth

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
)

// IntegrityBridgeResponse is the JSON payload returned by the Android
// headless WebView integrity bridge (GET TWITCH_INTEGRITY_BRIDGE_URL).
type IntegrityBridgeResponse struct {
	Token           string `json:"token"`
	Expiration      int64  `json:"expiration"`
	DeviceID        string `json:"device_id"`
	ClientID        string `json:"client_id"`
	ClientVersion   string `json:"client_version"`
	ClientSessionID string `json:"client_session_id"`
	UserAgent       string `json:"user_agent"`
}

// ForceRefreshIntegrityToken invalidates any cached integrity token and
// requests a fresh token from the Android WebView bridge (?force=1).
func (a *Authenticator) ForceRefreshIntegrityToken(ctx context.Context) (string, error) {
	a.mu.Lock()
	defer a.mu.Unlock()
	a.integrityToken = ""
	a.integrityExpire = 0
	resp, err := a.fetchIntegrityFromBridge(ctx, true)
	if err != nil {
		return "", err
	}
	if resp == nil {
		return "", nil
	}
	return a.integrityToken, nil
}

// fetchIntegrityFromBridge queries the local loopback Android WebView integrity
// bridge when TWITCH_INTEGRITY_BRIDGE_URL is configured.
// Caller must hold a.mu.
func (a *Authenticator) fetchIntegrityFromBridge(ctx context.Context, force bool) (*IntegrityBridgeResponse, error) {
	bridgeURL := strings.TrimSpace(os.Getenv("TWITCH_INTEGRITY_BRIDGE_URL"))
	if bridgeURL == "" {
		return nil, nil
	}
	if force {
		if strings.Contains(bridgeURL, "?") {
			bridgeURL += "&force=1"
		} else {
			bridgeURL += "?force=1"
		}
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, bridgeURL, nil)
	if err != nil {
		return nil, fmt.Errorf("creating integrity bridge request: %w", err)
	}
	if secret := os.Getenv("TWITCH_INTEGRITY_BRIDGE_SECRET"); secret != "" {
		req.Header.Set("X-Bridge-Secret", secret)
	}

	resp, err := a.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("calling integrity bridge: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
	if err != nil {
		return nil, fmt.Errorf("reading integrity bridge response: %w", err)
	}
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("integrity bridge returned HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(body)))
	}

	var result IntegrityBridgeResponse
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parsing integrity bridge response: %w", err)
	}
	if result.Token == "" {
		return nil, fmt.Errorf("integrity bridge returned empty token")
	}

	a.integrityToken = result.Token
	a.integrityExpire = result.Expiration
	if result.DeviceID != "" {
		a.deviceID = result.DeviceID
	}
	if result.ClientSessionID != "" {
		a.clientSession = result.ClientSessionID
	}
	return &result, nil
}
