package software.wings.service.impl.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class TimeSeriesFilter {
  private String cvConfigId;
  private long startTime;
  private long endTime;
  private long historyStartTime;
  private Set<String> txnNames;
  private Set<String> metricNames;
  private Set<String> tags;
}
