package io.harness.serializer.spring.converters.plannode;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.plan.PlanNodeProto;
import io.harness.serializer.spring.ProtoReadConverter;

import com.google.inject.Singleton;
import org.springframework.data.convert.ReadingConverter;

@OwnedBy(CDC)
@Singleton
@ReadingConverter
public class PlanNodeProtoReadConverter extends ProtoReadConverter<PlanNodeProto> {
  public PlanNodeProtoReadConverter() {
    super(PlanNodeProto.class);
  }
}
