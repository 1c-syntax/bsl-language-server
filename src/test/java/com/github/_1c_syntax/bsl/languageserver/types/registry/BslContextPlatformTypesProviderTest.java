/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.platform.PlatformContextConstructor;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextCollection;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethodSignature;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import com.github._1c_syntax.bsl.context.platform.primitive.PrimitivePlaceholderType;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты на адаптер {@link BslContextPlatformTypesProvider}: проверяем
 * правильность маппинга {@code Context} → {@code TypeDecl}/{@code MemberDescriptor}.
 * Источник данных — синтетический {@link PlatformContextProvider} из
 * builder'ов bsl-context (без чтения реального HBK).
 */
@ExtendWith(MockitoExtension.class)
class BslContextPlatformTypesProviderTest {

  /** Имитация {@link BslContextHolder} без Spring. */
  private static BslContextHolder holderOf(ContextProvider provider) {
    return new BslContextHolder(null) {
      @Override
      public Optional<ContextProvider> get() {
        return Optional.ofNullable(provider);
      }
    };
  }

  // BslContextHolder теперь принимает PlatformContextProviderFactory.
  // В тестах подменяем сам holder через override get(), фабрика не используется.

  @Test
  void emptyHolderProducesNoTypes() {
    var adapter = new BslContextPlatformTypesProvider(holderOf(null));
    assertThat(adapter.getTypes()).isEmpty();
  }

  @Test
  void primitiveBecomesPrimitiveTypeDecl() {
    var primitive = new PrimitivePlaceholderType(new ContextName("Строка", "String"), "");
    var provider = providerOf(primitive);

    var types = new BslContextPlatformTypesProvider(holderOf(provider)).getTypes();

    assertThat(types).hasSize(1);
    var decl = types.iterator().next();
    assertThat(decl.kind()).isEqualTo(TypeKind.PRIMITIVE);
    assertThat(decl.qualifiedName()).isEqualTo("Строка");
    // en-имя живёт детерминированно в bilingualName.
    assertThat(decl.name().en()).isEqualTo("String");
    assertThat(decl.name().ru()).isEqualTo("Строка");
    assertThat(decl.members()).isEmpty();
  }

  @Test
  void platformTypePublishesMethodsAndPropertiesAsMembers() {
    var stringType = primitive("Строка", "String");
    var numberType = primitive("Число", "Number");

    var addedParam = PlatformContextSignatureParameter.builder()
      .name(new ContextName("Значение", ""))
      .isRequired(true)
      .rawTypes(List.of("Строка"))
      .description("Добавляемое значение")
      .build();
    var addSig = PlatformContextMethodSignature.builder()
      .name(new ContextName("", ""))
      .parameters(new ArrayList<>(List.of((ContextSignatureParameter) addedParam)))
      .description("")
      .build();
    var addMethod = PlatformContextMethod.builder()
      .name(new ContextName("Добавить", "Add"))
      .description("Добавляет элемент.")
      .availabilities(List.of())
      .rawReturnValues(List.of())
      .signatures(new ArrayList<>(List.of((ContextMethodSignature) addSig)))
      .build();

    var countProperty = PlatformContextProperty.builder()
      .name(new ContextName("Количество", "Count"))
      .rawTypes(List.of("Число"))
      .description("Количество элементов.")
      .availabilities(List.of())
      .build();

    var arrayType = PlatformContextType.builder()
      .name(new ContextName("Массив", "Array"))
      .methods(new ArrayList<>(List.of(addMethod)))
      .properties(new ArrayList<>(List.of(countProperty)))
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("Универсальная коллекция.")
      .build();

    var provider = providerOf(arrayType, stringType, numberType);

    var types = new BslContextPlatformTypesProvider(holderOf(provider)).getTypes();
    var decl = types.stream()
      .filter(t -> "Массив".equals(t.qualifiedName()))
      .findFirst().orElseThrow();

    assertThat(decl.kind()).isEqualTo(TypeKind.PLATFORM);
    assertThat(decl.name().en()).isEqualTo("Array");
    assertThat(decl.name().ru()).isEqualTo("Массив");
    // Порядок: properties → methods.
    var members = List.copyOf(decl.members());
    assertThat(members).hasSize(2);
    assertThat(members.get(0).name()).isEqualTo("Количество");
    assertThat(members.get(0).kind()).isEqualTo(MemberKind.PROPERTY);
    assertThat(members.get(0).returnType().qualifiedName()).isEqualTo("Число");
    assertThat(members.get(1).name()).isEqualTo("Добавить");
    assertThat(members.get(1).kind()).isEqualTo(MemberKind.METHOD);
    assertThat(members.get(1).signatures()).hasSize(1);
    var param = members.get(1).signatures().get(0).parameters().get(0);
    assertThat(param.name()).isEqualTo("Значение");
    assertThat(param.optional()).isFalse();

    // Двуязычность: member хранит ru/en имя в BilingualString из ContextName
    // и резолвится через matches() по обоим написаниям.
    var addMember = members.get(1);
    assertThat(addMember.bilingualName().ru()).isEqualTo("Добавить");
    assertThat(addMember.bilingualName().en()).isEqualTo("Add");
    assertThat(addMember.matches("Add"))
      .as("lookup по en-имени должен находить member, объявленный в ru")
      .isTrue();
    assertThat(addMember.matches("ADD")).isTrue();
    assertThat(addMember.matches("Добавить")).isTrue();
    assertThat(addMember.matches("OtherName")).isFalse();
    assertThat(addMember.displayName(Language.EN)).isEqualTo("Add");
    assertThat(addMember.displayName(Language.RU)).isEqualTo("Добавить");
  }

  @Test
  void enumValueWithPlaceholderName_isMarkedAsGenericTemplate() {
    // Значение enum-«библиотеки» с placeholder в имени (<Имя картинки>) должно
    // быть помечено generic=true, чтобы expandedMembers мог раскрыть его в
    // конкретные имена из конфигурации (CommonPicture).
    var pictureLib = PlatformContextEnum.builder()
      .name(new ContextName("БиблиотекаКартинок", "PictureLib"))
      .values(List.of(
        new PlatformContextEnumValue(new ContextName("<Имя картинки>", "<Icon name>")),
        new PlatformContextEnumValue(new ContextName("АктивироватьЗадачу", "ActivateTask"))
      ))
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(pictureLib)))
      .getTypes().iterator().next();

    var members = decl.members();
    assertThat(members).hasSize(2);
    var templates = members.stream().filter(com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor::generic).toList();
    var concrete = members.stream().filter(m -> !m.generic()).toList();
    assertThat(templates).extracting(m -> m.name()).containsExactly("<Имя картинки>");
    assertThat(concrete).extracting(m -> m.name()).containsExactly("АктивироватьЗадачу");
  }

  @Test
  void enumValueReturnType_followsEnumValueType_whenPresent() {
    // У enum-«библиотеки» определён общий тип элементов (valueType()).
    // Соответственно returnType значений должен быть этим типом, а не самим
    // enum'ом — иначе БиблиотекаКартинок.ИнтервалДат имеет тип
    // «БиблиотекаКартинок» вместо «Картинка».
    var pictureLib = PlatformContextEnum.builder()
      .name(new ContextName("БиблиотекаКартинок", "PictureLib"))
      .values(List.of(
        new PlatformContextEnumValue(new ContextName("ИнтервалДат", "DateInterval"))
      ))
      .valueType(new ContextName("Картинка", "Picture"))
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(pictureLib)))
      .getTypes().iterator().next();

    assertThat(decl.members()).hasSize(1);
    assertThat(decl.members().iterator().next().returnType().qualifiedName())
      .isEqualTo("Картинка");
  }

  @Test
  void enumValueReturnType_fallbacksToEnumRef_whenValueTypeAbsent() {
    // Обычное системное перечисление: valueType отсутствует —
    // returnType значений = сам enum.
    var enumeration = PlatformContextEnum.builder()
      .name(new ContextName("ВидДвиженияНакопления", "AccumulationRecordType"))
      .values(List.of(
        new PlatformContextEnumValue(new ContextName("Приход", "Receipt"))
      ))
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(enumeration)))
      .getTypes().iterator().next();

    assertThat(decl.members().iterator().next().returnType().qualifiedName())
      .isEqualTo("ВидДвиженияНакопления");
  }

  @Test
  void enumValuesPublishedAsPropertiesOfEnumType() {
    var enumeration = PlatformContextEnum.builder()
      .name(new ContextName("КодировкаТекста", "TextEncoding"))
      .values(List.of(
        new PlatformContextEnumValue(new ContextName("UTF8", "UTF8"),
          "Кодировка UTF-8.", "", "", List.of()),
        new PlatformContextEnumValue(new ContextName("ANSI", "ANSI"),
          "ANSI-кодировка.", "", "", List.of())
      ))
      .build();

    var types = new BslContextPlatformTypesProvider(holderOf(providerOf(enumeration))).getTypes();
    var decl = types.iterator().next();

    assertThat(decl.kind()).isEqualTo(TypeKind.PLATFORM);
    assertThat(decl.qualifiedName()).isEqualTo("КодировкаТекста");
    assertThat(decl.members()).hasSize(2);
    decl.members().forEach(m -> {
      assertThat(m.kind()).isEqualTo(MemberKind.PROPERTY);
      // Тип значения = сам enum (для dot-completion'а с его именами).
      assertThat(m.returnType().qualifiedName()).isEqualTo("КодировкаТекста");
    });
  }

  @Test
  void globalContextAndLanguageKeywordAreSkippedAsTypes() {
    var globalContext = PlatformGlobalContext.builder()
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .applicationEvents(Collections.emptyList())
      .ordinaryApplicationEvents(Collections.emptyList())
      .sessionModuleEvents(Collections.emptyList())
      .externalConnectionModuleEvents(Collections.emptyList())
      .build();
    var keyword = PlatformLanguageKeyword.builder()
      .name(new ContextName("Если", "If"))
      .category(LanguageKeywordCategory.STATEMENT)
      .description("")
      .snippet(com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet.EMPTY)
      .build();
    var realType = primitive("Строка", "String");

    var types = new BslContextPlatformTypesProvider(
      holderOf(providerOf(globalContext, keyword, realType))).getTypes();

    // Только примитив, без global-context и language-keyword.
    assertThat(types)
      .extracting(t -> t.qualifiedName())
      .containsExactly("Строка");
  }

  @Test
  void exposedAsGlobalHeuristicTriggersOnGenericProperty() {
    // Универсальный менеджер с generic-property — должен быть помечен exposedAsGlobal=true.
    var genericProp = PlatformContextProperty.builder()
      .name(new ContextName("<Имя справочника>", ""))
      .rawTypes(List.of())
      .description("")
      .availabilities(List.of())
      .build();
    var catalogsManager = PlatformContextType.builder()
      .name(new ContextName("СправочникиМенеджер", "CatalogsManager"))
      .methods(Collections.emptyList())
      .properties(new ArrayList<>(List.of(genericProp)))
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .build();

    // Обычный тип без generic — exposedAsGlobal=false.
    var plainType = PlatformContextType.builder()
      .name(new ContextName("ТаблицаЗначений", "ValueTable"))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .build();

    var types = new BslContextPlatformTypesProvider(
      holderOf(providerOf(catalogsManager, plainType))).getTypes();

    var manager = types.stream().filter(t -> "СправочникиМенеджер".equals(t.qualifiedName()))
      .findFirst().orElseThrow();
    var plain = types.stream().filter(t -> "ТаблицаЗначений".equals(t.qualifiedName()))
      .findFirst().orElseThrow();

    assertThat(manager.exposedAsGlobal()).isTrue();
    assertThat(plain.exposedAsGlobal()).isFalse();
  }

  @Test
  void typeDescriptionPropagatedFromContextType() {
    var arrayType = PlatformContextType.builder()
      .name(new ContextName("Массив", "Array"))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("Универсальная коллекция значений.")
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(arrayType)))
      .getTypes().iterator().next();

    assertThat(decl.description().ru()).isEqualTo("Универсальная коллекция значений.");
  }

  @Test
  void primitiveDescriptionPropagatedFromShlangPage() {
    var primitive = new PrimitivePlaceholderType(
      new ContextName("Строка", "String"),
      "Значения данного типа содержат строку в формате Unicode.");

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(primitive)))
      .getTypes().iterator().next();

    assertThat(decl.description().ru()).contains("Unicode");
  }

  @Test
  void constructorsMappedToSignatureDescriptors() {
    var stringType = primitive("Строка", "String");

    var defaultCtor = PlatformContextConstructor.builder()
      .name(new ContextName("По умолчанию", "Default"))
      .description("Создаёт пустой массив.")
      .parameters(Collections.emptyList())
      .build();

    var capacityParam = PlatformContextSignatureParameter.builder()
      .name(new ContextName("ФиксированныйРазмер", "FixedSize"))
      .isRequired(true)
      .rawTypes(List.of("Строка"))
      .description("Начальная вместимость массива.")
      .build();
    var byCapacityCtor = PlatformContextConstructor.builder()
      .name(new ContextName("По размеру", "ByCapacity"))
      .description("Создаёт массив с заданной вместимостью.")
      .parameters(List.of(capacityParam))
      .build();

    var arrayType = PlatformContextType.builder()
      .name(new ContextName("Массив", "Array"))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(new ArrayList<>(List.of(
        (ContextConstructor) defaultCtor,
        (ContextConstructor) byCapacityCtor)))
      .description("")
      .build();

    var decl = new BslContextPlatformTypesProvider(
      holderOf(providerOf(arrayType, stringType)))
      .getTypes().stream()
      .filter(t -> "Массив".equals(t.qualifiedName()))
      .findFirst().orElseThrow();

    var ctors = decl.constructors();
    assertThat(ctors).hasSize(2);
    // 1. По умолчанию: без параметров.
    assertThat(ctors.get(0).description()).isEqualTo("Создаёт пустой массив.");
    assertThat(ctors.get(0).parameters()).isEmpty();
    assertThat(ctors.get(0).returnType().qualifiedName())
      .as("returnType конструктора — сам тип Массив")
      .isEqualTo("Массив");
    // 2. По размеру: один обязательный параметр с резолвом типа.
    assertThat(ctors.get(1).description()).isEqualTo("Создаёт массив с заданной вместимостью.");
    assertThat(ctors.get(1).parameters()).hasSize(1);
    var param = ctors.get(1).parameters().get(0);
    assertThat(param.name()).isEqualTo("ФиксированныйРазмер");
    assertThat(param.optional()).isFalse();
    assertThat(ctors.get(1).returnType().qualifiedName()).isEqualTo("Массив");
  }

  @Test
  void multiVariantMethodPublishesMultipleSignatures() {
    // Метод с двумя вариантами синтаксиса — например, Найти(Подстрока)
    // и Найти(Подстрока, НомерСимвола). LS теперь умеет выбирать вариант
    // по фактическому числу аргументов (HoverProvider /
    // SignatureSelection.pickIndexByArity), поэтому важно публиковать все
    // варианты как разные SignatureDescriptor с одним returnType метода.
    var substringParam = PlatformContextSignatureParameter.builder()
      .name(new ContextName("Подстрока", "SearchString"))
      .isRequired(true)
      .rawTypes(List.of())
      .description("")
      .build();
    var sig1 = PlatformContextMethodSignature.builder()
      .name(new ContextName("Основной", ""))
      .parameters(new ArrayList<>(List.of((ContextSignatureParameter) substringParam)))
      .description("Поиск с начала строки.")
      .build();

    var fromParam = PlatformContextSignatureParameter.builder()
      .name(new ContextName("НомерСимвола", "CharNumber"))
      .isRequired(true)
      .rawTypes(List.of())
      .description("")
      .build();
    var sig2 = PlatformContextMethodSignature.builder()
      .name(new ContextName("СНачалаПоиска", ""))
      .parameters(new ArrayList<>(List.of(
        (ContextSignatureParameter) substringParam,
        (ContextSignatureParameter) fromParam)))
      .description("Поиск начиная с указанной позиции.")
      .build();

    var findMethod = PlatformContextMethod.builder()
      .name(new ContextName("Найти", "Find"))
      .description("Ищет вхождение подстроки.")
      .availabilities(List.of())
      .rawReturnValues(List.of())
      .signatures(new ArrayList<>(List.of(
        (ContextMethodSignature) sig1, (ContextMethodSignature) sig2)))
      .build();
    var stringType = PlatformContextType.builder()
      .name(new ContextName("Строка", "String"))
      .methods(new ArrayList<>(List.of(findMethod)))
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(stringType)))
      .getTypes().iterator().next();

    var member = decl.members().iterator().next();
    assertThat(member.signatures())
      .as("Оба варианта синтаксиса должны быть опубликованы")
      .hasSize(2);
    assertThat(member.signatures().get(0).parameters()).hasSize(1);
    assertThat(member.signatures().get(0).description()).isEqualTo("Поиск с начала строки.");
    assertThat(member.signatures().get(1).parameters()).hasSize(2);
    assertThat(member.signatures().get(1).description())
      .isEqualTo("Поиск начиная с указанной позиции.");
  }

  @Test
  void typeWithoutConstructorsHasEmptyConstructorList() {
    var arrayType = PlatformContextType.builder()
      .name(new ContextName("Массив", "Array"))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(arrayType)))
      .getTypes().iterator().next();

    assertThat(decl.constructors()).isEmpty();
  }

  // --- builders ---

  private static PrimitivePlaceholderType primitive(String ru, String en) {
    return new PrimitivePlaceholderType(new ContextName(ru, en), "");
  }

  private static ContextProvider providerOf(Context... contexts) {
    return new PlatformContextProvider(
      new PlatformContextStorage(new ArrayList<>(List.of(contexts))));
  }

  @Test
  void memberNameIsBilingualIndependentOfConfig() {
    // Раньше член имел single primary name, выбираемое pickPrimary(name, language).
    // После рефактора bilingualName хранит обе локали детерминированно;
    // displayName(lang) выбирает на лету. Конструктор провайдера не принимает
    // language вообще — он не нужен.
    var stringType = primitive("Строка", "String");
    var property = PlatformContextProperty.builder()
      .name(new ContextName("Имя", "Name"))
      .rawTypes(List.of("Строка"))
      .description("")
      .availabilities(List.of())
      .build();
    var type = PlatformContextType.builder()
      .name(new ContextName("Объект", "Object"))
      .properties(new ArrayList<>(List.of(property)))
      .methods(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("")
      .build();

    var types = new BslContextPlatformTypesProvider(
      holderOf(providerOf(type, stringType))
    ).getTypes();

    var member = types.stream()
      .filter(t -> "Объект".equals(t.qualifiedName()))
      .findFirst().orElseThrow()
      .members().iterator().next();
    assertThat(member.bilingualName().ru()).isEqualTo("Имя");
    assertThat(member.bilingualName().en()).isEqualTo("Name");
    assertThat(member.displayName(Language.RU)).isEqualTo("Имя");
    assertThat(member.displayName(Language.EN)).isEqualTo("Name");
  }

  // --- Phase 1: platform metadata propagation ---

  @Test
  void propertyMetadataPropagatedFromContextProperty() {
    var stringType = primitive("Строка", "String");
    var readOnlyProp = PlatformContextProperty.builder()
      .name(new ContextName("Ссылка", "Ref"))
      .rawTypes(List.of("Строка"))
      .description("Ссылка на объект.")
      .accessMode(com.github._1c_syntax.bsl.context.api.AccessMode.READ)
      .availabilities(List.of(
        com.github._1c_syntax.bsl.context.api.Availability.SERVER,
        com.github._1c_syntax.bsl.context.api.Availability.THIN_CLIENT))
      .sinceVersion("8.3.10")
      .deprecatedSinceVersion("8.3.27")
      .recommendedReplacements(List.of("СсылкаНаОбъект"))
      .build();
    var type = PlatformContextType.builder()
      .name(new ContextName("СправочникСсылка.X", "CatalogRef.X"))
      .properties(new ArrayList<>(List.of(readOnlyProp)))
      .methods(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("")
      .build();

    var decl = new BslContextPlatformTypesProvider(
      holderOf(providerOf(type, stringType)))
      .getTypes().stream()
      .filter(t -> "СправочникСсылка.X".equals(t.qualifiedName()))
      .findFirst().orElseThrow();

    var member = decl.members().iterator().next();
    var metadata = member.metadata();
    assertThat(metadata.sinceVersion()).isEqualTo("8.3.10");
    assertThat(metadata.deprecatedSinceVersion()).isEqualTo("8.3.27");
    assertThat(metadata.recommendedReplacements()).containsExactly("СсылкаНаОбъект");
    assertThat(metadata.accessMode()).isEqualTo(AccessMode.READ);
    assertThat(metadata.availabilities())
      .containsExactlyInAnyOrder(Availability.SERVER, Availability.THIN_CLIENT);
  }

  @Test
  void methodMetadataPropagatedFromContextMethod() {
    var method = PlatformContextMethod.builder()
      .name(new ContextName("УстаревшийМетод", "ObsoleteMethod"))
      .description("Описание.")
      .availabilities(List.of(com.github._1c_syntax.bsl.context.api.Availability.SERVER))
      .rawReturnValues(List.of())
      .signatures(Collections.emptyList())
      .build();
    // sinceVersion/deprecatedSinceVersion/returnValueDescription/notes/examples/seeAlso —
    // у PlatformContextMethod это final-поля с дефолтами (lombok @Builder.Default не выставлен),
    // поэтому здесь мы проверяем дефолтные значения (пустые), а не выставленные:
    // настоящие данные приходят из HBK.
    var type = PlatformContextType.builder()
      .name(new ContextName("Объект", "Object"))
      .properties(Collections.emptyList())
      .methods(new ArrayList<>(List.of(method)))
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("")
      .build();

    var decl = new BslContextPlatformTypesProvider(
      holderOf(providerOf(type)))
      .getTypes().stream()
      .filter(t -> "Объект".equals(t.qualifiedName()))
      .findFirst().orElseThrow();

    var member = decl.members().iterator().next();
    assertThat(member.metadata().availabilities()).containsExactly(Availability.SERVER);
    // Дефолтные пустые значения не должны кидать NPE и сериализоваться корректно
    assertThat(member.metadata().sinceVersion()).isEmpty();
    assertThat(member.metadata().deprecatedSinceVersion()).isEmpty();
  }

  @Test
  void parameterDefaultValuePropagated() {
    var paramWithDefault = PlatformContextSignatureParameter.builder()
      .name(new ContextName("Флаг", "Flag"))
      .isRequired(false)
      .rawTypes(List.of())
      .description("")
      .defaultValue("Истина")
      .build();
    var signature = PlatformContextMethodSignature.builder()
      .name(new ContextName("Основной", ""))
      .parameters(new ArrayList<>(List.of((ContextSignatureParameter) paramWithDefault)))
      .description("")
      .build();
    var method = PlatformContextMethod.builder()
      .name(new ContextName("Сделать", "Do"))
      .description("")
      .availabilities(List.of())
      .rawReturnValues(List.of())
      .signatures(new ArrayList<>(List.of((ContextMethodSignature) signature)))
      .build();
    var type = PlatformContextType.builder()
      .name(new ContextName("Объект", "Object"))
      .properties(Collections.emptyList())
      .methods(new ArrayList<>(List.of(method)))
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("")
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(type)))
      .getTypes().iterator().next();

    var param = decl.members().iterator().next().signatures().get(0).parameters().get(0);
    assertThat(param.optional()).isTrue();
    assertThat(param.defaultValue()).isEqualTo("Истина");
  }

  @Test
  void collectionForEachAndIndexAccessDescriptionPropagated() {
    var collection = PlatformContextCollection.builder()
      .name(new ContextName("ТаблицаЗначений", "ValueTable"))
      .methods(Collections.emptyList())
      .properties(Collections.emptyList())
      .events(Collections.emptyList())
      .constructors(Collections.emptyList())
      .description("")
      .rawCollectionElementTypes(List.of())
      .supportsForEach(true)
      .forEachDescription("Обход выбирает строки таблицы.")
      .supportsIndexAccess(true)
      .indexAccessDescription("Индексатор — индекс строки с 0.")
      .build();

    var decl = new BslContextPlatformTypesProvider(holderOf(providerOf(collection)))
      .getTypes().iterator().next();

    assertThat(decl.supportsForEach()).isTrue();
    assertThat(decl.supportsIndexAccess()).isTrue();
    assertThat(decl.forEachDescription().ru()).isEqualTo("Обход выбирает строки таблицы.");
    assertThat(decl.indexAccessDescription().ru()).isEqualTo("Индексатор — индекс строки с 0.");
  }
}
