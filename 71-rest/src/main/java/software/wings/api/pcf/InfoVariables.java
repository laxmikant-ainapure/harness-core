package software.wings.api.pcf;

import io.harness.data.SweepingOutput;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InfoVariables implements SweepingOutput {
  public static final String SWEEPING_OUTPUT_NAME = "pcf";

  private String newAppName;
  private String newAppGuid;
  private List<String> newAppRoutes;

  private String oldAppName;
  private String oldAppGuid;
  private List<String> oldAppRoutes;

  private List<String> finalRoutes;
  private List<String> tempRoutes;
}
