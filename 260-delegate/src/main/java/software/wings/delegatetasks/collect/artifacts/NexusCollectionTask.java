package software.wings.delegatetasks.collect.artifacts;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.Module;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.config.NexusConfig;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.helpers.ext.nexus.NexusService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Created by srinivas on 4/4/17.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class NexusCollectionTask extends AbstractDelegateRunnableTask {
  @Inject private NexusService nexusService;

  @Inject private DelegateFileManager delegateFileManager;

  @Inject private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;

  public NexusCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public ListNotifyResponseData run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ListNotifyResponseData run(Object[] parameters) {
    try {
      return run((NexusConfig) parameters[0], (List<EncryptedDataDetail>) parameters[1],
          (ArtifactStreamAttributes) parameters[2], (Map<String, String>) parameters[3]);
    } catch (Exception e) {
      log.error("Exception occurred while collecting artifact", e);
      return new ListNotifyResponseData();
    }
  }

  public ListNotifyResponseData run(NexusConfig nexusConfig, List<EncryptedDataDetail> encryptionDetails,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata) {
    ListNotifyResponseData res = new ListNotifyResponseData();
    try {
      log.info("Collecting artifact {}  from Nexus server {}", nexusConfig.getNexusUrl());
      nexusService.downloadArtifacts(nexusConfig, encryptionDetails, artifactStreamAttributes, artifactMetadata,
          getDelegateId(), getTaskId(), getAccountId(), res);

    } catch (Exception e) {
      log.warn("Exception: " + ExceptionUtils.getMessage(e), e);
    }
    return res;
  }
}
