package software.wings.graphql.datafetcher.cloudefficiencyevents;

import static io.harness.rule.OwnerRule.SHUBHANSHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ccm.cluster.dao.K8sYamlDao;
import io.harness.ccm.cluster.entities.K8sYaml;
import io.harness.rule.Owner;

import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.schema.query.QLK8sEventYamlDiffQueryParameters;
import software.wings.service.intfc.ce.CeAccountExpirationChecker;

import com.google.inject.Inject;
import java.sql.SQLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class K8sEventYamlDiffDataFetcherTest extends AbstractDataFetcherTestBase {
  @Mock K8sYamlDao k8sYamlDao;
  @Mock CeAccountExpirationChecker accountChecker;
  @Inject @InjectMocks K8sEventYamlDiffDataFetcher k8sEventYamlDiffDataFetcher;

  private static final String ACCOUNT_ID = "accountId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String UID = "uid";
  private static final String RESOURCE_VERSION = "kind";
  private static final String OLD_YAML = "old_yaml";
  private static final String NEW_YAML = "new_yaml";
  private static final String HASH1 = K8sYaml.hash(ACCOUNT_ID, CLUSTER_ID, UID, OLD_YAML);
  private static final String HASH2 = K8sYaml.hash(ACCOUNT_ID, CLUSTER_ID, UID, NEW_YAML);

  @Before
  public void setup() throws SQLException {
    when(k8sYamlDao.getYaml(ACCOUNT_ID, HASH1)).thenReturn(getTestYamlRecord(UID, OLD_YAML));
    when(k8sYamlDao.getYaml(ACCOUNT_ID, HASH2)).thenReturn(getTestYamlRecord(UID, NEW_YAML));
    doNothing().when(accountChecker).checkIsCeEnabled(anyString());
  }

  @Test
  @Owner(developers = SHUBHANSHU)
  @Category(UnitTests.class)
  public void testFetchYamlDiff() throws SQLException {
    QLK8sEventYamlDiffQueryParameters parameters = new QLK8sEventYamlDiffQueryParameters(HASH1, HASH2);
    QLK8sEventYamlDiff yamlDiff = k8sEventYamlDiffDataFetcher.fetch(parameters, ACCOUNT_ID);
    assertThat(yamlDiff).isNotNull();
    assertThat(yamlDiff.getData().getOldYaml()).isEqualTo(OLD_YAML);
    assertThat(yamlDiff.getData().getNewYaml()).isEqualTo(NEW_YAML);
  }

  private K8sYaml getTestYamlRecord(String uid, String yaml) {
    return K8sYaml.builder()
        .accountId(ACCOUNT_ID)
        .clusterId(CLUSTER_ID)
        .resourceVersion(RESOURCE_VERSION)
        .uid(uid)
        .yaml(yaml)
        .build();
  }
}
