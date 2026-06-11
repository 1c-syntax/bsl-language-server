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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptAnnotations;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptMetaAnnotationResolver;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnCollectionIndexTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  @Mock
  private TypeRegistry typeRegistry;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnCollectionIndex collectionIndex;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    // Реальный резолвер мета-аннотаций поверх пустого репозитория: для базового
    // имени &ПрилепляемаяКоллекция он короткозамыкается. Пользовательских
    // алиасов в этих тестах нет.
    var metaResolver = new OScriptMetaAnnotationResolver(new AnnotationRepository());
    collectionIndex = new AutumnCollectionIndex(libraryIndex, serverContextProvider, typeRegistry, metaResolver);
  }

  @Test
  void returnsEmptyForBlankNames() {
    // given
    init();

    // when / then
    assertThat(collectionIndex.resolve("").isEmpty()).isTrue();
    assertThat(collectionIndex.resolve("   ").isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyForUnknownCollection() {
    // given
    init();

    // when / then
    assertThat(collectionIndex.resolve("НетТакой").isEmpty()).isTrue();
  }

  @Test
  void resolvesCollectionByGetterReturnedType() {
    // given: класс с &ПрилепляемаяКоллекция("Массив") и Получить() с описанием
    // возвращаемого типа ФиксированныйМассив.
    var fixedArray = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");
    when(typeRegistry.resolve("ФиксированныйМассив")).thenReturn(Optional.of(fixedArray));
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(typeDescription("ФиксированныйМассив")));
    init();

    // when / then
    assertThat(collectionIndex.resolve("Массив").refs()).containsExactly(fixedArray);
  }

  @Test
  void resolvesCollectionCaseInsensitively() {
    // given
    var fixedArray = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");
    when(typeRegistry.resolve("ФиксированныйМассив")).thenReturn(Optional.of(fixedArray));
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(typeDescription("ФиксированныйМассив")));
    init();

    // when / then: имя коллекции резолвится регистронезависимо.
    assertThat(collectionIndex.resolve("МАССИВ").refs()).containsExactly(fixedArray);
    assertThat(collectionIndex.resolve("массив").refs()).containsExactly(fixedArray);
  }

  @Test
  void returnsEmptyWhenGetterHasNoDescription() {
    // given: коллекция объявлена, но у Получить() bsldoc-описания нет — кейс
    // фоллбэка из issue #3959 (вызывающая сторона должна попробовать TypeRegistry).
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(/* без описания */));
    init();

    // when / then
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyWhenGetterIsMissing() {
    // given: аннотация коллекции есть, но метода Получить() в классе нет.
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив"))
      /* без getter'а */);
    init();

    // when / then
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
  }

  @Test
  void skipsNonExportedGetter() {
    // given: Получить() есть, но не экспортный — наружу он не доступен,
    // регистрировать тип незачем.
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(false, true, List.of(typeDescription("ФиксированныйМассив"))));
    init();

    // when / then
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
  }

  @Test
  void skipsProcedureGetter() {
    // given: метод с именем «Получить», но процедура — не функция, типа не имеет.
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(true, false, List.of(typeDescription("ФиксированныйМассив"))));
    init();

    // when / then
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
  }

  @Test
  void skipsClassWithoutCollectionAnnotation() {
    // given: обычный .os-класс без &ПрилепляемаяКоллекция.
    registerClass("ОбычныйКласс",
      constructor(/* без аннотаций */),
      getter(typeDescription("ФиксированныйМассив")));
    init();

    // when / then: в индексе ничего нет.
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
  }

  @Test
  void skipsAnnotationDefinitionClass() {
    // given: класс-определение пользовательской аннотации (&Аннотация(...)) —
    // не реализация коллекции, индексировать его не нужно.
    registerClass("АннотацияПрилепляемаяКоллекция",
      constructor(annotation(OScriptAnnotations.ANNOTATION_MARKER, "ПрилепляемаяКоллекция"),
        collectionAnnotation("ПрилепляемаяКоллекция")),
      getter(typeDescription("Произвольный")));
    init();

    // when / then
    assertThat(collectionIndex.resolve("ПрилепляемаяКоллекция").isEmpty()).isTrue();
  }

  @Test
  void skipsCollectionWithBlankName() {
    // given: имя коллекции пустое — регистрировать нечего.
    registerClass("ПлохаяКоллекция",
      constructor(collectionAnnotation("")),
      getter(typeDescription("ФиксированныйМассив")));
    init();

    // when / then
    assertThat(collectionIndex.resolve("").isEmpty()).isTrue();
  }

  @Test
  void skipsTypeUnresolvedInRegistry() {
    // given: описанный тип не зарегистрирован в реестре — резолв пуст.
    when(typeRegistry.resolve("ОченьЭкзотическийТип")).thenReturn(Optional.empty());
    registerClass("ПрилепляемаяКоллекцияСвоя",
      constructor(collectionAnnotation("Своя")),
      getter(typeDescription("ОченьЭкзотическийТип")));
    init();

    // when / then
    assertThat(collectionIndex.resolve("Своя").isEmpty()).isTrue();
  }

  @Test
  void rebuildsContributionOfChangedDocument() {
    // given: коллекция «Массив» возвращает ФиксированныйМассив.
    var uri = URI.create("file:///collections/ПрилепляемаяКоллекцияМассив.os");
    var entry = new LibraryEntry(uri, "ПрилепляемаяКоллекцияМассив", EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);

    var fixedArray = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");
    var fixedList = new TypeRef(TypeKind.PLATFORM, "ФиксированныйСписок");
    when(typeRegistry.resolve("ФиксированныйМассив")).thenReturn(Optional.of(fixedArray));
    when(typeRegistry.resolve("ФиксированныйСписок")).thenReturn(Optional.of(fixedList));

    var ctor = constructor(collectionAnnotation("Массив"));
    var initialGetter = getter(typeDescription("ФиксированныйМассив"));
    when(symbolTree.getConstructor()).thenReturn(Optional.of(ctor));
    when(symbolTree.getMethods()).thenReturn(List.of(ctor, initialGetter));
    init();
    assertThat(collectionIndex.resolve("Массив").refs()).containsExactly(fixedArray);

    // when: документ отредактирован — коллекция переименована и тип сменился.
    var newCtor = constructor(collectionAnnotation("Список"));
    var newGetter = getter(typeDescription("ФиксированныйСписок"));
    when(symbolTree.getConstructor()).thenReturn(Optional.of(newCtor));
    when(symbolTree.getMethods()).thenReturn(List.of(newCtor, newGetter));
    when(document.getFileType()).thenReturn(FileType.OS);
    when(document.getUri()).thenReturn(uri);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    collectionIndex.handleDocumentChange(event);

    // then: старое имя коллекции больше не известно, новое — резолвится в новый тип.
    assertThat(collectionIndex.resolve("Массив").isEmpty()).isTrue();
    assertThat(collectionIndex.resolve("Список").refs()).containsExactly(fixedList);
  }

  @Test
  void ignoresBslDocumentChange() {
    // given
    var fixedArray = new TypeRef(TypeKind.PLATFORM, "ФиксированныйМассив");
    when(typeRegistry.resolve("ФиксированныйМассив")).thenReturn(Optional.of(fixedArray));
    registerClass("ПрилепляемаяКоллекцияМассив",
      constructor(collectionAnnotation("Массив")),
      getter(typeDescription("ФиксированныйМассив")));
    init();
    assertThat(collectionIndex.resolve("Массив").refs()).containsExactly(fixedArray);

    // when: правится .bsl-документ — к коллекциям отношения не имеет.
    var document = mock(DocumentContext.class);
    when(document.getFileType()).thenReturn(FileType.BSL);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    collectionIndex.handleDocumentChange(event);

    // then: индекс не тронут.
    assertThat(collectionIndex.resolve("Массив").refs()).containsExactly(fixedArray);
  }

  // --- helpers ---------------------------------------------------------------

  private URI registerEntry(String qualifiedName) {
    var uri = URI.create("file:///collections/" + qualifiedName + ".os");
    var entry = new LibraryEntry(uri, qualifiedName, EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    return uri;
  }

  /**
   * Зарегистрировать .os-класс с конструктором и (опционально) методами.
   * Конструктор несёт аннотации коллекции; первый метод обычно — getter Получить().
   */
  private void registerClass(String qualifiedName, ConstructorSymbol ctor, MethodSymbol... methods) {
    var uri = registerEntry(qualifiedName);
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getConstructor()).thenReturn(Optional.ofNullable(ctor));
    var allMethods = new ArrayList<MethodSymbol>();
    if (ctor != null) {
      allMethods.add(ctor);
    }
    allMethods.addAll(List.of(methods));
    when(symbolTree.getMethods()).thenReturn(allMethods);
  }

  private static ConstructorSymbol constructor(Annotation... annotations) {
    var ctor = mock(ConstructorSymbol.class);
    lenient().when(ctor.getAnnotations()).thenReturn(List.of(annotations));
    return ctor;
  }

  /** Экспортная функция {@code Получить()} с заданным списком типов в bsldoc. */
  private static MethodSymbol getter(TypeDescription... returnedTypes) {
    return getter(true, true, List.of(returnedTypes));
  }

  /**
   * Метод {@code Получить()} с явным указанием признаков «экспортный/функция» и
   * списком типов возвращаемого значения. Пустой список = у getter'а нет описания.
   */
  private static MethodSymbol getter(boolean exported, boolean function, List<TypeDescription> returnedTypes) {
    var method = mock(MethodSymbol.class);
    lenient().when(method.getName()).thenReturn(AutumnAnnotations.ATTACHABLE_COLLECTION_GETTER);
    lenient().when(method.isExport()).thenReturn(exported);
    lenient().when(method.isFunction()).thenReturn(function);
    if (returnedTypes.isEmpty()) {
      lenient().when(method.getDescription()).thenReturn(Optional.empty());
    } else {
      var description = mock(MethodDescription.class);
      lenient().when(description.getReturnedValue()).thenReturn(returnedTypes);
      lenient().when(method.getDescription()).thenReturn(Optional.of(description));
    }
    return method;
  }

  private static TypeDescription typeDescription(String name) {
    var td = mock(TypeDescription.class);
    lenient().when(td.name()).thenReturn(name);
    return td;
  }

  private static Annotation collectionAnnotation(String name) {
    var parameters = new ArrayList<AnnotationParameterDefinition>();
    parameters.add(new AnnotationParameterDefinition(OScriptAnnotations.VALUE_PARAMETER,
      Either.forLeft(name), true));
    return Annotation.builder()
      .name(AutumnAnnotations.ATTACHABLE_COLLECTION)
      .kind(AnnotationKind.CUSTOM)
      .parameters(parameters)
      .build();
  }

  private static Annotation annotation(String annotationName, String value) {
    var builder = Annotation.builder().name(annotationName).kind(AnnotationKind.CUSTOM);
    if (value != null) {
      builder.parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)));
    }
    return builder.build();
  }
}
