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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.oscript.TypeRelations;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Провайдер запроса {@code textDocument/implementation} для интерфейсов
 * OneScript библиотеки <a href="https://github.com/oscript-library/extends">extends</a>.
 * <p>
 * Интерфейс объявляется аннотацией-маркером {@code &Интерфейс} на конструкторе
 * класса-интерфейса; класс реализует его аннотацией {@code &Реализует("Интерфейс")}
 * (повторяемой). Переход к реализациям:
 * <ul>
 *   <li>курсор на экспортном методе интерфейса → одноимённые методы во всех
 *       реализующих классах;</li>
 *   <li>курсор в любом другом месте файла-интерфейса → сами реализующие классы.</li>
 * </ul>
 * Сам разбор отношений {@code &Реализует}/{@code &Расширяет} (в т.ч.
 * транзитивный обход через абстрактных родителей и иерархию интерфейсов)
 * делегирован {@link TypeRelations#implementors}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation">Goto Implementation Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class ImplementationProvider {

  private final TypeRelations typeRelations;

  // Результаты — по одной локации на класс/метод-реализатор, поэтому URI
  // уникален: сортировки по нему достаточно для детерминированного порядка.
  private final Comparator<Location> locationComparator = Comparator.comparing(Location::getUri);

  /**
   * Найти реализации интерфейса, представленного текущим документом.
   *
   * @param documentContext контекст документа (ожидается файл-интерфейс)
   * @param params          параметры запроса (позиция курсора)
   * @return локации реализующих методов/классов; пустой список, если документ
   *         не является интерфейсом
   */
  public List<Location> getImplementations(DocumentContext documentContext, ImplementationParams params) {
    if (documentContext.getFileType() != FileType.OS
      || !typeRelations.isInterface(documentContext)) {
      return Collections.emptyList();
    }

    var methodName = methodNameAt(documentContext, params);

    var result = new ArrayList<Location>();
    for (var implementor : typeRelations.implementors(documentContext)) {
      if (methodName != null) {
        implementor.getSymbolTree().getMethodSymbol(methodName)
          .filter(MethodSymbol::isExport)
          .ifPresent(method -> result.add(location(implementor, method.getSelectionRange())));
      } else {
        result.add(location(implementor, typeRelations.classSelectionRange(implementor)));
      }
    }
    result.sort(locationComparator);
    return result;
  }

  /**
   * Имя экспортного метода интерфейса под курсором (для перехода к одноимённым
   * реализациям), либо {@code null}, если курсор не на экспортном методе (тогда
   * переход — к самим реализующим классам). Конструктор {@code ПриСозданииОбъекта}
   * по соглашению не экспортный, поэтому отсекается фильтром экспортности
   * (а у реализаций — фильтром {@code isExport} при поиске одноимённого метода).
   */
  private static @Nullable String methodNameAt(DocumentContext documentContext, ImplementationParams params) {
    var symbol = documentContext.getSymbolTree().getSymbolAtPosition(params.getPosition());
    if (symbol instanceof MethodSymbol method && method.isExport()) {
      return method.getName();
    }
    return null;
  }

  private static Location location(DocumentContext documentContext, Range range) {
    return new Location(documentContext.getUri().toString(), range);
  }
}
