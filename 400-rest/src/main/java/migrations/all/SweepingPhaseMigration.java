package migrations.all;

import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceKeys;
import io.harness.persistence.HIterator;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import migrations.Migration;
import org.mongodb.morphia.query.UpdateOperations;

public class SweepingPhaseMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try (HIterator<SweepingOutputInstance> iterator =
             new HIterator<SweepingOutputInstance>(wingsPersistence.createQuery(SweepingOutputInstance.class)
                                                       .field(SweepingOutputInstanceKeys.phaseExecutionId)
                                                       .doesNotExist()
                                                       .fetch())) {
      for (SweepingOutputInstance sweepingOutputInstance : iterator) {
        final UpdateOperations<SweepingOutputInstance> updateOperations =
            wingsPersistence.createUpdateOperations(SweepingOutputInstance.class)
                .set(SweepingOutputInstanceKeys.phaseExecutionId, generateUuid());
        wingsPersistence.update(sweepingOutputInstance, updateOperations);
      }
    }
  }
}
