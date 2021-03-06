package io.harness.ccm.billing;

import static io.harness.ccm.billing.graphql.CloudBillingFilter.BILLING_GCP_STARTTIME;
import static io.harness.rule.OwnerRule.HANTANG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.billing.graphql.CloudBillingTimeFilter;
import io.harness.rule.Owner;

import software.wings.graphql.schema.type.aggregation.QLTimeOperator;

import com.healthmarketscience.sqlbuilder.Condition;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CloudBillingTimeFilterTest extends CategoryTest {
  private Number value = 0;
  private CloudBillingTimeFilter timeFilter;

  @Before
  public void setUp() {
    timeFilter = CloudBillingTimeFilter.builder()
                     .operator(QLTimeOperator.AFTER)
                     .variable(BILLING_GCP_STARTTIME)
                     .value(value)
                     .build();
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void testToCondition() {
    Condition condition = timeFilter.toCondition();
    assertThat(condition.toString()).isEqualTo("(t0.startTime >= '1970-01-01T00:00:00Z')");
  }
}
