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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты резолвера мета-аннотаций фреймворка «ОСень».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnMetaAnnotationResolverTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnMetaAnnotationResolver resolver;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    resolver = new AutumnMetaAnnotationResolver(libraryIndex, serverContextProvider);
  }

  @Test
  void baseAnnotationMatchesItsRole() {
    // given
    init();

    // when / then
    assertThat(resolver.isRole("Пластилин", AutumnAnnotations.INJECTION)).isTrue();
    assertThat(resolver.isRole("Желудь", AutumnAnnotations.COMPONENT)).isTrue();
  }

  @Test
  void unknownAnnotationDoesNotMatchRole() {
    // given
    init();

    // when / then
    assertThat(resolver.isRole("Желудь", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void findsAnnotationByRole() {
    // given
    init();
    var annotations = List.of(plainAnnotation("Прочее"), injection("ИмяЖелудя"));

    // when
    var found = resolver.findByRole(annotations, AutumnAnnotations.INJECTION);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Пластилин");
  }

  @Test
  void hasRoleReportsAbsence() {
    // given
    init();
    var annotations = List.of(plainAnnotation("Прочее"));

    // when / then
    assertThat(resolver.hasRole(annotations, AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void collectsValuesByRole() {
    // given
    init();
    var annotations = List.of(qualifier("Васян"), qualifier("Панк"), plainAnnotation("Прочее"));

    // when
    var values = resolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER);

    // then
    assertThat(values).containsExactly("Васян", "Панк");
  }

  @Test
  void resolvesCustomAnnotationThroughMetaAnnotation() {
    // given
    // АннотацияВнедряемое: &Аннотация("Внедряемое") &Пластилин
    registerAnnotationClass("АннотацияВнедряемое",
      marker("Внедряемое"),
      plainAnnotation("Пластилин"));
    init();

    // when / then
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.COMPONENT)).isFalse();
  }

  @Test
  void skipsDefinitionWithoutMarker() {
    // given
    registerAnnotationClass("ПростоКласс", plainAnnotation("Пластилин"));
    init();

    // when / then
    assertThat(resolver.isRole("ПростоКласс", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void skipsDefinitionWithBlankCustomName() {
    // given
    registerAnnotationClass("АннотацияПустая", marker(""), plainAnnotation("Пластилин"));
    init();

    // when / then
    assertThat(resolver.isRole("", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void skipsEntryWithoutServerContext() {
    // given
    var uri = URI.create("file:///ann/Нет.os");
    var entry = new LibraryEntry(uri, "Нет", EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.empty());
    init();

    // when / then
    assertThat(resolver.isRole("ЧтоУгодно", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void skipsEntryWithoutDocument() {
    // given
    var uri = URI.create("file:///ann/Нет.os");
    var entry = new LibraryEntry(uri, "Нет", EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    var serverContext = mock(ServerContext.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(null);
    init();

    // when / then
    assertThat(resolver.isRole("ЧтоУгодно", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void breaksMetaAnnotationCycle() {
    // given
    // А ссылается на Б, Б ссылается на А — цикл не должен зациклить резолвер
    registerAnnotationClass("АннотацияА", marker("А"), plainAnnotation("Б"));
    registerAnnotationClass("АннотацияБ", marker("Б"), plainAnnotation("А"));
    init();

    // when / then
    assertThat(resolver.isRole("А", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void rebuildsContributionOfChangedDocument() {
    // given: класс-определение объявляет аннотацию "Внедряемое" = &Пластилин
    var uri = URI.create("file:///ann/АннотацияВнедряемое.os");
    var entry = new LibraryEntry(uri, "АннотацияВнедряемое", EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    var method = mock(MethodSymbol.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getMethods()).thenReturn(List.of(method));
    when(method.getAnnotations()).thenReturn(List.of(marker("Внедряемое"), plainAnnotation("Пластилин")));
    init();
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();

    // when: класс-определение отредактирован — аннотация переименована в "Впрыск"
    when(method.getAnnotations()).thenReturn(List.of(marker("Впрыск"), plainAnnotation("Пластилин")));
    when(document.getFileType()).thenReturn(FileType.OS);
    when(document.getUri()).thenReturn(uri);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    resolver.handleDocumentChange(event);

    // then: старое имя больше не разворачивается в роль, новое — разворачивается
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isFalse();
    assertThat(resolver.isRole("Впрыск", AutumnAnnotations.INJECTION)).isTrue();
  }

  // --- helpers ---------------------------------------------------------------

  private void registerAnnotationClass(String qualifiedName, Annotation... constructorAnnotations) {
    var uri = URI.create("file:///ann/" + qualifiedName + ".os");
    var entry = new LibraryEntry(uri, qualifiedName, EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    var method = mock(MethodSymbol.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getMethods()).thenReturn(List.of(method));
    when(method.getAnnotations()).thenReturn(List.of(constructorAnnotations));
  }

  private static Annotation marker(String customName) {
    return Annotation.builder()
      .name("Аннотация")
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(customName), true)))
      .build();
  }

  private static Annotation plainAnnotation(String name) {
    return Annotation.builder().name(name).kind(AnnotationKind.CUSTOM).build();
  }

  private static Annotation injection(String value) {
    return withValue(AutumnAnnotations.INJECTION, value);
  }

  private static Annotation qualifier(String value) {
    return withValue(AutumnAnnotations.QUALIFIER, value);
  }

  private static Annotation withValue(String name, String value) {
    return Annotation.builder()
      .name(name)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)))
      .build();
  }
}
