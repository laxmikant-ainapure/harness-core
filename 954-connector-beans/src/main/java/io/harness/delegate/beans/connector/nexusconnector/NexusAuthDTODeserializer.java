package io.harness.delegate.beans.connector.nexusconnector;

import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class NexusAuthDTODeserializer extends StdDeserializer<NexusAuthenticationDTO> {
  public NexusAuthDTODeserializer() {
    super(NexusAuthDTODeserializer.class);
  }

  public NexusAuthDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public NexusAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    NexusAuthType type = getType(typeNode);
    NexusAuthCredentialsDTO nexusAuthenticationDTO = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == NexusAuthType.USER_PASSWORD) {
      nexusAuthenticationDTO = mapper.readValue(authSpec.toString(), NexusUsernamePasswordAuthDTO.class);
    } else if (type == NexusAuthType.ANONYMOUS) {
      if (authSpec != null && !authSpec.isNull()) {
        throw new InvalidRequestException("No spec should be provided with the anonymous type");
      }
    }

    return NexusAuthenticationDTO.builder().authType(type).credentials(nexusAuthenticationDTO).build();
  }

  NexusAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return NexusAuthType.fromString(typeValue);
  }
}
