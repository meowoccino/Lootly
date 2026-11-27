//go:build ios || android

package tray

// Links holds the local URLs the tray menu opens (unused on mobile).
type Links struct {
	DashboardURL    string
	ConfigEditorURL string
}

// Options control tray behaviour (unused on mobile).
type Options struct {
	Links   Links
	Title   string
	ExePath string
	OnExit  func()
	OnLog   func(format string, args ...any)
}

// Available always returns false on mobile platforms.
func Available() bool { return false }

// Run is a no-op on mobile platforms.
func Run(_ Options) {}

// Quit is a no-op on mobile platforms.
func Quit() {}
