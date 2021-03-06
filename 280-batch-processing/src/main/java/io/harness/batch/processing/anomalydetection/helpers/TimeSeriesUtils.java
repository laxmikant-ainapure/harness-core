package io.harness.batch.processing.anomalydetection.helpers;

import io.harness.batch.processing.anomalydetection.AnomalyDetectionConstants;
import io.harness.batch.processing.anomalydetection.AnomalyDetectionTimeSeries;
import io.harness.batch.processing.anomalydetection.TimeSeriesMetaData;
import io.harness.ccm.anomaly.entities.TimeGranularity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.NonNull;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class TimeSeriesUtils {
  private TimeSeriesUtils() {}

  public static List<Double> getStats(@NonNull AnomalyDetectionTimeSeries anomalyDetectionTimeSeries) {
    DescriptiveStatistics descriptiveStatistics = new DescriptiveStatistics();
    for (Double value : anomalyDetectionTimeSeries.getTrainDataPoints()) {
      descriptiveStatistics.addValue(value);
    }
    return Arrays.asList(descriptiveStatistics.getMean(), descriptiveStatistics.getStandardDeviation());
  }

  public static boolean validate(
      AnomalyDetectionTimeSeries anomalyDetectionTimeSeries, TimeSeriesMetaData timeSeriesMetaData) {
    return validateTimeSeriesTestData(anomalyDetectionTimeSeries)
        && validateTimeSeriesTrainData(anomalyDetectionTimeSeries, timeSeriesMetaData);
  }

  public static boolean validateTimeSeriesTestData(AnomalyDetectionTimeSeries anomalyDetectionTimeSeries) {
    return Collections.frequency(
               anomalyDetectionTimeSeries.getTestDataPointsList(), AnomalyDetectionConstants.DEFAULT_COST)
        == 0;
  }

  public static boolean validateTimeSeriesTrainData(
      AnomalyDetectionTimeSeries anomalyDetectionTimeSeries, TimeSeriesMetaData timeSeriesMetaData) {
    int age = anomalyDetectionTimeSeries.getTrainDataPoints().size();
    for (Double current : anomalyDetectionTimeSeries.getTrainDataPoints()) {
      if (current.equals(AnomalyDetectionConstants.DEFAULT_COST)) {
        age = age - 1;
      }
    }
    if (timeSeriesMetaData.getTimeGranularity() == TimeGranularity.DAILY) {
      return age >= AnomalyDetectionConstants.MIN_DAYS_REQUIRED_DAILY;
    }
    return false;
  }
}
