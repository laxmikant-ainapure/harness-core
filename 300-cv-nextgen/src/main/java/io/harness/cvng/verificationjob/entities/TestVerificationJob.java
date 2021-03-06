package io.harness.cvng.verificationjob.entities;

import static io.harness.cvng.core.utils.ErrorMessageUtils.generateErrorMessageFromParam;

import io.harness.cvng.beans.job.Sensitivity;
import io.harness.cvng.beans.job.TestVerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobDTO;
import io.harness.cvng.beans.job.VerificationJobType;
import io.harness.cvng.core.beans.TimeRange;
import io.harness.cvng.verificationjob.services.api.VerificationJobInstanceService;

import com.google.common.base.Preconditions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@FieldNameConstants(innerTypeName = "TestVerificationJobKeys")
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class TestVerificationJob extends VerificationJob {
  private RuntimeParameter sensitivity;
  private String baselineVerificationJobInstanceId;
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.TEST;
  }

  public Sensitivity getSensitivity() {
    if (sensitivity.isRuntimeParam()) {
      return null;
    }
    return Sensitivity.valueOf(sensitivity.getValue());
  }

  public void setSensitivity(String sensitivity, boolean isRuntimeParam) {
    this.sensitivity = sensitivity == null
        ? null
        : RuntimeParameter.builder().isRuntimeParam(isRuntimeParam).value(sensitivity).build();
  }

  public void setSensitivity(Sensitivity sensitivity) {
    this.sensitivity =
        sensitivity == null ? null : RuntimeParameter.builder().isRuntimeParam(false).value(sensitivity.name()).build();
  }

  @Override
  public VerificationJobDTO getVerificationJobDTO() {
    TestVerificationJobDTO testVerificationJobDTO = new TestVerificationJobDTO();
    populateCommonFields(testVerificationJobDTO);
    testVerificationJobDTO.setSensitivity(getSensitivity() == null ? null : getSensitivity().name());
    if (baselineVerificationJobInstanceId == null) {
      testVerificationJobDTO.setBaselineVerificationJobInstanceId("LAST");
    } else {
      testVerificationJobDTO.setBaselineVerificationJobInstanceId(baselineVerificationJobInstanceId);
    }
    return testVerificationJobDTO;
  }

  @Override
  public boolean shouldDoDataCollection() {
    return true;
  }
  @Override
  protected void validateParams() {
    Preconditions.checkNotNull(sensitivity, generateErrorMessageFromParam(TestVerificationJobKeys.sensitivity));
  }

  @Override
  public Optional<TimeRange> getPreActivityTimeRange(Instant deploymentStartTime) {
    return Optional.empty();
  }

  @Override
  public Optional<TimeRange> getPostActivityTimeRange(Instant deploymentStartTime) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public List<TimeRange> getDataCollectionTimeRanges(Instant startTime) {
    return getTimeRangesForDuration(startTime);
  }

  @Override
  public void fromDTO(VerificationJobDTO verificationJobDTO) {
    addCommonFileds(verificationJobDTO);
    TestVerificationJobDTO testVerificationJobDTO = (TestVerificationJobDTO) verificationJobDTO;
    this.setSensitivity(testVerificationJobDTO.getSensitivity(),
        VerificationJobDTO.isRuntimeParam(testVerificationJobDTO.getSensitivity()));
    if (!testVerificationJobDTO.getBaselineVerificationJobInstanceId().equals("LAST")) {
      this.setBaselineVerificationJobInstanceId(testVerificationJobDTO.getBaselineVerificationJobInstanceId());
    }
  }

  @Override
  public void resolveJobParams(Map<String, String> runtimeParameters) {}

  @Override
  public boolean collectHostData() {
    return false;
  }
  @Override
  public VerificationJob resolveAdditionsFields(VerificationJobInstanceService verificationJobInstanceService) {
    if (baselineVerificationJobInstanceId == null) {
      baselineVerificationJobInstanceId = verificationJobInstanceService
                                              .getLastSuccessfulTestVerificationJobExecutionId(getAccountId(),
                                                  getOrgIdentifier(), getProjectIdentifier(), getIdentifier())
                                              .orElse(null);
    }
    return this;
  }

  public static class TestVerificationUpdatableEntity
      extends VerificationJobUpdatableEntity<TestVerificationJob, TestVerificationJobDTO> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<TestVerificationJob> updateOperations, TestVerificationJobDTO dto) {
      setCommonOperations(updateOperations, dto);
      updateOperations.set(TestVerificationJobKeys.sensitivity,
          getRunTimeParameter(dto.getSensitivity(), VerificationJobDTO.isRuntimeParam(dto.getEnvIdentifier())));
      updateOperations.set(
          TestVerificationJobKeys.baselineVerificationJobInstanceId, dto.getBaselineVerificationJobInstanceId());
    }
  }
}
