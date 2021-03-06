package io.harness.pms.sdk.core;

import io.harness.CategoryTest;
import io.harness.MockableTestMixin;
import io.harness.rule.LifecycleRule;

import org.junit.Rule;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public abstract class PmsSdkCoreTestBase extends CategoryTest implements MockableTestMixin {
  @Rule public LifecycleRule lifecycleRule = new LifecycleRule();
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Rule public PmsSdkCoreRule orchestrationRule = new PmsSdkCoreRule(lifecycleRule.getClosingFactory());
}
