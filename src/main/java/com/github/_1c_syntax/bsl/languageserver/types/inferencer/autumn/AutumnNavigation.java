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
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex.BeanDefinition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnInjectionPointIndex.InjectionPoint;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Цели навигации по внедрению зависимостей «ОСени» — общая логика прямой/обратной линз
 * ({@code CodeLens}) и навигационных code action'ов.
 * <p>
 * Прямое направление: от точки внедрения ({@code &Пластилин}) к местоположениям
 * методов-производителей желудя. Обратное: от производителя (конструктор компонентного желудя
 * или метод {@code &Завязь}) к точкам внедрения, разрешающимся именно в него.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class AutumnNavigation {

  private final AutumnBeanIndex beanIndex;
  private final AutumnInjectionPointIndex injectionPointIndex;
  private final ServerContextProvider serverContextProvider;

  /**
   * Определения производителей под именем желудя: для коллекции — все подходящие желуди
   * (без приоритета {@code &Верховный}), для одиночного внедрения — с приоритетом.
   *
   * @param beanName   Имя или прозвище внедряемого желудя.
   * @param collection Признак внедрения прилепляемой коллекции.
   * @return Определения производителей; пусто, если желудь не разрешается.
   */
  public List<BeanDefinition> producerDefinitions(String beanName, boolean collection) {
    return collection
      ? beanIndex.resolveAllDefinitions(beanName)
      : beanIndex.resolveDefinitions(beanName);
  }

  /**
   * Местоположения производителей желудя — диапазоны методов-производителей (конструктора
   * класса-компонента либо метода {@code &Завязь}) в их .os-файлах.
   *
   * @param beanName   Имя или прозвище внедряемого желудя.
   * @param collection Признак внедрения прилепляемой коллекции.
   * @return Местоположения производителей; пусто, если желудь или его файлы не разрешаются.
   */
  public List<Location> producerLocations(String beanName, boolean collection) {
    return producerDefinitions(beanName, collection).stream()
      .map(this::locateProducer)
      .flatMap(Optional::stream)
      .toList();
  }

  /**
   * Точки внедрения компонентного желудя файла, разрешающиеся именно в него
   * (обратная навигация с конструктора).
   *
   * @param uri URI .os-файла производителя.
   * @return Местоположения точек внедрения; пусто, если их нет.
   */
  public List<Location> componentUsageLocations(URI uri) {
    return toLocations(injectionPointIndex.usagesOfComponent(uri, beanIndex.componentBeanNamesForUri(uri)));
  }

  /**
   * Точки внедрения желудя конкретного фабричного метода {@code &Завязь}
   * (обратная навигация с метода).
   *
   * @param uri               URI .os-файла производителя.
   * @param factoryMethodName Имя фабричного метода {@code &Завязь}.
   * @return Местоположения точек внедрения; пусто, если их нет.
   */
  public List<Location> factoryMethodUsageLocations(URI uri, String factoryMethodName) {
    return beanIndex.factoryMethodBeansForUri(uri).stream()
      .filter(factoryMethod -> factoryMethod.factoryMethodName().equals(factoryMethodName))
      .findFirst()
      .map(factoryMethod ->
        toLocations(injectionPointIndex.usagesOfFactoryMethod(uri, factoryMethodName, factoryMethod.beanNames())))
      .orElseGet(List::of);
  }

  /**
   * Местоположение производителя желудя: диапазон метода-производителя в его .os-файле.
   */
  private Optional<Location> locateProducer(BeanDefinition definition) {
    return serverContextProvider.getServerContext(definition.sourceUri())
      .map(serverContext -> serverContext.getDocument(definition.sourceUri()))
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> producerRange(symbolTree, definition))
      .map(range -> new Location(definition.sourceUri().toString(), range));
  }

  private static Optional<Range> producerRange(SymbolTree symbolTree, BeanDefinition definition) {
    // Производитель — это метод (конструктор ПриСозданииОбъекта тоже MethodSymbol), поэтому ищем
    // единообразно по имени метода-производителя; ветвление по виду производителя не нужно.
    return symbolTree.getMethodSymbol(definition.producerMethodName())
      .map(SourceDefinedSymbol::getSelectionRange);
  }

  private static List<Location> toLocations(List<InjectionPoint> points) {
    return points.stream()
      .map(point -> new Location(point.uri().toString(), point.range()))
      .toList();
  }
}
