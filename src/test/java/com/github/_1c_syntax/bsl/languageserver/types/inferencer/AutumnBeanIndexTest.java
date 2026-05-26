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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
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
class AutumnBeanIndexTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  @Mock
  private TypeRegistry typeRegistry;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnBeanIndex beanIndex;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    var metaResolver = new AutumnMetaAnnotationResolver(libraryIndex, serverContextProvider);
    beanIndex = new AutumnBeanIndex(libraryIndex, serverContextProvider, typeRegistry, metaResolver);
  }

  @Test
  void returnsEmptyForBlankNames() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolve(null).isEmpty()).isTrue();
    assertThat(beanIndex.resolve("").isEmpty()).isTrue();
    assertThat(beanIndex.resolve("   ").isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyForUnknownBean() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolve("НетТакого").isEmpty()).isTrue();
  }

  @Test
  void skipsEntryWithoutServerContext() {
    // given
    var uri = registerEntry("Безконтекста");
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.empty());
    init();

    // when / then
    assertThat(beanIndex.resolve("Безконтекста").isEmpty()).isTrue();
  }

  @Test
  void skipsEntryWithoutDocument() {
    // given
    var uri = registerEntry("БезДокумента");
    var serverContext = mock(ServerContext.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(null);
    init();

    // when / then
    assertThat(beanIndex.resolve("БезДокумента").isEmpty()).isTrue();
  }

  @Test
  void skipsComponentWhenTypeUnresolved() {
    // given
    var type = new TypeRef(TypeKind.USER, "Компонент");
    // тип НЕ зарегистрирован в реестре -> ownerType == null
    registerClass("Компонент", null, method(component(null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Компонент").isEmpty()).isTrue();
    assertThat(type).isNotNull();
  }

  @Test
  void registersComponentByDefaultName() {
    // given
    var type = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", type, method(component(null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);
  }

  @Test
  void registersComponentByExplicitName() {
    // given
    var type = new TypeRef(TypeKind.USER, "РеальныйКласс");
    registerClass("РеальныйКласс", type, method(component("ЛогическоеИмя")));
    init();

    // when / then
    assertThat(beanIndex.resolve("ЛогическоеИмя").refs()).containsExactly(type);
  }

  @Test
  void registersQualifierAlias() {
    // given
    var type = new TypeRef(TypeKind.USER, "Василий");
    registerClass("Василий", type, method(component(null), qualifier("Васян")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Васян").refs()).containsExactly(type);
  }

  @Test
  void prefersPrimaryOnConflict() {
    // given
    var sid = new TypeRef(TypeKind.USER, "СидВишес");
    var johnny = new TypeRef(TypeKind.USER, "ДжонниРоттен");
    registerClass("ДжонниРоттен", johnny, method(component(null), qualifier("Панк")));
    registerClass("СидВишес", sid, method(component(null), primary(), qualifier("Панк")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Панк").refs()).containsExactly(sid);
  }

  @Test
  void unionsCandidatesWithoutPrimary() {
    // given
    var first = new TypeRef(TypeKind.USER, "Первый");
    var second = new TypeRef(TypeKind.USER, "Второй");
    registerClass("Первый", first, method(component(null), qualifier("Общий")));
    registerClass("Второй", second, method(component(null), qualifier("Общий")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Общий").refs()).containsExactlyInAnyOrder(first, second);
  }

  @Test
  void registersFactoryByMethodNameWithBeanLiteralType() {
    // given
    var type = new TypeRef(TypeKind.USER, "СоединениеСБазой");
    when(typeRegistry.resolve("СоединениеСБазой")).thenReturn(Optional.of(type));
    // &Завязь без имени и с Тип="Желудь" -> имя = имя метода, тип резолвится по имени
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      namedMethod("СоединениеСБазой", factory(null, "Желудь")));
    init();

    // when / then
    assertThat(beanIndex.resolve("СоединениеСБазой").refs()).containsExactly(type);
  }

  @Test
  void registersFactoryWithExplicitCollectionType() {
    // given
    var array = new TypeRef(TypeKind.PLATFORM, "Массив");
    when(typeRegistry.resolve("Массив")).thenReturn(Optional.of(array));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      namedMethod("СписокЖелудей", factory("СписокЖелудей", "Массив")));
    init();

    // when / then
    assertThat(beanIndex.resolve("СписокЖелудей").refs()).containsExactly(array);
  }

  @Test
  void registersFactoryWithBlankTypeByName() {
    // given
    var type = new TypeRef(TypeKind.USER, "Хлеб");
    when(typeRegistry.resolve("Хлеб")).thenReturn(Optional.of(type));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      namedMethod("СоздатьХлеб", factory("Хлеб", "")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Хлеб").refs()).containsExactly(type);
  }

  @Test
  void skipsFactoryWhenTypeUnresolved() {
    // given
    when(typeRegistry.resolve("Неизвестный")).thenReturn(Optional.empty());
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      namedMethod("Неизвестный", factory(null, null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Неизвестный").isEmpty()).isTrue();
  }

  // --- helpers ---------------------------------------------------------------

  private URI registerEntry(String qualifiedName) {
    var uri = URI.create("file:///beans/" + qualifiedName + ".os");
    entries.add(new LibraryEntry(uri, qualifiedName, EntryKind.CLASS, "lib", false));
    return uri;
  }

  private void registerClass(String qualifiedName, TypeRef ownerType, MethodSymbol... methods) {
    var uri = registerEntry(qualifiedName);
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getMethods()).thenReturn(List.of(methods));
    when(typeRegistry.resolve(qualifiedName)).thenReturn(Optional.ofNullable(ownerType));
  }

  private static MethodSymbol method(Annotation... annotations) {
    return namedMethod("ПриСозданииОбъекта", annotations);
  }

  private static MethodSymbol namedMethod(String name, Annotation... annotations) {
    var method = mock(MethodSymbol.class);
    lenient().when(method.getName()).thenReturn(name);
    lenient().when(method.getAnnotations()).thenReturn(List.of(annotations));
    return method;
  }

  private static Annotation component(String name) {
    return annotation(AutumnAnnotations.COMPONENT, name);
  }

  private static Annotation qualifier(String alias) {
    return annotation(AutumnAnnotations.QUALIFIER, alias);
  }

  private static Annotation primary() {
    return Annotation.builder().name(AutumnAnnotations.PRIMARY).kind(AnnotationKind.CUSTOM).build();
  }

  private static Annotation factory(String name, String type) {
    var parameters = new ArrayList<AnnotationParameterDefinition>();
    if (name != null) {
      parameters.add(new AnnotationParameterDefinition("Значение", Either.forLeft(name), true));
    }
    if (type != null) {
      parameters.add(new AnnotationParameterDefinition("Тип", Either.forLeft(type), true));
    }
    return Annotation.builder()
      .name(AutumnAnnotations.FACTORY)
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
