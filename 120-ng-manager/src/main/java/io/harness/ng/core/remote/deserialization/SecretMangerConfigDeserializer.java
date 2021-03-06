package io.harness.ng.core.remote.deserialization;

import io.harness.beans.SecretManagerConfig;
import io.harness.security.encryption.EncryptionType;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.CyberArkConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.VaultConfig;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

public class SecretMangerConfigDeserializer extends StdDeserializer<SecretManagerConfig> {
  public SecretMangerConfigDeserializer() {
    super(SecretManagerConfig.class);
  }

  @Override
  public SecretManagerConfig deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    ObjectNode obj = mapper.readTree(jp);
    EncryptionType encryptionType = EncryptionType.valueOf(obj.get("encryptionType").asText());
    switch (encryptionType) {
      case VAULT:
        return mapper.treeToValue(obj, VaultConfig.class);
      case LOCAL:
        return mapper.treeToValue(obj, LocalEncryptionConfig.class);
      case AWS_SECRETS_MANAGER:
        return mapper.treeToValue(obj, AwsSecretsManagerConfig.class);
      case KMS:
        return mapper.treeToValue(obj, KmsConfig.class);
      case GCP_KMS:
        return mapper.treeToValue(obj, GcpKmsConfig.class);
      case CUSTOM:
        return mapper.treeToValue(obj, CustomSecretsManagerConfig.class);
      case AZURE_VAULT:
        return mapper.treeToValue(obj, AzureVaultConfig.class);
      case CYBERARK:
        return mapper.treeToValue(obj, CyberArkConfig.class);
      default:
        throw new IllegalArgumentException("Unsupported type: " + encryptionType);
    }
  }
}
