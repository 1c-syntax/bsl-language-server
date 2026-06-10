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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnInjectionPointIndex.InjectionPoint;
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

/**
 * Обратная линза навигации по внедрению зависимостей «ОСени»: показывает, в скольких точках
 * внедряется объявленный желудь, и ведёт к этим точкам ({@code &Пластилин}).
 * <p>
 * Линза ставится по <i>производителю</i> и показывается всегда, даже при нуле точек внедрения:
 * <ul>
 *   <li>на конструкторе — для компонентного желудя ({@code &Желудь}/{@code &Дуб});</li>
 *   <li>на каждом фабричном методе {@code &Завязь} — для производимого им желудя.</li>
 * </ul>
 * Производители берутся из {@link AutumnBeanIndex}, точки внедрения — из
 * {@link AutumnInjectionPointIndex#usagesOfComponent}, который переигрывает выбор производителя на каждую
 * точку (одиночное внедрение — только у выбранного DI производителя, коллекция — у всех).
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

    // Линза на конструкторе — если файл объявляет компонентный желудь (&Желудь/&Дуб).
    // Показывается всегда, даже при нуле точек внедрения (производитель — сам по себе повод).
    if (!beanIndex.componentBeanNamesForUri(uri).isEmpty()) {
      symbolTree.getConstructor().ifPresent(constructor -> {
        var codeLens = new CodeLens(constructor.getSelectionRange());
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), null));
        codeLenses.add(codeLens);
      });
    }

    // Линза на каждом методе &Завязь — для производимого им желудя (тоже всегда).
    for (var factoryMethod : beanIndex.factoryMethodBeansForUri(uri)) {
      var methodName = factoryMethod.factoryMethodName();
      symbolTree.getMethodSymbol(methodName).ifPresent(method -> {
        var codeLens = new CodeLens(method.getSelectionRange());
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), methodName));
        codeLenses.add(codeLens);
      });
    }

    return codeLenses;
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, BeanUsagesCodeLensData data) {
    var uri = documentContext.getUri();
    // Линза показывается всегда (даже при нуле точек внедрения), поэтому команду ставим
    // безусловно: заголовок отражает число точек, поповер показывает их список (пустой при нуле).
    var locations = data.getFactoryMethodName() == null
      ? componentLocations(uri)
      : factoryMethodLocations(uri, data.getFactoryMethodName());

    var title = resources.getResourceString(getClass(), TITLE_KEY, locations.size());
    var position = unresolved.getRange().getStart();
    var command = navigationCommandBuilder.referencesCommand(title, uri, position, locations);
    unresolved.setCommand(command);
    return unresolved;
  }

  @Override
  public Class<BeanUsagesCodeLensData> getCodeLensDataClass() {
    return BeanUsagesCodeLensData.class;
  }

  /** Точки внедрения компонентного желудя файла, разрешающиеся именно в него (линза на конструкторе). */
  private List<Location> componentLocations(URI uri) {
    return toLocations(injectionPointIndex.usagesOfComponent(uri, beanIndex.componentBeanNamesForUri(uri)));
  }

  /** Точки внедрения желудя конкретного фабричного метода {@code &Завязь}. */
  private List<Location> factoryMethodLocations(URI uri, String factoryMethodName) {
    return beanIndex.factoryMethodBeansForUri(uri).stream()
      .filter(factoryMethod -> factoryMethod.factoryMethodName().equals(factoryMethodName))
      .findFirst()
      .map(factoryMethod ->
        toLocations(injectionPointIndex.usagesOfFactoryMethod(uri, factoryMethodName, factoryMethod.beanNames())))
      .orElseGet(List::of);
  }

  private static List<Location> toLocations(List<InjectionPoint> points) {
    return points.stream()
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
