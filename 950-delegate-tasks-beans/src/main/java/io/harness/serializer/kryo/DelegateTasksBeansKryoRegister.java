package io.harness.serializer.kryo;

import io.harness.capability.AwsRegionParameters;
import io.harness.capability.CapabilityParameters;
import io.harness.capability.CapabilitySubjectPermission.PermissionResult;
import io.harness.capability.ChartMuseumParameters;
import io.harness.capability.HelmInstallationParameters;
import io.harness.capability.HttpConnectionParameters;
import io.harness.capability.ProcessExecutorParameters;
import io.harness.capability.SftpCapabilityParameters;
import io.harness.capability.SmbConnectionParameters;
import io.harness.capability.SocketConnectivityParameters;
import io.harness.capability.SystemEnvParameters;
import io.harness.capability.TestingCapability;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateStringProgressData;
import io.harness.delegate.beans.DelegateStringResponseData;
import io.harness.delegate.beans.DelegateTaskDetails;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.RemoteMethodReturnValueData;
import io.harness.delegate.beans.SecretDetail;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskParams;
import io.harness.delegate.beans.artifactory.ArtifactoryTaskResponse;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.OSType;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.AzureVMAuthType;
import io.harness.delegate.beans.azure.GalleryImageDefinitionDTO;
import io.harness.delegate.beans.azure.ManagementGroupData;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceApplicationSettingDTO;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceConnectionStringDTO;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceSettingConstants;
import io.harness.delegate.beans.azure.appservicesettings.AzureAppServiceSettingDTO;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.ci.CIBuildSetupTaskParams;
import io.harness.delegate.beans.ci.CIClusterType;
import io.harness.delegate.beans.ci.CIK8BuildTaskParams;
import io.harness.delegate.beans.ci.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.ExecuteCommandTaskParams;
import io.harness.delegate.beans.ci.K8ExecCommandParams;
import io.harness.delegate.beans.ci.K8ExecuteCommandTaskParams;
import io.harness.delegate.beans.ci.ShellScriptType;
import io.harness.delegate.beans.ci.k8s.CIContainerStatus;
import io.harness.delegate.beans.ci.k8s.CiK8sTaskResponse;
import io.harness.delegate.beans.ci.k8s.K8sTaskExecutionResponse;
import io.harness.delegate.beans.ci.k8s.PodStatus;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.CIK8ServicePodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerParams;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.EncryptedVariableWithType;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.HostAliasParams;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.PodParams;
import io.harness.delegate.beans.ci.pod.SSHKeyDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.ci.status.BuildStatusPushResponse;
import io.harness.delegate.beans.connector.ConnectorHeartbeatDelegateResponse;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.NoOpConnectorValidationParams;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectionTaskParams;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectionTaskResponse;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryValidationParams;
import io.harness.delegate.beans.connector.awsconnector.AwsDelegateTaskResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskType;
import io.harness.delegate.beans.connector.awsconnector.AwsValidateTaskResponse;
import io.harness.delegate.beans.connector.azureconnector.AzureContainerRegistryConnectorDTO;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskParams;
import io.harness.delegate.beans.connector.cvconnector.CVConnectorTaskResponse;
import io.harness.delegate.beans.connector.docker.DockerTestConnectionTaskParams;
import io.harness.delegate.beans.connector.docker.DockerTestConnectionTaskResponse;
import io.harness.delegate.beans.connector.docker.DockerValidationParams;
import io.harness.delegate.beans.connector.gcpkmsconnector.GcpKmsValidationParams;
import io.harness.delegate.beans.connector.jira.JiraConnectionTaskParams;
import io.harness.delegate.beans.connector.jira.connection.JiraTestConnectionTaskNGResponse;
import io.harness.delegate.beans.connector.k8Connector.K8sValidationParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskParams;
import io.harness.delegate.beans.connector.k8Connector.KubernetesConnectionTaskResponse;
import io.harness.delegate.beans.connector.nexusconnector.NexusValidationParams;
import io.harness.delegate.beans.connector.scm.ScmValidationParams;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectionTaskParams;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectionTaskResponse;
import io.harness.delegate.beans.connector.vaultconnector.VaultValidationParams;
import io.harness.delegate.beans.executioncapability.AlwaysFalseValidationCapability;
import io.harness.delegate.beans.executioncapability.AwsRegionCapability;
import io.harness.delegate.beans.executioncapability.CapabilityType;
import io.harness.delegate.beans.executioncapability.ChartMuseumCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.GitConnectionNGCapability;
import io.harness.delegate.beans.executioncapability.GitInstallationCapability;
import io.harness.delegate.beans.executioncapability.HelmInstallationCapability;
import io.harness.delegate.beans.executioncapability.HttpConnectionExecutionCapability;
import io.harness.delegate.beans.executioncapability.KustomizeCapability;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.delegate.beans.executioncapability.PcfConnectivityCapability;
import io.harness.delegate.beans.executioncapability.ProcessExecutorCapability;
import io.harness.delegate.beans.executioncapability.SSHHostValidationCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.beans.executioncapability.SmbConnectionCapability;
import io.harness.delegate.beans.executioncapability.SmtpCapability;
import io.harness.delegate.beans.executioncapability.SocketConnectivityExecutionCapability;
import io.harness.delegate.beans.executioncapability.SystemEnvCheckerCapability;
import io.harness.delegate.beans.executioncapability.WinrmHostValidationCapability;
import io.harness.delegate.beans.git.GitCommandExecutionResponse;
import io.harness.delegate.beans.git.GitCommandExecutionResponse.GitCommandStatus;
import io.harness.delegate.beans.git.GitCommandParams;
import io.harness.delegate.beans.git.GitCommandType;
import io.harness.delegate.beans.git.YamlGitConfigDTO;
import io.harness.delegate.beans.gitapi.GitApiFindPRTaskResponse;
import io.harness.delegate.beans.gitapi.GitApiRequestType;
import io.harness.delegate.beans.gitapi.GitApiResult;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.gitapi.GitApiTaskResponse;
import io.harness.delegate.beans.gitapi.GitRepoType;
import io.harness.delegate.beans.logstreaming.CommandUnitStatusProgress;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.nexus.NexusTaskParams;
import io.harness.delegate.beans.nexus.NexusTaskResponse;
import io.harness.delegate.beans.secrets.SSHConfigValidationTaskResponse;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.exception.DelegateRetryableException;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.ArtifactTaskType;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.docker.DockerArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.gcr.GcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsElbListenerRuleData;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.aws.LbDetailsForAlbTrafficShift;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.aws.LoadBalancerType;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.AzureTaskParameters;
import io.harness.delegate.task.azure.AzureTaskResponse;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceType;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppDeploymentSlotsParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppInstancesParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppListWebAppNamesParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppRollbackParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotSetupParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSlotShiftTrafficParameters;
import io.harness.delegate.task.azure.appservice.webapp.request.AzureWebAppSwapSlotsParameters;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppDeploymentSlotsResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppInstancesResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppListWebAppNamesResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppRollbackResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotResizeResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotShiftTrafficResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSwapSlotsResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.DeploymentSlotData;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMTaskParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.request.AzureARMDeploymentParameters;
import io.harness.delegate.task.azure.arm.request.AzureARMRollbackParameters;
import io.harness.delegate.task.azure.arm.response.AzureARMDeploymentResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListManagementGroupResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMListSubscriptionLocationsResponse;
import io.harness.delegate.task.azure.arm.response.AzureARMRollbackResponse;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;
import io.harness.delegate.task.azure.request.AzureVMSSDeployTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSGetVirtualMachineScaleSetParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancerBackendPoolsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListLoadBalancersNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListResourceGroupsNamesParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListSubscriptionsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVMDataParameters;
import io.harness.delegate.task.azure.request.AzureVMSSListVirtualMachineScaleSetsParameters;
import io.harness.delegate.task.azure.request.AzureVMSSSetupTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSSwitchRouteTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters;
import io.harness.delegate.task.azure.request.AzureVMSSTaskParameters.AzureVMSSTaskType;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSDeployTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSGetVirtualMachineScaleSetResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancerBackendPoolsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListLoadBalancersNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListResourceGroupsNamesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListSubscriptionsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVMDataResponse;
import io.harness.delegate.task.azure.response.AzureVMSSListVirtualMachineScaleSetsResponse;
import io.harness.delegate.task.azure.response.AzureVMSSSetupTaskResponse;
import io.harness.delegate.task.azure.response.AzureVMSSSwitchRoutesResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.delegate.task.azure.response.AzureVMSSTaskResponse;
import io.harness.delegate.task.ci.CIBuildPushParameters;
import io.harness.delegate.task.ci.CIBuildPushParameters.CIBuildPushTaskType;
import io.harness.delegate.task.ci.CIBuildStatusPushParameters;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.delegate.task.gcp.request.GcpRequest;
import io.harness.delegate.task.gcp.request.GcpValidationRequest;
import io.harness.delegate.task.gcp.response.GcpValidationTaskResponse;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.delegate.task.jira.JiraTaskNGParameters;
import io.harness.delegate.task.jira.response.JiraTaskNGResponse;
import io.harness.delegate.task.jira.response.JiraTaskNGResponse.JiraIssueData;
import io.harness.delegate.task.k8s.DirectK8sInfraDelegateConfig;
import io.harness.delegate.task.k8s.K8sApplyRequest;
import io.harness.delegate.task.k8s.K8sBGDeployRequest;
import io.harness.delegate.task.k8s.K8sBGDeployResponse;
import io.harness.delegate.task.k8s.K8sCanaryDeployRequest;
import io.harness.delegate.task.k8s.K8sCanaryDeployResponse;
import io.harness.delegate.task.k8s.K8sDeleteRequest;
import io.harness.delegate.task.k8s.K8sDeployRequest;
import io.harness.delegate.task.k8s.K8sDeployResponse;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sRollingDeployRequest;
import io.harness.delegate.task.k8s.K8sRollingDeployResponse;
import io.harness.delegate.task.k8s.K8sRollingRollbackDeployRequest;
import io.harness.delegate.task.k8s.K8sScaleRequest;
import io.harness.delegate.task.k8s.K8sScaleResponse;
import io.harness.delegate.task.k8s.K8sSwapServiceSelectorsRequest;
import io.harness.delegate.task.k8s.K8sTaskType;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.shell.ShellScriptApprovalTaskParameters;
import io.harness.delegate.task.shell.ShellScriptTaskParametersNG;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.delegate.task.spotinst.request.SpotInstDeployTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstGetElastigroupJsonParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupInstancesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstListElastigroupNamesParameters;
import io.harness.delegate.task.spotinst.request.SpotInstSetupTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstSwapRoutesTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters.SpotInstTaskType;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbDeployParameters;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSetupParameters;
import io.harness.delegate.task.spotinst.request.SpotinstTrafficShiftAlbSwapRoutesParameters;
import io.harness.delegate.task.spotinst.response.SpotInstDeployTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstGetElastigroupJsonResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupNamesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstSetupTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbDeployResponse;
import io.harness.delegate.task.spotinst.response.SpotinstTrafficShiftAlbSetupResponse;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskParameters;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.ng.core.dto.secrets.KerberosConfigDTO;
import io.harness.ng.core.dto.secrets.SSHAuthDTO;
import io.harness.ng.core.dto.secrets.SSHConfigDTO;
import io.harness.ng.core.dto.secrets.SSHCredentialType;
import io.harness.ng.core.dto.secrets.SSHKeyPathCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeyReferenceCredentialDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.ng.core.dto.secrets.SSHPasswordCredentialDTO;
import io.harness.ng.core.dto.secrets.TGTGenerationMethod;
import io.harness.ng.core.dto.secrets.TGTKeyTabFilePathSpecDTO;
import io.harness.ng.core.dto.secrets.TGTPasswordSpecDTO;
import io.harness.secretmanagerclient.SSHAuthScheme;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.serializer.KryoRegistrar;

import software.wings.beans.TaskType;

import com.esotericsoftware.kryo.Kryo;
import com.google.protobuf.UnknownFieldSet;
import org.eclipse.jgit.api.GitCommand;
import org.json.JSONArray;
import org.json.JSONObject;

public class DelegateTasksBeansKryoRegister implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(AlwaysFalseValidationCapability.class, 19036);
    kryo.register(AppDynamicsConnectionTaskParams.class, 19107);
    kryo.register(AppDynamicsConnectionTaskResponse.class, 19108);
    kryo.register(ArtifactFileMetadata.class, 19034);
    kryo.register(AwsElbListener.class, 5600);
    kryo.register(AwsElbListenerRuleData.class, 19035);
    kryo.register(AwsLoadBalancerDetails.class, 19024);
    kryo.register(AwsRegionCapability.class, 19008);
    kryo.register(AzureVMSSGetVirtualMachineScaleSetParameters.class, 19075);
    kryo.register(AzureVMSSGetVirtualMachineScaleSetResponse.class, 19080);
    kryo.register(AzureVMSSListResourceGroupsNamesParameters.class, 19076);
    kryo.register(AzureVMSSListResourceGroupsNamesResponse.class, 19081);
    kryo.register(AzureVMSSListSubscriptionsParameters.class, 19077);
    kryo.register(AzureVMSSListSubscriptionsResponse.class, 19082);
    kryo.register(AzureVMSSListVirtualMachineScaleSetsParameters.class, 19078);
    kryo.register(AzureVMSSListVirtualMachineScaleSetsResponse.class, 19083);
    kryo.register(AzureVMSSTaskExecutionResponse.class, 19084);
    kryo.register(AzureVMSSTaskParameters.class, 19079);
    kryo.register(AzureVMSSTaskResponse.class, 19085);
    kryo.register(AzureVMSSTaskType.class, 19086);
    kryo.register(CapabilityType.class, 19004);
    kryo.register(ChartMuseumCapability.class, 19038);
    kryo.register(TaskType.class, 5005);
    kryo.register(CommandExecutionResult.class, 5036);
    kryo.register(DelegateMetaInfo.class, 5372);
    kryo.register(DelegateRetryableException.class, 5521);
    kryo.register(DelegateTaskDetails.class, 19044);
    kryo.register(DelegateTaskNotifyResponseData.class, 5373);
    kryo.register(DelegateTaskPackage.class, 7150);
    kryo.register(DelegateTaskResponse.class, 5006);
    kryo.register(DelegateTaskResponse.ResponseCode.class, 5520);
    kryo.register(DirectK8sInfraDelegateConfig.class, 19102);
    kryo.register(ErrorNotifyResponseData.class, 5213);
    kryo.register(FetchType.class, 8030);
    kryo.register(GitCommand.class, 19062);
    kryo.register(GitCommandExecutionResponse.class, 19067);
    kryo.register(GitCommandParams.class, 19061);
    kryo.register(GitCommandStatus.class, 19074);
    kryo.register(GitCommandType.class, 19071);
    kryo.register(GitStoreDelegateConfig.class, 19104);
    kryo.register(HelmInstallationCapability.class, 19120);
    kryo.register(HttpConnectionExecutionCapability.class, 19003);
    kryo.register(HttpTaskParameters.class, 20002);
    kryo.register(K8sDeployRequest.class, 19101);
    kryo.register(K8sDeployResponse.class, 19099);
    kryo.register(K8sManifestDelegateConfig.class, 19103);
    kryo.register(K8sRollingDeployRequest.class, 19100);
    kryo.register(K8sTaskType.class, 7125);
    kryo.register(KubernetesConnectionTaskParams.class, 19057);
    kryo.register(KubernetesConnectionTaskResponse.class, 19056);
    kryo.register(KustomizeCapability.class, 7437);
    kryo.register(LbDetailsForAlbTrafficShift.class, 19037);
    kryo.register(LoadBalancerDetailsForBGDeployment.class, 19031);
    kryo.register(LoadBalancerType.class, 19032);
    kryo.register(PcfAutoScalarCapability.class, 19122);
    kryo.register(PcfConnectivityCapability.class, 19123);
    kryo.register(PcfManifestsPackage.class, 19033);
    kryo.register(ProcessExecutorCapability.class, 19007);
    kryo.register(RemoteMethodReturnValueData.class, 5122);
    kryo.register(SecretDetail.class, 19001);
    kryo.register(SelectorCapability.class, 19098);
    kryo.register(ShellScriptApprovalTaskParameters.class, 20001);
    kryo.register(SmbConnectionCapability.class, 19119);
    kryo.register(SmtpCapability.class, 19121);
    kryo.register(SocketConnectivityExecutionCapability.class, 19009);
    kryo.register(SSHHostValidationCapability.class, 19125);
    kryo.register(SpotInstDeployTaskParameters.class, 19018);
    kryo.register(SpotInstDeployTaskResponse.class, 19017);
    kryo.register(SpotInstGetElastigroupJsonParameters.class, 19025);
    kryo.register(SpotInstGetElastigroupJsonResponse.class, 19028);
    kryo.register(SpotInstListElastigroupInstancesParameters.class, 19026);
    kryo.register(SpotInstListElastigroupInstancesResponse.class, 19029);
    kryo.register(SpotInstListElastigroupNamesParameters.class, 19027);
    kryo.register(SpotInstListElastigroupNamesResponse.class, 19030);
    kryo.register(SpotInstSetupTaskParameters.class, 19012);
    kryo.register(SpotInstSetupTaskResponse.class, 19016);
    kryo.register(SpotInstSwapRoutesTaskParameters.class, 19023);
    kryo.register(SpotInstTaskExecutionResponse.class, 19014);
    kryo.register(SpotInstTaskParameters.class, 19011);
    kryo.register(SpotInstTaskResponse.class, 19015);
    kryo.register(SpotInstTaskType.class, 19013);
    kryo.register(SpotinstTrafficShiftAlbDeployParameters.class, 19041);
    kryo.register(SpotinstTrafficShiftAlbDeployResponse.class, 19042);
    kryo.register(SpotinstTrafficShiftAlbSetupParameters.class, 19039);
    kryo.register(SpotinstTrafficShiftAlbSetupResponse.class, 19040);
    kryo.register(SpotinstTrafficShiftAlbSwapRoutesParameters.class, 19043);
    kryo.register(SystemEnvCheckerCapability.class, 19022);
    kryo.register(WinrmHostValidationCapability.class, 19124);
    kryo.register(TaskData.class, 19002);
    kryo.register(YamlGitConfigDTO.class, 19087);
    kryo.register(YamlGitConfigDTO.RootFolder.class, 19095);
    kryo.register(AzureVMSSPreDeploymentData.class, 19106);
    kryo.register(SplunkConnectionTaskParams.class, 19109);
    kryo.register(SplunkConnectionTaskResponse.class, 19110);
    kryo.register(DockerTestConnectionTaskParams.class, 19117);
    kryo.register(DockerTestConnectionTaskResponse.class, 19118);
    kryo.register(ArtifactTaskParameters.class, 19300);
    kryo.register(ArtifactTaskResponse.class, 19301);
    kryo.register(DockerArtifactDelegateRequest.class, 19302);
    kryo.register(DockerArtifactDelegateResponse.class, 19303);
    kryo.register(ArtifactTaskType.class, 19304);
    kryo.register(ArtifactDelegateResponse.class, 19305);
    kryo.register(ArtifactTaskExecutionResponse.class, 19306);
    kryo.register(ArtifactBuildDetailsNG.class, 19307);
    kryo.register(ArtifactSourceType.class, 19308);
    kryo.register(DelegateStringResponseData.class, 19309);
    kryo.register(AzureVMSSSetupTaskParameters.class, 19310);
    kryo.register(AzureVMSSListVMDataParameters.class, 19311);
    kryo.register(AzureVMSSListLoadBalancersNamesParameters.class, 19312);
    kryo.register(AzureVMSSListLoadBalancerBackendPoolsNamesParameters.class, 19313);
    kryo.register(AzureVMSSDeployTaskParameters.class, 19314);
    kryo.register(AzureLoadBalancerDetailForBGDeployment.class, 19315);
    kryo.register(AzureVMInstanceData.class, 19316);
    kryo.register(AzureVMSSDeployTaskResponse.class, 19317);
    kryo.register(AzureVMSSListLoadBalancerBackendPoolsNamesResponse.class, 19318);
    kryo.register(AzureVMSSListLoadBalancersNamesResponse.class, 19319);
    kryo.register(AzureVMSSListVMDataResponse.class, 19320);
    kryo.register(AzureVMSSSetupTaskResponse.class, 19321);
    kryo.register(AzureVMSSSwitchRoutesResponse.class, 19322);
    kryo.register(AzureVMSSSwitchRouteTaskParameters.class, 19323);
    kryo.register(GitFetchRequest.class, 19324);
    kryo.register(GitFetchFilesConfig.class, 19325);
    kryo.register(GitFetchResponse.class, 19326);
    kryo.register(TaskStatus.class, 19327);
    kryo.register(K8sRollingDeployResponse.class, 19328);
    kryo.register(StepStatusTaskParameters.class, 19329);
    kryo.register(StepStatusTaskResponseData.class, 19330);
    kryo.register(StepStatus.class, 19331);
    kryo.register(StepMapOutput.class, 19332);
    kryo.register(StepExecutionStatus.class, 19333);
    kryo.register(GitConnectionNGCapability.class, 19334);
    kryo.register(GitInstallationCapability.class, 19550);
    kryo.register(GcpRequest.RequestType.class, 19335);
    kryo.register(GcpValidationRequest.class, 19336);
    kryo.register(GcpValidationTaskResponse.class, 19337);
    kryo.register(SSHConfigValidationTaskResponse.class, 19338);
    kryo.register(AzureConfigDTO.class, 19339);
    kryo.register(AzureVMAuthDTO.class, 19340);
    kryo.register(AzureVMAuthType.class, 19341);
    kryo.register(ExecutionCapability.class, 19343);
    kryo.register(AwsDelegateTaskResponse.class, 19356);
    kryo.register(AwsTaskParams.class, 19359);
    kryo.register(AwsTaskType.class, 19360);
    kryo.register(AwsValidateTaskResponse.class, 19361);
    kryo.register(AzureMachineImageArtifactDTO.class, 19363);
    kryo.register(GalleryImageDefinitionDTO.class, 19364);
    kryo.register(OSType.class, 19365);
    kryo.register(ImageType.class, 19366);
    kryo.register(JiraTaskNGParameters.class, 19367);
    kryo.register(JiraTaskNGResponse.class, 19368);
    kryo.register(JiraIssueData.class, 19369);
    kryo.register(JiraConnectionTaskParams.class, 19370);
    kryo.register(JiraTestConnectionTaskNGResponse.class, 19371);
    kryo.register(JSONArray.class, 19373);
    kryo.register(JSONObject.class, 19374);
    kryo.register(CVConnectorTaskParams.class, 19375);
    kryo.register(CVConnectorTaskResponse.class, 19376);
    kryo.register(BuildStatusPushResponse.class, 19377);
    kryo.register(BuildStatusPushResponse.Status.class, 19378);
    kryo.register(CIBuildPushParameters.class, 19379);
    kryo.register(CIBuildPushTaskType.class, 19380);
    kryo.register(CIBuildStatusPushParameters.class, 19381);
    kryo.register(AzureWebAppListWebAppDeploymentSlotsParameters.class, 19382);
    kryo.register(AzureWebAppListWebAppNamesParameters.class, 19383);
    kryo.register(AzureWebAppListWebAppDeploymentSlotsResponse.class, 19384);
    kryo.register(AzureWebAppListWebAppNamesResponse.class, 19385);
    kryo.register(AzureAppServiceTaskParameters.class, 19386);
    kryo.register(AzureAppServiceTaskResponse.class, 19387);
    kryo.register(AzureTaskParameters.class, 19388);
    kryo.register(AzureTaskResponse.class, 19389);
    kryo.register(AzureAppServiceTaskType.class, 19390);
    kryo.register(AzureAppServiceType.class, 19391);
    kryo.register(AzureTaskExecutionResponse.class, 19393);
    kryo.register(CIBuildSetupTaskParams.class, 19394);
    kryo.register(CIK8BuildTaskParams.class, 19395);
    kryo.register(CIK8PodParams.class, 19396);
    kryo.register(CIBuildSetupTaskParams.Type.class, 19397);
    kryo.register(CIContainerType.class, 19398);
    kryo.register(CIK8ContainerParams.class, 19399);
    kryo.register(ContainerParams.class, 19400);
    kryo.register(ContainerResourceParams.class, 19401);
    kryo.register(PodParams.class, 19402);
    kryo.register(CIClusterType.class, 19403);
    kryo.register(ExecuteCommandTaskParams.class, 19404);
    kryo.register(K8ExecuteCommandTaskParams.class, 19405);
    kryo.register(K8ExecCommandParams.class, 19406);
    kryo.register(ExecuteCommandTaskParams.Type.class, 19407);
    kryo.register(ShellScriptType.class, 19408);
    kryo.register(CIK8CleanupTaskParams.class, 19409);
    kryo.register(ImageDetailsWithConnector.class, 19410);
    kryo.register(EncryptedVariableWithType.class, 19411);
    kryo.register(EncryptedVariableWithType.Type.class, 19412);
    kryo.register(ContainerSecrets.class, 19413);
    kryo.register(PVCParams.class, 19414);
    kryo.register(SecretVariableDTO.class, 19415);
    kryo.register(SecretVariableDTO.Type.class, 19416);
    kryo.register(SecretVariableDetails.class, 19417);
    kryo.register(ConnectorDetails.class, 193418);
    kryo.register(CIK8ServicePodParams.class, 19419);
    kryo.register(HostAliasParams.class, 19420);
    kryo.register(CiK8sTaskResponse.class, 19421);
    kryo.register(K8sTaskExecutionResponse.class, 19422);
    kryo.register(PodStatus.class, 19423);
    kryo.register(PodStatus.Status.class, 19424);
    kryo.register(CIContainerStatus.class, 19425);
    kryo.register(CIContainerStatus.Status.class, 19426);
    kryo.register(ConnectorHeartbeatDelegateResponse.class, 19427);
    kryo.register(DelegateStringProgressData.class, 19428);
    kryo.register(GitSCMType.class, 19429);
    kryo.register(EnvVariableEnum.class, 19430);
    kryo.register(AzureWebAppListWebAppInstancesParameters.class, 19431);
    kryo.register(AzureWebAppListWebAppInstancesResponse.class, 19432);
    kryo.register(CommandUnitStatusProgress.class, 19433);
    kryo.register(K8sBGDeployRequest.class, 19435);
    kryo.register(K8sBGDeployResponse.class, 19436);
    kryo.register(K8sApplyRequest.class, 19437);
    kryo.register(HttpTaskParametersNg.class, 19438);
    kryo.register(HttpStepResponse.class, 19439);

    kryo.register(DeploymentSlotData.class, 19457);
    kryo.register(ShellScriptTaskParametersNG.class, 19463);
    kryo.register(ShellScriptTaskResponseNG.class, 19464);
    kryo.register(AzureWebAppSlotSetupParameters.class, 19465);
    kryo.register(AzureWebAppRollbackParameters.class, 19466);
    kryo.register(AzureWebAppSlotShiftTrafficParameters.class, 19467);
    kryo.register(AzureWebAppSwapSlotsParameters.class, 19468);
    kryo.register(AzureRegistryType.class, 19469);
    kryo.register(AzureAppServiceApplicationSettingDTO.class, 19470);
    kryo.register(AzureAppServiceConnectionStringDTO.class, 19471);
    kryo.register(AzureWebAppSlotSetupResponse.class, 19475);
    kryo.register(AzureAppServicePreDeploymentData.class, 19476);
    kryo.register(AzureWebAppRollbackResponse.class, 19477);
    kryo.register(AzureWebAppSlotResizeResponse.class, 19478);
    kryo.register(AzureAppDeploymentData.class, 19479);
    kryo.register(AzureWebAppSlotShiftTrafficResponse.class, 19480);
    kryo.register(AzureWebAppSwapSlotsResponse.class, 19481);
    kryo.register(AzureAppServiceSettingConstants.class, 19485);
    kryo.register(AzureAppServiceSettingDTO.class, 19486);
    kryo.register(NexusTaskResponse.class, 19494);
    kryo.register(ArtifactoryTaskResponse.class, 19495);
    kryo.register(NexusTaskParams.class, 19496);
    kryo.register(ArtifactoryTaskParams.class, 19497);
    kryo.register(NexusTaskParams.TaskType.class, 19504);
    kryo.register(ArtifactoryTaskParams.TaskType.class, 19505);
    kryo.register(AzureContainerRegistryConnectorDTO.class, 19507);
    kryo.register(SSHKeyDetails.class, 19513);
    kryo.register(GitRepoType.class, 19514);
    kryo.register(GitApiRequestType.class, 19515);
    kryo.register(GitApiTaskParams.class, 19516);
    kryo.register(GitApiFindPRTaskResponse.class, 19517);
    kryo.register(GitApiTaskResponse.class, 19518);
    kryo.register(GitApiResult.class, 19519);
    kryo.register(DockerValidationParams.class, 19531);
    kryo.register(K8sValidationParams.class, 19532);
    kryo.register(ScmValidationParams.class, 19533);
    kryo.register(VaultValidationParams.class, 19534);
    kryo.register(GcpKmsValidationParams.class, 19535);
    kryo.register(NoOpConnectorValidationParams.class, 19536);
    kryo.register(ConnectorValidationParams.class, 19537);
    kryo.register(NexusValidationParams.class, 19538);
    kryo.register(ArtifactoryValidationParams.class, 19539);

    kryo.register(SecretType.class, 543214);
    kryo.register(ValueType.class, 543215);
    kryo.register(SSHKeySpecDTO.class, 543222);
    kryo.register(SSHAuthScheme.class, 543223);
    kryo.register(SSHConfigDTO.class, 543224);
    kryo.register(TGTGenerationMethod.class, 543226);
    kryo.register(TGTPasswordSpecDTO.class, 543227);
    kryo.register(SSHCredentialType.class, 543228);
    kryo.register(TGTKeyTabFilePathSpecDTO.class, 543229);
    kryo.register(SSHKeyReferenceCredentialDTO.class, 543230);
    kryo.register(SSHPasswordCredentialDTO.class, 543231);
    kryo.register(SSHKeyPathCredentialDTO.class, 543232);
    kryo.register(KerberosConfigDTO.class, 543233);
    kryo.register(SSHAuthDTO.class, 543234);
    kryo.register(GcrArtifactDelegateRequest.class, 543235);
    kryo.register(GcrArtifactDelegateResponse.class, 543236);
    kryo.register(K8sRollingRollbackDeployRequest.class, 543239);
    kryo.register(K8sScaleRequest.class, 543240);
    kryo.register(K8sScaleResponse.class, 543241);
    kryo.register(AzureARMPreDeploymentData.class, 543242);
    kryo.register(AzureARMTaskParameters.class, 543243);
    kryo.register(AzureARMTaskResponse.class, 543244);
    kryo.register(AzureARMDeploymentParameters.class, 543245);
    kryo.register(AzureARMRollbackParameters.class, 543246);
    kryo.register(AzureARMDeploymentResponse.class, 543247);
    kryo.register(AzureARMRollbackResponse.class, 543248);
    kryo.register(K8sCanaryDeployRequest.class, 543249);
    kryo.register(K8sCanaryDeployResponse.class, 543250);
    kryo.register(AzureARMListManagementGroupResponse.class, 543251);
    kryo.register(AzureARMListSubscriptionLocationsResponse.class, 543252);
    kryo.register(AzureARMTaskParameters.AzureARMTaskType.class, 543253);
    kryo.register(K8sSwapServiceSelectorsRequest.class, 543254);
    kryo.register(K8sDeleteRequest.class, 543255);
    kryo.register(ManagementGroupData.class, 543256);

    kryo.register(CapabilityParameters.class, 10001);
    kryo.register(PermissionResult.class, 10002);
    kryo.register(UnknownFieldSet.class, 10003);
    kryo.register(TestingCapability.class, 10004);
    kryo.register(SmbConnectionParameters.class, 10005);
    kryo.register(SftpCapabilityParameters.class, 10006);
    kryo.register(AwsRegionParameters.class, 10007);
    kryo.register(ChartMuseumParameters.class, 10008);
    kryo.register(HttpConnectionParameters.class, 10009);
    kryo.register(ProcessExecutorParameters.class, 10010);
    kryo.register(SocketConnectivityParameters.class, 10011);
    kryo.register(SystemEnvParameters.class, 10012);
    kryo.register(HelmInstallationParameters.class, 10013);

    kryo.register(UnitProgressData.class, 95001);
  }
}
