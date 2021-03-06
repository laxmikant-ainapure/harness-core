package io.harness.core;

import static io.harness.rule.OwnerRule.ALEXEI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.RecasterTestBase;
import io.harness.category.element.UnitTests;
import io.harness.exceptions.RecasterException;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RecastTest extends RecasterTestBase {
  private Recaster recaster;

  @Before
  public void setup() {
    recaster = new Recaster();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithNull() {
    Recast recast = new Recast(recaster, ImmutableSet.of());
    DummyLong recastedDummyLong = recast.fromDocument(null, DummyLong.class);
    assertThat(recastedDummyLong).isNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithEmptyDocument() {
    Recast recast = new Recast(recaster, ImmutableSet.of());
    assertThatThrownBy(() -> recast.fromDocument(new Document(), DummyLong.class))
        .isInstanceOf(RecasterException.class)
        .hasMessageContaining("The document does not contain a __recast key. Determining entity type is impossible.");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithLong() {
    final Long longClass = 10L;
    final long longPrimitive = 10;
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyLong.class));
    DummyLong dummyLong = DummyLong.builder().longClass(longClass).longPrimitive(longPrimitive).build();

    Document document = recast.toDocument(dummyLong);
    assertThat(document).isNotEmpty();
    assertThat(document.get("longClass")).isEqualTo(longClass);
    assertThat(document.get("longPrimitive")).isEqualTo(longPrimitive);

    DummyLong recastedDummyLong = recast.fromDocument(document, DummyLong.class);
    assertThat(recastedDummyLong).isNotNull();
    assertThat(recastedDummyLong.longClass).isEqualTo(longClass);
    assertThat(recastedDummyLong.longPrimitive).isEqualTo(longPrimitive);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyLong {
    private Long longClass;
    private long longPrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithInteger() {
    final Integer integerClass = 10;
    final int intPrimitive = 10;
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyInteger.class));
    DummyInteger dummyInteger = DummyInteger.builder().integerClass(integerClass).intPrimitive(intPrimitive).build();

    Document document = recast.toDocument(dummyInteger);
    assertThat(document).isNotEmpty();
    assertThat(document.get("integerClass")).isEqualTo(integerClass);
    assertThat(document.get("intPrimitive")).isEqualTo(intPrimitive);

    DummyInteger recastedDummyInteger = recast.fromDocument(document, DummyInteger.class);
    assertThat(recastedDummyInteger).isNotNull();
    assertThat(recastedDummyInteger.integerClass).isEqualTo(integerClass);
    assertThat(recastedDummyInteger.intPrimitive).isEqualTo(intPrimitive);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyInteger {
    private Integer integerClass;
    private int intPrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithDouble() {
    final Double doubleClass = 10.0;
    final double doublePrimitive = 10.0;
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyDouble.class));
    DummyDouble dummyLong = DummyDouble.builder().doubleClass(doubleClass).doublePrimitive(doublePrimitive).build();

    Document document = recast.toDocument(dummyLong);
    assertThat(document).isNotEmpty();
    assertThat(document.get("doubleClass")).isEqualTo(doubleClass);
    assertThat(document.get("doublePrimitive")).isEqualTo(doublePrimitive);

    DummyDouble recastedDummyDouble = recast.fromDocument(document, DummyDouble.class);
    assertThat(recastedDummyDouble).isNotNull();
    assertThat(recastedDummyDouble.doubleClass).isEqualTo(doubleClass);
    assertThat(recastedDummyDouble.doubleClass).isEqualTo(doubleClass);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyDouble {
    private Double doubleClass;
    private double doublePrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithFloat() {
    final Float floatClass = 10.0f;
    final float floatPrimitive = 10.0f;
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyFloat.class));
    DummyFloat dummyFloat = DummyFloat.builder().floatClass(floatClass).floatPrimitive(floatPrimitive).build();

    Document document = recast.toDocument(dummyFloat);
    assertThat(document).isNotEmpty();
    assertThat(document.get("floatClass")).isEqualTo(floatClass);
    assertThat(document.get("floatPrimitive")).isEqualTo(floatPrimitive);

    DummyFloat recastedDummyFloat = recast.fromDocument(document, DummyFloat.class);
    assertThat(recastedDummyFloat).isNotNull();
    assertThat(recastedDummyFloat.floatClass).isEqualTo(floatClass);
    assertThat(recastedDummyFloat.floatPrimitive).isEqualTo(floatPrimitive);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyFloat {
    private Float floatClass;
    private float floatPrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithCharacter() {
    final Character characterClass = 'A';
    final char charPrimitive = 'a';
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyCharacter.class));
    DummyCharacter dummyCharacter =
        DummyCharacter.builder().characterClass(characterClass).charPrimitive(charPrimitive).build();

    Document document = recast.toDocument(dummyCharacter);
    assertThat(document).isNotEmpty();
    assertThat(document.get("characterClass")).isEqualTo(characterClass);
    assertThat(document.get("charPrimitive")).isEqualTo(charPrimitive);

    DummyCharacter recastedDummyCharacter = recast.fromDocument(document, DummyCharacter.class);
    assertThat(recastedDummyCharacter).isNotNull();
    assertThat(recastedDummyCharacter.characterClass).isEqualTo(characterClass);
    assertThat(recastedDummyCharacter.charPrimitive).isEqualTo(charPrimitive);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyCharacter {
    private Character characterClass;
    private char charPrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithBoolean() {
    final Boolean booleanClass = true;
    final boolean booleanPrimitive = false;
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyBoolean.class));
    DummyBoolean dummyBoolean =
        DummyBoolean.builder().booleanClass(booleanClass).booleanPrimitive(booleanPrimitive).build();

    Document document = recast.toDocument(dummyBoolean);
    assertThat(document).isNotEmpty();
    assertThat(document.get("booleanClass")).isEqualTo(booleanClass);
    assertThat(document.get("booleanPrimitive")).isEqualTo(booleanPrimitive);

    DummyBoolean recastedDummyBoolean = recast.fromDocument(document, DummyBoolean.class);
    assertThat(recastedDummyBoolean).isNotNull();
    assertThat(recastedDummyBoolean.booleanClass).isEqualTo(booleanClass);
    assertThat(recastedDummyBoolean.booleanPrimitive).isEqualTo(booleanPrimitive);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyBoolean {
    private Boolean booleanClass;
    private boolean booleanPrimitive;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithString() {
    final String stringClass = "sdgg";
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyString.class));
    DummyString dummyBoolean = DummyString.builder().stringClass(stringClass).build();

    Document document = recast.toDocument(dummyBoolean);
    assertThat(document).isNotEmpty();
    assertThat(document.get("stringClass")).isEqualTo(stringClass);

    DummyString recastedDummyString = recast.fromDocument(document, DummyString.class);
    assertThat(recastedDummyString).isNotNull();
    assertThat(recastedDummyString.stringClass).isEqualTo(stringClass);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyString {
    private String stringClass;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithEnum() {
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyEnum.class));
    DummyEnum dummyEnum = DummyEnum.builder().type(DummyEnum.Type.SUPER_DUMMY).build();

    Document document = recast.toDocument(dummyEnum);
    assertThat(document).isNotEmpty();
    assertThat(document.get("type")).isEqualTo(DummyEnum.Type.SUPER_DUMMY.name());

    DummyEnum recastedDummyEnum = recast.fromDocument(document, DummyEnum.class);
    assertThat(recastedDummyEnum).isNotNull();
    assertThat(recastedDummyEnum.type).isEqualTo(DummyEnum.Type.SUPER_DUMMY);
  }

  @Builder
  @FieldNameConstants(innerTypeName = "DummyEnumNameConstants")
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyEnum {
    private Type type;
    private enum Type { SUPER_DUMMY }
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithStringKeyMap() {
    Map<String, String> map = new HashMap<>();
    map.put("Test", "Success");
    map.put("Test1", "Success");
    Recast recast = new Recast(recaster, ImmutableSet.of(DummyStringKeyMap.class));
    DummyStringKeyMap stringKeyMap = DummyStringKeyMap.builder().map(map).build();
    Document document = recast.toDocument(stringKeyMap);
    assertThat(document).isNotEmpty();
    assertThat((Document) document.get("map"))
        .isEqualTo(new Document().append("Test", "Success").append("Test1", "Success"));

    DummyStringKeyMap recastedDummyMap = recast.fromDocument(document, DummyStringKeyMap.class);
    assertThat(recastedDummyMap).isNotNull();
    assertThat(recastedDummyMap.map).isEqualTo(map);
  }

  @Builder
  @FieldNameConstants(innerTypeName = "DummyStringKeyMapNameConstants")
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyStringKeyMap {
    private Map<String, String> map;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithBasicCharacterArray() {
    final Character[] characterClassArray = new Character[] {'A', 'B'};
    final char[] charPrimitiveArray = new char[] {'a', 'b'};

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyCharacterArray.class));
    DummyCharacterArray user = DummyCharacterArray.builder()
                                   .characterClassArray(characterClassArray)
                                   .charPrimitiveArray(charPrimitiveArray)
                                   .build();

    Document document = recast.toDocument(user);
    assertThat(document).isNotEmpty();
    assertThat(document.get("characterClassArray")).isEqualTo("AB");
    assertThat(document.get("charPrimitiveArray")).isEqualTo("ab");
    assertThat(Document.parse(document.toJson())).isEqualTo(document);

    DummyCharacterArray recastedDummyCharacterArray = recast.fromDocument(document, DummyCharacterArray.class);
    assertThat(recastedDummyCharacterArray).isNotNull();
    assertThat(recastedDummyCharacterArray.characterClassArray).isEqualTo(characterClassArray);
    assertThat(recastedDummyCharacterArray.charPrimitiveArray).isEqualTo(charPrimitiveArray);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyCharacterArray {
    private Character[] characterClassArray;
    private char[] charPrimitiveArray;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithDate() {
    final Instant instant = Instant.now();

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyDate.class));
    DummyDate dummyDate = DummyDate.builder().date(Date.from(instant)).build();
    Document document = recast.toDocument(dummyDate);

    assertThat(document).isNotEmpty();
    assertThat(document.get("date")).isEqualTo(dummyDate.date);

    DummyDate recastedDummyDate = recast.fromDocument(document, DummyDate.class);
    assertThat(recastedDummyDate).isNotNull();
    assertThat(recastedDummyDate.date.toInstant().toEpochMilli()).isEqualTo(instant.toEpochMilli());
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyDate {
    private Date date;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithLocalDate() {
    final LocalDate now = LocalDate.now();

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyLocalDate.class));
    DummyLocalDate dummyLocalDate = DummyLocalDate.builder().localDate(now).build();
    Document document = recast.toDocument(dummyLocalDate);

    assertThat(document).isNotEmpty();
    assertThat(document.get("localDate")).isEqualTo(dummyLocalDate.localDate);

    DummyLocalDate recastedDummyLocalDate = recast.fromDocument(document, DummyLocalDate.class);
    assertThat(recastedDummyLocalDate).isNotNull();
    assertThat(recastedDummyLocalDate.localDate).isEqualTo(now);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyLocalDate {
    private LocalDate localDate;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithLocalDateTime() {
    final LocalDateTime now = LocalDateTime.now();

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyLocalDateTime.class));
    DummyLocalDateTime dummyLocalDateTime = DummyLocalDateTime.builder().localDatetime(now).build();
    Document document = recast.toDocument(dummyLocalDateTime);

    assertThat(document).isNotEmpty();
    assertThat(document.get("localDatetime")).isEqualTo(now);

    DummyLocalDateTime recastedDummyLocalDateTime = recast.fromDocument(document, DummyLocalDateTime.class);
    assertThat(recastedDummyLocalDateTime).isNotNull();
    assertThat(recastedDummyLocalDateTime.localDatetime).isEqualTo(now);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyLocalDateTime {
    private LocalDateTime localDatetime;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithLocalTime() {
    final LocalTime now = LocalTime.now();

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyLocalTime.class));
    DummyLocalTime dummyLocalDateTime = DummyLocalTime.builder().localTime(now).build();
    Document document = recast.toDocument(dummyLocalDateTime);

    assertThat(document).isNotEmpty();
    assertThat(document.get("localTime")).isEqualTo(now);

    DummyLocalTime recastedDummyLocalTime = recast.fromDocument(document, DummyLocalTime.class);
    assertThat(recastedDummyLocalTime).isNotNull();
    assertThat(recastedDummyLocalTime.localTime).isEqualTo(now);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyLocalTime {
    private LocalTime localTime;
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestRecasterWithInstant() {
    final Instant instant = Instant.now();

    Recast recast = new Recast(recaster, ImmutableSet.of(DummyInstant.class));
    DummyInstant dummyInstant = DummyInstant.builder().instant(instant).build();
    Document document = recast.toDocument(dummyInstant);

    assertThat(document).isNotEmpty();
    assertThat(document.get("instant")).isEqualTo(instant);

    DummyInstant recastedDummyInstant = recast.fromDocument(document, DummyInstant.class);
    assertThat(recastedDummyInstant).isNotNull();
    assertThat(recastedDummyInstant.instant).isEqualTo(instant);
  }

  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  private static class DummyInstant {
    private Instant instant;
  }
}
