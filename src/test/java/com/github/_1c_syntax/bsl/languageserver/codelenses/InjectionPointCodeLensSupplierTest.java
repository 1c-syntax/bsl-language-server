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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex.BeanDefinition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer.InjectedBean;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InjectionPointCodeLensSupplierTest {

  private static final URI CONSUMER_URI = Absolute.uri("file:///Потребитель.os");
  private static final URI PRODUCER_URI = Absolute.uri("file:///Лог.os");
  private static final Range MEMBER_RANGE = range(2);
  private static final Range PRODUCER_RANGE = range(7);

  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private AutumnComponentInferencer componentInferencer;
  @Mock
  private AutumnBeanIndex beanIndex;
  @Mock
  private ServerContextProvider serverContextProvider;
  @Mock
  private NavigationCommandBuilder navigationCommandBuilder;

  @Mock
  private DocumentContext documentContext;
  @Mock
  private SymbolTree symbolTree;

  private InjectionPointCodeLensSupplier supplier() {
    when(documentContext.getUri()).thenReturn(CONSUMER_URI);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getConstructor()).thenReturn(Optional.empty());
    when(symbolTree.getVariables()).thenReturn(List.of());
    return new InjectionPointCodeLensSupplier(
      new Resources(configuration), componentInferencer, beanIndex, serverContextProvider, navigationCommandBuilder);
  }

  @Test
  void buildsLensOverInjectedField() {
    // given
    var supplier = supplier();
    var field = injectionField("Лог", MEMBER_RANGE);
    when(symbolTree.getVariables()).thenReturn(List.of(field));
    when(componentInferencer.injectedBean(anyList(), eq("Лог"))).thenReturn(injection("Лог"));
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).singleElement().satisfies(codeLens -> {
      assertThat(codeLens.getRange()).isEqualTo(MEMBER_RANGE);
      assertThat(codeLens.getCommand()).isNull();
      var data = (InjectionPointCodeLensSupplier.InjectionPointCodeLensData) codeLens.getData();
      assertThat(data.getBeanName()).isEqualTo("Лог");
      assertThat(data.isCollection()).isFalse();
      assertThat(data.isParameter()).isFalse();
    });
  }

  @Test
  void buildsLensOverInjectedConstructorParameter() {
    // given
    var supplier = supplier();
    var constructor = mock(ConstructorSymbol.class);
    var parameter = ParameterDefinition.builder()
      .name("лог")
      .annotations(List.of())
      .range(MEMBER_RANGE)
      .build();
    when(constructor.getParameters()).thenReturn(List.of(parameter));
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    when(componentInferencer.injectedBean(anyList(), eq("лог"))).thenReturn(injection("Лог"));
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).singleElement().satisfies(codeLens -> {
      assertThat(codeLens.getRange()).isEqualTo(MEMBER_RANGE);
      var data = (InjectionPointCodeLensSupplier.InjectionPointCodeLensData) codeLens.getData();
      assertThat(data.isParameter()).isTrue();
    });
  }

  @Test
  void buildsCollectionLensFromAllMembers() {
    // given: внедрение коллекции -> члены резолвятся БЕЗ primary-фильтра, целей несколько
    var supplier = supplier();
    var field = injectionField("Обработчик", MEMBER_RANGE);
    when(symbolTree.getVariables()).thenReturn(List.of(field));
    when(componentInferencer.injectedBean(anyList(), eq("Обработчик"))).thenReturn(collectionInjection("Обработчик"));
    when(beanIndex.resolveAllDefinitions("Обработчик"))
      .thenReturn(List.of(componentDeclaration(PRODUCER_URI), componentDeclaration(PRODUCER_URI)));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).singleElement().satisfies(codeLens -> {
      var data = (InjectionPointCodeLensSupplier.InjectionPointCodeLensData) codeLens.getData();
      assertThat(data.getBeanName()).isEqualTo("Обработчик");
      assertThat(data.isCollection()).isTrue();
    });
  }

  @Test
  void doesNotBuildLensWhenInjectionDoesNotResolve() {
    // given
    var supplier = supplier();
    var field = injectionField("НетТакого", MEMBER_RANGE);
    when(symbolTree.getVariables()).thenReturn(List.of(field));
    when(componentInferencer.injectedBean(anyList(), eq("НетТакого"))).thenReturn(injection("НетТакого"));
    when(beanIndex.resolveDefinitions("НетТакого")).thenReturn(List.of());

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).isEmpty();
  }

  @Test
  void doesNotBuildLensForNonInjectionMember() {
    // given
    var supplier = supplier();
    var field = injectionField("Обычное", MEMBER_RANGE);
    when(symbolTree.getVariables()).thenReturn(List.of(field));
    when(componentInferencer.injectedBean(anyList(), eq("Обычное"))).thenReturn(Optional.empty());

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).isEmpty();
  }

  @Test
  void resolveBuildsGotoCommandToProducer() {
    // given
    var supplier = supplier();
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));
    stubProducerConstructor();
    var expectedLocation = new Location(PRODUCER_URI.toString(), PRODUCER_RANGE);
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.gotoCommand(
      anyString(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), eq(List.of(expectedLocation))))
      .thenReturn(command);
    var unresolved = unresolvedLens("Лог", false);

    // when
    var resolved = supplier.resolve(documentContext, unresolved, dataOf(unresolved));

    // then
    assertThat(resolved.getCommand()).isSameAs(command);
  }

  @Test
  void resolveCollectionUsesAllDeclarations() {
    // given
    var supplier = supplier();
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(beanIndex.resolveAllDefinitions("Обработчик")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));
    stubProducerConstructor();
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.gotoCommand(anyString(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), anyList()))
      .thenReturn(command);
    var unresolved = unresolvedLens("Обработчик", true);

    // when
    var resolved = supplier.resolve(documentContext, unresolved, dataOf(unresolved));

    // then
    assertThat(resolved.getCommand()).isSameAs(command);
  }

  @Test
  void resolveLeavesLensWithoutCommandWhenProducerNotLocatable() {
    // given
    var supplier = supplier();
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));
    when(serverContextProvider.getServerContext(PRODUCER_URI)).thenReturn(Optional.empty());
    var unresolved = unresolvedLens("Лог", false);

    // when
    var resolved = supplier.resolve(documentContext, unresolved, dataOf(unresolved));

    // then
    assertThat(resolved.getCommand()).isNull();
  }

  private void stubProducerConstructor() {
    var serverContext = mock(ServerContext.class);
    var producerDocument = mock(DocumentContext.class);
    var producerTree = mock(SymbolTree.class);
    var producerMethod = mock(MethodSymbol.class);
    when(serverContextProvider.getServerContext(PRODUCER_URI)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(PRODUCER_URI)).thenReturn(producerDocument);
    when(producerDocument.getSymbolTree()).thenReturn(producerTree);
    // производитель ищется единообразно по имени метода-производителя (конструктор — тоже MethodSymbol)
    when(producerTree.getMethodSymbol("ПриСозданииОбъекта")).thenReturn(Optional.of(producerMethod));
    when(producerMethod.getSelectionRange()).thenReturn(PRODUCER_RANGE);
  }

  private static Optional<InjectedBean> injection(String name) {
    return Optional.of(new InjectedBean(name, false));
  }

  private static Optional<InjectedBean> collectionInjection(String name) {
    return Optional.of(new InjectedBean(name, true));
  }

  private static VariableSymbol injectionField(String name, Range nameRange) {
    var field = mock(VariableSymbol.class);
    when(field.getName()).thenReturn(name);
    when(field.getAnnotations()).thenReturn(List.of());
    when(field.getVariableNameRange()).thenReturn(nameRange);
    when(field.getKind()).thenReturn(VariableKind.MODULE);
    return field;
  }

  private static BeanDefinition componentDeclaration(URI sourceUri) {
    return new BeanDefinition(
      new TypeRef(TypeKind.USER, "Лог"), false, sourceUri, "ПриСозданииОбъекта", true);
  }

  @Test
  void skipsParameterKindVariableDuplicate() {
    // given: параметр конструктора дублируется среди переменных (kind=PARAMETER,
    // VariableSymbolComputer) — вторая линза по дубликату не строится
    var supplier = supplier();
    var constructor = mock(ConstructorSymbol.class);
    var parameter = ParameterDefinition.builder()
      .name("лог")
      .annotations(List.of())
      .range(MEMBER_RANGE)
      .build();
    when(constructor.getParameters()).thenReturn(List.of(parameter));
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    var parameterVariable = injectionField("лог", range(9));
    when(parameterVariable.getKind()).thenReturn(VariableKind.PARAMETER);
    when(symbolTree.getVariables()).thenReturn(List.of(parameterVariable));
    when(componentInferencer.injectedBean(anyList(), eq("лог"))).thenReturn(injection("Лог"));
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));

    // when / then: одна линза — по параметру конструктора
    assertThat(supplier.getCodeLenses(documentContext)).hasSize(1);
  }

  @Test
  void resolveParameterLensIncludesBeanNameInTitle() {
    // given: линзы параметров рендерятся стопкой над конструктором — в заголовке нужно имя желудя
    var supplier = supplier();
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDeclaration(PRODUCER_URI)));
    stubProducerConstructor();
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.gotoCommand(anyString(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), anyList()))
      .thenReturn(command);
    var unresolved = new CodeLens(MEMBER_RANGE);
    unresolved.setData(new InjectionPointCodeLensSupplier.InjectionPointCodeLensData(
      CONSUMER_URI, "injectionPoint", "Лог", false, true));

    // when
    supplier.resolve(documentContext, unresolved, dataOf(unresolved));

    // then
    var titleCaptor = ArgumentCaptor.forClass(String.class);
    verify(navigationCommandBuilder)
      .gotoCommand(titleCaptor.capture(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), anyList());
    assertThat(titleCaptor.getValue()).contains("Лог");
  }

  private CodeLens unresolvedLens(String beanName, boolean collection) {
    var codeLens = new CodeLens(MEMBER_RANGE);
    codeLens.setData(new InjectionPointCodeLensSupplier.InjectionPointCodeLensData(
      CONSUMER_URI, "injectionPoint", beanName, collection, false));
    return codeLens;
  }

  private static InjectionPointCodeLensSupplier.InjectionPointCodeLensData dataOf(CodeLens codeLens) {
    return (InjectionPointCodeLensSupplier.InjectionPointCodeLensData) codeLens.getData();
  }

  private static Range range(int line) {
    return new Range(new Position(line, 2), new Position(line, 8));
  }
}
