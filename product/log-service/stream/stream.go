// Package stream defines the live log streaming interface.
package stream

import (
	"context"
	"errors"
	"io"
	"time"
)

// ErrNotFound is returned when a stream is not registered
// with the Streamer.
var ErrNotFound = errors.New("stream: not found")

// Stream defines the live log streaming interface.
type Stream interface {
	// Create creates the log stream for the string key.
	Create(context.Context, string) error

	// Delete deletes the log stream for the string key.
	Delete(context.Context, string) error

	// Write writes to the log stream.
	// TODO(bradrydzewski) change *Line to a proper slice.
	Write(context.Context, string, ...*Line) error

	// Tail tails the log stream.
	Tail(context.Context, string) (<-chan *Line, <-chan error)

	// Info returns internal stream information.
	Info(context.Context) *Info

	// CopyTo copies the contents of the stream to the writer
	CopyTo(ctx context.Context, key string, rc io.WriteCloser) error
}

// Line represents a line in the logs.
type Line struct {
	Level     string            `json:"level"`
	Number    int               `json:"pos"`
	Message   string            `json:"out"`
	Timestamp time.Time         `json:"time"`
	Args      map[string]string `json:"args"`
}

// Info provides internal stream information. This can be
// used to monitor the number of registered streams and
// subscribers.
type Info struct {
	// Streams is a key-value pair mapping the unique key to
	// the count of subscribers
	Streams map[string]Stats `json:"streams"`
}

// Stats provides statistics about an individual stream,
// including the size of the stream, the number of
// subscribers and the TTL. These values will be -1 if
// not set.
type Stats struct {
	Size int    `json:"size"`
	Subs int    `json:"subscribers"`
	TTL  string `json:"ttl"` // Unix time
}
