// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/instancesync/custom_deployment_perpetual_task_params.proto

package io.harness.perpetualtask.instancesync;

@javax.annotation.Generated(value = "protoc", comments = "annotations:CustomDeploymentPerpetualTaskParams.java.pb.meta")
public final class CustomDeploymentPerpetualTaskParams {
  private CustomDeploymentPerpetualTaskParams() {}
  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistryLite registry) {}

  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions((com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
      internal_static_io_harness_perpetualtask_instancesync_CustomDeploymentInstanceSyncTaskParams_descriptor;
  static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_io_harness_perpetualtask_instancesync_CustomDeploymentInstanceSyncTaskParams_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor descriptor;
  static {
    java.lang.String[] descriptorData = {"\nSio/harness/perpetualtask/instancesync/"
        + "custom_deployment_perpetual_task_params."
        + "proto\022%io.harness.perpetualtask.instance"
        + "sync\"\236\001\n&CustomDeploymentInstanceSyncTas"
        + "kParams\022\025\n\006app_id\030\001 \001(\tR\005appId\022\035\n\naccoun"
        + "t_id\030\002 \001(\tR\taccountId\022\026\n\006script\030\003 \001(\tR\006s"
        + "cript\022&\n\017output_path_key\030\004 \001(\tR\routputPa"
        + "thKeyB\002P\001b\006proto3"};
    descriptor = com.google.protobuf.Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
        descriptorData, new com.google.protobuf.Descriptors.FileDescriptor[] {});
    internal_static_io_harness_perpetualtask_instancesync_CustomDeploymentInstanceSyncTaskParams_descriptor =
        getDescriptor().getMessageTypes().get(0);
    internal_static_io_harness_perpetualtask_instancesync_CustomDeploymentInstanceSyncTaskParams_fieldAccessorTable =
        new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
            internal_static_io_harness_perpetualtask_instancesync_CustomDeploymentInstanceSyncTaskParams_descriptor,
            new java.lang.String[] {
                "AppId",
                "AccountId",
                "Script",
                "OutputPathKey",
            });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
