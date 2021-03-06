package io.harness.ccm.anomaly;

import static io.harness.rule.OwnerRule.SANDESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.entities.TimeGranularity;
import io.harness.ccm.anomaly.mappers.QlAnomalyMapper;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.anomaly.QLAnomalyData;

import java.sql.SQLException;
import java.time.Instant;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class QlAnomalyMapperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  AnomalyEntity anomalyEntity1;

  @Before
  public void setUp() throws SQLException {
    anomalyEntity1 = AnomalyEntity.builder()
                         .id("ANOMALY_ID1")
                         .accountId("ACCOUNT_ID")
                         .actualCost(10.1)
                         .expectedCost(12.3)
                         .anomalyTime(Instant.now())
                         .note("TEST_NOTE")
                         .anomalyScore(12.34)
                         .clusterId("CLUSTER_ID")
                         .clusterName("CLUSTER_NAME")
                         .gcpProduct("GCP_PRODUCT")
                         .gcpProject("GCP_PROJECT")
                         .gcpSKUId("GCP_SKU_ID")
                         .gcpSKUDescription("GCP_SKU_DESCRIPTION")
                         .awsAccount("AWS_ACCOUNT")
                         .awsService("AWS_SERVICE")
                         .awsUsageType("AWS_USAGE_TYPE")
                         .awsInstanceType("AWS_INSTANCE_TYPE")
                         .timeGranularity(TimeGranularity.DAILY)
                         .build();
  }

  @Test
  @Owner(developers = SANDESH)
  @Category(UnitTests.class)
  @Ignore("ignore this test for now")
  public void shouldConvertEntity() {
    QLAnomalyData qlAnomalyData = QlAnomalyMapper.toDto(anomalyEntity1);
    assertThat(qlAnomalyData).isEqualToIgnoringGivenFields(anomalyEntity1, "entity", "time");
    assertThat(qlAnomalyData.getEntity()).isEqualToIgnoringGivenFields(anomalyEntity1);
  }
}
