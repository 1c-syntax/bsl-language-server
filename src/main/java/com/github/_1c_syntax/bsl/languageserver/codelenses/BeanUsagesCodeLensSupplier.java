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
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnNavigation;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import org.eclipse.lsp4j.CodeLens;
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
 * {@link AutumnInjectionPointIndex#usagesOfComponent} / {@link AutumnInjectionPointIndex#usagesOfFactoryMethod},
 * которые переигрывают выбор производителя на каждую точку (одиночное внедрение — только у
 * выбранного DI производителя, коллекция — у всех).
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class BeanUsagesCodeLensSupplier
  implements CodeLensSupplier<BeanUsagesCodeLensSupplier.BeanUsagesCodeLensData> {

  private static final String TITLE_KEY = "usages";

  private final Resources resources;
  private final AutumnBeanIndex beanIndex;
  private final AutumnNavigation autumnNavigation;
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
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), constructor.getName(), true));
        codeLenses.add(codeLens);
      });
    }

    // Линза на каждом методе &Завязь — для производимого им желудя (тоже всегда).
    for (var factoryMethod : beanIndex.factoryMethodBeansForUri(uri)) {
      var methodName = factoryMethod.factoryMethodName();
      symbolTree.getMethodSymbol(methodName).ifPresent(method -> {
        var codeLens = new CodeLens(method.getSelectionRange());
        codeLens.setData(new BeanUsagesCodeLensData(uri, getId(), methodName, false));
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
    var locations = data.isConstructor()
      ? autumnNavigation.componentUsageLocations(uri)
      : autumnNavigation.factoryMethodUsageLocations(uri, data.getProducerMethodName());

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

  /**
   * DTO обратной линзы — зеркало {@code BeanDefinition}: имя метода-производителя и признак
   * конструктора различают линзу компонентного желудя (на конструкторе) и линзу фабричного
   * метода {@code &Завязь}.
   */
  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class BeanUsagesCodeLensData extends DefaultCodeLensData {

    /**
     * Имя метода-производителя: конструктора для линзы компонентного желудя либо метода
     * {@code &Завязь} для пер-методной линзы.
     */
    String producerMethodName;

    /**
     * Признак, что производитель — конструктор класса (линза компонентного желудя).
     */
    boolean constructor;

    /**
     * Конструктор данных обратной линзы.
     *
     * @param uri                URI документа.
     * @param id                 Идентификатор поставщика линз.
     * @param producerMethodName Имя метода-производителя (конструктор или метод {@code &Завязь}).
     * @param constructor        {@code true} для линзы компонентного желудя на конструкторе.
     */
    @ConstructorProperties({"uri", "id", "producerMethodName", "constructor"})
    public BeanUsagesCodeLensData(URI uri, String id, String producerMethodName, boolean constructor) {
      super(uri, id);
      this.producerMethodName = producerMethodName;
      this.constructor = constructor;
    }
  }
}
