package migrations.all;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.newrelic.NewRelicMetricDataRecord;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

/**
 * Created by rsingh on 3/26/18.
 */
@Slf4j
public class NewRelicMetricDataRecordsMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    DBCollection collection = wingsPersistence.getCollection(NewRelicMetricDataRecord.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

    DBCursor metricDataRecords = collection.find();

    log.info("will go through " + metricDataRecords.size() + " records");

    int updated = 0;
    int batched = 0;
    while (metricDataRecords.hasNext()) {
      DBObject next = metricDataRecords.next();

      String uuId = (String) next.get("_id");
      String appId = (String) next.get("applicationId");
      if (isEmpty(appId)) {
        continue;
      }
      bulkWriteOperation
          .find(wingsPersistence.createQuery(NewRelicMetricDataRecord.class)
                    .filter(NewRelicMetricDataRecord.ID_KEY2, uuId)
                    .getQueryObject())
          .updateOne(new BasicDBObject("$set", new BasicDBObject("appId", appId)));
      updated++;
      batched++;

      if (updated != 0 && updated % 1000 == 0) {
        bulkWriteOperation.execute();
        bulkWriteOperation = collection.initializeUnorderedBulkOperation();
        batched = 0;
        log.info("updated: " + updated);
      }
    }

    if (batched != 0) {
      bulkWriteOperation.execute();
      log.info("updated: " + updated);
    }

    log.info("Complete. Updated " + updated + " records.");
  }
}
