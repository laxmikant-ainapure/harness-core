package io.harness.cvng.dashboard.beans;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.TimeSeriesMetricType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.NotNull;

@Data
@Builder
public class TimeSeriesMetricDataDTO implements Comparable<TimeSeriesMetricDataDTO> {
  String projectIdentifier;
  String orgIdentifier;
  String environmentIdentifier;
  String serviceIdentifier;
  TimeSeriesMetricType metricType;

  CVMonitoringCategory category;

  String groupName;
  String metricName;

  @Builder.Default Integer totalRisk = 0;

  SortedSet<MetricData> metricDataList;

  @JsonIgnore
  public Integer getTotalRisk() {
    return totalRisk;
  }

  public void addMetricData(double value, long timestamp, Double risk) {
    if (metricDataList == null) {
      metricDataList = new TreeSet<>();
    }
    totalRisk += risk.intValue();
    metricDataList.add(
        MetricData.builder().timestamp(timestamp).value(value).risk(Risk.valueOf(risk.intValue())).build());
  }

  @Override
  public int compareTo(@NotNull TimeSeriesMetricDataDTO o) {
    if (totalRisk != o.getTotalRisk()) {
      return o.getTotalRisk().compareTo(totalRisk);
    }
    if (!groupName.equals(o.getGroupName())) {
      return groupName.compareTo(o.getGroupName());
    }
    return metricName.compareTo(o.getMetricName());
  }

  @Data
  @Builder
  public static class MetricData implements Comparable<MetricData> {
    private Long timestamp;
    private double value;
    Risk risk;
    @Override
    public int compareTo(@NotNull MetricData o) {
      return timestamp.compareTo(o.timestamp);
    }
  }
}
