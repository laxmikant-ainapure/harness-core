package software.wings.service.impl.artifact;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_STREAM_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.service.intfc.ArtifactStreamService;

import com.google.inject.Inject;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ArtifactStreamSettingAttributePTaskManagerTest extends CategoryTest {
  private static final String PERPETUAL_TASK_ID = "PERPETUAL_TASK_ID";

  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private ArtifactStreamPTaskHelper artifactStreamPTaskHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private PerpetualTaskService perpetualTaskService;

  @Inject @InjectMocks ArtifactStreamSettingAttributePTaskManager manager;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setUp() {
    enableFeatureFlag();
  }

  @Test(expected = Test.None.class)
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnSaved() {
    manager.onSaved(prepareSettingAttribute());
  }

  @Test
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnUpdated() {
    SettingAttribute settingAttribute = prepareSettingAttribute();
    ArtifactStream artifactStream = prepareArtifactStream();
    artifactStream.setPerpetualTaskId(PERPETUAL_TASK_ID);
    when(artifactStreamService.listAllBySettingId(SETTING_ID))
        .thenReturn(asList(artifactStream, prepareArtifactStream()));

    disableFeatureFlag();
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, never()).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));

    enableFeatureFlag();
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, times(1)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));

    when(artifactStreamService.listAllBySettingId(SETTING_ID)).thenReturn(Collections.emptyList());
    manager.onUpdated(settingAttribute, settingAttribute);
    verify(perpetualTaskService, times(1)).resetTask(eq(ACCOUNT_ID), eq(PERPETUAL_TASK_ID), eq(null));
  }

  @Test(expected = Test.None.class)
  @Owner(developers = OwnerRule.GARVIT)
  @Category(UnitTests.class)
  public void testOnDeleted() {
    manager.onDeleted(prepareSettingAttribute());
  }

  private void enableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(true);
  }

  private void disableFeatureFlag() {
    when(featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, ACCOUNT_ID)).thenReturn(false);
  }

  private SettingAttribute prepareSettingAttribute() {
    return aSettingAttribute().withUuid(SETTING_ID).withAccountId(ACCOUNT_ID).build();
  }

  private ArtifactStream prepareArtifactStream() {
    return DockerArtifactStream.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(ARTIFACT_STREAM_ID)
        .settingId(SETTING_ID)
        .imageName("wingsplugins/todolist")
        .autoPopulate(true)
        .serviceId(SERVICE_ID)
        .build();
  }
}
