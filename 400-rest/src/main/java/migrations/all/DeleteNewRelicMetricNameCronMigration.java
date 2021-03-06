package migrations.all;

import static io.harness.persistence.HPersistence.DEFAULT_STORE;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import migrations.Migration;

public class DeleteNewRelicMetricNameCronMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(DEFAULT_STORE, "quartz_verification_jobs");
    collection.findAndRemove(new BasicDBObject("keyName", "NEW_RELIC_METRIC_NAME_COLLECT_CRON"));

    collection = wingsPersistence.getCollection(DEFAULT_STORE, "quartz_verification_triggers");
    collection.findAndRemove(new BasicDBObject("keyName", "NEW_RELIC_METRIC_NAME_COLLECT_CRON"));
  }
}
