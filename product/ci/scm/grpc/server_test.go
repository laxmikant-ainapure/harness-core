package grpc

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/wings-software/portal/commons/go/lib/logs"
	"go.uber.org/zap"
	"google.golang.org/grpc"
)

func TestServerFailToListen(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	_, err := NewSCMServer(65536, log.Sugar())
	assert.Error(t, err)
}

func TestStopNilServer(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	stopCh := make(chan bool, 1)
	s := &scmServer{
		port:   65534,
		log:    log.Sugar(),
		stopCh: stopCh,
	}
	stopCh <- true
	s.Stop()
}

func TestStopRunningServer(t *testing.T) {
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	stopCh := make(chan bool, 1)
	s := &scmServer{
		port:       65533,
		grpcServer: grpc.NewServer(),
		log:        log.Sugar(),
		stopCh:     stopCh,
	}
	stopCh <- true
	s.Stop()
}

func TestNewSCMServer(t *testing.T) {
	port := uint(5000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	_, err := NewSCMServer(port, log.Sugar())
	assert.Equal(t, err, nil)
}
