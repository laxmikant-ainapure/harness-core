// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/k8s/watch/k8s_messages.proto

package io.harness.perpetualtask.k8s.watch;

@javax.annotation.Generated(value = "protoc", comments = "annotations:K8sClusterEventOrBuilder.java.pb.meta")
public interface K8sClusterEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.harness.perpetualtask.k8s.watch.K8sClusterEvent)
    com.google.protobuf.MessageOrBuilder {
  /**
   * <pre>
   * cluster details
   * </pre>
   *
   * <code>string cluster_id = 1;</code>
   */
  java.lang.String getClusterId();
  /**
   * <pre>
   * cluster details
   * </pre>
   *
   * <code>string cluster_id = 1;</code>
   */
  com.google.protobuf.ByteString getClusterIdBytes();

  /**
   * <code>string cluster_name = 2;</code>
   */
  java.lang.String getClusterName();
  /**
   * <code>string cluster_name = 2;</code>
   */
  com.google.protobuf.ByteString getClusterNameBytes();

  /**
   * <code>string cloud_provider_id = 3;</code>
   */
  java.lang.String getCloudProviderId();
  /**
   * <code>string cloud_provider_id = 3;</code>
   */
  com.google.protobuf.ByteString getCloudProviderIdBytes();

  /**
   * <pre>
   * event details
   * </pre>
   *
   * <code>string reason = 4;</code>
   */
  java.lang.String getReason();
  /**
   * <pre>
   * event details
   * </pre>
   *
   * <code>string reason = 4;</code>
   */
  com.google.protobuf.ByteString getReasonBytes();

  /**
   * <code>string message = 5;</code>
   */
  java.lang.String getMessage();
  /**
   * <code>string message = 5;</code>
   */
  com.google.protobuf.ByteString getMessageBytes();

  /**
   * <code>string source_component = 6;</code>
   */
  java.lang.String getSourceComponent();
  /**
   * <code>string source_component = 6;</code>
   */
  com.google.protobuf.ByteString getSourceComponentBytes();

  /**
   * <code>.io.harness.perpetualtask.k8s.watch.K8sObjectReference involved_object = 7;</code>
   */
  boolean hasInvolvedObject();
  /**
   * <code>.io.harness.perpetualtask.k8s.watch.K8sObjectReference involved_object = 7;</code>
   */
  io.harness.perpetualtask.k8s.watch.K8sObjectReference getInvolvedObject();
  /**
   * <code>.io.harness.perpetualtask.k8s.watch.K8sObjectReference involved_object = 7;</code>
   */
  io.harness.perpetualtask.k8s.watch.K8sObjectReferenceOrBuilder getInvolvedObjectOrBuilder();

  /**
   * <code>string kube_system_uid = 8;</code>
   */
  java.lang.String getKubeSystemUid();
  /**
   * <code>string kube_system_uid = 8;</code>
   */
  com.google.protobuf.ByteString getKubeSystemUidBytes();
}
