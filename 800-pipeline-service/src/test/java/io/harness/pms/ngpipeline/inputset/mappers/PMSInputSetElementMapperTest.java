package io.harness.pms.ngpipeline.inputset.mappers;

import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.mapper.TagMapper;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.overlayinputset.beans.resource.OverlayInputSetResponseDTOPMS;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PMSInputSetElementMapperTest extends CategoryTest {
  private final String PIPELINE_IDENTIFIER = "Test_Pipline11";
  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";

  String inputSetYaml;
  String overlayInputSetYaml;

  @Before
  public void setUp() throws IOException {
    ClassLoader classLoader = getClass().getClassLoader();
    String inputSet = "inputSet1.yml";
    inputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSet)), StandardCharsets.UTF_8);

    String overlayInputSet = "overlaySet1.yml";
    overlayInputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(overlayInputSet)), StandardCharsets.UTF_8);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntity() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    List<NGTag> tagsList = TagMapper.convertToList(tags);

    assertThat(entity.getIdentifier()).isEqualTo("input1");
    assertThat(entity.getName()).isEqualTo("this name");
    assertThat(entity.getDescription()).isEqualTo("this has a description too");
    assertThat(entity.getTags()).isEqualTo(tagsList);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetEntityForOverlay() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<NGTag> tagsList = TagMapper.convertToList(tags);
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");

    assertThat(entity.getIdentifier()).isEqualTo("overlay1");
    assertThat(entity.getName()).isEqualTo("thisName");
    assertThat(entity.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(entity.getTags()).isEqualTo(tagsList);
    assertThat(entity.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    InputSetResponseDTOPMS inputSetResponseDTOPMS = PMSInputSetElementMapper.toInputSetResponseDTOPMS(entity);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInputSetErrorWrapper()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToOverlayInputSetResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity);
    assertThat(inputSetResponseDTOPMS.isErrorResponse()).isFalse();
    assertThat(inputSetResponseDTOPMS.getInvalidInputSetReferences()).isNull();

    assertThat(inputSetResponseDTOPMS.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(inputSetResponseDTOPMS.getOrgIdentifier()).isEqualTo(ORG_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getProjectIdentifier()).isEqualTo(PROJ_IDENTIFIER);
    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    List<String> references = new ArrayList<>();
    references.add("inputSet2");
    references.add("inputSet22");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);
    assertThat(inputSetResponseDTOPMS.getInputSetReferences()).isEqualTo(references);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToInputSetSummaryResponseDTOPMS() {
    InputSetEntity entity = PMSInputSetElementMapper.toInputSetEntity(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, inputSetYaml);
    InputSetSummaryResponseDTOPMS inputSetResponseDTOPMS =
        PMSInputSetElementMapper.toInputSetSummaryResponseDTOPMS(entity);

    assertThat(inputSetResponseDTOPMS.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    Map<String, String> tags = new LinkedHashMap<>();
    tags.put("company", "harness");
    tags.put("kind", "normal");
    assertThat(inputSetResponseDTOPMS.getIdentifier()).isEqualTo("input1");
    assertThat(inputSetResponseDTOPMS.getName()).isEqualTo("this name");
    assertThat(inputSetResponseDTOPMS.getDescription()).isEqualTo("this has a description too");
    assertThat(inputSetResponseDTOPMS.getTags()).isEqualTo(tags);

    InputSetEntity entity1 = PMSInputSetElementMapper.toInputSetEntityForOverlay(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, overlayInputSetYaml);
    OverlayInputSetResponseDTOPMS inputSetResponseDTOPMS1 =
        PMSInputSetElementMapper.toOverlayInputSetResponseDTOPMS(entity1);

    assertThat(inputSetResponseDTOPMS1.getPipelineIdentifier()).isEqualTo(PIPELINE_IDENTIFIER);

    tags = new LinkedHashMap<>();
    tags.put("isOverlaySet", "yes it is");
    assertThat(inputSetResponseDTOPMS1.getIdentifier()).isEqualTo("overlay1");
    assertThat(inputSetResponseDTOPMS1.getName()).isEqualTo("thisName");
    assertThat(inputSetResponseDTOPMS1.getDescription()).isEqualTo("this is an overlay input set");
    assertThat(inputSetResponseDTOPMS1.getTags()).isEqualTo(tags);
  }
}