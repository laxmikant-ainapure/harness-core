# Create image for ci-lite-engine which will orchestrate steps in a stage
#
# Build the ci-engine docker image using:
# > bazel build --platforms=@io_bazel_rules_go//go/toolchain:linux_amd64 //product/ci/engine/...
# > docker build -t harness/ci-lite-engine:<tag> -f product/ci/engine/Dockerfile bazel-bin/product/ci/engine/

FROM alpine:3.12
RUN apk add --update \
    curl \
    && rm -rf /var/cache/apk/*
COPY linux_amd64_pure_stripped/engine /usr/local/bin/ci-lite-engine
RUN chmod +x /usr/local/bin/ci-lite-engine
CMD ["/usr/local/bin/ci-lite-engine"]