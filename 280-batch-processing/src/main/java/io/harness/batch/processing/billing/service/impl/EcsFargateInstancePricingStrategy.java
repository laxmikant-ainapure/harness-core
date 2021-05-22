package io.harness.batch.processing.billing.service.impl;

import io.harness.batch.processing.billing.service.PricingData;
import io.harness.batch.processing.billing.service.intfc.InstancePricingStrategy;
import io.harness.batch.processing.ccm.PricingSource;
import io.harness.batch.processing.pricing.data.EcsFargatePricingInfo;
import io.harness.batch.processing.pricing.data.VMInstanceBillingData;
import io.harness.batch.processing.pricing.service.intfc.AwsCustomBillingService;
import io.harness.batch.processing.pricing.service.intfc.VMPricingService;
import io.harness.batch.processing.service.intfc.CustomBillingMetaDataService;
import io.harness.batch.processing.tasklet.util.K8sResourceUtils;
import io.harness.batch.processing.writer.constants.InstanceMetaDataConstants;
import io.harness.ccm.commons.entities.InstanceData;

import java.time.Instant;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EcsFargateInstancePricingStrategy implements InstancePricingStrategy {
  private final VMPricingService vmPricingService;
  private final CustomBillingMetaDataService customBillingMetaDataService;
  private final AwsCustomBillingService awsCustomBillingService;

  @Autowired
  public EcsFargateInstancePricingStrategy(VMPricingService vmPricingService,
      CustomBillingMetaDataService customBillingMetaDataService, AwsCustomBillingService awsCustomBillingService) {
    this.vmPricingService = vmPricingService;
    this.customBillingMetaDataService = customBillingMetaDataService;
    this.awsCustomBillingService = awsCustomBillingService;
  }

  @Override
  public PricingData getPricePerHour(InstanceData instanceData, Instant startTime, Instant endTime,
      double instanceActiveSeconds, double parentInstanceActiveSecond) {
    Map<String, String> instanceMetaData = instanceData.getMetaData();
    String region = instanceMetaData.get(InstanceMetaDataConstants.REGION);
    EcsFargatePricingInfo fargatePricingInfo = vmPricingService.getFargatePricingInfo(region);

    PricingData customFargatePricing = getCustomFargatePricing(instanceData, startTime, endTime, instanceActiveSeconds);
    if (null != customFargatePricing) {
      return customFargatePricing;
    }

    Double cpuUnits = instanceData.getTotalResource().getCpuUnits();
    Double memoryMb = instanceData.getTotalResource().getMemoryMb();

    if (null != instanceData.getPricingResource()) {
      cpuUnits = instanceData.getPricingResource().getCpuUnits();
      memoryMb = instanceData.getPricingResource().getMemoryMb();
    }

    double cpuPricePerHour = K8sResourceUtils.getFargateVCpu(cpuUnits) * fargatePricingInfo.getCpuPrice();
    double memoryPricePerHour = K8sResourceUtils.getFargateMemoryGb(memoryMb) * fargatePricingInfo.getMemoryPrice();
    return PricingData.builder()
        .pricePerHour(cpuPricePerHour + memoryPricePerHour)
        .cpuPricePerHour(cpuPricePerHour)
        .memoryPricePerHour(memoryPricePerHour)
        .cpuUnit(instanceData.getTotalResource().getCpuUnits())
        .memoryMb(instanceData.getTotalResource().getMemoryMb())
        .build();
  }

  public PricingData getCustomFargatePricing(
      InstanceData instanceData, Instant startTime, Instant endTime, double instanceActiveSeconds) {
    PricingData pricingData = null;
    String awsDataSetId = customBillingMetaDataService.getAwsDataSetId(instanceData.getAccountId());
    if (null != awsDataSetId) {
      double cpuUnit = instanceData.getTotalResource().getCpuUnits();
      double memoryMb = instanceData.getTotalResource().getMemoryMb();

      VMInstanceBillingData vmInstanceBillingData =
          awsCustomBillingService.getFargateVMPricingInfo(instanceData.getInstanceId(), startTime, endTime);
      log.info("Custom Fargate Pricing: {}", vmInstanceBillingData);
      if (null != vmInstanceBillingData && !Double.isNaN(vmInstanceBillingData.getComputeCost())) {
        double pricePerHr = (vmInstanceBillingData.getComputeCost() * 3600) / instanceActiveSeconds;
        double cpuPricePerHr = (vmInstanceBillingData.getCpuCost() * 3600) / instanceActiveSeconds;
        double memoryPricePerHr = (vmInstanceBillingData.getMemoryCost() * 3600) / instanceActiveSeconds;
        pricingData = PricingData.builder()
                          .pricePerHour(pricePerHr)
                          .cpuPricePerHour(cpuPricePerHr)
                          .memoryPricePerHour(memoryPricePerHr)
                          .networkCost(vmInstanceBillingData.getNetworkCost())
                          .pricingSource(PricingSource.CUR_REPORT)
                          .cpuUnit(cpuUnit)
                          .memoryMb(memoryMb)
                          .build();
      }
    }
    return pricingData;
  }
}
