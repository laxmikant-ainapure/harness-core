package io.harness.batch.processing.billing.tasklet.dao.impl;

import io.harness.batch.processing.billing.tasklet.dao.intfc.DataGeneratedNotificationDao;
import io.harness.batch.processing.billing.tasklet.entities.DataGeneratedNotification;
import io.harness.batch.processing.billing.tasklet.entities.DataGeneratedNotification.DataGeneratedNotificationKeys;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class DataGeneratedNotificationDaoImpl implements DataGeneratedNotificationDao {
  @Autowired @Inject private HPersistence hPersistence;

  @Override
  public boolean save(DataGeneratedNotification notification) {
    return hPersistence.save(notification) != null;
  }

  @Override
  public boolean isMailSent(String accountId) {
    Query<DataGeneratedNotification> query = hPersistence.createQuery(DataGeneratedNotification.class)
                                                 .filter(DataGeneratedNotificationKeys.accountId, accountId)
                                                 .filter(DataGeneratedNotificationKeys.mailSent, true);
    return query.get() != null;
  }
}
