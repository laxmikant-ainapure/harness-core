package software.wings.search.framework.changestreams;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;

import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j

public class ChangeTrackerTest extends WingsBaseTest {
  @Inject private ChangeTracker changeTracker;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  @Ignore("Investigate to make sure Search Unit Tests are not creating system resources such as Threads")
  public void changeStreamTrackerTest() {
    Set<ChangeTrackingInfo<?>> changeTrackingInfos = new HashSet<>();
    ChangeTrackingInfo<?> changeTrackingInfo =
        new ChangeTrackingInfo<>(Application.class, changeEvent -> log.info(changeEvent.toString()), null);
    changeTrackingInfos.add(changeTrackingInfo);

    changeTracker.start(changeTrackingInfos);
    assertThat(changeTracker.checkIfAnyChangeTrackerIsAlive()).isTrue();

    changeTracker.stop();
  }
}
