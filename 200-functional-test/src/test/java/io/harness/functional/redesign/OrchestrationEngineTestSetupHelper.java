package io.harness.functional.redesign;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.execution.PlanExecution;
import io.harness.execution.PlanExecution.PlanExecutionKeys;
import io.harness.functional.redesign.mixins.adviserobtainment.AdviserObtainmentTestMixin;
import io.harness.functional.redesign.mixins.advisertype.AdviserTypeTestMixin;
import io.harness.functional.redesign.mixins.ambiance.AmbianceTestMixin;
import io.harness.functional.redesign.mixins.executionmetadata.ExecutionMetadataTestMixin;
import io.harness.functional.redesign.mixins.facilitatorobtainment.FacilitatorObtainmentTestMixin;
import io.harness.functional.redesign.mixins.facilitatortype.FacilitatorTypeTestMixin;
import io.harness.functional.redesign.mixins.failuretype.FailureInfoTestMixin;
import io.harness.functional.redesign.mixins.plannode.PlanNodeProtoTestMixin;
import io.harness.functional.redesign.mixins.refobject.RefObjectTestMixin;
import io.harness.functional.redesign.mixins.reftype.RefTypeTestMixin;
import io.harness.functional.redesign.mixins.stepType.StepTypeTestMixin;
import io.harness.functional.redesign.mixins.stepoutcomeref.StepOutcomeRefTestMixin;
import io.harness.functional.redesign.mixins.stepparameters.StepParametersTestMixin;
import io.harness.interrupts.Interrupt;
import io.harness.interrupts.Interrupt.InterruptKeys;
import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.rest.RestResponse;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.testframework.framework.Setup;

import software.wings.api.HttpStateExecutionData;
import software.wings.api.WaitStateExecutionData;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.GenericType;
import org.awaitility.Awaitility;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

public class OrchestrationEngineTestSetupHelper {
  @Inject private MongoTemplate mongoTemplate;

  public RequestSpecification getPortalRequestSpecification(String bearerToken) {
    return Setup.portal()
        .config(RestAssuredConfig.config()
                    .objectMapperConfig(new ObjectMapperConfig().jackson2ObjectMapperFactory((cls, charset) -> {
                      ObjectMapper mapper = new ObjectMapper();
                      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                      mapper.registerModule(new JavaTimeModule());
                      mapper.registerSubtypes(
                          HttpStateExecutionData.class, WaitStateExecutionData.class, BarrierOutcome.class);
                      mapper.addMixIn(StepParameters.class, StepParametersTestMixin.class);
                      mapper.addMixIn(Ambiance.class, AmbianceTestMixin.class);
                      mapper.addMixIn(AdviserType.class, AdviserTypeTestMixin.class);
                      mapper.addMixIn(AdviserObtainment.class, AdviserObtainmentTestMixin.class);
                      mapper.addMixIn(FacilitatorType.class, FacilitatorTypeTestMixin.class);
                      mapper.addMixIn(RefType.class, RefTypeTestMixin.class);
                      mapper.addMixIn(FacilitatorObtainment.class, FacilitatorObtainmentTestMixin.class);
                      mapper.addMixIn(StepType.class, StepTypeTestMixin.class);
                      mapper.addMixIn(RefObject.class, RefObjectTestMixin.class);
                      mapper.addMixIn(FailureInfo.class, FailureInfoTestMixin.class);
                      mapper.addMixIn(PlanNodeProto.class, PlanNodeProtoTestMixin.class);
                      mapper.addMixIn(StepOutcomeRef.class, StepOutcomeRefTestMixin.class);
                      mapper.addMixIn(ExecutionMetadata.class, ExecutionMetadataTestMixin.class);
                      mapper.registerModule(new ProtobufModule());
                      return mapper;
                    }))
                    .sslConfig(new SSLConfig().relaxedHTTPSValidation()))
        .auth()
        .oauth2(bearerToken);
  }

  public PlanExecution executePlan(String bearerToken, String accountId, String appId, String planType) {
    PlanExecution original = startPlanExecution(bearerToken, accountId, appId, planType);

    final String finalStatusEnding = "ED";
    Awaitility.await().atMost(6, TimeUnit.MINUTES).pollInterval(10, TimeUnit.SECONDS).until(() -> {
      final PlanExecution planExecution = getPlanExecution(original.getUuid());
      return planExecution != null && planExecution.getStatus().name().endsWith(finalStatusEnding);
    });

    return getPlanExecution(original.getUuid());
  }

  private PlanExecution startPlanExecution(String bearerToken, String accountId, String appId, String planType) {
    GenericType<RestResponse<PlanExecution>> returnType = new GenericType<RestResponse<PlanExecution>>() {};

    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("accountId", accountId);
    queryParams.put("appId", appId);

    RestResponse<PlanExecution> response = getPortalRequestSpecification(bearerToken)
                                               .queryParams(queryParams)
                                               .contentType(ContentType.JSON)
                                               .get("/execute2/" + planType)
                                               .as(returnType.getType());

    return response.getResource();
  }

  public PlanExecution getPlanExecution(String uuid) {
    Query query = query(where(PlanExecutionKeys.uuid).is(uuid));
    query.fields().include(PlanExecutionKeys.status);
    return mongoTemplate.findOne(query, PlanExecution.class);
  }

  public List<NodeExecution> getNodeExecutions(String planExecutionId) {
    Query query = query(where(NodeExecutionKeys.planExecutionId).is(planExecutionId))
                      .with(Sort.by(Sort.Direction.DESC, NodeExecutionKeys.createdAt));
    return mongoTemplate.find(query, NodeExecution.class);
  }

  public List<Interrupt> getPlanInterrupts(String planExecutionId) {
    return mongoTemplate.find(query(where(InterruptKeys.planExecutionId).is(planExecutionId)), Interrupt.class);
  }
}
