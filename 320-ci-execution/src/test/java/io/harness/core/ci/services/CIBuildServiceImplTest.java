package io.harness.core.ci.services;

import static io.harness.rule.OwnerRule.ALEKSANDAR;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.CIBuild;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.repositories.CIBuildRepository;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class CIBuildServiceImplTest extends CIExecutionTestBase {
  @Mock CIBuildRepository ciBuildRepository;
  @InjectMocks CIBuildServiceImpl ciBuildService;

  private final CIBuild ciBuild = CIBuild.builder().build();

  @Test
  @Owner(developers = ALEKSANDAR)
  @Category(UnitTests.class)
  public void shouldSave() {
    when(ciBuildRepository.save(eq(ciBuild))).thenReturn(ciBuild);
    ciBuildService.save(ciBuild);
    verify(ciBuildRepository, times(1)).save(ciBuild);
  }
}
