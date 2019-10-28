package io.harness.grpc.server;

import static io.harness.rule.OwnerRule.AVMOHAN;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.OwnerRule.Owner;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ConnectorTest extends CategoryTest {
  @Test
  @Owner(emails = AVMOHAN, resent = false)
  @Category(UnitTests.class)
  public void shouldFailIfSecureConnectorWithoutKeyFile() throws Exception {
    assertThatIllegalArgumentException().isThrownBy(() -> new Connector(123, true, "", null));
  }

  @Test
  @Owner(emails = AVMOHAN, resent = false)
  @Category(UnitTests.class)
  public void shouldFailIfSecureConnectorWithoutCertFile() throws Exception {
    assertThatIllegalArgumentException().isThrownBy(() -> new Connector(123, true, null, ""));
  }

  @Test
  @Owner(emails = AVMOHAN, resent = false)
  @Category(UnitTests.class)
  public void shouldPassIfSecureConnectorWithKeyFileAndCertFile() throws Exception {
    assertThatCode(() -> new Connector(123, true, "", "")).doesNotThrowAnyException();
  }

  @Test
  @Owner(emails = AVMOHAN, resent = false)
  @Category(UnitTests.class)
  public void shouldPassInsecureConnectorWithoutKeyFileAndCertFile() throws Exception {
    assertThatCode(() -> new Connector(123, false, null, null)).doesNotThrowAnyException();
  }
}