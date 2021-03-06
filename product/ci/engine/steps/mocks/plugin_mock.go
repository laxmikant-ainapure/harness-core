// Code generated by MockGen. DO NOT EDIT.
// Source: plugin.go

// Package steps is a generated GoMock package.
package steps

import (
	context "context"
	gomock "github.com/golang/mock/gomock"
	reflect "reflect"
)

// MockPluginStep is a mock of PluginStep interface.
type MockPluginStep struct {
	ctrl     *gomock.Controller
	recorder *MockPluginStepMockRecorder
}

// MockPluginStepMockRecorder is the mock recorder for MockPluginStep.
type MockPluginStepMockRecorder struct {
	mock *MockPluginStep
}

// NewMockPluginStep creates a new mock instance.
func NewMockPluginStep(ctrl *gomock.Controller) *MockPluginStep {
	mock := &MockPluginStep{ctrl: ctrl}
	mock.recorder = &MockPluginStepMockRecorder{mock}
	return mock
}

// EXPECT returns an object that allows the caller to indicate expected use.
func (m *MockPluginStep) EXPECT() *MockPluginStepMockRecorder {
	return m.recorder
}

// Run mocks base method.
func (m *MockPluginStep) Run(ctx context.Context) (int32, error) {
	m.ctrl.T.Helper()
	ret := m.ctrl.Call(m, "Run", ctx)
	ret0, _ := ret[0].(int32)
	ret1, _ := ret[1].(error)
	return ret0, ret1
}

// Run indicates an expected call of Run.
func (mr *MockPluginStepMockRecorder) Run(ctx interface{}) *gomock.Call {
	mr.mock.ctrl.T.Helper()
	return mr.mock.ctrl.RecordCallWithMethodType(mr.mock, "Run", reflect.TypeOf((*MockPluginStep)(nil).Run), ctx)
}
