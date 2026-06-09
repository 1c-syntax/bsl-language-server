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
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Location;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Обратная линза навигации по внедрению зависимостей «ОСени»: над конструктором класса-желудя
 * показывает, в скольких точках внедряется объявленный им желудь, и ведёт к этим точкам
 * ({@code &Пластилин}).
 * <p>
 * Имена желудей документа берутся из {@link AutumnBeanIndex#namesForUri} (что объявляет этот
 * файл-производитель), точки внедрения — из {@link AutumnInjectionPointIndex}. Линза ставится на
 * конструкторе и охватывает все желуди файла (включая фабричные {@code &Завязь}); разнесение по
 * отдельным фабричным методам — отдельная итерация.
 */
@Component
@Order(7)
@RequiredArgsConstructor
public class BeanUsagesCodeLensSupplier implements CodeLensSupplier<DefaultCodeLensData> {

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
    if (injectionLocations(documentContext).isEmpty()) {
      return List.of();
    }
    return documentContext.getSymbolTree().getConstructor()
      .map(constructor -> {
        var codeLens = new CodeLens(constructor.getSelectionRange());
        codeLens.setData(new DefaultCodeLensData(documentContext.getUri(), getId()));
        return List.of(codeLens);
      })
      .orElseGet(List::of);
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, DefaultCodeLensData data) {
    var locations = injectionLocations(documentContext);
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
  public Class<DefaultCodeLensData> getCodeLensDataClass() {
    return DefaultCodeLensData.class;
  }

  /** Точки внедрения всех желудей, объявленных в документе, как {@link Location}. */
  private List<Location> injectionLocations(DocumentContext documentContext) {
    Set<String> beanNames = beanIndex.namesForUri(documentContext.getUri());
    return beanNames.stream()
      .map(injectionPointIndex::resolve)
      .flatMap(List::stream)
      .distinct()
      .map(point -> new Location(point.uri().toString(), point.range()))
      .toList();
  }
}
