package software.wings.security.authentication.recaptcha;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Value;

@OwnedBy(PL)
@Value
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerificationStatus {
  private Boolean success;
  private String hostname;
  @JsonProperty("challenge_ts") private String challengeTs;
  @JsonProperty("error-codes") private List<String> errorCodes;
}
