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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer.InjectedBean;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnInjectionPointIndexTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;
  @Mock
  private ServerContextProvider serverContextProvider;
  @Mock
  private AutumnComponentInferencer componentInferencer;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnInjectionPointIndex index;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    index = new AutumnInjectionPointIndex(libraryIndex, serverContextProvider, componentInferencer);
  }

  @Test
  void returnsEmptyForBlankOrUnknownName() {
    // given
    init();

    // when / then
    assertThat(index.resolve("")).isEmpty();
    assertThat(index.resolve("НетТакого")).isEmpty();
  }

  @Test
  void indexesFieldInjection() {
    // given: класс-потребитель с полем &Пластилин, внедряющим желудь "Лог"
    var range = range(3);
    var uri = registerClass("Потребитель", field("Лог", range), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    init();

    // when
    var points = index.resolve("Лог");

    // then
    assertThat(points).singleElement().satisfies(point -> {
      assertThat(point.uri()).isEqualTo(uri);
      assertThat(point.range()).isEqualTo(range);
    });
  }

  @Test
  void indexesConstructorParameterInjection() {
    // given: внедрение через параметр конструктора
    var range = range(1);
    var parameter = ParameterDefinition.builder().name("лог").annotations(List.of()).range(range).build();
    var uri = registerClass("ПотребительВКонструкторе", null, List.of(parameter));
    when(componentInferencer.injectedBean(anyList(), eq("лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    init();

    // when
    var points = index.resolve("Лог");

    // then
    assertThat(points).singleElement().satisfies(point -> assertThat(point.range()).isEqualTo(range));
  }

  @Test
  void ignoresMembersWithoutInjection() {
    // given
    var uri = registerClass("Обычный", field("Поле", range(2)), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Поле"))).thenReturn(Optional.empty());
    init();

    // when / then
    assertThat(index.resolve("Поле")).isEmpty();
    assertThat(uri).isNotNull();
  }

  @Test
  void skipsAnnotationDefinitionClass() {
    // given: класс-определение аннотации с &Пластилин на параметре — это не точка внедрения
    var parameter = ParameterDefinition.builder().name("Тип").annotations(List.of()).range(range(1)).build();
    registerClass("АннотацияВнедрение", null, List.of(parameter), marker("Внедрение"));
    init();

    // when / then: аннотация-определение пропускается, точка не индексируется
    assertThat(index.resolve("Внедрение")).isEmpty();
  }

  // --- helpers ---------------------------------------------------------------

  private URI registerClass(String qualifiedName, VariableSymbol variable,
                            List<ParameterDefinition> parameters, Annotation... constructorAnnotations) {
    var uri = Absolute.uri("file:///beans/" + qualifiedName + ".os");
    var entry = new LibraryEntry(uri, qualifiedName, EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));

    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    var constructor = mock(ConstructorSymbol.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    lenient().when(constructor.getAnnotations()).thenReturn(List.of(constructorAnnotations));
    lenient().when(constructor.getParameters()).thenReturn(parameters);
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    when(symbolTree.getVariables()).thenReturn(variable == null ? List.of() : List.of(variable));
    return uri;
  }

  private static VariableSymbol field(String name, Range nameRange) {
    var variable = mock(VariableSymbol.class);
    lenient().when(variable.getName()).thenReturn(name);
    lenient().when(variable.getAnnotations()).thenReturn(List.of());
    lenient().when(variable.getVariableNameRange()).thenReturn(nameRange);
    return variable;
  }

  private static Annotation marker(String customName) {
    return Annotation.builder()
      .name(AutumnAnnotations.ANNOTATION_MARKER)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(customName), true)))
      .build();
  }

  private static Range range(int line) {
    return new Range(new Position(line, 6), new Position(line, 12));
  }
}
