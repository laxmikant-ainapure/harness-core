package software.wings.helpers.ext.gcb.models;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

@OwnedBy(CDC)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@Data
public class BuildOptions {
  private List<HashType> sourceProvenanceHash;
  private VerifyOption requestedVerifyOption;
  private MachineType machineType;
  private String diskSizeGb;
  private SubstitutionOption substitutionOption;
  private LogStreamingOption logStreamingOption;
  private LoggingMode logging;
  private List<String> env;
  private List<String> secretEnv;
  private List<Volume> volumes;
}
