package io.harness.pms.serializer.kryo;

import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.pms.contracts.advisers.AdviserResponse;
import io.harness.pms.contracts.advisers.AdviserType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.data.StepOutcomeRef;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.skip.SkipInfo;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.contracts.steps.SkipType;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.serializer.kryo.serializers.AdviserResponseKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.AmbianceKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.ExecutableResponseSerializer;
import io.harness.pms.serializer.kryo.serializers.FailureInfoKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.LevelKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.RefTypeKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.SkipInfoKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.StepOutcomeRefKryoSerializer;
import io.harness.pms.serializer.kryo.serializers.StepTypeKryoSerializer;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class PmsContractsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(Ambiance.class, AmbianceKryoSerializer.getInstance(), 2601);
    kryo.register(Level.class, LevelKryoSerializer.getInstance(), 2602);
    kryo.register(ExecutionMode.class, 2603);
    kryo.register(Status.class, 2604);
    kryo.register(AdviserType.class, 2605);
    kryo.register(AdviserObtainment.class, 2606);
    kryo.register(SkipType.class, 2607);
    kryo.register(FacilitatorType.class, 2608);
    kryo.register(RefType.class, RefTypeKryoSerializer.getInstance(), 2609);
    kryo.register(RefObject.class, 2610);
    kryo.register(StepType.class, StepTypeKryoSerializer.getInstance(), 2611);
    kryo.register(FailureType.class, 2612);
    kryo.register(FailureInfo.class, FailureInfoKryoSerializer.getInstance(), 2613);
    kryo.register(StepOutcomeRef.class, StepOutcomeRefKryoSerializer.getInstance(), 2614);
    kryo.register(ExecutableResponse.class, ExecutableResponseSerializer.getInstance(), 2615);
    kryo.register(RepairActionCode.class, 2616);
    kryo.register(SkipInfo.class, SkipInfoKryoSerializer.getInstance(), 2617);
    kryo.register(AdviserResponse.class, AdviserResponseKryoSerializer.getInstance(), 2618);
  }
}
