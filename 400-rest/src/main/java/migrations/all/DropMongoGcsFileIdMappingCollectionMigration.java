package migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

/**
 * Dropping the 'mongoGcsFileIdMapping' collection which is no longer needed or used.
 *
 * @author marklu on 2019-03-17
 */
@Slf4j
public class DropMongoGcsFileIdMappingCollectionMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    try {
      wingsPersistence.getCollection(DEFAULT_STORE, "mongoGcsFileIdMapping").drop();
    } catch (RuntimeException ex) {
      log.error("Drop collection error", ex);
    }
  }
}
