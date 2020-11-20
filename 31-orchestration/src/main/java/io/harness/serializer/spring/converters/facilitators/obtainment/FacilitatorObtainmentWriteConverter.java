package io.harness.serializer.spring.converters.facilitators.obtainment;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import com.google.inject.Singleton;

import io.harness.annotations.dev.OwnedBy;
import io.harness.orchestration.persistence.ProtoWriteConverter;
import io.harness.pms.facilitators.FacilitatorObtainment;
import org.springframework.data.convert.WritingConverter;

@OwnedBy(CDC)
@Singleton
@WritingConverter
public class FacilitatorObtainmentWriteConverter extends ProtoWriteConverter<FacilitatorObtainment> {}
