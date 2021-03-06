package main

/*
	CI-addon is an entrypoint for run step & plugin step container images. It executes a step on receiving GRPC.
*/
import (
	"fmt"
	"os"

	"github.com/alexflint/go-arg"
	"github.com/wings-software/portal/commons/go/lib/logs"
	"github.com/wings-software/portal/commons/go/lib/metrics"
	"github.com/wings-software/portal/product/ci/addon/grpc"
	addonlogs "github.com/wings-software/portal/product/ci/addon/logs"
	"github.com/wings-software/portal/product/ci/addon/services"
	"github.com/wings-software/portal/product/ci/engine/logutil"
	"go.uber.org/zap"
)

const (
	applicationName = "CI-addon"
	deployable      = "ci-addon"
)

var (
	addonServer         = grpc.NewAddonServer
	newGrpcRemoteLogger = logutil.GetGrpcRemoteLogger
	newIntegrationSvc   = services.NewIntegrationSvc
)

// schema for running functional test service
type service struct {
	ID         string   `arg:"--id, required" help:"Service ID"`
	Image      string   `arg:"--image, required" help:"docker image name for the service"`
	Entrypoint []string `arg:"env:HARNESS_SERVICE_ENTRYPOINT" help:"entrypoint for the service"`
	Args       []string `arg:"env:HARNESS_SERVICE_ARGS" help:"arguments for the service"`
}

var args struct {
	Service *service `arg:"subcommand:service" help:"integration service arguments"`

	Port                  uint   `arg:"--port, required" help:"port for running GRPC server"`
	Verbose               bool   `arg:"--verbose" help:"enable verbose logging mode"`
	LogMetrics            bool   `arg:"--log_metrics" help:"enable metric logging"`
	Deployment            string `arg:"env:DEPLOYMENT" help:"name of the deployment"`
	DeploymentEnvironment string `arg:"env:DEPLOYMENT_ENVIRONMENT" help:"environment of the deployment"`
}

func parseArgs() {
	// set defaults here
	args.DeploymentEnvironment = "prod"
	args.Verbose = false
	args.LogMetrics = true

	arg.MustParse(&args)
}

func init() {
	//TODO: perform any initialization
}

func main() {
	parseArgs()

	// Addon logs not part of a step go to addon_stage_logs-<port>
	logState := addonlogs.LogState()
	pendingLogs := logState.PendingLogs()
	key := fmt.Sprintf("addon_stage_logs-%d", args.Port)
	remoteLogger, err := newGrpcRemoteLogger(key)
	if err != nil {
		// Could not create a logger
		panic(err)
	}
	pendingLogs <- remoteLogger
	log := remoteLogger.BaseLogger

	if args.LogMetrics {
		metrics.Log(int32(os.Getpid()), "addon", log)
	}

	var serviceLogger *logs.RemoteLogger

	// Start integration test service in a separate goroutine
	if args.Service != nil {
		svc := args.Service

		// create logger for service logs
		serviceLogger, err = newGrpcRemoteLogger(svc.ID)
		if err != nil {
			panic(err) // Could not create a logger
		}
		pendingLogs <- serviceLogger

		go func() {
			newIntegrationSvc(svc.ID, svc.Image, svc.Entrypoint, svc.Args, serviceLogger.BaseLogger,
				serviceLogger.Writer, args.LogMetrics, log).Run()
		}()
	}

	log.Infow("Starting CI addon server", "port", args.Port)
	s, err := addonServer(args.Port, args.LogMetrics, log)
	if err != nil {
		log.Errorw("error while running CI addon server", "port", args.Port, "error_msg", zap.Error(err))
		addonlogs.LogState().ClosePendingLogs()
		os.Exit(1) // Exit addon with exit code 1
	}

	// Wait for stop signal and shutdown the server upon receiving it in a separate goroutine
	go s.Stop()
	if err := s.Start(); err != nil {
		addonlogs.LogState().ClosePendingLogs()
		os.Exit(1) // Exit addon with exit code 1
	}
}
