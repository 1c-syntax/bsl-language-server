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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnBeanIndex.BeanDefinition;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer.InjectedBean;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.utils.Absolute;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptAnnotations;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnInjectionPointIndexTest {

  private static final URI PRODUCER_URI = Absolute.uri("file:///beans/Лог.os");
  private static final URI OTHER_PRODUCER_URI = Absolute.uri("file:///beans/ДругойЛог.os");

  @Mock
  private OScriptLibraryIndex libraryIndex;
  @Mock
  private ServerContextProvider serverContextProvider;
  @Mock
  private AutumnComponentInferencer componentInferencer;
  @Mock
  private AutumnBeanIndex beanIndex;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnInjectionPointIndex index;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    index = new AutumnInjectionPointIndex(libraryIndex, serverContextProvider,
      new OScriptMetaAnnotationResolver(new AnnotationRepository()), componentInferencer, beanIndex);
  }

  @Test
  void returnsEmptyForBlankOrUnknownName() {
    // given
    init();

    // when / then
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of())).isEmpty();
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of("НетТакого"))).isEmpty();
  }

  @Test
  void indexesFieldInjectionResolvedToProducer() {
    // given: класс-потребитель с полем &Пластилин, внедряющим желудь "Лог" (одиночно),
    // производитель которого — компонент в PRODUCER_URI
    var range = range(3);
    var uri = registerClass("Потребитель", field("Лог", range), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    componentProducer("Лог", PRODUCER_URI);
    init();

    // when
    var points = index.usagesOfComponent(PRODUCER_URI, Set.of("Лог"));

    // then
    assertThat(points).singleElement().satisfies(point -> {
      assertThat(point.uri()).isEqualTo(uri);
      assertThat(point.range()).isEqualTo(range);
      assertThat(point.collection()).isFalse();
    });
  }

  @Test
  void indexesConstructorParameterInjection() {
    // given: внедрение через параметр конструктора
    var range = range(1);
    var parameter = ParameterDefinition.builder().name("лог").annotations(List.of()).range(range).build();
    registerClass("ПотребительВКонструкторе", null, List.of(parameter));
    when(componentInferencer.injectedBean(anyList(), eq("лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    componentProducer("Лог", PRODUCER_URI);
    init();

    // when
    var points = index.usagesOfComponent(PRODUCER_URI, Set.of("Лог"));

    // then
    assertThat(points).singleElement().satisfies(point -> assertThat(point.range()).isEqualTo(range));
  }

  @Test
  void singletonInjectionHiddenForNonSelectedProducer() {
    // given: одиночное внедрение "Лог" разрешается DI в PRODUCER_URI (например, по &Верховный),
    // а не в одноимённого непримарного производителя OTHER_PRODUCER_URI
    registerClass("Потребитель", field("Лог", range(3)), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    componentProducer("Лог", PRODUCER_URI);
    init();

    // when / then: линза выбранного производителя показывает точку, чужого — нет
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of("Лог"))).hasSize(1);
    assertThat(index.usagesOfComponent(OTHER_PRODUCER_URI, Set.of("Лог"))).isEmpty();
  }

  @Test
  void collectionInjectionIncludedForAnyProducerOfName() {
    // given: внедрение коллекции "Обработчик" — внедряет всех производителей имени, поэтому
    // показывается на любом из них, даже если resolveDefinitions выбрал бы другого
    registerClass("Потребитель", field("Обработчик", range(4)), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Обработчик")))
      .thenReturn(Optional.of(new InjectedBean("Обработчик", true)));
    componentProducer("Обработчик", OTHER_PRODUCER_URI);
    init();

    // when / then: коллекция показывается на PRODUCER_URI несмотря на выбор OTHER_PRODUCER_URI
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of("Обработчик")))
      .singleElement().satisfies(point -> assertThat(point.collection()).isTrue());
  }

  @Test
  void ignoresMembersWithoutInjection() {
    // given
    registerClass("Обычный", field("Поле", range(2)), List.of());
    when(componentInferencer.injectedBean(anyList(), eq("Поле"))).thenReturn(Optional.empty());
    init();

    // when / then
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of("Поле"))).isEmpty();
  }

  @Test
  void skipsAnnotationDefinitionClass() {
    // given: класс-определение аннотации с &Пластилин на параметре — это не точка внедрения
    var parameter = ParameterDefinition.builder().name("Тип").annotations(List.of()).range(range(1)).build();
    registerClass("АннотацияВнедрение", null, List.of(parameter), marker("Внедрение"));
    init();

    // when / then: аннотация-определение пропускается, точка не индексируется
    assertThat(index.usagesOfComponent(PRODUCER_URI, Set.of("Внедрение"))).isEmpty();
  }

  @Test
  void doesNotDoubleCountParameterDuplicatedAsVariable() {
    // given: параметр конструктора дублируется среди переменных (kind=PARAMETER) с теми же
    // аннотациями — без фильтра точка внедрения индексировалась бы дважды
    var paramRange = range(1);
    var parameter = ParameterDefinition.builder().name("лог").annotations(List.of()).range(paramRange).build();
    var parameterVariable = field("лог", range(2));
    lenient().when(parameterVariable.getKind()).thenReturn(VariableKind.PARAMETER);
    registerClass("Потребитель", parameterVariable, List.of(parameter));
    when(componentInferencer.injectedBean(anyList(), eq("лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    componentProducer("Лог", PRODUCER_URI);
    init();

    // when
    var points = index.usagesOfComponent(PRODUCER_URI, Set.of("Лог"));

    // then: одна точка — по параметру конструктора
    assertThat(points).singleElement().satisfies(point -> assertThat(point.range()).isEqualTo(paramRange));
  }

  // --- helpers ---------------------------------------------------------------

  /** Замокать одиночного производителя желудя: resolveDefinitions(имя) -> компонент в producerUri. */
  private void componentProducer(String beanName, URI producerUri) {
    when(beanIndex.resolveDefinitions(beanName)).thenReturn(List.of(
      new BeanDefinition(new TypeRef(TypeKind.USER, beanName), false, producerUri, "ПриСозданииОбъекта", true)));
  }

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
    lenient().when(variable.getKind()).thenReturn(VariableKind.MODULE);
    return variable;
  }

  private static Annotation marker(String customName) {
    return Annotation.builder()
      .name(OScriptAnnotations.ANNOTATION_MARKER)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(customName), true)))
      .build();
  }

  private static Range range(int line) {
    return new Range(new Position(line, 6), new Position(line, 12));
  }
}
