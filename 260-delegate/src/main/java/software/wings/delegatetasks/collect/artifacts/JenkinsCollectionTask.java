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

import software.wings.beans.JenkinsConfig;
import software.wings.beans.artifact.Artifact.ArtifactMetadataKeys;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;

import com.google.inject.Inject;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by rishi on 12/14/16.
 */
@OwnedBy(CDC)
@Slf4j
@TargetModule(Module._930_DELEGATE_TASKS)
public class JenkinsCollectionTask extends AbstractDelegateRunnableTask {
  @Inject private JenkinsUtils jenkinsUtil;
  @Inject private EncryptionService encryptionService;
  @Inject private ArtifactCollectionTaskHelper artifactCollectionTaskHelper;

  public JenkinsCollectionTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public ListNotifyResponseData run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ListNotifyResponseData run(Object[] parameters) {
    return run((JenkinsConfig) parameters[0], (List<EncryptedDataDetail>) parameters[1], (String) parameters[2],
        (List<String>) parameters[3], (Map<String, String>) parameters[4]);
  }

  public ListNotifyResponseData run(JenkinsConfig jenkinsConfig, List<EncryptedDataDetail> encryptionDetails,
      String jobName, List<String> artifactPaths, Map<String, String> arguments) {
    ListNotifyResponseData res = new ListNotifyResponseData();

    try {
      encryptionService.decrypt(jenkinsConfig, encryptionDetails, false);
      Jenkins jenkins = jenkinsUtil.getJenkins(jenkinsConfig);

      for (String artifactPath : artifactPaths) {
        log.info("Collecting artifact {} of job {}", artifactPath, jobName);
        Pair<String, InputStream> fileInfo =
            jenkins.downloadArtifact(jobName, arguments.get(ArtifactMetadataKeys.buildNo), artifactPath);
        artifactCollectionTaskHelper.addDataToResponse(
            fileInfo, artifactPath, res, getDelegateId(), getTaskId(), getAccountId());
      }
    } catch (Exception e) {
      log.warn("Exception: " + ExceptionUtils.getMessage(e), e);
      // TODO: better error handling

      //      if (e instanceof WingsException)
      //        WingsException ex = (WingsException) e;
      //        errorMessage = Joiner.on(",").join(ex.getResponseMessageList().stream()
      //            .map(responseMessage ->
      //            MessageManager.getInstance().getResponseMessage(responseMessage.getCode(),
      //            ex.getParams()).getMessage()) .collect(toList()));
      //      } else {
      //        errorMessage = e.getMessage();
      //      }
      //      executionStatus = executionStatus.FAILED;
      //      jenkinsExecutionResponse.setErrorMessage(errorMessage);
    }

    return res;
  }
}
