// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: io/harness/perpetualtask/k8s.watch/k8s_messages.proto

package io.harness.perpetualtask.k8s.watch;

@javax.annotation.Generated(value = "protoc", comments = "annotations:NodeEventOrBuilder.java.pb.meta")
public interface NodeEventOrBuilder extends
    // @@protoc_insertion_point(interface_extends:io.harness.perpetualtask.k8s.watch.NodeEvent)
    com.google.protobuf.MessageOrBuilder {
  /**
   * <code>string cloud_provider_id = 2;</code>
   */
  java.lang.String getCloudProviderId();
  /**
   * <code>string cloud_provider_id = 2;</code>
   */
  com.google.protobuf.ByteString getCloudProviderIdBytes();

  /**
   * <code>string node_uid = 3;</code>
   */
  java.lang.String getNodeUid();
  /**
   * <code>string node_uid = 3;</code>
   */
  com.google.protobuf.ByteString getNodeUidBytes();

  /**
   * <code>.io.harness.perpetualtask.k8s.watch.NodeEvent.EventType type = 4;</code>
   */
  int getTypeValue();
  /**
   * <code>.io.harness.perpetualtask.k8s.watch.NodeEvent.EventType type = 4;</code>
   */
  io.harness.perpetualtask.k8s.watch.NodeEvent.EventType getType();

  /**
   * <code>.google.protobuf.Timestamp timestamp = 5;</code>
   */
  boolean hasTimestamp();
  /**
   * <code>.google.protobuf.Timestamp timestamp = 5;</code>
   */
  com.google.protobuf.Timestamp getTimestamp();
  /**
   * <code>.google.protobuf.Timestamp timestamp = 5;</code>
   */
  com.google.protobuf.TimestampOrBuilder getTimestampOrBuilder();

  /**
   * <code>string node_name = 6;</code>
   */
  java.lang.String getNodeName();
  /**
   * <code>string node_name = 6;</code>
   */
  com.google.protobuf.ByteString getNodeNameBytes();
}
