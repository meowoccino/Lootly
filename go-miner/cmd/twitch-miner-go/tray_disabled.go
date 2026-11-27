//go:build ios || android

package main

import "github.com/Guliveer/twitch-miner-go/internal/logger"

func startTray(_ *logger.Logger, _ bool, _ string, _ int, _ bool, _ func()) {}

func applyNoConsole(_ bool) {}
