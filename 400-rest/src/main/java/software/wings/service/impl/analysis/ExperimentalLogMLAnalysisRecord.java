package software.wings.service.impl.analysis;

import static software.wings.common.VerificationConstants.ML_RECORDS_TTL_MONTHS;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.IgnoreUnusedIndex;
import io.harness.mongo.index.CdIndex;
import io.harness.mongo.index.FdIndex;
import io.harness.mongo.index.FdTtlIndex;
import io.harness.mongo.index.Field;
import io.harness.mongo.index.IndexType;

import software.wings.beans.Base;
import software.wings.service.impl.splunk.LogMLClusterScores;
import software.wings.service.impl.splunk.SplunkAnalysisCluster;
import software.wings.sm.StateType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.reinert.jjschema.SchemaIgnore;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Entity;

@CdIndex(name = "stateExecutionIdx",
    fields = { @Field("stateExecutionId")
               , @Field(value = "logCollectionMinute", type = IndexType.DESC) })
@Data
@EqualsAndHashCode(callSuper = false, exclude = {"validUntil"})
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ExperimentalLogMLAnalysisRecordKeys")
@IgnoreUnusedIndex
@Entity(value = "experimentalLogAnalysisRecords", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ExperimentalLogMLAnalysisRecord extends Base {
  @NotEmpty @FdIndex private String stateExecutionId;

  @NotEmpty private StateType stateType;
  @NotEmpty private String experiment_name;

  @NotEmpty private int logCollectionMinute;

  private String envId;
  private String workflowExecutionId;

  private boolean isBaseLineCreated = true;

  private String query;
  private String analysisSummaryMessage;
  private double score;
  private List<List<SplunkAnalysisCluster>> unknown_events;
  private Map<String, List<SplunkAnalysisCluster>> test_events;
  private Map<String, List<SplunkAnalysisCluster>> control_events;
  private Map<String, Map<String, SplunkAnalysisCluster>> control_clusters;
  private Map<String, Map<String, SplunkAnalysisCluster>> unknown_clusters;
  private Map<String, Map<String, SplunkAnalysisCluster>> test_clusters;
  private Map<String, Map<String, SplunkAnalysisCluster>> ignore_clusters;
  private LogMLAnalysisStatus analysisStatus;
  @FdIndex private String cvConfigId;
  private LogMLClusterScores cluster_scores;
  @JsonProperty("comparison_msg_pairs") private transient List<ExperimentalMessageComparisonResult> comparisonMsgPairs;
  @JsonProperty("model_version") private String modelVersion;
  @JsonProperty("analysis_metadata") private Map<String, String> analysisMetadata;

  @JsonIgnore
  @SchemaIgnore
  @FdTtlIndex
  private Date validUntil = Date.from(OffsetDateTime.now().plusMonths(ML_RECORDS_TTL_MONTHS).toInstant());
}
