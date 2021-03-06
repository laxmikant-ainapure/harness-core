package io.harness.cvng.activity.jobs;

import io.harness.cvng.activity.entities.Activity;
import io.harness.cvng.activity.services.api.ActivityService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ActivityStatusJob implements MongoPersistenceIterator.Handler<Activity> {
  @Inject private ActivityService activityService;

  @Override
  public void handle(Activity entity) {
    activityService.updateActivityStatus(entity);
  }
}
