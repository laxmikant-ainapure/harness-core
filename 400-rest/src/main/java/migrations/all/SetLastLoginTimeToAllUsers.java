package migrations.all;

import io.harness.persistence.HIterator;

import software.wings.beans.User;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import migrations.Migration;

/**
 * Migration script to set last login time for all existing users.
 * Going forward, the correct last login time would be set.
 * @author rktummala on 12/18/18
 */
@Slf4j
public class SetLastLoginTimeToAllUsers implements Migration {
  @Inject private UserService userService;
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Start - Setting user last login time for users");
    long currentTime = System.currentTimeMillis();
    try (HIterator<User> users = new HIterator<>(wingsPersistence.createQuery(User.class).fetch())) {
      for (User user : users) {
        try {
          user.setLastLogin(currentTime);
          userService.update(user);
        } catch (Exception ex) {
          log.error("Error while setting user last login for user {}", user == null ? "NA" : user.getName(), ex);
        }
      }
    }

    log.info("End - Setting user last login time for users");
  }
}
