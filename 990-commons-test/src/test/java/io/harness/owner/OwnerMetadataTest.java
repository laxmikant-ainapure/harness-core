package io.harness.owner;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.GHPRB_PULL_AUTHOR_EMAIL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.FunctionalTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.rule.UserInfo;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class OwnerMetadataTest extends CategoryTest {
  @Test
  @Owner(developers = GEORGE)
  @Category({FunctionalTests.class})
  public void testTheOwnerMetadata() {
    String email = System.getenv(GHPRB_PULL_AUTHOR_EMAIL);
    if (email == null) {
      return;
    }

    String developerId = OwnerRule.findDeveloperId(email);
    assertThat(developerId)
        .as("You are not in the list of the owners or your email is incorrect.\n"
            + "Add or correct your metadata in the OwnerRule class")
        .isNotNull();

    UserInfo userInfo = OwnerRule.findDeveloper(developerId);
    assertThat(userInfo)
        .as("You are not in the list of the owners.\n"
            + "Add your metadata in the OwnerRule class")
        .isNotNull();

    assertThat(userInfo.getSlack()).as("Please add your slack account id to the metadata").isNotNull();
    assertThat(userInfo.getJira()).as("Please add your jira account id to the metadata").isNotNull();
    assertThat(userInfo.getTeam()).as("Please add your team to the metadata").isNotNull();
  }
}
