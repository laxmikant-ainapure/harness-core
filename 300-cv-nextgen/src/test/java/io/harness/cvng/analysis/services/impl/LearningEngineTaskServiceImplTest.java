package io.harness.cvng.analysis.services.impl;

import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_FEEDBACK_ANALYSIS;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_LOG_ANALYSIS;
import static io.harness.cvng.analysis.entities.LearningEngineTask.LearningEngineTaskType.SERVICE_GUARD_TIME_SERIES;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.PRAVEEN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.analysis.entities.LearningEngineTask;
import io.harness.cvng.analysis.entities.LearningEngineTask.ExecutionStatus;
import io.harness.cvng.analysis.entities.TimeSeriesLearningEngineTask;
import io.harness.cvng.analysis.exceptions.ServiceGuardAnalysisException;
import io.harness.cvng.analysis.services.api.LearningEngineTaskService;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.assertj.core.util.Sets;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

public class LearningEngineTaskServiceImplTest extends CvNextGenTestBase {
  @Inject HPersistence hPersistence;

  @Mock HPersistence mockHPersistence;
  @Mock Query<LearningEngineTask> mockLETaskQuery;
  @Mock UpdateOperations<LearningEngineTask> mockUpdateOperations;
  @Mock FieldEnd mockField;

  @Inject private LearningEngineTaskService learningEngineTaskService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_noTaskType() {
    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNull();
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    hPersistence.save(taskToSave);

    task = learningEngineTaskService.getNextAnalysisTask();
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId1");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_withTaskType() {
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    hPersistence.save(taskToSave);

    taskToSave.setUuid("leTaskId2");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_TIME_SERIES);
    hPersistence.save(taskToSave);

    LearningEngineTask task = learningEngineTaskService.getNextAnalysisTask(Arrays.asList(SERVICE_GUARD_TIME_SERIES));
    assertThat(task).isNotNull();
    assertThat(task.getUuid()).isEqualTo("leTaskId2");
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetNextAnalysisTask_withTaskTypeNoTaskAvailable() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.QUEUED);
    hPersistence.save(taskToSave);

    taskToSave.setUuid("leTaskId2");
    taskToSave.setTaskStatus(ExecutionStatus.QUEUED);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_TIME_SERIES);
    hPersistence.save(taskToSave);

    LearningEngineTask task =
        learningEngineTaskService.getNextAnalysisTask(Arrays.asList(SERVICE_GUARD_FEEDBACK_ANALYSIS));
    assertThat(task).isNull();
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testCreateLearningEngineTasks() {
    LearningEngineTask taskToSave = getTaskToSave(null);
    List<String> tasks = learningEngineTaskService.createLearningEngineTasks(Arrays.asList(taskToSave));
    assertThat(tasks).isNotNull();
    assertThat(tasks.size()).isEqualTo(1);
    assertThat(tasks.get(0)).isEqualTo(taskToSave.getUuid());

    taskToSave = hPersistence.get(LearningEngineTask.class, tasks.get(0));
    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.QUEUED.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    Map<String, ExecutionStatus> statuses =
        learningEngineTaskService.getTaskStatus(Sets.newHashSet(Arrays.asList(taskToSave.getUuid())));

    assertThat(statuses.size()).isEqualTo(1);
    assertThat(statuses.containsKey(taskToSave.getUuid())).isTrue();
    assertThat(statuses.get(taskToSave.getUuid()).name()).isEqualTo(ExecutionStatus.RUNNING.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testGetTaskStatus_timeout() throws Exception {
    FieldUtils.writeField(learningEngineTaskService, "hPersistence", mockHPersistence, true);
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    taskToSave.setLastUpdatedAt(Instant.now().minus(1, ChronoUnit.HOURS).toEpochMilli());

    when(mockHPersistence.createQuery(LearningEngineTask.class)).thenReturn(mockLETaskQuery);
    when(mockHPersistence.createUpdateOperations(LearningEngineTask.class)).thenReturn(mockUpdateOperations);
    when(mockHPersistence.createQuery(LearningEngineTask.class, excludeAuthority)).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.filter(any(), any())).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.field(any())).thenReturn(mockField);
    when(mockField.in(any())).thenReturn(mockLETaskQuery);
    when(mockField.equal(any())).thenReturn(mockLETaskQuery);
    when(mockLETaskQuery.get()).thenReturn(taskToSave);
    when(mockLETaskQuery.asList()).thenReturn(Arrays.asList(taskToSave));

    Map<String, ExecutionStatus> statuses =
        learningEngineTaskService.getTaskStatus(Sets.newHashSet(Arrays.asList(taskToSave.getUuid())));

    assertThat(statuses.size()).isEqualTo(1);
    assertThat(statuses.containsKey(taskToSave.getUuid())).isTrue();
    assertThat(statuses.get(taskToSave.getUuid()).name()).isEqualTo(ExecutionStatus.TIMEOUT.name());
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkCompleted() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markCompleted(taskToSave.getUuid());

    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());

    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.SUCCESS.name());
  }

  @Test(expected = ServiceGuardAnalysisException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkCompleted_badTaskId() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markCompleted(null);
  }

  @Test
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkFailed() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markFailure(taskToSave.getUuid());
    taskToSave = hPersistence.get(LearningEngineTask.class, taskToSave.getUuid());

    assertThat(taskToSave.getTaskStatus().name()).isEqualTo(ExecutionStatus.FAILED.name());
  }

  @Test(expected = ServiceGuardAnalysisException.class)
  @Owner(developers = PRAVEEN)
  @Category(UnitTests.class)
  public void testMarkFailed_badTaskId() {
    LearningEngineTask taskToSave = getTaskToSave(ExecutionStatus.RUNNING);
    hPersistence.save(taskToSave);

    learningEngineTaskService.markFailure(null);
  }

  private LearningEngineTask getTaskToSave(ExecutionStatus taskStatus) {
    LearningEngineTask taskToSave = TimeSeriesLearningEngineTask.builder().build();
    taskToSave.setUuid("leTaskId1");
    taskToSave.setTaskStatus(taskStatus);
    taskToSave.setVerificationTaskId(generateUuid());
    taskToSave.setAnalysisType(SERVICE_GUARD_LOG_ANALYSIS);
    return taskToSave;
  }
}
