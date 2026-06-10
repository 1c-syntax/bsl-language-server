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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционные тесты централизованных обходов отношений наследования
 * библиотеки extends на реальных {@code .os}-фикстурах: иерархия типов
 * ({@code &Расширяет}), транзитивная реализация интерфейсов
 * ({@code &Реализует}/иерархия интерфейсов), унаследованные члены, защита от
 * циклов и поправка на мета-аннотации «ОСени» ({@code &Аннотация}).
 */
@CleanupContextBeforeClassAndAfterClass
class TypeRelationsTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRelations index;

  @Autowired
  private OScriptLibraryIndex libraryIndex;

  @Autowired
  private TypeRegistry typeRegistry;

  private static final Path TYPE_HIERARCHY = Path.of("src/test/resources/type-hierarchy").toAbsolutePath();
  private static final Path EXTENDS_LIB =
    Path.of("src/test/resources/oscript-libraries/extends-lib").toAbsolutePath();
  private static final Path INTERFACE_HIERARCHY =
    Path.of("src/test/resources/oscript-libraries/interface-hierarchy-lib").toAbsolutePath();
  private static final Path INTERFACE_ABSTRACT =
    Path.of("src/test/resources/oscript-libraries/interface-abstract-lib").toAbsolutePath();
  private static final Path AUTUMN_DATA =
    Path.of("src/test/resources/oscript-libraries/autumn-data-sample").toAbsolutePath();
  private static final Path CYCLE_LIB =
    Path.of("src/test/resources/oscript-libraries/cycle-lib").toAbsolutePath();

  // --- иерархия типов (&Расширяет, basename-резолв) ---

  @Test
  void supertypeNameReturnsDeclaredParent() {
    initPlain(TYPE_HIERARCHY);
    var cat = plainDoc(TYPE_HIERARCHY, "Кошка.os");

    assertThat(index.supertypeName(cat)).contains("Млекопитающее");
    assertThat(index.isInterface(cat)).isFalse();
  }

  @Test
  void supertypeResolvesParentDocument() {
    initPlain(TYPE_HIERARCHY);
    var cat = plainDoc(TYPE_HIERARCHY, "Кошка.os");
    var mammal = plainDoc(TYPE_HIERARCHY, "Млекопитающее.os");

    assertThat(index.supertype(cat)).contains(mammal);
  }

  @Test
  void supertypeEmptyForRootClass() {
    initPlain(TYPE_HIERARCHY);
    var animal = plainDoc(TYPE_HIERARCHY, "Животное.os");

    assertThat(index.supertype(animal)).isEmpty();
    assertThat(index.supertypeName(animal)).isEmpty();
  }

  @Test
  void subtypesReturnsDirectChildren() {
    initPlain(TYPE_HIERARCHY);
    var mammal = plainDoc(TYPE_HIERARCHY, "Млекопитающее.os");

    assertThat(index.subtypes(mammal))
      .extracting(documentName())
      .containsExactlyInAnyOrder("Кошка", "Собака");
  }

  @Test
  void subtypesEmptyForLeafClass() {
    initPlain(TYPE_HIERARCHY);
    var cat = plainDoc(TYPE_HIERARCHY, "Кошка.os");

    assertThat(index.subtypes(cat)).isEmpty();
  }

  // --- реализация интерфейсов (&Реализует, иерархия интерфейсов) ---

  @Test
  void isInterfaceTrueForInterfaceMarker() {
    initLibrary(INTERFACE_HIERARCHY);
    var baseInterface = libDoc(INTERFACE_HIERARCHY, "БазовыйИнтерфейс.os");

    assertThat(index.isInterface(baseInterface)).isTrue();
  }

  @Test
  void implementorsOfBaseInterfaceIncludeDerivedInterfaceImplementors() {
    // given: РеализацияБазового реализует БазовыйИнтерфейс напрямую,
    // РеализацияПроизводного — через ПроизводныйИнтерфейс (&Расширяет базового)
    initLibrary(INTERFACE_HIERARCHY);
    var baseInterface = libDoc(INTERFACE_HIERARCHY, "БазовыйИнтерфейс.os");

    // when / then
    assertThat(index.implementors(baseInterface))
      .extracting(documentName())
      .containsExactlyInAnyOrder("РеализацияБазового", "РеализацияПроизводного");
  }

  @Test
  void implementorsOfDerivedInterfaceExcludeBaseImplementors() {
    // given
    initLibrary(INTERFACE_HIERARCHY);
    var derivedInterface = libDoc(INTERFACE_HIERARCHY, "ПроизводныйИнтерфейс.os");

    // when / then: реализатор только базового интерфейса не реализует производный
    assertThat(index.implementors(derivedInterface))
      .extracting(documentName())
      .containsExactly("РеализацияПроизводного");
  }

  @Test
  void implementorsIncludeSubclassesOfAbstractImplementor() {
    // given: интерфейс реализует абстрактный родитель, конкретный класс наследует его
    initLibrary(INTERFACE_ABSTRACT);
    var storageInterface = libDoc(INTERFACE_ABSTRACT, "ИнтерфейсХранилища.os");

    // when / then
    assertThat(index.implementors(storageInterface))
      .extracting(documentName())
      .containsExactlyInAnyOrder("АбстрактноеХранилище", "КонкретноеХранилище");
  }

  // --- унаследованные члены ---

  @Test
  void inheritedMembersReturnsParentMembersTransitively() {
    initLibrary(EXTENDS_LIB);
    var child = libDoc(EXTENDS_LIB, "ДочернийКласс.os");
    var childRef = typeRegistry.resolve("ДочернийКласс", FileType.OS).orElseThrow();

    assertThat(index.inheritedMembers(child, childRef))
      .extracting(MemberDescriptor::name)
      .contains("ПромежуточныйМетод", "БазовыйМетод");
  }

  @Test
  void inheritedMembersEmptyForRootClass() {
    initLibrary(EXTENDS_LIB);
    var base = libDoc(EXTENDS_LIB, "БазовыйКласс.os");
    var baseRef = typeRegistry.resolve("БазовыйКласс", FileType.OS).orElseThrow();

    assertThat(index.inheritedMembers(base, baseRef)).isEmpty();
  }

  // --- мета-аннотации «ОСени»: класс-аннотация — не подтип обычного класса ---

  @Test
  void supertypeSuppressedForAnnotationDefinitionExtendingPlainClass() {
    initLibrary(AUTUMN_DATA);
    var annotation = libDoc(AUTUMN_DATA, "АннотацияХранилищеСущностей.os");

    assertThat(index.supertype(annotation)).isEmpty();
    assertThat(index.supertypeName(annotation)).isEmpty();
  }

  @Test
  void subtypesExcludesAnnotationDefinitionButKeepsAnnotatedClass() {
    initLibrary(AUTUMN_DATA);
    var base = libDoc(AUTUMN_DATA, "ХранилищеСущностей.os");

    assertThat(index.subtypes(base))
      .extracting(documentName())
      .containsExactly("СправочникиХранилище")
      .doesNotContain("АннотацияХранилищеСущностей");
  }

  @Test
  void inheritedMembersSuppressedForAnnotationDefinition() {
    initLibrary(AUTUMN_DATA);
    var annotation = libDoc(AUTUMN_DATA, "АннотацияХранилищеСущностей.os");
    var annotationRef = typeRegistry.resolve("АннотацияХранилищеСущностей", FileType.OS).orElseThrow();

    assertThat(index.inheritedMembers(annotation, annotationRef)).isEmpty();
  }

  // --- защита от циклов ---

  @Test
  void inheritedMembersTerminatesOnExtendsCycle() {
    initLibrary(CYCLE_LIB);
    var cycleA = libDoc(CYCLE_LIB, "ЦиклА.os");
    var cycleARef = typeRegistry.resolve("ЦиклА", FileType.OS).orElseThrow();

    // Не должно уйти в бесконечную рекурсию; набор членов конечен.
    assertThat(index.inheritedMembers(cycleA, cycleARef))
      .extracting(MemberDescriptor::name)
      .contains("МетодБ");
  }

  @Test
  void implementorsTerminateOnInterfaceHierarchyCycle() {
    // given: ИнтерфейсЦиклА и ИнтерфейсЦиклБ расширяют друг друга (цикл);
    // РеализаторЦикла реализует ИнтерфейсЦиклА
    initLibrary(CYCLE_LIB);
    var cycledInterface = libDoc(CYCLE_LIB, "ИнтерфейсЦиклБ.os");

    // when / then: обход замыкания завершается, реализатор найден через цикл
    assertThat(index.implementors(cycledInterface))
      .extracting(documentName())
      .containsExactly("РеализаторЦикла");
  }

  // --- helpers ---

  private void initLibrary(Path root) {
    initServerContext(root, false);
    libraryIndex.reindex(context);
  }

  private void initPlain(Path root) {
    initServerContext(root);
  }

  private DocumentContext libDoc(Path root, String fileName) {
    return document(Absolute.uri(root.resolve("src").resolve(fileName).toUri()), fileName);
  }

  private DocumentContext plainDoc(Path root, String fileName) {
    return document(Absolute.uri(root.resolve(fileName).toUri()), fileName);
  }

  private DocumentContext document(URI uri, String name) {
    var documentContext = context.getDocument(uri);
    assertThat(documentContext).as("document %s must be populated", name).isNotNull();
    return documentContext;
  }

  private static java.util.function.Function<DocumentContext, String> documentName() {
    return doc -> {
      var path = doc.getUri().getPath();
      var base = path.substring(path.lastIndexOf('/') + 1);
      return base.endsWith(".os") ? base.substring(0, base.length() - 3) : base;
    };
  }
}
