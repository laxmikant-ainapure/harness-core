package io.harness.stateutils.buildstate.providers;

import static io.harness.common.CIExecutionConstants.LITE_ENGINE_CONTAINER_NAME;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.CIExecutionConstants.SETUP_ADDON_ARGS;
import static io.harness.common.CIExecutionConstants.SETUP_ADDON_CONTAINER_NAME;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InternalContainerParamsProviderTest extends CIExecutionTestBase {
  @Inject InternalContainerParamsProvider internalContainerParamsProvider;

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getSetupAddonContainerParams() {
    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();

    CIK8ContainerParams containerParams =
        internalContainerParamsProvider.getSetupAddonContainerParams(connectorDetails, null, "workspace");

    assertThat(containerParams.getName()).isEqualTo(SETUP_ADDON_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.ADD_ON);
    assertThat(containerParams.getArgs()).isEqualTo(Arrays.asList(SETUP_ADDON_ARGS));
  }

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void getLiteEngineContainerParams() {
    int buildID = 1;
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(buildID)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();
    K8PodDetails k8PodDetails = K8PodDetails.builder().stageID("stage").build();

    ConnectorDetails connectorDetails = ConnectorDetails.builder().build();
    Map<String, ConnectorDetails> publishArtifactConnectorDetailsMap = new HashMap<>();
    String logSecret = "secret";
    String logEndpoint = "http://localhost:8079";
    Map<String, String> logEnvVars = new HashMap<>();
    logEnvVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    logEnvVars.put(LOG_SERVICE_TOKEN_VARIABLE, logSecret);

    String tiToken = "token";
    String tiEndpoint = "http://localhost:8078";
    Map<String, String> tiEnvVars = new HashMap<>();
    tiEnvVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    tiEnvVars.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);

    Map<String, String> volumeToMountPath = new HashMap<>();

    String serialisedStage = "test";
    String serviceToken = "test";
    Integer stageCpuRequest = 500;
    Integer stageMemoryRequest = 200;

    CIK8ContainerParams containerParams = internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails,
        publishArtifactConnectorDetailsMap, k8PodDetails, serialisedStage, serviceToken, stageCpuRequest,
        stageMemoryRequest, null, logEnvVars, tiEnvVars, volumeToMountPath, "/step-exec/workspace", ambiance);

    Map<String, String> expectedEnv = new HashMap<>();
    expectedEnv.put(LOG_SERVICE_ENDPOINT_VARIABLE, logEndpoint);
    expectedEnv.put(LOG_SERVICE_TOKEN_VARIABLE, logSecret);
    expectedEnv.put(TI_SERVICE_ENDPOINT_VARIABLE, tiEndpoint);
    expectedEnv.put(TI_SERVICE_TOKEN_VARIABLE, tiToken);

    Map<String, String> gotEnv = containerParams.getEnvVars();
    assertThat(gotEnv).containsAllEntriesOf(expectedEnv);
    assertThat(containerParams.getName()).isEqualTo(LITE_ENGINE_CONTAINER_NAME);
    assertThat(containerParams.getContainerType()).isEqualTo(CIContainerType.LITE_ENGINE);
  }
}
