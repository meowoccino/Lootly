//go:build android

// Package androidnet configures pure-Go DNS resolution and Conscrypt CA
// certificate discovery for Android ARM64 builds (CGO_ENABLED=0).
package androidnet

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

var (
	dohEndpoints = []string{
		"https://1.1.1.1/dns-query",
		"https://1.0.0.1/dns-query",
	}
	fallbackDNSServers = []string{
		"1.1.1.1:53",
		"8.8.8.8:53",
		"9.9.9.9:53",
	}
	conscryptCertDirs = []string{
		"/apex/com.android.conscrypt/cacerts",
		"/system/etc/security/cacerts",
	}
)

func init() {
	configureCertDir()
	configureResolver()
}

// configureCertDir sets SSL_CERT_DIR for Android 14+ Conscrypt CA certificates
// when not already configured by the caller.
func configureCertDir() {
	if os.Getenv("SSL_CERT_DIR") != "" {
		return
	}
	var existing []string
	for _, dir := range conscryptCertDirs {
		if info, err := os.Stat(dir); err == nil && info.IsDir() {
			existing = append(existing, dir)
		}
	}
	if len(existing) > 0 {
		_ = os.Setenv("SSL_CERT_DIR", strings.Join(existing, ":"))
	}
}

func configureResolver() {
	// Honor ANDROID_DNS_SERVERS (comma-separated IPs) if provided by the Android host.
	servers := make([]string, 0, len(fallbackDNSServers)+4)
	if envDNS := strings.TrimSpace(os.Getenv("ANDROID_DNS_SERVERS")); envDNS != "" {
		for _, entry := range strings.Split(envDNS, ",") {
			entry = strings.TrimSpace(entry)
			if entry == "" {
				continue
			}
			if _, _, err := net.SplitHostPort(entry); err != nil {
				entry = net.JoinHostPort(entry, "53")
			}
			servers = append(servers, entry)
		}
	}
	servers = append(servers, fallbackDNSServers...)

	// Direct dialer used by DoH and fallback UDP/TCP DNS queries.
	directDialer := &net.Dialer{Timeout: 5 * time.Second}

	dohClient := &http.Client{
		Timeout: 5 * time.Second,
		Transport: &http.Transport{
			DialContext: directDialer.DialContext,
		},
	}

	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			var lastErr error
			for _, server := range servers {
				conn, err := directDialer.DialContext(ctx, network, server)
				if err == nil {
					return conn, nil
				}
				lastErr = err
			}
			return nil, lastErr
		},
	}

	// Wrap http.DefaultTransport so outbound HTTP requests resolve via DoH first
	// (bypassing local VPN firewalls like AdGuard blocking spade.twitch.tv or UDP/53)
	// and fall back to the system/public UDP DNS resolver.
	if dt, ok := http.DefaultTransport.(*http.Transport); ok {
		dt.DialContext = NewDialContext(dohClient, directDialer)
	}
}

type dohResponse struct {
	Status int `json:"Status"`
	Answer []struct {
		Type int    `json:"type"`
		Data string `json:"data"`
	} `json:"Answer"`
}

// ResolveDoH resolves a hostname to IP addresses using Cloudflare DNS-over-HTTPS.
func ResolveDoH(ctx context.Context, client *http.Client, host string) ([]string, error) {
	if ip := net.ParseIP(host); ip != nil {
		return []string{host}, nil
	}
	var lastErr error
	for _, endpoint := range dohEndpoints {
		u := fmt.Sprintf("%s?name=%s&type=A", endpoint, url.QueryEscape(host))
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
		if err != nil {
			lastErr = err
			continue
		}
		req.Header.Set("Accept", "application/dns-json")
		resp, err := client.Do(req)
		if err != nil {
			lastErr = err
			continue
		}
		var parsed dohResponse
		decErr := json.NewDecoder(resp.Body).Decode(&parsed)
		_ = resp.Body.Close()
		if decErr != nil {
			lastErr = decErr
			continue
		}
		if parsed.Status != 0 {
			lastErr = fmt.Errorf("DoH status %d for %s", parsed.Status, host)
			continue
		}
		var ips []string
		for _, ans := range parsed.Answer {
			if ans.Type == 1 || ans.Type == 28 {
				if ip := net.ParseIP(ans.Data); ip != nil {
					ips = append(ips, ip.String())
				}
			}
		}
		if len(ips) > 0 {
			return ips, nil
		}
		lastErr = fmt.Errorf("DoH returned no A/AAAA records for %s", host)
	}
	return nil, lastErr
}

// NewDialContext returns a DialContext function that resolves hosts via DoH first
// and falls back to net.DefaultResolver (public UDP DNS).
func NewDialContext(dohClient *http.Client, dialer *net.Dialer) func(ctx context.Context, network, addr string) (net.Conn, error) {
	return func(ctx context.Context, network, addr string) (net.Conn, error) {
		host, port, err := net.SplitHostPort(addr)
		if err != nil {
			return dialer.DialContext(ctx, network, addr)
		}
		if host == "localhost" || net.ParseIP(host) != nil {
			return dialer.DialContext(ctx, network, addr)
		}
		if ips, err := ResolveDoH(ctx, dohClient, host); err == nil && len(ips) > 0 {
			for _, ip := range ips {
				conn, dialErr := dialer.DialContext(ctx, network, net.JoinHostPort(ip, port))
				if dialErr == nil {
					return conn, nil
				}
			}
		}
		return dialer.DialContext(ctx, network, addr)
	}
}
