package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionFunctor;
import io.harness.expression.ImageSecretFunctor;
import io.harness.ff.FeatureFlagService;
import io.harness.security.SimpleEncryption;
import io.harness.tasks.Cd1SetupFields;

import software.wings.expression.NgSecretManagerFunctor.NgSecretManagerFunctorBuilder;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ConfigService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.NGSecretService;
import software.wings.service.intfc.security.SecretManager;

import java.util.Map;
import lombok.Value;

@OwnedBy(CDC)
@Value
public class ManagerPreExecutionExpressionEvaluator extends ExpressionEvaluator {
  private final ExpressionFunctor secretManagerFunctor;
  private final ExpressionFunctor ngSecretManagerFunctor;
  private final SweepingOutputSecretFunctor sweepingOutputSecretFunctor;

  public ManagerPreExecutionExpressionEvaluator(SecretManagerMode mode, ServiceTemplateService serviceTemplateService,
      ConfigService configService, ArtifactCollectionUtils artifactCollectionUtils,
      FeatureFlagService featureFlagService, ManagerDecryptionService managerDecryptionService,
      SecretManager secretManager, String accountId, String workflowExecutionId, int expressionFunctorToken,
      NGSecretService ngSecretService, Map<String, String> taskSetupAbstractions) {
    String appId = taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.APP_ID_FIELD);
    String envId = taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.ENV_ID_FIELD);
    String serviceTemplateId =
        taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD);
    String artifactStreamId =
        taskSetupAbstractions == null ? null : taskSetupAbstractions.get(Cd1SetupFields.ARTIFACT_STREAM_ID_FIELD);

    addFunctor("configFile",
        ConfigFileFunctor.builder()
            .appId(appId)
            .envId(envId)
            .serviceTemplateId(serviceTemplateId)
            .configService(configService)
            .serviceTemplateService(serviceTemplateService)
            .build());

    addFunctor("dockerconfig",
        DockerConfigFunctor.builder()
            .appId(appId)
            .artifactStreamId(artifactStreamId)
            .artifactCollectionUtils(artifactCollectionUtils)
            .build());

    secretManagerFunctor = SecretManagerFunctor.builder()
                               .mode(mode)
                               .featureFlagService(featureFlagService)
                               .managerDecryptionService(managerDecryptionService)
                               .secretManager(secretManager)
                               .accountId(accountId)
                               .appId(appId)
                               .envId(envId)
                               .workflowExecutionId(workflowExecutionId)
                               .expressionFunctorToken(expressionFunctorToken)
                               .build();
    addFunctor(SecretManagerFunctorInterface.FUNCTOR_NAME, secretManagerFunctor);

    NgSecretManagerFunctorBuilder ngSecretManagerFunctorBuilder = NgSecretManagerFunctor.builder()
                                                                      .mode(mode)
                                                                      .accountId(accountId)
                                                                      .expressionFunctorToken(expressionFunctorToken)
                                                                      .secretManager(secretManager)
                                                                      .ngSecretService(ngSecretService);

    if (EmptyPredicate.isNotEmpty(taskSetupAbstractions)) {
      ngSecretManagerFunctorBuilder.orgId(taskSetupAbstractions.get("orgIdentifier"))
          .projectId(taskSetupAbstractions.get("projectIdentifier"));
    }

    ngSecretManagerFunctor = ngSecretManagerFunctorBuilder.build();
    addFunctor(NgSecretManagerFunctorInterface.FUNCTOR_NAME, ngSecretManagerFunctor);

    addFunctor(ImageSecretFunctor.FUNCTOR_NAME, new ImageSecretFunctor());

    sweepingOutputSecretFunctor =
        SweepingOutputSecretFunctor.builder().mode(mode).simpleEncryption(new SimpleEncryption()).build();

    addFunctor("sweepingOutputSecrets", sweepingOutputSecretFunctor);
  }
}
