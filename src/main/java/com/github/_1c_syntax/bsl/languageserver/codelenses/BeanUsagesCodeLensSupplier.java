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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnInjectionPointIndex;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Location;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.beans.ConstructorProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Обратная линза навигации по внедрению зависимостей «ОСени»: показывает, в скольких точках
 * внедряется объявленный желудь, и ведёт к этим точкам ({@code &Пластилин}).
 * <p>
 * Имена желудей документа берутся из {@link AutumnBeanIndex} (что объявляет этот файл-производитель),
 * точки внедрения — из {@link AutumnInjectionPointIndex}. Линзы ставятся:
 * <ul>
 *   <li>на конструкторе — агрегатная, по всем желудям файла (включая желудь {@code &Дуба});</li>
 *   <li>на каждом фабричном методе {@code &Завязь} — по точкам внедрения именно его желудя.</li>
 * </ul>
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class BeanUsagesCodeLensSupplier
  implements CodeLensSupplier<BeanUsagesCodeLensSupplier.BeanUsagesCodeLensData> {

  private static final String TITLE_KEY = "usages";

  private final Resources resources;
  private final AutumnBeanIndex beanIndex;
  private final AutumnInjectionPointIndex injectionPointIndex;
  private final NavigationCommandBuilder navigationCommandBuilder;

  @Override
  public boolean isApplicable(DocumentContext documentContext) {
    return documentContext.getFileType() == FileType.OS;
  }

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {
    var symbolTree = documentContext.getSymbolTree();
    var uri = documentContext.getUri();
    var codeLenses = new ArrayList<CodeLens>();

    // Агрегатная линза на конструкторе: все желуди файла (включая желудь &Дуба).
    if (!aggregateLocations(documentContext).isEmpty()) {
      symbolTree.getConstructor().ifPresent(constructor -> {
        var codeLens = new CodeLens(constructor.getSelectionRange());
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), null));
        codeLenses.add(codeLens);
      });
    }

    // Отдельная линза на каждом методе &Завязь: точки внедрения именно его желудя.
    for (var factoryBean : beanIndex.factoryBeansForUri(uri)) {
      if (locationsFor(factoryBean.beanNames()).isEmpty()) {
        continue;
      }
      symbolTree.getMethodSymbol(factoryBean.factoryMethodName()).ifPresent(method -> {
        var codeLens = new CodeLens(method.getSelectionRange());
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), factoryBean.factoryMethodName()));
        codeLenses.add(codeLens);
      });
    }

    return codeLenses;
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, BeanUsagesCodeLensData data) {
    var locations = data.getFactoryMethodName() == null
      ? aggregateLocations(documentContext)
      : factoryLocations(documentContext, data.getFactoryMethodName());
    if (locations.isEmpty()) {
      return unresolved;
    }

    var title = resources.getResourceString(getClass(), TITLE_KEY, locations.size());
    var position = unresolved.getRange().getStart();
    var command = navigationCommandBuilder.referencesCommand(title, documentContext.getUri(), position, locations);
    unresolved.setCommand(command);
    return unresolved;
  }

  @Override
  public Class<BeanUsagesCodeLensData> getCodeLensDataClass() {
    return BeanUsagesCodeLensData.class;
  }

  /** Точки внедрения всех желудей, объявленных в документе (агрегатная линза на конструкторе). */
  private List<Location> aggregateLocations(DocumentContext documentContext) {
    return locationsFor(beanIndex.namesForUri(documentContext.getUri()));
  }

  /** Точки внедрения желудя конкретного фабричного метода {@code &Завязь}. */
  private List<Location> factoryLocations(DocumentContext documentContext, String factoryMethodName) {
    return beanIndex.factoryBeansForUri(documentContext.getUri()).stream()
      .filter(factoryBean -> factoryBean.factoryMethodName().equals(factoryMethodName))
      .findFirst()
      .map(factoryBean -> locationsFor(factoryBean.beanNames()))
      .orElseGet(List::of);
  }

  /** Точки внедрения переданных желудей как {@link Location}. */
  private List<Location> locationsFor(Set<String> beanNames) {
    return beanNames.stream()
      .map(injectionPointIndex::resolve)
      .flatMap(List::stream)
      .distinct()
      .map(point -> new Location(point.uri().toString(), point.range()))
      .toList();
  }

  /**
   * DTO обратной линзы: для линзы на методе {@code &Завязь} хранит имя метода; для агрегатной
   * линзы на конструкторе — {@code null} (тогда резолв собирает все желуди файла).
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class BeanUsagesCodeLensData extends DefaultCodeLensData {

    /**
     * Имя фабричного метода {@code &Завязь} для пер-методной линзы; {@code null} для агрегатной.
     */
    @Nullable
    String factoryMethodName;

    /**
     * Конструктор данных обратной линзы.
     *
     * @param uri               URI документа.
     * @param id                Идентификатор поставщика линз.
     * @param factoryMethodName Имя метода {@code &Завязь} либо {@code null} для агрегатной линзы.
     */
    @ConstructorProperties({"uri", "id", "factoryMethodName"})
    public BeanUsagesCodeLensData(URI uri, String id, @Nullable String factoryMethodName) {
      super(uri, id);
      this.factoryMethodName = factoryMethodName;
    }
  }
}
