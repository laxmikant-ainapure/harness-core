package grpc

import (
	"github.com/wings-software/portal/commons/go/lib/images"
	"github.com/wings-software/portal/product/ci/engine/jexl"
	"github.com/wings-software/portal/product/ci/engine/output"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"github.com/wings-software/portal/product/ci/engine/state"
	"go.uber.org/zap"
	"golang.org/x/net/context"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

var (
	getPublicImgMetadata  = images.PublicMetadata
	getPrivateImgMetadata = images.PrivateMetadata
	evaluateJEXL          = jexl.EvaluateJEXL
)

// handler is used to implement EngineServer
type engineHandler struct {
	log *zap.SugaredLogger
}

// NewEngineHandler returns a GRPC handler that implements pb.EngineServer
func NewEngineHandler(log *zap.SugaredLogger) pb.LiteEngineServer {
	return &engineHandler{log}
}

// UpdateState updates the execution state.
// If required state is resume, it sets the execution state to running and sends a signal via resume channel to resume the paused execution.
func (h *engineHandler) UpdateState(ctx context.Context, in *pb.UpdateStateRequest) (*pb.UpdateStateResponse, error) {
	if in.GetAction() == pb.UpdateStateRequest_UNKNOWN {
		h.log.Errorw("unknown action in incoming request")
		return &pb.UpdateStateResponse{}, status.Error(codes.InvalidArgument, "unknown action")
	}

	s := state.ExecutionState()
	if in.GetAction() == pb.UpdateStateRequest_PAUSE {
		h.log.Infow("Pausing pipeline execution")
		s.SetState(state.PAUSED)
	} else {
		h.log.Infow("Resuming pipeline execution")
		s.SetState(state.RUNNING)
		ch := s.ResumeSignal()
		ch <- true
	}
	return &pb.UpdateStateResponse{}, nil
}

// GetImageEntrypoint returns entrypoint of an image.
func (h *engineHandler) GetImageEntrypoint(ctx context.Context, in *pb.GetImageEntrypointRequest) (*pb.GetImageEntrypointResponse, error) {
	if in.GetImage() == "" {
		h.log.Errorw("image is not set", "request_arg", in.String())
		return &pb.GetImageEntrypointResponse{}, status.Error(codes.InvalidArgument, "image is not set")
	}

	var err error
	var entrypoint, args []string
	if in.GetSecret() == "" {
		entrypoint, args, err = getPublicImgMetadata(in.GetImage())
	} else {
		entrypoint, args, err = getPrivateImgMetadata(in.GetImage(), in.GetSecret())
	}

	if err != nil {
		h.log.Errorw("failed to find image entrypoint", "request_arg", in.String(), zap.Error(err))
		return &pb.GetImageEntrypointResponse{}, err
	}

	response := &pb.GetImageEntrypointResponse{
		Entrypoint: entrypoint,
		Args:       args,
	}
	return response, nil
}

// EvaluateJEXL evaluates JEXL expressions.
func (h *engineHandler) EvaluateJEXL(ctx context.Context, in *pb.EvaluateJEXLRequest) (
	*pb.EvaluateJEXLResponse, error) {
	so := make(output.StageOutput)
	for stepID, stepOutput := range in.GetStepOutputs() {
		o := &output.StepOutput{}
		o.Output.Variables = stepOutput.GetOutput()
		so[stepID] = o
	}

	result, err := evaluateJEXL(ctx, in.GetStepId(), in.GetExpressions(), so, false, h.log)
	if err != nil {
		h.log.Errorw("failed to evaluate expression", "request_arg", in.String(), zap.Error(err))
		return &pb.EvaluateJEXLResponse{}, err
	}

	response := &pb.EvaluateJEXLResponse{
		EvaluatedExpressions: result,
	}
	return response, nil
}
