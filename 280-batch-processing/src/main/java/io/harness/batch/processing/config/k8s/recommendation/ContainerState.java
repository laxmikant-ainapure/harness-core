package io.harness.batch.processing.config.k8s.recommendation;

import static io.harness.ccm.recommender.k8sworkload.RecommenderUtils.newCpuHistogram;
import static io.harness.ccm.recommender.k8sworkload.RecommenderUtils.newMemoryHistogram;

import io.harness.histogram.Histogram;

import software.wings.graphql.datafetcher.ce.recommendation.entity.ContainerCheckpoint;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * ContainerState maintains the live structures for each container, such as cpu & mem histograms and the various
 * timestamps.
 */
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContainerState {
  Instant lastUpdateTime;
  Histogram cpuHistogram = newCpuHistogram();
  Histogram memoryHistogram = newMemoryHistogram();

  // Below are all based on cpu
  private Instant firstSampleStart;
  private Instant lastSampleStart;
  private int totalSamplesCount;

  long memoryPeak;
  Instant windowEnd;
  int version;

  static ContainerState fromCheckpoint(ContainerCheckpoint containerCheckpoint) {
    ContainerState containerState = new ContainerState();
    containerState.setLastUpdateTime(containerCheckpoint.getLastUpdateTime());
    containerState.cpuHistogram.loadFromCheckPoint(containerCheckpoint.getCpuHistogram());
    containerState.memoryHistogram.loadFromCheckPoint(containerCheckpoint.getMemoryHistogram());
    containerState.setFirstSampleStart(containerCheckpoint.getFirstSampleStart());
    containerState.setLastSampleStart(containerCheckpoint.getLastSampleStart());
    containerState.setTotalSamplesCount(containerCheckpoint.getTotalSamplesCount());
    containerState.setMemoryPeak(containerCheckpoint.getMemoryPeak());
    containerState.setWindowEnd(containerCheckpoint.getWindowEnd());
    containerState.setVersion(containerCheckpoint.getVersion());
    return containerState;
  }

  ContainerCheckpoint toContainerCheckpoint() {
    return ContainerCheckpoint.builder()
        .lastUpdateTime(lastUpdateTime)
        .cpuHistogram(cpuHistogram.saveToCheckpoint())
        .memoryHistogram(memoryHistogram.saveToCheckpoint())
        .firstSampleStart(firstSampleStart)
        .lastSampleStart(lastSampleStart)
        .totalSamplesCount(totalSamplesCount)
        .memoryPeak(memoryPeak)
        .windowEnd(windowEnd)
        .version(version)
        .build();
  }
}
