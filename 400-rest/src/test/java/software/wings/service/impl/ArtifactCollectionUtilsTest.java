package software.wings.service.impl;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.artifact.Artifact.Builder.anArtifact;
import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.artifact.ArtifactStreamType.SFTP;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.utils.ArtifactType.JAR;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.DockerConfig;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.artifact.AmazonS3ArtifactStream;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.Artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.delegatetasks.buildsource.BuildSourceParameters;
import software.wings.delegatetasks.buildsource.BuildSourceParameters.BuildSourceRequestType;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.SettingsService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mongodb.morphia.query.MorphiaIterator;
import org.mongodb.morphia.query.Query;

public class ArtifactCollectionUtilsTest extends WingsBaseTest {
  @Mock private SettingsService settingsService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Mock private ArtifactService artifactService;
  @Mock private Query<Artifact> artifactQuery;
  @Mock private MorphiaIterator<Artifact, Artifact> artifactIterator;

  @Inject @InjectMocks private ArtifactCollectionUtils artifactCollectionUtils;
  private static final String SCRIPT_STRING = "echo Hello World!! and echo ${secrets.getValue(My Secret)}";

  @Before
  public void setUp() {
    when(artifactService.prepareArtifactWithMetadataQuery(any(ArtifactStream.class))).thenReturn(artifactQuery);
    when(artifactQuery.fetch()).thenReturn(artifactIterator);
    when(artifactIterator.hasNext()).thenReturn(true).thenReturn(false);
    when(artifactIterator.next()).thenReturn(anArtifact().build());
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldSkipArtifactStreamIterationExcessiveFailedAttempts() {
    ArtifactStream artifactStream = DockerArtifactStream.builder().accountId(ACCOUNT_ID).build();
    artifactStream.setFailedCronAttempts(ArtifactCollectionResponseHandler.MAX_FAILED_ATTEMPTS + 1);

    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true)).isTrue();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldSkipArtifactStreamIterationForInvalidSetting() {
    when(settingsService.getOnlyConnectivityError(SETTING_ID)).thenReturn(null);
    ArtifactStream artifactStream = DockerArtifactStream.builder().accountId(ACCOUNT_ID).settingId(SETTING_ID).build();

    artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldSkipArtifactStreamIterationForConnectivityError() {
    when(settingsService.getOnlyConnectivityError(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().withConnectivityError("err").build());
    ArtifactStream artifactStream = DockerArtifactStream.builder().accountId(ACCOUNT_ID).settingId(SETTING_ID).build();

    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true)).isTrue();
    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, false)).isTrue();
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldNotSkipArtifactStreamIterationForConnectivityError() {
    when(settingsService.getOnlyConnectivityError(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    ArtifactStream artifactStream = DockerArtifactStream.builder().accountId(ACCOUNT_ID).settingId(SETTING_ID).build();

    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true)).isFalse();
    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, false)).isFalse();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldNotSkipArtifactStreamIterationForConnectivityErrorForCustomArtifactSource() {
    ArtifactStream artifactStream = CustomArtifactStream.builder().accountId(ACCOUNT_ID).build();
    assertThat(artifactCollectionUtils.skipArtifactStreamIteration(artifactStream, true)).isFalse();
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareValidateTaskForCustomArtifactStream() {
    ArtifactStream customArtifactStream = CustomArtifactStream.builder()
                                              .accountId(ACCOUNT_ID)
                                              .appId(APP_ID)
                                              .uuid(ARTIFACT_STREAM_ID)
                                              .serviceId(SERVICE_ID)
                                              .name("Custom Artifact Stream" + System.currentTimeMillis())
                                              .scripts(asList(CustomArtifactStream.Script.builder()
                                                                  .action(CustomArtifactStream.Action.FETCH_VERSIONS)
                                                                  .scriptString(SCRIPT_STRING)
                                                                  .build()))
                                              .tags(asList("Delegate Tag"))
                                              .build();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(customArtifactStream);
    final DelegateTask delegateTask = artifactCollectionUtils.prepareValidateTask(ARTIFACT_STREAM_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getTags()).contains("Delegate Tag");
    TaskData data = delegateTask.getData();
    assertThat(data.getTaskType()).isEqualTo(TaskType.BUILD_SOURCE_TASK.name());
    assertThat(data.getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(1));
    assertThat(delegateTask.getExpiry()).isNotZero();
    BuildSourceParameters parameters = (BuildSourceParameters) data.getParameters()[0];
    assertThat(parameters.getBuildSourceRequestType()).isEqualTo(BuildSourceRequestType.GET_BUILDS);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareValidateTaskForS3ArtifactStream() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .uuid(ARTIFACT_STREAM_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .name("Amazon S3")
                                                        .serviceId(SERVICE_ID)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(amazonS3ArtifactStream);
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withAccountId(ACCOUNT_ID)
                        .withValue(AwsConfig.builder().tag("AWS Tag").build())
                        .build());
    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder()
                        .uuid(SERVICE_ID)
                        .appId(APP_ID)
                        .name("SERVICE_NAME")
                        .description("SERVICE_DESC")
                        .artifactType(JAR)
                        .build());

    final DelegateTask delegateTask = artifactCollectionUtils.prepareValidateTask(ARTIFACT_STREAM_ID);

    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(delegateTask.getTags()).contains("AWS Tag");
    TaskData data = delegateTask.getData();
    assertThat(data.getTaskType()).isEqualTo(TaskType.BUILD_SOURCE_TASK.name());
    assertThat(data.getTimeout()).isEqualTo(TimeUnit.MINUTES.toMillis(1));
    BuildSourceParameters parameters = (BuildSourceParameters) data.getParameters()[0];
    assertThat(parameters.getBuildSourceRequestType()).isEqualTo(BuildSourceRequestType.GET_BUILDS);
    assertThat(parameters.getSettingValue()).isNotNull();
    assertThat(parameters.getEncryptedDataDetails()).isNotNull();
    assertThat(delegateTask.getExpiry()).isNotZero();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareValidateTaskArtifactStreamNotExists() {
    artifactCollectionUtils.prepareValidateTask(ARTIFACT_STREAM_ID);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareValidateTaskSettingAttributeNotExists() {
    AmazonS3ArtifactStream amazonS3ArtifactStream = AmazonS3ArtifactStream.builder()
                                                        .accountId(ACCOUNT_ID)
                                                        .uuid(ARTIFACT_STREAM_ID)
                                                        .appId(APP_ID)
                                                        .settingId(SETTING_ID)
                                                        .jobname("harnessapps")
                                                        .name("Amazon S3")
                                                        .serviceId(SERVICE_ID)
                                                        .artifactPaths(asList("dev/todolist.war"))
                                                        .build();

    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(amazonS3ArtifactStream);
    artifactCollectionUtils.prepareValidateTask(ARTIFACT_STREAM_ID);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareCustomBuildSourceParameters() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID))
        .thenReturn(CustomArtifactStream.builder()
                        .accountId(ACCOUNT_ID)
                        .appId(APP_ID)
                        .uuid(ARTIFACT_STREAM_ID)
                        .serviceId(SERVICE_ID)
                        .name("Custom Artifact Stream" + System.currentTimeMillis())
                        .scripts(asList(CustomArtifactStream.Script.builder()
                                            .action(CustomArtifactStream.Action.FETCH_VERSIONS)
                                            .scriptString(SCRIPT_STRING)
                                            .build()))
                        .tags(asList("Delegate Tag"))
                        .build());

    BuildSourceParameters buildSourceParameters =
        artifactCollectionUtils.prepareBuildSourceParameters(ARTIFACT_STREAM_ID);
    assertThat(buildSourceParameters).isNotNull();
    assertThat(buildSourceParameters.isCollection()).isTrue();
    assertThat(buildSourceParameters.getBuildSourceRequestType()).isEqualTo(BuildSourceRequestType.GET_BUILDS);
    assertThat(buildSourceParameters.getArtifactStreamAttributes().getCustomArtifactStreamScript())
        .isEqualTo(SCRIPT_STRING);
    assertThat(buildSourceParameters.getArtifactStreamAttributes().getArtifactStreamType()).isEqualTo(CUSTOM.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category({UnitTests.class})
  public void shouldPrepareDockerBuildSourceParameters() {
    when(artifactStreamService.get(ARTIFACT_STREAM_ID)).thenReturn(constructDockerArtifactStream());

    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withAccountId(ACCOUNT_ID)
                        .withValue(DockerConfig.builder().dockerRegistryUrl("https://harness.dockerhub.com").build())
                        .build());

    when(artifactStreamServiceBindingService.getService(APP_ID, ARTIFACT_STREAM_ID, true))
        .thenReturn(Service.builder()
                        .uuid(SERVICE_ID)
                        .appId(APP_ID)
                        .name("SERVICE_NAME")
                        .description("SERVICE_DESC")
                        .artifactType(JAR)
                        .build());

    BuildSourceParameters buildSourceParameters =
        artifactCollectionUtils.prepareBuildSourceParameters(ARTIFACT_STREAM_ID);
    assertThat(buildSourceParameters).isNotNull();
    assertThat(buildSourceParameters.getAccountId()).isNotNull();
    assertThat(buildSourceParameters.getBuildSourceRequestType()).isEqualTo(BuildSourceRequestType.GET_BUILDS);
    assertThat(buildSourceParameters.getArtifactStreamAttributes().getArtifactStreamType())
        .isEqualTo(ArtifactStreamType.DOCKER.name());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotProcessNullBuildSourceResponse() {
    artifactCollectionUtils.processBuilds(constructDockerArtifactStream(), new ArrayList<>());
    verify(artifactService, never()).create(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldNotProcessIfNoArtifactStream() {
    artifactCollectionUtils.processBuilds(null, asList(aBuildDetails().build()));

    verify(artifactService, never()).create(any());
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldProcessBuilds() {
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withAccountId(ACCOUNT_ID)
                        .withValue(DockerConfig.builder().dockerRegistryUrl("https://harness.dockerhub.com").build())
                        .build());

    artifactCollectionUtils.processBuilds(constructDockerArtifactStream(), asList(aBuildDetails().build()));

    verify(artifactService).create(any(Artifact.class));
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldVerifyArtifactFileNameForS3() {
    when(settingsService.get(SETTING_ID))
        .thenReturn(SettingAttribute.Builder.aSettingAttribute()
                        .withValue(AwsConfig.builder()
                                       .accessKey("accessKey".toCharArray())
                                       .secretKey("secretKey".toCharArray())
                                       .build())
                        .build());
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails()
                                    .withNumber("testfolder/todolist-1.war")
                                    .withRevision("testfolder/todolist-1.war")
                                    .withArtifactPath("testfolder/todolist-1.war")
                                    .build();
    Artifact artifact = artifactCollectionUtils.getArtifact(constructS3ArtifactStream(), buildDetails);
    assertThat(artifact.getMetadata().get(ArtifactMetadataKeys.artifactFileName)).isEqualTo("todolist-1.war");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testSupportsCleanup() {
    List<ArtifactStreamType> supported = asList(DOCKER, AMI, ARTIFACTORY, GCR, ECR, ACR, NEXUS);
    List<ArtifactStreamType> unsupported = asList(JENKINS, BAMBOO, AMAZON_S3, GCS, SMB, SFTP, AZURE_ARTIFACTS, CUSTOM);
    for (ArtifactStreamType artifactStreamType : supported) {
      assertThat(ArtifactCollectionUtils.supportsCleanup(artifactStreamType.name())).isTrue();
    }
    for (ArtifactStreamType artifactStreamType : unsupported) {
      assertThat(ArtifactCollectionUtils.supportsCleanup(artifactStreamType.name())).isFalse();
    }
  }

  private DockerArtifactStream constructDockerArtifactStream() {
    return DockerArtifactStream.builder()
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .build();
  }

  private AmazonS3ArtifactStream constructS3ArtifactStream() {
    return AmazonS3ArtifactStream.builder()
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .settingId(SETTING_ID)
        .artifactPaths(Lists.newArrayList("testfolder/"))
        .build();
  }
}
