# Create image for ci-scm service
#
# Build the ci-scm docker image using:
# > bazel build --platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 //product/ci/scm/...
# > docker build -t harness/ci-scm:<tag> -f product/ci/scm/Dockerfile bazel-bin/product/ci/scm/linux_amd64_pure_stripped

# First stage
FROM alpine:3.12
RUN GRPC_HEALTH_PROBE_VERSION=v0.3.3 && \
    wget -qO/bin/grpc_health_probe https://github.com/grpc-ecosystem/grpc-health-probe/releases/download/${GRPC_HEALTH_PROBE_VERSION}/grpc_health_probe-linux-amd64 && \
    chmod +x /bin/grpc_health_probe

# Second stage
FROM alpine:3.12
COPY --from=0 /bin/grpc_health_probe ./grpc_health_probe
# Copy ci-scm binary
COPY linux_amd64_pure_stripped/scm /usr/local/bin/scm
RUN chmod +x /usr/local/bin/scm
CMD ["/usr/local/bin/scm"]
