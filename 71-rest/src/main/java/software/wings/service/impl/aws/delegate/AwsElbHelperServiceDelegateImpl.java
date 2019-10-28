package software.wings.service.impl.aws.delegate;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INIT_TIMEOUT;
import static io.harness.exception.WingsException.USER;
import static io.harness.threading.Morpheus.sleep;
import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static software.wings.beans.Log.LogLevel.ERROR;
import static software.wings.service.impl.aws.model.AwsConstants.FORWARD_LISTENER_ACTION;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeInstanceHealthResult;
import com.amazonaws.services.elasticloadbalancing.model.InstanceState;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.Action;
import com.amazonaws.services.elasticloadbalancingv2.model.ActionTypeEnum;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateListenerResult;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.CreateTargetGroupResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeListenersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetHealthResult;
import com.amazonaws.services.elasticloadbalancingv2.model.Listener;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.elasticloadbalancingv2.model.ModifyListenerRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroup;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetGroupNotFoundException;
import com.amazonaws.services.elasticloadbalancingv2.model.TargetHealthDescription;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.task.aws.AwsElbListener;
import io.harness.delegate.task.aws.AwsLoadBalancerDetails;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.security.encryption.EncryptedDataDetail;
import software.wings.beans.AwsConfig;
import software.wings.beans.Log.LogLevel;
import software.wings.beans.command.ExecutionLogCallback;
import software.wings.service.intfc.aws.delegate.AwsAsgHelperServiceDelegate;
import software.wings.service.intfc.aws.delegate.AwsElbHelperServiceDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class AwsElbHelperServiceDelegateImpl
    extends AwsHelperServiceDelegateBase implements AwsElbHelperServiceDelegate {
  public static final String ALB = "application";
  public static final String NLB = "network";

  @Inject private AwsAsgHelperServiceDelegate awsAsgHelperServiceDelegate;
  @Inject private TimeLimiter timeLimiter;

  @VisibleForTesting
  com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient getClassicElbClient(
      Regions region, AwsConfig awsConfig) {
    AmazonElasticLoadBalancingClientBuilder builder =
        AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region);
    attachCredentials(builder, awsConfig);
    return (com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient) builder.build();
  }

  @VisibleForTesting
  AmazonElasticLoadBalancingClient getAmazonElasticLoadBalancingClientV2(Regions region, AwsConfig awsConfig) {
    com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder builder =
        com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder.standard().withRegion(
            region);
    attachCredentials(builder, awsConfig);
    return (AmazonElasticLoadBalancingClient) builder.build();
  }

  @Override
  public List<String> listClassicLoadBalancers(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      List<String> result = new ArrayList<>();
      String nextMarker = null;
      do {
        com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest()
                .withPageSize(400)
                .withMarker(nextMarker);
        com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult describeLoadBalancersResult =
            getClassicElbClient(Regions.fromName(region), awsConfig)
                .describeLoadBalancers(describeLoadBalancersRequest);
        tracker.trackClassicELBCall("Get LB descriptions");
        result.addAll(describeLoadBalancersResult.getLoadBalancerDescriptions()
                          .stream()
                          .map(LoadBalancerDescription::getLoadBalancerName)
                          .collect(toList()));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  @Override
  public List<AwsLoadBalancerDetails> listApplicationLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(ALB)));
  }

  @Override
  public List<AwsLoadBalancerDetails> listNetworkLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(NLB)));
  }

  @Override
  public List<AwsLoadBalancerDetails> listElasticLoadBalancerDetails(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region) {
    return generateLoadBalancersList(awsConfig, encryptionDetails, region, new HashSet<>(Arrays.asList(ALB, NLB)));
  }

  private List<AwsLoadBalancerDetails> generateLoadBalancersList(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, Set<String> neededTypes) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      List<AwsLoadBalancerDetails> result = new ArrayList<>();
      String nextMarker = null;
      do {
        AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
            getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
        DescribeLoadBalancersRequest describeLoadBalancersRequest =
            new DescribeLoadBalancersRequest().withMarker(nextMarker).withPageSize(400);
        tracker.trackELBCall("Describe Load Balancers");
        DescribeLoadBalancersResult describeLoadBalancersResult =
            amazonElasticLoadBalancingClient.describeLoadBalancers(describeLoadBalancersRequest);
        result.addAll(describeLoadBalancersResult.getLoadBalancers()
                          .stream()
                          .filter(loadBalancer -> isNeededLoadBalancer(loadBalancer, neededTypes))
                          .map(loadBalancer
                              -> AwsLoadBalancerDetails.builder()
                                     .arn(loadBalancer.getLoadBalancerArn())
                                     .name(loadBalancer.getLoadBalancerName())
                                     .type(loadBalancer.getType())
                                     .scheme(loadBalancer.getScheme())
                                     .dNSName(loadBalancer.getDNSName())
                                     .ipAddressType(loadBalancer.getIpAddressType())
                                     .vpcId(loadBalancer.getVpcId())
                                     .build())
                          .collect(toList()));
        nextMarker = describeLoadBalancersResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyList();
  }

  private boolean isNeededLoadBalancer(LoadBalancer loadBalancer, Set<String> typesNeeded) {
    String type = loadBalancer.getType();
    return typesNeeded.contains(type.toLowerCase());
  }

  @Override
  public Map<String, String> listTargetGroupsForAlb(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
          getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
      String loadBalancerArn = null;
      if (isNotBlank(loadBalancerName)) {
        DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest();
        request.withNames(loadBalancerName);
        tracker.trackELBCall("Describe Load Balancers");
        loadBalancerArn = amazonElasticLoadBalancingClient.describeLoadBalancers(request)
                              .getLoadBalancers()
                              .get(0)
                              .getLoadBalancerArn();
      }
      Map<String, String> result = new HashMap<>();
      String nextMarker = null;
      do {
        DescribeTargetGroupsRequest describeTargetGroupsRequest =
            new DescribeTargetGroupsRequest().withMarker(nextMarker).withPageSize(400);
        if (loadBalancerArn != null) {
          describeTargetGroupsRequest.withLoadBalancerArn(loadBalancerArn);
        }
        tracker.trackELBCall("Describe Target Groups");
        DescribeTargetGroupsResult describeTargetGroupsResult =
            amazonElasticLoadBalancingClient.describeTargetGroups(describeTargetGroupsRequest);
        describeTargetGroupsResult.getTargetGroups().forEach(
            group -> result.put(group.getTargetGroupArn(), group.getTargetGroupName()));
        nextMarker = describeTargetGroupsResult.getNextMarker();
      } while (nextMarker != null);
      return result;
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }
    return emptyMap();
  }

  @Override
  public Optional<TargetGroup> getTargetGroup(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
          getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withTargetGroupArns(targetGroupArn);
      tracker.trackClassicELBCall("Describe Target Group");
      DescribeTargetGroupsResult describeTargetGroupsResult =
          amazonElasticLoadBalancingClient.describeTargetGroups(describeTargetGroupsRequest);

      if (EmptyPredicate.isNotEmpty(describeTargetGroupsResult.getTargetGroups())) {
        return Optional.of(describeTargetGroupsResult.getTargetGroups().get(0));
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }

    return Optional.empty();
  }

  @Override
  public Optional<TargetGroup> getTargetGroupByName(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupName) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
          getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withNames(targetGroupName);
      tracker.trackELBCall("Describe Target Group");
      DescribeTargetGroupsResult describeTargetGroupsResult =
          amazonElasticLoadBalancingClient.describeTargetGroups(describeTargetGroupsRequest);

      if (EmptyPredicate.isNotEmpty(describeTargetGroupsResult.getTargetGroups())) {
        return Optional.of(describeTargetGroupsResult.getTargetGroups().get(0));
      }
    } catch (AmazonServiceException amazonServiceException) {
      // Aws throws this exception if mentioned target group is not found
      if (amazonServiceException instanceof TargetGroupNotFoundException) {
        return Optional.empty();
      }
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }

    return Optional.empty();
  }

  @Override
  public Optional<LoadBalancer> getLoadBalancer(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    try {
      encryptionService.decrypt(awsConfig, encryptionDetails);
      AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
          getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
      DescribeLoadBalancersRequest describeLoadBalancersRequest =
          new DescribeLoadBalancersRequest().withNames(loadBalancerName);
      tracker.trackELBCall("Desribe Load Balancer");
      DescribeLoadBalancersResult describeLoadBalancersResult =
          amazonElasticLoadBalancingClient.describeLoadBalancers(describeLoadBalancersRequest);

      if (EmptyPredicate.isNotEmpty(describeLoadBalancersResult.getLoadBalancers())) {
        return Optional.of(describeLoadBalancersResult.getLoadBalancers().get(0));
      }
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }

    return Optional.empty();
  }

  @Override
  public void waitForAsgInstancesToDeRegisterWithClassicLB(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String classicLB, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try {
      timeLimiter.callWithTimeout(() -> {
        com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
            getClassicElbClient(Regions.fromName(region), awsConfig);
        List<String> instanceIds =
            awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, asgName);
        while (true) {
          if (allInstancesDeRegistered(amazonElasticLoadBalancingClient, instanceIds, classicLB, logCallback)) {
            logCallback.saveExecutionLog(format("All targets  de  registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      }, timeout, MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(format("Registration timed out for Asg: [%s]", asgName), e);
    }
  }

  @VisibleForTesting
  boolean allInstancesDeRegistered(
      com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> instanceIds, String classicLB, ExecutionLogCallback logCallback) {
    if (isEmpty(instanceIds)) {
      return true;
    }
    DescribeInstanceHealthRequest request = new DescribeInstanceHealthRequest().withLoadBalancerName(classicLB);
    tracker.trackClassicELBCall("Describe Instance Health");
    DescribeInstanceHealthResult result = amazonElasticLoadBalancingClient.describeInstanceHealth(request);
    List<InstanceState> instances = result.getInstanceStates();
    if (isEmpty(instances)) {
      return true;
    }
    Set<String> instanceIdsInService =
        instances.stream().map(InstanceState::getInstanceId).filter(instanceIds::contains).collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%s] instances still registered", instanceIdsInService.size(), instanceIds.size()));
    return instanceIdsInService.size() == 0;
  }

  @Override
  public void waitForAsgInstancesToRegisterWithClassicLB(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String classicLB, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try {
      timeLimiter.callWithTimeout(() -> {
        com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
            getClassicElbClient(Regions.fromName(region), awsConfig);
        List<String> instanceIds =
            awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, asgName);
        while (true) {
          if (allInstancesRegistered(amazonElasticLoadBalancingClient, instanceIds, classicLB, logCallback)) {
            logCallback.saveExecutionLog(format("All targets registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      }, timeout, MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(format("Registration timed out for Asg: [%s]", asgName), e);
    }
  }

  @VisibleForTesting
  boolean allInstancesRegistered(
      com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> instanceIds, String classicLB, ExecutionLogCallback logCallback) {
    if (isEmpty(instanceIds)) {
      return true;
    }
    DescribeInstanceHealthRequest request = new DescribeInstanceHealthRequest().withLoadBalancerName(classicLB);
    tracker.trackClassicELBCall("Describe Instance Healths");
    DescribeInstanceHealthResult result = amazonElasticLoadBalancingClient.describeInstanceHealth(request);
    List<InstanceState> instances = result.getInstanceStates();
    if (isEmpty(instances)) {
      return false;
    }
    Set<String> instanceIdsInService = instances.stream()
                                           .filter(instance -> instance.getState().equalsIgnoreCase("InService"))
                                           .map(InstanceState::getInstanceId)
                                           .filter(instanceIds::contains)
                                           .collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%s] instances registered", instanceIdsInService.size(), instanceIds.size()));
    return instanceIdsInService.containsAll(instanceIds);
  }

  @Override
  public void waitForAsgInstancesToDeRegisterWithTargetGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try {
      timeLimiter.callWithTimeout(() -> {
        AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
            getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
        List<String> instanceIds =
            awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, asgName);
        while (true) {
          if (allTargetsDeRegistered(amazonElasticLoadBalancingClient, instanceIds, targetGroupArn, logCallback)) {
            logCallback.saveExecutionLog(format("All targets de-registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      }, timeout, MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Deregistration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(format("Registration timed out for Asg: [%s]", asgName), e);
    }
  }

  @VisibleForTesting
  boolean allTargetsDeRegistered(AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> targetIds, String targetGroupARN, ExecutionLogCallback logCallback) {
    if (isEmpty(targetIds)) {
      return true;
    }
    DescribeTargetHealthRequest request = new DescribeTargetHealthRequest().withTargetGroupArn(targetGroupARN);
    tracker.trackELBCall("Describe Target Health");
    DescribeTargetHealthResult result = amazonElasticLoadBalancingClient.describeTargetHealth(request);
    List<TargetHealthDescription> healthDescriptions = result.getTargetHealthDescriptions();
    if (isEmpty(healthDescriptions)) {
      return true;
    }
    Set<String> instanceIdsStillRegistered = healthDescriptions.stream()
                                                 .map(description -> description.getTarget().getId())
                                                 .filter(targetIds::contains)
                                                 .collect(toSet());
    logCallback.saveExecutionLog(
        format("[%d] out of [%d] targets still registered", instanceIdsStillRegistered.size(), targetIds.size()));
    return instanceIdsStillRegistered.size() == 0;
  }

  @Override
  public void waitForAsgInstancesToRegisterWithTargetGroup(AwsConfig awsConfig,
      List<EncryptedDataDetail> encryptionDetails, String region, String targetGroupArn, String asgName, int timeout,
      ExecutionLogCallback logCallback) {
    try {
      timeLimiter.callWithTimeout(() -> {
        AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
            getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
        List<String> instanceIds =
            awsAsgHelperServiceDelegate.listAutoScalingGroupInstanceIds(awsConfig, encryptionDetails, region, asgName);
        while (true) {
          if (allTargetsRegistered(amazonElasticLoadBalancingClient, instanceIds, targetGroupArn, logCallback)) {
            logCallback.saveExecutionLog(format("All targets registered for Asg: [%s]", asgName));
            return true;
          }
          sleep(ofSeconds(15));
        }
      }, timeout, MINUTES, true);
    } catch (UncheckedTimeoutException e) {
      String errorMessage = format("Registration timed out for Asg: [%s]", asgName);
      logCallback.saveExecutionLog(errorMessage, ERROR);
      throw new WingsException(INIT_TIMEOUT).addParam("message", errorMessage);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(format("Registration timed out for Asg: [%s]", asgName), e);
    }
  }

  @VisibleForTesting
  boolean allTargetsRegistered(AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient,
      List<String> targetIds, String targetGroupARN, ExecutionLogCallback logCallback) {
    if (isEmpty(targetIds)) {
      return true;
    }
    DescribeTargetHealthRequest request = new DescribeTargetHealthRequest().withTargetGroupArn(targetGroupARN);
    tracker.trackELBCall("Describe Target Health");
    DescribeTargetHealthResult result = amazonElasticLoadBalancingClient.describeTargetHealth(request);
    List<TargetHealthDescription> healthDescriptions = result.getTargetHealthDescriptions();
    if (isEmpty(healthDescriptions)) {
      return false;
    }
    Set<String> instanceIdsRegistered =
        healthDescriptions.stream()
            .filter(description -> description.getTargetHealth().getState().equalsIgnoreCase("Healthy"))
            .map(description -> description.getTarget().getId())
            .filter(targetIds::contains)
            .collect(toSet());
    logCallback.saveExecutionLog(format(
        "[%d] out of [%d] targets registered and in healthy state", instanceIdsRegistered.size(), targetIds.size()));
    return instanceIdsRegistered.containsAll(targetIds);
  }

  @Override
  public List<AwsElbListener> getElbListenersForLoadBalaner(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String loadBalancerName) {
    encryptionService.decrypt(awsConfig, encryptionDetails);

    AmazonElasticLoadBalancingClient amazonElasticLoadBalancingClient =
        getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
    DescribeLoadBalancersRequest request = new DescribeLoadBalancersRequest().withNames(loadBalancerName);

    tracker.trackELBCall("Describe Load Balancers");
    DescribeLoadBalancersResult result = amazonElasticLoadBalancingClient.describeLoadBalancers(request);
    if (EmptyPredicate.isEmpty(result.getLoadBalancers())) {
      throw new WingsException(
          ErrorCode.INVALID_ARGUMENT, "Invalid Load Balancer Name Provided. Could not be found", WingsException.USER)
          .addParam("message", "Invalid Load Balancer Name Provided. Could not be found");
    }

    String elbArn = result.getLoadBalancers().get(0).getLoadBalancerArn();

    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);

    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult listenerResult =
        client.describeListeners(new DescribeListenersRequest().withLoadBalancerArn(elbArn));

    if (EmptyPredicate.isEmpty(listenerResult.getListeners())) {
      return Collections.emptyList();
    }

    return listenerResult.getListeners()
        .stream()
        .map(listener
            -> AwsElbListener.builder()
                   .listenerArn(listener.getListenerArn())
                   .loadBalancerArn(elbArn)
                   .protocol(listener.getProtocol())
                   .port(listener.getPort())
                   .build())
        .collect(toList());
  }

  @Override
  public Listener getElbListener(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region, String listenerArn) {
    encryptionService.decrypt(awsConfig, encryptionDetails);

    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
    tracker.trackELBCall("Describe Listener");
    DescribeListenersResult listenerResult =
        client.describeListeners(new DescribeListenersRequest().withListenerArns(listenerArn));
    if (EmptyPredicate.isEmpty(listenerResult.getListeners())) {
      throw new WingsException(
          ErrorCode.INVALID_ARGUMENT, "Invalid ListenerArn. Listener could not be found", WingsException.USER)
          .addParam("message", "Invalid ListenerArn. Listener could not be found");
    }

    return listenerResult.getListeners().get(0);
  }

  @Override
  public TargetGroup cloneTargetGroup(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String targetGroupArn, String newTargetGroupName) {
    try {
      if (isBlank(targetGroupArn)) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "TargetGroupArn to be cloned from can not be empty", WingsException.USER)
            .addParam("message", "TargetGroupArn to be cloned from can not be empty");
      }
      encryptionService.decrypt(awsConfig, encryptionDetails);
      AmazonElasticLoadBalancingClient client =
          getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
      tracker.trackELBCall("Describe Target Groups");
      DescribeTargetGroupsRequest describeTargetGroupsRequest =
          new DescribeTargetGroupsRequest().withTargetGroupArns(targetGroupArn);
      DescribeTargetGroupsResult describeTargetGroupsResult = client.describeTargetGroups(describeTargetGroupsRequest);
      List<TargetGroup> targetGroups = describeTargetGroupsResult.getTargetGroups();
      if (isEmpty(targetGroups)) {
        throw new WingsException(
            ErrorCode.INVALID_ARGUMENT, "TargetGroupArn to be cloned from could not be Found", WingsException.USER)
            .addParam("message", "TargetGroupArn to be cloned from could not be Found");
      }
      TargetGroup sourceTargetGroup = targetGroups.get(0);
      CreateTargetGroupRequest createTargetGroupRequest =
          new CreateTargetGroupRequest()
              .withName(newTargetGroupName)
              .withTargetType(sourceTargetGroup.getTargetType())
              .withHealthCheckPath(sourceTargetGroup.getHealthCheckPath())
              .withHealthCheckPort(sourceTargetGroup.getHealthCheckPort())
              .withHealthCheckIntervalSeconds(sourceTargetGroup.getHealthCheckIntervalSeconds())
              .withHealthCheckProtocol(sourceTargetGroup.getHealthCheckProtocol())
              .withHealthCheckTimeoutSeconds(sourceTargetGroup.getHealthCheckTimeoutSeconds())
              .withHealthyThresholdCount(sourceTargetGroup.getHealthyThresholdCount())
              .withPort(sourceTargetGroup.getPort())
              .withProtocol(sourceTargetGroup.getProtocol())
              .withTargetType(sourceTargetGroup.getTargetType())
              .withVpcId(sourceTargetGroup.getVpcId())
              .withUnhealthyThresholdCount(sourceTargetGroup.getUnhealthyThresholdCount());

      CreateTargetGroupResult createTargetGroupResult = client.createTargetGroup(createTargetGroupRequest);
      tracker.trackELBCall("Create Target Group");
      return createTargetGroupResult.getTargetGroups().get(0);
    } catch (AmazonServiceException amazonServiceException) {
      handleAmazonServiceException(amazonServiceException);
    } catch (AmazonClientException amazonClientException) {
      handleAmazonClientException(amazonClientException);
    }

    return null;
  }

  @Override
  public Listener createStageListener(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      String listenerArn, Integer port, String targetGroupArn) {
    Listener prodListener = getElbListener(awsConfig, encryptionDetails, region, listenerArn);

    CreateListenerRequest createListenerRequest = new CreateListenerRequest();
    createListenerRequest.withProtocol(prodListener.getProtocol())
        .withPort(port)
        .withLoadBalancerArn(prodListener.getLoadBalancerArn())
        .withCertificates(prodListener.getCertificates())
        .withSslPolicy(prodListener.getSslPolicy());

    createListenerRequest.withDefaultActions(
        new Action().withType(ActionTypeEnum.Forward).withTargetGroupArn(targetGroupArn));

    encryptionService.decrypt(awsConfig, encryptionDetails);

    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
    tracker.trackELBCall("Create Listener");

    CreateListenerResult result = client.createListener(createListenerRequest);
    return result.getListeners().get(0);
  }

  @Override
  public void updateListenersForEcsBG(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      String prodListenerArn, String stageListenerArn, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails);

    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult prodListenerResult =
        client.describeListeners(new DescribeListenersRequest().withListenerArns(prodListenerArn));
    tracker.trackELBCall("Describe Listeners");
    DescribeListenersResult stageListenerResult =
        client.describeListeners(new DescribeListenersRequest().withListenerArns(stageListenerArn));
    Listener prodListener = prodListenerResult.getListeners().get(0);
    Listener stageListener = stageListenerResult.getListeners().get(0);
    tracker.trackELBCall("Modify Listeners");
    client.modifyListener(new ModifyListenerRequest()
                              .withListenerArn(prodListener.getListenerArn())
                              .withDefaultActions(stageListener.getDefaultActions()));
    tracker.trackELBCall("Modify Listeners");
    client.modifyListener(new ModifyListenerRequest()
                              .withListenerArn(stageListener.getListenerArn())
                              .withDefaultActions(prodListener.getDefaultActions()));
  }

  @Override
  public void updateListenersForBGDeployment(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails,
      List<LoadBalancerDetailsForBGDeployment> lbDetailsForBGDeployments, String region) {
    encryptionService.decrypt(awsConfig, encryptionDetails);

    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);

    lbDetailsForBGDeployments.forEach(lbDetailsForBGDeployment -> {
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult prodListenerResult = client.describeListeners(
          new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getProdListenerArn()));
      tracker.trackELBCall("Describe Listeners");
      DescribeListenersResult stageListenerResult = client.describeListeners(
          new DescribeListenersRequest().withListenerArns(lbDetailsForBGDeployment.getStageListenerArn()));
      Listener prodListener = prodListenerResult.getListeners().get(0);
      Listener stageListener = stageListenerResult.getListeners().get(0);
      tracker.trackELBCall("Modify Listeners");
      client.modifyListener(new ModifyListenerRequest()
                                .withListenerArn(prodListener.getListenerArn())
                                .withDefaultActions(stageListener.getDefaultActions()));
      tracker.trackELBCall("Modify Listeners");
      client.modifyListener(new ModifyListenerRequest()
                                .withListenerArn(stageListener.getListenerArn())
                                .withDefaultActions(prodListener.getDefaultActions()));
    });
  }

  @Override
  public DescribeListenersResult describeListenerResult(
      AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String listenerArn, String region) {
    AmazonElasticLoadBalancing client = getAmazonElasticLoadBalancingClientV2(Regions.fromName(region), awsConfig);
    tracker.trackELBCall("Describe Listeners");
    return client.describeListeners(new DescribeListenersRequest().withListenerArns(listenerArn));
  }

  @Override
  public String getTargetGroupForDefaultAction(Listener listener, ExecutionLogCallback executionLogCallback) {
    Optional<Action> action = listener.getDefaultActions()
                                  .stream()
                                  .filter(listenerAction
                                      -> isNotEmpty(listenerAction.getTargetGroupArn())
                                          && FORWARD_LISTENER_ACTION.equalsIgnoreCase(listenerAction.getType()))
                                  .findFirst();

    if (!action.isPresent()) {
      String message = format("Did not find any default forward actions for listener: [%s]", listener.getListenerArn());
      executionLogCallback.saveExecutionLog(message, LogLevel.ERROR);
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, message, USER).addParam("message", message);
    }
    return action.get().getTargetGroupArn();
  }
}