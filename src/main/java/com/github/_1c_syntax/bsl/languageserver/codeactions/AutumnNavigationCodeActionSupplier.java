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
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer.InjectedBean;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnNavigation;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Навигационные code action'ы по внедрению зависимостей «ОСени» — дополнение к линзам
 * ({@code InjectionPointCodeLensSupplier}/{@code BeanUsagesCodeLensSupplier}) для вызова
 * с клавиатуры (Ctrl+. / Alt+Enter):
 * <ul>
 *   <li>курсор на точке внедрения ({@code &Пластилин} на поле модуля или параметре
 *       конструктора) — переход к производителю желудя;</li>
 *   <li>курсор на имени конструктора компонентного желудя или метода {@code &Завязь} —
 *       показ точек внедрения производимого желудя.</li>
 * </ul>
 * Цели вычисляются той же {@link AutumnNavigation}, что и у линз; счётчики входят в заголовок
 * действия. {@code kind} не задаётся — действие навигационное, категории правок ему не подходят.
 */
@Component
@RequiredArgsConstructor
public class AutumnNavigationCodeActionSupplier implements CodeActionSupplier {

  private static final String GOTO_PRODUCER_KEY = "gotoProducer";
  private static final String GOTO_PRODUCER_MANY_KEY = "gotoProducerMany";
  private static final String SHOW_USAGES_KEY = "showUsages";

  private final Resources resources;
  private final AutumnComponentInferencer componentInferencer;
  private final AutumnBeanIndex beanIndex;
  private final AutumnNavigation autumnNavigation;
  private final NavigationCommandBuilder navigationCommandBuilder;

  @Override
  public List<CodeAction> getCodeActions(CodeActionParams params, DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return List.of();
    }
    var position = params.getRange().getStart();
    var actions = new ArrayList<CodeAction>();
    injectionAction(documentContext, position).ifPresent(actions::add);
    actions.addAll(producerActions(documentContext, position));
    return actions;
  }

  /**
   * Действие перехода к производителю — если курсор стоит на точке внедрения.
   */
  private Optional<CodeAction> injectionAction(DocumentContext documentContext, Position position) {
    return injectedBeanAt(documentContext.getSymbolTree(), position)
      .flatMap(bean -> gotoProducerAction(documentContext, position, bean));
  }

  /**
   * Внедряемый желудь точки внедрения под курсором — курсор должен стоять на ИМЕНИ параметра
   * конструктора либо ИМЕНИ поля модуля с {@code &Пластилин} (переменные-дубликаты параметров
   * с kind=PARAMETER не учитываются).
   */
  private Optional<InjectedBean> injectedBeanAt(SymbolTree symbolTree, Position position) {
    var fromParameter = symbolTree.getConstructor().stream()
      .flatMap(constructor -> constructor.getParameters().stream())
      // ParameterDefinition.getRange() — это диапазон имени параметра (IDENTIFIER)
      .filter(parameter -> Ranges.containsPosition(parameter.getRange(), position))
      .findFirst()
      .flatMap(parameter -> componentInferencer.injectedBean(parameter.getAnnotations(), parameter.getName()));
    if (fromParameter.isPresent()) {
      return fromParameter;
    }
    return symbolTree.getVariables().stream()
      .filter(variable -> variable.getKind() == VariableKind.MODULE)
      .filter(variable -> Ranges.containsPosition(variable.getVariableNameRange(), position))
      .findFirst()
      .flatMap(variable -> componentInferencer.injectedBean(variable.getAnnotations(), variable.getName()));
  }

  private Optional<CodeAction> gotoProducerAction(
    DocumentContext documentContext,
    Position position,
    InjectedBean bean
  ) {
    var locations = autumnNavigation.producerLocations(bean.name(), bean.collection());
    if (locations.isEmpty()) {
      return Optional.empty();
    }
    var title = locations.size() == 1
      ? resources.getResourceString(getClass(), GOTO_PRODUCER_KEY, bean.name())
      : resources.getResourceString(getClass(), GOTO_PRODUCER_MANY_KEY, bean.name(), locations.size());
    var command = navigationCommandBuilder.gotoCommand(title, documentContext.getUri(), position, locations);
    return Optional.of(toAction(command.getTitle(), command));
  }

  /**
   * Действия показа точек внедрения — если курсор стоит на имени конструктора компонентного
   * желудя либо на имени метода {@code &Завязь}.
   */
  private List<CodeAction> producerActions(DocumentContext documentContext, Position position) {
    var actions = new ArrayList<CodeAction>();
    var symbolTree = documentContext.getSymbolTree();
    var uri = documentContext.getUri();

    if (!beanIndex.componentBeanNamesForUri(uri).isEmpty()) {
      symbolTree.getConstructor()
        .filter(constructor -> Ranges.containsPosition(constructor.getSelectionRange(), position))
        .map(constructor -> usagesAction(uri, position, autumnNavigation.componentUsageLocations(uri)))
        .ifPresent(actions::add);
    }

    for (var factoryMethod : beanIndex.factoryMethodBeansForUri(uri)) {
      var methodName = factoryMethod.factoryMethodName();
      symbolTree.getMethodSymbol(methodName)
        .filter(method -> Ranges.containsPosition(method.getSelectionRange(), position))
        .map(method ->
          usagesAction(uri, position, autumnNavigation.factoryMethodUsageLocations(uri, methodName)))
        .ifPresent(actions::add);
    }

    return actions;
  }

  private CodeAction usagesAction(URI uri, Position position, List<Location> locations) {
    var title = resources.getResourceString(getClass(), SHOW_USAGES_KEY, locations.size());
    var command = navigationCommandBuilder.referencesCommand(title, uri, position, locations);
    return toAction(title, command);
  }

  private static CodeAction toAction(String title, Command command) {
    var codeAction = new CodeAction(title);
    codeAction.setCommand(command);
    return codeAction;
  }
}
