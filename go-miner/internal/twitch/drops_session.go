package twitch

import (
	"encoding/json"
	"math/rand/v2"
	"time"
)

// DropSessionProgress represents live per-channel Watch Drops progress returned
// by Twitch's DropCurrentSessionContext GQL operation.
type DropSessionProgress struct {
	DropID                 string `json:"dropID"`
	ChannelID              string `json:"channelID"`
	GameID                 string `json:"gameID"`
	CurrentMinutesWatched  int    `json:"currentMinutesWatched"`
	RequiredMinutesWatched int    `json:"requiredMinutesWatched"`
}

// ParseDropCurrentSessionContext extracts live watch-drop progress from a raw
// DropCurrentSessionContext GQL data payload.
func ParseDropCurrentSessionContext(raw json.RawMessage) (*DropSessionProgress, error) {
	var resp struct {
		CurrentUser *struct {
			DropCurrentSession *struct {
				DropID                 string `json:"dropID"`
				CurrentMinutesWatched  int    `json:"currentMinutesWatched"`
				RequiredMinutesWatched int    `json:"requiredMinutesWatched"`
				Channel                *struct {
					ID string `json:"id"`
				} `json:"channel"`
				Game *struct {
					ID string `json:"id"`
				} `json:"game"`
			} `json:"dropCurrentSession"`
		} `json:"currentUser"`
	}
	if err := json.Unmarshal(raw, &resp); err != nil {
		return nil, err
	}
	if resp.CurrentUser == nil || resp.CurrentUser.DropCurrentSession == nil {
		return nil, nil
	}
	s := resp.CurrentUser.DropCurrentSession
	out := &DropSessionProgress{
		DropID:                 s.DropID,
		CurrentMinutesWatched:  s.CurrentMinutesWatched,
		RequiredMinutesWatched: s.RequiredMinutesWatched,
	}
	if s.Channel != nil {
		out.ChannelID = s.Channel.ID
	}
	if s.Game != nil {
		out.GameID = s.Game.ID
	}
	return out, nil
}

// RandomClaimDelay returns a randomized 20–55s delay before claiming a completed badge drop.
func RandomClaimDelay() time.Duration {
	return time.Duration(20+rand.IntN(36)) * time.Second
}

// RandomSwitchCooldown returns a randomized 5–12s cooldown when switching streams.
func RandomSwitchCooldown() time.Duration {
	return time.Duration(5+rand.IntN(8)) * time.Second
}

// JitteredScanInterval applies ±4 minute jitter to the base catalog scan interval.
func JitteredScanInterval(base time.Duration) time.Duration {
	const jitterSeconds = 4 * 60
	offset := time.Duration(rand.IntN(2*jitterSeconds+1)-jitterSeconds) * time.Second
	result := base + offset
	if result < time.Minute {
		return time.Minute
	}
	return result
}
