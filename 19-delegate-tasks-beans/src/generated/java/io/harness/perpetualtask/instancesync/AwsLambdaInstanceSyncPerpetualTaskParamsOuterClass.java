// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/instancesync/aws_lambda_instance_sync_perpetual_task_params.proto

package io.harness.perpetualtask.instancesync;

@javax.annotation.
Generated(value = "protoc", comments = "annotations:AwsLambdaInstanceSyncPerpetualTaskParamsOuterClass.java.pb.meta")
public final class AwsLambdaInstanceSyncPerpetualTaskParamsOuterClass {
  private AwsLambdaInstanceSyncPerpetualTaskParamsOuterClass() {}
  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistryLite registry) {}

  public static void registerAllExtensions(com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions((com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
      internal_static_io_harness_perpetualtask_instancesync_AwsLambdaInstanceSyncPerpetualTaskParams_descriptor;
  static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_io_harness_perpetualtask_instancesync_AwsLambdaInstanceSyncPerpetualTaskParams_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
    return descriptor;
  }
  private static com.google.protobuf.Descriptors.FileDescriptor descriptor;
  static {
    java.lang.String[] descriptorData = {"\nZio/harness/perpetualtask/instancesync/"
        + "aws_lambda_instance_sync_perpetual_task_"
        + "params.proto\022%io.harness.perpetualtask.i"
        + "nstancesync\"\352\001\n(AwsLambdaInstanceSyncPer"
        + "petualTaskParams\022\035\n\naws_config\030\001 \001(\014R\taw"
        + "sConfig\022%\n\016encrypted_data\030\002 \001(\014R\rencrypt"
        + "edData\022\026\n\006region\030\003 \001(\tR\006region\022#\n\rfuncti"
        + "on_name\030\004 \001(\tR\014functionName\022\034\n\tqualifier"
        + "\030\005 \001(\tR\tqualifier\022\035\n\nstart_date\030\006 \001(\003R\ts"
        + "tartDateB\002P\001b\006proto3"};
    descriptor = com.google.protobuf.Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
        descriptorData, new com.google.protobuf.Descriptors.FileDescriptor[] {});
    internal_static_io_harness_perpetualtask_instancesync_AwsLambdaInstanceSyncPerpetualTaskParams_descriptor =
        getDescriptor().getMessageTypes().get(0);
    internal_static_io_harness_perpetualtask_instancesync_AwsLambdaInstanceSyncPerpetualTaskParams_fieldAccessorTable =
        new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
            internal_static_io_harness_perpetualtask_instancesync_AwsLambdaInstanceSyncPerpetualTaskParams_descriptor,
            new java.lang.String[] {
                "AwsConfig",
                "EncryptedData",
                "Region",
                "FunctionName",
                "Qualifier",
                "StartDate",
            });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
