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
package com.github._1c_syntax.bsl.languageserver.codeactions;

import com.github._1c_syntax.bsl.languageserver.codelenses.NavigationCommandBuilder;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnBeanIndex.FactoryMethodBean;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer.InjectedBean;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnNavigation;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnNavigationCodeActionSupplierTest {

  private static final URI CONSUMER_URI = Absolute.uri("file:///Потребитель.os");
  private static final URI PRODUCER_URI = Absolute.uri("file:///Лог.os");
  private static final Range MEMBER_RANGE = range(2);
  private static final Range CONSTRUCTOR_NAME_RANGE = range(5);
  private static final Range FACTORY_METHOD_NAME_RANGE = range(8);

  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private AutumnComponentInferencer componentInferencer;
  @Mock
  private AutumnBeanIndex beanIndex;
  @Mock
  private AutumnNavigation autumnNavigation;
  @Mock
  private NavigationCommandBuilder navigationCommandBuilder;

  @Mock
  private DocumentContext documentContext;
  @Mock
  private SymbolTree symbolTree;

  private AutumnNavigationCodeActionSupplier supplier() {
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(documentContext.getUri()).thenReturn(CONSUMER_URI);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getConstructor()).thenReturn(Optional.empty());
    when(symbolTree.getVariables()).thenReturn(List.of());
    when(beanIndex.componentBeanNamesForUri(CONSUMER_URI)).thenReturn(Set.of());
    when(beanIndex.factoryMethodBeansForUri(CONSUMER_URI)).thenReturn(List.of());
    return new AutumnNavigationCodeActionSupplier(
      new Resources(configuration), componentInferencer, beanIndex, autumnNavigation, navigationCommandBuilder);
  }

  @Test
  void gotoProducerActionOnInjectedField() {
    // given: курсор на поле модуля с &Пластилин
    var supplier = supplier();
    var field = moduleVariable("Лог", MEMBER_RANGE);
    when(symbolTree.getVariables()).thenReturn(List.of(field));
    when(componentInferencer.injectedBean(anyList(), eq("Лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    when(autumnNavigation.producerLocations("Лог", false))
      .thenReturn(List.of(new Location(PRODUCER_URI.toString(), range(7))));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.gotoCommand(anyString(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), anyList()))
      .thenReturn(command);

    // when
    var actions = supplier.getCodeActions(paramsAt(MEMBER_RANGE.getStart()), documentContext);

    // then
    assertThat(actions).singleElement().satisfies(action -> {
      assertThat(action.getCommand()).isSameAs(command);
      assertThat(action.getKind()).isNull();
    });
  }

  @Test
  void gotoProducerActionOnConstructorParameter() {
    // given: курсор на параметре конструктора с &Пластилин
    var supplier = supplier();
    var constructor = mock(ConstructorSymbol.class);
    var parameter = ParameterDefinition.builder()
      .name("лог")
      .annotations(List.of())
      .range(MEMBER_RANGE)
      .build();
    when(constructor.getParameters()).thenReturn(List.of(parameter));
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    when(componentInferencer.injectedBean(anyList(), eq("лог")))
      .thenReturn(Optional.of(new InjectedBean("Лог", false)));
    when(autumnNavigation.producerLocations("Лог", false))
      .thenReturn(List.of(new Location(PRODUCER_URI.toString(), range(7))));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.gotoCommand(anyString(), eq(CONSUMER_URI), eq(MEMBER_RANGE.getStart()), anyList()))
      .thenReturn(command);

    // when
    var actions = supplier.getCodeActions(paramsAt(MEMBER_RANGE.getStart()), documentContext);

    // then
    assertThat(actions).singleElement()
      .satisfies(action -> assertThat(action.getCommand()).isSameAs(command));
  }

  @Test
  void usagesActionOnComponentConstructorName() {
    // given: курсор на имени конструктора компонентного желудя
    var supplier = supplier();
    var constructor = mock(ConstructorSymbol.class);
    when(constructor.getSelectionRange()).thenReturn(CONSTRUCTOR_NAME_RANGE);
    when(constructor.getParameters()).thenReturn(List.of());
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    when(beanIndex.componentBeanNamesForUri(CONSUMER_URI)).thenReturn(Set.of("лог"));
    when(autumnNavigation.componentUsageLocations(CONSUMER_URI))
      .thenReturn(List.of(new Location(PRODUCER_URI.toString(), range(3))));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.referencesCommand(
      anyString(), eq(CONSUMER_URI), eq(CONSTRUCTOR_NAME_RANGE.getStart()), anyList()))
      .thenReturn(command);

    // when
    var actions = supplier.getCodeActions(paramsAt(CONSTRUCTOR_NAME_RANGE.getStart()), documentContext);

    // then
    assertThat(actions).singleElement()
      .satisfies(action -> assertThat(action.getCommand()).isSameAs(command));
  }

  @Test
  void usagesActionOnFactoryMethodName() {
    // given: курсор на имени метода &Завязь
    var supplier = supplier();
    when(beanIndex.factoryMethodBeansForUri(CONSUMER_URI))
      .thenReturn(List.of(new FactoryMethodBean("СоздатьЛог", Set.of("лог"))));
    var method = mock(MethodSymbol.class);
    when(method.getSelectionRange()).thenReturn(FACTORY_METHOD_NAME_RANGE);
    when(symbolTree.getMethodSymbol("СоздатьЛог")).thenReturn(Optional.of(method));
    when(autumnNavigation.factoryMethodUsageLocations(CONSUMER_URI, "СоздатьЛог"))
      .thenReturn(List.of(new Location(PRODUCER_URI.toString(), range(3))));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.referencesCommand(
      anyString(), eq(CONSUMER_URI), eq(FACTORY_METHOD_NAME_RANGE.getStart()), anyList()))
      .thenReturn(command);

    // when
    var actions = supplier.getCodeActions(paramsAt(FACTORY_METHOD_NAME_RANGE.getStart()), documentContext);

    // then
    assertThat(actions).singleElement()
      .satisfies(action -> assertThat(action.getCommand()).isSameAs(command));
  }

  @Test
  void noActionsOutsideNavigationTargets() {
    // given: курсор вне точек внедрения и производителей
    var supplier = supplier();

    // when / then
    assertThat(supplier.getCodeActions(paramsAt(new Position(0, 0)), documentContext)).isEmpty();
  }

  @Test
  void noActionsForBslFile() {
    // given: code action'ы «ОСени» применимы только к OneScript-файлам
    var supplier = supplier();
    when(documentContext.getFileType()).thenReturn(FileType.BSL);

    // when / then
    assertThat(supplier.getCodeActions(paramsAt(new Position(0, 0)), documentContext)).isEmpty();
  }

  // --- helpers ---------------------------------------------------------------

  private static CodeActionParams paramsAt(Position position) {
    var params = new CodeActionParams();
    params.setTextDocument(new TextDocumentIdentifier(CONSUMER_URI.toString()));
    params.setRange(new Range(position, position));
    params.setContext(new CodeActionContext(List.of()));
    return params;
  }

  private static VariableSymbol moduleVariable(String name, Range nameRange) {
    var variable = mock(VariableSymbol.class);
    when(variable.getName()).thenReturn(name);
    when(variable.getAnnotations()).thenReturn(List.of());
    when(variable.getVariableNameRange()).thenReturn(nameRange);
    when(variable.getKind()).thenReturn(VariableKind.MODULE);
    return variable;
  }

  private static Range range(int line) {
    return new Range(new Position(line, 0), new Position(line, 10));
  }
}
