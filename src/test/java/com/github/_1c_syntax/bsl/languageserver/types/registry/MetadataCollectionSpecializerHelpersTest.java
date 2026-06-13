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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.Enum;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import com.github._1c_syntax.bsl.mdo.children.ObjectTabularSection;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты на pure-static helpers {@link MetadataCollectionSpecializer} —
 * без поднятия Spring/HBK. Покрывают форматирование имён, классификацию MDO,
 * extract'ы из mdclasses-объектов через их builder'ы.
 */
class MetadataCollectionSpecializerHelpersTest {

  // === suffixFor ===

  @Test
  void suffixFor_namespaceWithSingleSegment_returnsSegment() {
    assertThat(MetadataCollectionSpecializer.suffixFor("ОбъектМетаданных: Документ.Покупатели"))
      .isEqualTo("Покупатели");
  }

  @Test
  void suffixFor_namespaceWithNestedSection_returnsSubPath() {
    assertThat(MetadataCollectionSpecializer.suffixFor("ОбъектМетаданных: ТабличнаяЧасть.Покупатели.Товары"))
      .isEqualTo("Покупатели.Товары");
  }

  @Test
  void suffixFor_withoutColon_returnsAsIs() {
    assertThat(MetadataCollectionSpecializer.suffixFor("Документ.Покупатели"))
      .isEqualTo("Документ.Покупатели");
  }

  @Test
  void suffixFor_colonWithoutDot_returnsAfterColon() {
    assertThat(MetadataCollectionSpecializer.suffixFor("ОбъектМетаданных: Реквизит"))
      .isEqualTo("Реквизит");
  }

  // === safe ===

  @Test
  void safe_nullToEmpty() {
    assertThat(MetadataCollectionSpecializer.safe(null)).isEmpty();
  }

  @Test
  void safe_nonNullStays() {
    assertThat(MetadataCollectionSpecializer.safe("X")).isEqualTo("X");
  }

  // === isMetadataConfiguration ===

  @Test
  void isMetadataConfiguration_ruName_isTrue() {
    assertThat(MetadataCollectionSpecializer.isMetadataConfiguration("ОбъектМетаданныхКонфигурация")).isTrue();
  }

  @Test
  void isMetadataConfiguration_enName_isTrue() {
    assertThat(MetadataCollectionSpecializer.isMetadataConfiguration("ConfigurationMetadataObject")).isTrue();
  }

  @Test
  void isMetadataConfiguration_other_isFalse() {
    assertThat(MetadataCollectionSpecializer.isMetadataConfiguration("Массив")).isFalse();
  }

  // === isRegister ===

  @Test
  void isRegister_informationRegister_isTrue() {
    MD ir = InformationRegister.builder().name("РегистрСведений1").build();
    assertThat(MetadataMdoPredicates.isRegister(ir)).isTrue();
  }

  @Test
  void isRegister_catalog_isFalse() {
    MD catalog = Catalog.builder().name("Контрагенты").build();
    assertThat(MetadataMdoPredicates.isRegister(catalog)).isFalse();
  }

  @Test
  void isRegister_accumulationRegister_isTrue() {
    MD ar = AccumulationRegister.builder().name("X").build();
    assertThat(MetadataMdoPredicates.isRegister(ar)).isTrue();
  }

  @Test
  void isRegister_accountingRegister_isTrue() {
    MD ar = AccountingRegister.builder().name("X").build();
    assertThat(MetadataMdoPredicates.isRegister(ar)).isTrue();
  }

  @Test
  void isRegister_calculationRegister_isTrue() {
    MD cr = CalculationRegister.builder().name("X").build();
    assertThat(MetadataMdoPredicates.isRegister(cr)).isTrue();
  }

  // === ownerTypeFor ===

  @Test
  void ownerTypeFor_document_returnsHbkName() {
    MD doc = Document.builder().name("ПриходТовара").build();
    assertThat(StandardAttributesResolver.ownerTypeFor(doc))
      .isEqualTo("ОбъектМетаданных: Документ");
  }

  @Test
  void ownerTypeFor_catalog_returnsHbkName() {
    MD cat = Catalog.builder().name("Контрагенты").build();
    assertThat(StandardAttributesResolver.ownerTypeFor(cat))
      .isEqualTo("ОбъектМетаданных: Справочник");
  }

  // === hasStandardAttributes ===

  @Test
  void hasStandardAttributes_document_isTrue() {
    MD doc = Document.builder().name("ПриходТовара").build();
    assertThat(StandardAttributesResolver.hasStandardAttributes(doc)).isTrue();
  }

  @Test
  void hasStandardAttributes_catalog_isTrue() {
    MD cat = Catalog.builder().name("X").build();
    assertThat(StandardAttributesResolver.hasStandardAttributes(cat)).isTrue();
  }

  // === standardAttributesFor ===

  @Test
  void standardAttributesFor_document_returnsKnownAttributes() {
    MD doc = Document.builder().name("X").build();
    var attrs = StandardAttributesResolver.standardAttributesFor(doc);
    assertThat(attrs).extracting(c -> c.name().ru())
      .as("документ имеет стандартный реквизит «Ссылка» (из KnownStandardAttributes)")
      .contains("Ссылка");
  }

  // === singleLingualMdNames / mdoReferenceNames / customAttributeNames / tabularSectionEntries ===

  @Test
  void singleLingualMdNames_emptyInput_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.singleLingualMdNames(List.of())).isEmpty();
  }

  @Test
  void singleLingualMdNames_nonEmptyInput_extractsRuName() {
    MD a = Document.builder().name("A").build();
    MD b = Document.builder().name("B").build();
    var names = MetadataChildrenExtractor.singleLingualMdNames(List.of(a, b));
    assertThat(names).extracting(c -> c.name().ru()).containsExactly("A", "B");
  }

  @Test
  void mdoReferenceNames_emptyInput_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.mdoReferenceNames(List.of())).isEmpty();
  }

  @Test
  void mdoReferenceNames_withTypedRef_buildsReturnTypeOverride() {
    var ref = MdoReference.create(
      MDOType.ACCUMULATION_REGISTER, "ОстаткиТоваров");
    var entries = MetadataChildrenExtractor.mdoReferenceNames(List.of(ref));
    assertThat(entries).hasSize(1);
    var entry = entries.get(0);
    assertThat(entry.name().ru()).isEqualTo("ОстаткиТоваров");
    assertThat(entry.returnTypeOverride())
      .contains("ОбъектМетаданных:")
      .contains("ОстаткиТоваров");
  }

  @Test
  void mdoReferenceNames_emptyRef_skipped() {
    var emptyRef = MdoReference.EMPTY;
    assertThat(MetadataChildrenExtractor.mdoReferenceNames(List.of(emptyRef))).isEmpty();
  }

  @Test
  void mdoReferenceNames_unknownType_skipsReturnTypeOverride() {
    var unknownRef = MdoReference.create(
      MDOType.UNKNOWN, "Безликая");
    var entries = MetadataChildrenExtractor.mdoReferenceNames(List.of(unknownRef));
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).returnTypeOverride()).isNull();
  }

  @Test
  void customAttributeNames_emptyInput_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.customAttributeNames(List.of())).isEmpty();
  }

  @Test
  void customAttributeNames_nonEmpty_skipsBlanks() {
    var named = ObjectAttribute.builder().name("Контрагент").build();
    var blank = ObjectAttribute.builder().name("").build();
    var names = MetadataChildrenExtractor.customAttributeNames(List.of(named, blank));
    assertThat(names).extracting(c -> c.name().ru()).containsExactly("Контрагент");
  }

  @Test
  void tabularSectionEntries_emptyInput_returnsEmpty() {
    assertThat(MetadataChildrenExtractor.tabularSectionEntries(List.of())).isEmpty();
  }

  @Test
  void tabularSectionEntries_nonEmpty_carriesChildReference() {
    var ts = ObjectTabularSection.builder().name("Товары").build();
    var entries = MetadataChildrenExtractor.tabularSectionEntries(List.of(ts));
    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).name().ru()).isEqualTo("Товары");
    assertThat(entries.get(0).child()).isSameAs(ts);
  }

  // === materializeChildMember / withElementReturnType ===

  @Test
  void materializeChildMember_propagatesMetadataAndName() {
    var template = MemberDescriptor.genericProperty("<X>",
      new TypeRef(TypeKind.PLATFORM, "Строка"), "desc");
    var stringRef = new TypeRef(TypeKind.PLATFORM, "Картинка");
    var materialized = MetadataCollectionSpecializer.materializeChildMember(
      template, BilingualString.of("Конкретный", "Concrete"),
      TypeSet.of(stringRef));
    assertThat(materialized.name()).isEqualTo("Конкретный");
    assertThat(materialized.returnType().qualifiedName()).isEqualTo("Картинка");
    assertThat(materialized.generic()).isFalse();
  }

  @Test
  void withElementReturnType_propertyChangesReturnType() {
    var template = MemberDescriptor.property("Получить",
      new TypeRef(TypeKind.PLATFORM, "ОбщийТип"));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var result = MetadataCollectionSpecializer.withElementReturnType(template,
      TypeSet.of(elementRef));
    assertThat(result.returnType().qualifiedName()).isEqualTo("ОбъектМетаданных: Документ");
    assertThat(result.name()).isEqualTo("Получить");
  }

  // === childReturnType ===

  @Test
  void childReturnType_overrideExisting_returnsResolvedRef() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var registerRef = registry.registerConfigurationType("ОбъектМетаданных: РегистрНакопления.ОстаткиТоваров");
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ЗначениеСвойстваОбъектаМетаданных");
    var defaultSet = TypeSet.of(elementRef);

    var child = MetadataCollectionSpecializer.ChildName.withReturnType("ОстаткиТоваров",
      "ОбъектМетаданных: РегистрНакопления.ОстаткиТоваров");
    var result = MetadataCollectionSpecializer.childReturnType(
      registry, child, elementRef, defaultSet, "Покупатели");
    assertThat(result.refs()).containsExactly(registerRef);
  }

  @Test
  void childReturnType_overrideMissing_internsPlatformRef() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "X");
    var defaultSet = TypeSet.of(elementRef);

    var child = MetadataCollectionSpecializer.ChildName.withReturnType("Y", "Z");
    var result = MetadataCollectionSpecializer.childReturnType(
      registry, child, elementRef, defaultSet, "any");
    assertThat(result.refs()).hasSize(1);
    assertThat(result.refs().iterator().next().qualifiedName()).isEqualTo("Z");
  }

  @Test
  void childReturnType_noChildAndNoOverride_returnsDefault() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var defaultSet = TypeSet.of(elementRef);

    var child = MetadataCollectionSpecializer.ChildName.of("Простой");
    var result = MetadataCollectionSpecializer.childReturnType(
      registry, child, elementRef, defaultSet, "Покупатели");
    assertThat(result).isSameAs(defaultSet);
  }

  @Test
  void childReturnType_blankChildName_returnsDefault() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var defaultSet = TypeSet.of(elementRef);

    var blankNamedChild = (MD)
      Document.builder().name("").build();
    var child = MetadataCollectionSpecializer.ChildName.of("ВнешнееИмя", blankNamedChild);
    var result = MetadataCollectionSpecializer.childReturnType(
      registry, child, elementRef, defaultSet, "Покупатели");
    assertThat(result).isSameAs(defaultSet);
  }

  @Test
  void childReturnType_namedChild_internsPerOwnerRef() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: ТабличнаяЧасть");
    var defaultSet = TypeSet.of(elementRef);

    var ts = (MD)
      ObjectTabularSection.builder().name("Товары").build();
    var child = MetadataCollectionSpecializer.ChildName.of("Товары", ts);
    var result = MetadataCollectionSpecializer.childReturnType(
      registry, child, elementRef, defaultSet, "Покупатели");
    assertThat(result.refs().iterator().next().qualifiedName())
      .isEqualTo("ОбъектМетаданных: ТабличнаяЧасть.Покупатели.Товары");
  }

  // === resolveCollection ===

  @Test
  void resolveCollection_baseNameAbsent_returnsNull() {
    var property = Mockito.mock(ContextProperty.class);
    Mockito.when(property.types()).thenReturn(java.util.List.of());
    assertThat(MetadataCollectionSpecializer.resolveCollection(property, "Что-то")).isNull();
  }

  @Test
  void resolveCollection_hbkMarkerWithName_winsOverFallback() {
    var baseCtx = mockCtxByName("КоллекцияОбъектовМетаданных");
    var elementCtx = mockCtxByName("ОбъектМетаданных: Документ");
    var property = Mockito.mock(ContextProperty.class);
    Mockito.when(property.types()).thenReturn(java.util.List.of(baseCtx));
    Mockito.when(property.collectionElementTypes()).thenReturn(java.util.List.of(elementCtx));

    var resolved = MetadataCollectionSpecializer.resolveCollection(property, "Документы");
    assertThat(resolved).isNotNull();
    assertThat(resolved.elementTypeName()).isEqualTo("ОбъектМетаданных: Документ");
  }

  @Test
  void resolveCollection_emptyHbkMarker_fallsBackByRuName() {
    var baseCtx = mockCtxByName("КоллекцияОбъектовМетаданных");
    var property = Mockito.mock(ContextProperty.class);
    Mockito.when(property.types()).thenReturn(java.util.List.of(baseCtx));
    Mockito.when(property.collectionElementTypes()).thenReturn(java.util.List.of());
    Mockito.when(property.name()).thenReturn(
      new ContextName("Реквизиты", "Attributes"));

    var resolved = MetadataCollectionSpecializer.resolveCollection(property, "Реквизиты");
    assertThat(resolved).isNotNull();
    assertThat(resolved.elementTypeName()).isEqualTo("ОбъектМетаданных: Реквизит");
  }

  @Test
  void resolveCollection_ruNameMiss_fallsBackByEnName() {
    var baseCtx = mockCtxByName("КоллекцияОбъектовМетаданных");
    var property = Mockito.mock(ContextProperty.class);
    Mockito.when(property.types()).thenReturn(java.util.List.of(baseCtx));
    Mockito.when(property.collectionElementTypes()).thenReturn(java.util.List.of());
    Mockito.when(property.name()).thenReturn(
      new ContextName("НикомуНеИзвестно", "Attributes"));

    var resolved = MetadataCollectionSpecializer.resolveCollection(property, "НикомуНеИзвестно");
    assertThat(resolved).isNotNull();
    assertThat(resolved.elementTypeName()).isEqualTo("ОбъектМетаданных: Реквизит");
  }

  @Test
  void resolveCollection_noFallbackMatch_returnsNull() {
    var baseCtx = mockCtxByName("КоллекцияОбъектовМетаданных");
    var property = Mockito.mock(ContextProperty.class);
    Mockito.when(property.types()).thenReturn(java.util.List.of(baseCtx));
    Mockito.when(property.collectionElementTypes()).thenReturn(java.util.List.of());
    Mockito.when(property.name()).thenReturn(
      new ContextName("НетТакой", ""));

    assertThat(MetadataCollectionSpecializer.resolveCollection(property, "НетТакой")).isNull();
  }

  private static Context mockCtxByName(String name) {
    var ctx = Mockito.mock(Context.class);
    Mockito.when(ctx.name())
      .thenReturn(new ContextName(name, ""));
    return ctx;
  }

  // === buildGroupCollectionMembers ===

  @Test
  void buildGroupCollectionMembers_emptyBase_returnsEmpty() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var baseRef = new TypeRef(TypeKind.PLATFORM, "Пусто");
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var result = MetadataCollectionSpecializer.buildGroupCollectionMembers(
      registry, baseRef, elementRef, java.util.List.<MD>of());
    assertThat(result).isEmpty();
  }

  @Test
  void buildGroupCollectionMembers_genericWithMdos_materializesNames() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var baseRef = registry.registerConfigurationType("Кол");
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var generic = MemberDescriptor.genericProperty("<X>",
      elementRef, "").withBilingualName(BilingualString.of("<X>", "<X>"));
    registry.registerMemberSource(baseRef, () -> java.util.List.of(generic),
      FileType.BSL);
    MD docA = Document.builder().name("ПродажиТоваров").build();
    MD docBlank = Document.builder().name("").build();

    var result = MetadataCollectionSpecializer.buildGroupCollectionMembers(
      registry, baseRef, elementRef, java.util.List.of(docA, docBlank));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("ПродажиТоваров");
    assertThat(result.get(0).returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: Документ.ПродажиТоваров");
  }

  @Test
  void buildGroupCollectionMembers_elementReturningMethod_replacesReturnType() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var baseRef = registry.registerConfigurationType("Кол2");
    var anyRef = registry.registerConfigurationType("Произвольный");
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Справочник");
    var sig = new SignatureDescriptor(
      java.util.List.of(),
      TypeSet.of(anyRef),
      BilingualString.EMPTY);
    var getMethod = MemberDescriptor.method("Получить", java.util.List.of(sig));
    var inerent = MemberDescriptor.property("Количество",
      registry.registerConfigurationType("Число"), "");
    registry.registerMemberSource(baseRef, () -> java.util.List.of(getMethod, inerent),
      FileType.BSL);

    var result = MetadataCollectionSpecializer.buildGroupCollectionMembers(
      registry, baseRef, elementRef, java.util.List.of());
    // Получить специализирован к element-типу; Количество — оставлен как есть.
    assertThat(result).extracting(MemberDescriptor::name)
      .containsExactlyInAnyOrder("Получить", "Количество");
    var get = result.stream().filter(m -> m.name().equals("Получить")).findFirst().orElseThrow();
    assertThat(get.returnType().qualifiedName()).isEqualTo("ОбъектМетаданных: Справочник");
  }

  // === buildPerOwnerCollectionMembers ===

  @Test
  void buildPerOwnerCollectionMembers_fallbackBranchMaterializesChildrenWithoutGenericTemplate() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var baseRef = new TypeRef(TypeKind.PLATFORM, "Пусто");  // нет members → fallback
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Реквизит");
    var children = java.util.List.of(
      MetadataCollectionSpecializer.ChildName.of("Контрагент"),
      MetadataCollectionSpecializer.ChildName.of("СуммаДок"));

    var result = MetadataCollectionSpecializer.buildPerOwnerCollectionMembers(
      registry, baseRef, elementRef, children, "Покупатели");
    assertThat(result).extracting(MemberDescriptor::name)
      .containsExactlyInAnyOrder("Контрагент", "СуммаДок");
  }

  @Test
  void buildPerOwnerCollectionMembers_genericExpansion_callsChildReturnType() {
    var registry = new TypeRegistry(java.util.List.of(),
      Mockito.mock(GlobalScopeProvider.class),
      Mockito.mock(MemberMetadataIndex.class));
    var baseRef = registry.registerConfigurationType("ОснПодколлекция");
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Реквизит");
    var generic = MemberDescriptor.genericProperty("<X>",
      elementRef, "").withBilingualName(BilingualString.of("<X>", "<X>"));
    registry.registerMemberSource(baseRef, () -> java.util.List.of(generic),
      FileType.BSL);
    var children = java.util.List.of(
      MetadataCollectionSpecializer.ChildName.of("Контрагент"));

    var result = MetadataCollectionSpecializer.buildPerOwnerCollectionMembers(
      registry, baseRef, elementRef, children, "Покупатели");
    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("Контрагент");
  }

  // === withElementReturnType (продолжение) ===

  @Test
  void withElementReturnType_methodRebuildsSignaturesReturnType() {
    var anyRef = new TypeRef(TypeKind.PLATFORM, "Произвольный");
    var ruleSig = new SignatureDescriptor(
      java.util.List.of(),
      TypeSet.of(anyRef),
      BilingualString.EMPTY);
    var template = MemberDescriptor.method("Получить", java.util.List.of(ruleSig));
    var elementRef = new TypeRef(TypeKind.PLATFORM, "ОбъектМетаданных: Документ");
    var elementSet = TypeSet.of(elementRef);
    var result = MetadataCollectionSpecializer.withElementReturnType(template, elementSet);
    assertThat(result.returnType().qualifiedName()).isEqualTo("ОбъектМетаданных: Документ");
    assertThat(result.signatures()).hasSize(1);
    assertThat(result.signatures().get(0).returnType().qualifiedName())
      .isEqualTo("ОбъектМетаданных: Документ");
  }

  // === isElementReturningMethod ===

  @Test
  void isElementReturningMethod_getMethod_isTrue() {
    var m = MemberDescriptor.method("Получить")
      .withBilingualName(BilingualString.of("Получить", "Get"));
    assertThat(MetadataCollectionSpecializer.isElementReturningMethod(m)).isTrue();
  }

  @Test
  void isElementReturningMethod_findMethod_isTrue() {
    var m = MemberDescriptor.method("Найти")
      .withBilingualName(BilingualString.of("Найти", "Find"));
    assertThat(MetadataCollectionSpecializer.isElementReturningMethod(m)).isTrue();
  }

  @Test
  void isElementReturningMethod_otherMethod_isFalse() {
    var m = MemberDescriptor.method("Записать")
      .withBilingualName(BilingualString.of("Записать", "Write"));
    assertThat(MetadataCollectionSpecializer.isElementReturningMethod(m)).isFalse();
  }

  @Test
  void isElementReturningMethod_property_isFalse() {
    var p = MemberDescriptor.property("Получить",
      new TypeRef(TypeKind.PLATFORM, "Строка"));
    assertThat(MetadataCollectionSpecializer.isElementReturningMethod(p)).isFalse();
  }

  // === COLLECTIONS list — все appliesTo и childExtractor вычисляются ===

  @Test
  void collections_appliesTo_matchOwnerMdoTypes() {
    var owners = List.<MD>of(
      Document.builder().name("D").build(),
      Catalog.builder().name("C").build(),
      InformationRegister.builder().name("IR").build(),
      AccumulationRegister.builder().name("AR").build(),
      AccountingRegister.builder().name("ACR").build(),
      CalculationRegister.builder().name("CR").build(),
      DocumentJournal.builder().name("J").build(),
      Enum.builder().name("E").build(),
      ChartOfAccounts.builder().name("COA").build(),
      Task.builder().name("T").build()
    );
    // Каждый predicate в COLLECTIONS на каждом owner'е выполняется без NPE и
    // даёт boolean — это покрытие appliesTo-лямбд + childExtractor-лямбд.
    for (var spec : MetadataCollectionSpecializer.COLLECTIONS) {
      for (var owner : owners) {
        var applies = spec.appliesTo().test(owner);
        var children = spec.childExtractor().apply(owner);
        assertThat(children).isNotNull();
        // если applies → children может быть пустым (нет такой коллекции у пустого
        // builder'а), но child-extractor не должен падать.
        if (!applies) {
          // не applies → children из default-ветки (List.of()).
          assertThat(children).isEmpty();
        }
      }
    }
  }

  // === registerDimensions / registerResources ===

  @Test
  void registerDimensions_informationRegister_returnsDelegated() {
    var ir = InformationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerDimensions(ir)).isEmpty();
  }

  @Test
  void registerDimensions_accumulationRegister_returnsDelegated() {
    var ar = AccumulationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerDimensions(ar)).isEmpty();
  }

  @Test
  void registerDimensions_accountingRegister_returnsDelegated() {
    var ar = AccountingRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerDimensions(ar)).isEmpty();
  }

  @Test
  void registerDimensions_calculationRegister_returnsDelegated() {
    var cr = CalculationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerDimensions(cr)).isEmpty();
  }

  @Test
  void registerDimensions_notRegister_returnsEmpty() {
    MD doc = Document.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerDimensions(doc)).isEmpty();
  }

  @Test
  void registerResources_informationRegister_returnsDelegated() {
    var ir = InformationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerResources(ir)).isEmpty();
  }

  @Test
  void registerResources_accumulationRegister_returnsDelegated() {
    var ar = AccumulationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerResources(ar)).isEmpty();
  }

  @Test
  void registerResources_accountingRegister_returnsDelegated() {
    var ar = AccountingRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerResources(ar)).isEmpty();
  }

  @Test
  void registerResources_calculationRegister_returnsDelegated() {
    var cr = CalculationRegister.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerResources(cr)).isEmpty();
  }

  @Test
  void registerResources_notRegister_returnsEmpty() {
    MD doc = Document.builder().name("X").build();
    assertThat(RegisterChildrenExtractor.registerResources(doc)).isEmpty();
  }

  // === ChildName factories ===

  @Test
  void childName_of_emptyOrBlank_returnsNull() {
    assertThat(MetadataCollectionSpecializer.ChildName.of("")).isNull();
    assertThat(MetadataCollectionSpecializer.ChildName.of("   ")).isNull();
  }

  @Test
  void childName_of_validName_setsNameOnly() {
    var c = MetadataCollectionSpecializer.ChildName.of("Имя");
    assertThat(c).isNotNull();
    assertThat(c.name().ru()).isEqualTo("Имя");
    assertThat(c.child()).isNull();
    assertThat(c.returnTypeOverride()).isNull();
  }

  @Test
  void childName_of_withChild_setsChild() {
    MD doc = Document.builder().name("X").build();
    var c = MetadataCollectionSpecializer.ChildName.of("Имя", doc);
    assertThat(c).isNotNull();
    assertThat(c.child()).isSameAs(doc);
  }

  @Test
  void childName_withReturnType_keepsOverride() {
    var c = MetadataCollectionSpecializer.ChildName.withReturnType("Имя", "ОбъектМетаданных: X");
    assertThat(c).isNotNull();
    assertThat(c.returnTypeOverride()).isEqualTo("ОбъектМетаданных: X");
  }

  @Test
  void childName_withReturnType_blankName_returnsNull() {
    assertThat(MetadataCollectionSpecializer.ChildName.withReturnType("", "X")).isNull();
  }

  @Test
  void childName_bilingual_bothEmpty_returnsNull() {
    assertThat(MetadataCollectionSpecializer.ChildName.bilingual("", "")).isNull();
  }

  @Test
  void childName_bilingual_onlyRu_keepsRuName() {
    var c = MetadataCollectionSpecializer.ChildName.bilingual("Ссылка", "");
    assertThat(c).isNotNull();
    assertThat(c.name().ru()).isEqualTo("Ссылка");
  }

  @Test
  void childName_bilingual_onlyEn_keepsEnName() {
    var c = MetadataCollectionSpecializer.ChildName.bilingual("", "Ref");
    assertThat(c).isNotNull();
    assertThat(c.name().ru()).isEmpty();
    assertThat(c.name().en()).isEqualTo("Ref");
  }

  @Test
  void childName_bilingual_both_keepsBoth() {
    var c = MetadataCollectionSpecializer.ChildName.bilingual("Ссылка", "Ref");
    assertThat(c).isNotNull();
    assertThat(c.name().ru()).isEqualTo("Ссылка");
    assertThat(c.name().en()).isEqualTo("Ref");
  }

  // === nonGenericMembers ===

  @Test
  void nonGenericMembers_filtersOutGenericTemplates() {
    var generic = MemberDescriptor.genericProperty("<Имя X>",
      new TypeRef(TypeKind.PLATFORM, "Строка"), "");
    var concrete = MemberDescriptor.property("Конкретный",
      new TypeRef(TypeKind.PLATFORM, "Число"));
    var result = MetadataCollectionSpecializer.nonGenericMembers(List.of(generic, concrete));
    assertThat(result).extracting(MemberDescriptor::name).containsExactly("Конкретный");
  }
}
