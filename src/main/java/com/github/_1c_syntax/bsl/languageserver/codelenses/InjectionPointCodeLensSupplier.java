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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex.BeanDefinition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Прямая линза навигации по внедрению зависимостей фреймворка «ОСень»: над точкой внедрения
 * ({@code &Пластилин} на поле модуля или параметре конструктора) показывает, какой желудь сюда
 * внедряется, и ведёт к объявлению его производителя — конструктору класса-компонента
 * ({@code &Желудь}/{@code &Дуб}) или фабричному методу ({@code &Завязь}).
 * <p>
 * Производитель резолвится по правилам DI-вывода через {@link AutumnBeanIndex}; при нескольких
 * кандидатах (например, конфликт имён без {@code &Верховный}) команда открывает поповер со
 * списком целей. Линза показывается только для разрешимых внедрений по имени желудя; для
 * прилепляемых коллекций навигация строится ко всем подходящим производителям.
 */
@Component
@Order(6)
@RequiredArgsConstructor
public class InjectionPointCodeLensSupplier
  implements CodeLensSupplier<InjectionPointCodeLensSupplier.InjectionPointCodeLensData> {

  private static final String TITLE_KEY = "injects";
  private static final String TITLE_MANY_KEY = "injectsMany";

  private final Resources resources;
  private final AutumnComponentInferencer componentInferencer;
  private final AutumnBeanIndex beanIndex;
  private final ServerContextProvider serverContextProvider;
  private final NavigationCommandBuilder navigationCommandBuilder;

  @Override
  public boolean isApplicable(DocumentContext documentContext) {
    return documentContext.getFileType() == FileType.OS;
  }

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
    var symbolTree = documentContext.getSymbolTree();
    var codeLenses = new ArrayList<CodeLens>();

    symbolTree.getConstructor().ifPresent(constructor ->
      constructor.getParameters().forEach(parameter ->
        toCodeLens(documentContext, parameter.getAnnotations(), parameter.getName(), parameter.getRange())
          .ifPresent(codeLenses::add)));

    for (var variable : symbolTree.getVariables()) {
      toCodeLens(documentContext, variable.getAnnotations(), variable.getName(), variable.getVariableNameRange())
        .ifPresent(codeLenses::add);
    }

    return codeLenses;
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, InjectionPointCodeLensData data) {
    var producers = declarationsFor(data.getBeanName(), data.isCollection()).stream()
      .map(this::locateProducer)
      .flatMap(Optional::stream)
      .toList();
    if (producers.isEmpty()) {
      return unresolved;
    }

    var locations = producers.stream().map(LocatedProducer::location).toList();
    var title = title(data.getBeanName(), producers);
    var position = unresolved.getRange().getStart();
    var command = navigationCommandBuilder.gotoCommand(title, documentContext.getUri(), position, locations);
    unresolved.setCommand(command);
    return unresolved;
  }

  @Override
  public Class<InjectionPointCodeLensData> getCodeLensDataClass() {
    return InjectionPointCodeLensData.class;
  }

  /**
   * Построить неразрешённую линзу для точки внедрения, если в ней внедряется разрешимый желудь.
   */
  private Optional<CodeLens> toCodeLens(
    DocumentContext documentContext,
    List<Annotation> annotations,
    String memberName,
    Range range
  ) {
    return componentInferencer.injectedBean(annotations, memberName)
      .filter(bean -> !declarationsFor(bean.name(), bean.collection()).isEmpty())
      .map(bean -> {
        var data = new InjectionPointCodeLensData(documentContext.getUri(), getId(), bean.name(), bean.collection());
        var codeLens = new CodeLens(range);
        codeLens.setData(data);
        return codeLens;
      });
  }

  /**
   * Объявления производителей под именем желудя: для коллекции — все подходящие желуди
   * (без приоритета {@code &Верховный}), для одиночного внедрения — с приоритетом.
   */
  private List<BeanDefinition> declarationsFor(String beanName, boolean collection) {
    return collection
      ? beanIndex.resolveAllDefinitions(beanName)
      : beanIndex.resolveDefinitions(beanName);
  }

  /**
   * Местоположение производителя желудя: диапазон конструктора класса-компонента либо
   * фабричного метода {@code &Завязь} в его .os-файле.
   */
  private Optional<LocatedProducer> locateProducer(BeanDefinition declaration) {
    return serverContextProvider.getServerContext(declaration.sourceUri())
      .map(serverContext -> serverContext.getDocument(declaration.sourceUri()))
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> producerRange(symbolTree, declaration))
      .map(range -> new LocatedProducer(declaration, new Location(declaration.sourceUri().toString(), range)));
  }

  private static Optional<Range> producerRange(SymbolTree symbolTree, BeanDefinition declaration) {
    // Производитель — это метод (конструктор ПриСозданииОбъекта тоже MethodSymbol), поэтому ищем
    // единообразно по имени метода-производителя; ветвление по виду производителя не нужно.
    return symbolTree.getMethodSymbol(declaration.producerMethodName())
      .map(SourceDefinedSymbol::getSelectionRange);
  }

  private String title(String beanName, List<LocatedProducer> producers) {
    if (producers.size() == 1) {
      var fileName = Path.of(producers.getFirst().declaration().sourceUri()).getFileName().toString();
      return resources.getResourceString(getClass(), TITLE_KEY, beanName, fileName);
    }
    return resources.getResourceString(getClass(), TITLE_MANY_KEY, producers.size());
  }

  /** Производитель желудя с уже вычисленным местоположением для навигации. */
  private record LocatedProducer(BeanDefinition declaration, Location location) {
  }

  /**
   * DTO линзы точки внедрения: добавляет к базовым данным имя внедряемого желудя.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class InjectionPointCodeLensData extends DefaultCodeLensData {

    /**
     * Имя внедряемого желудя (ключ резолва производителя).
     */
    String beanName;

    /**
     * Признак внедрения прилепляемой коллекции (цели — все подходящие желуди, без приоритета).
     */
    boolean collection;

    /**
     * Конструктор данных линзы точки внедрения.
     *
     * @param uri        URI документа.
     * @param id         Идентификатор поставщика линз.
     * @param beanName   Имя внедряемого желудя.
     * @param collection Признак внедрения прилепляемой коллекции.
     */
    @ConstructorProperties({"uri", "id", "beanName", "collection"})
    public InjectionPointCodeLensData(URI uri, String id, String beanName, boolean collection) {
      super(uri, id);
      this.beanName = beanName;
      this.collection = collection;
    }
  }
}
