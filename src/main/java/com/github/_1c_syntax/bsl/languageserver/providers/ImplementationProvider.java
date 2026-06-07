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
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptClassResolver;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptExtends;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_implementation">Goto Implementation Request specification</a>
 */
@Component
@RequiredArgsConstructor
public class ImplementationProvider {

  private final OScriptClassResolver classResolver;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

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
      || !OScriptExtends.isInterface(documentContext, metaAnnotationResolver)) {
      return Collections.emptyList();
    }

    var interfaceNames = classResolver.classNames(documentContext).stream()
      .map(name -> name.toLowerCase(Locale.ROOT))
      .collect(Collectors.toSet());

    var methodName = methodNameAt(documentContext, params);
    var serverContext = documentContext.getServerContext();

    var result = new ArrayList<Location>();
    for (var candidate : serverContext.getDocuments().values()) {
      if (candidate.getFileType() != FileType.OS || candidate.getUri().equals(documentContext.getUri())) {
        continue;
      }
      if (!implementsAnyTransitively(candidate, interfaceNames, serverContext)) {
        continue;
      }
      if (methodName != null) {
        candidate.getSymbolTree().getMethodSymbol(methodName)
          .filter(MethodSymbol::isExport)
          .ifPresent(method -> result.add(location(candidate, method.getSelectionRange())));
      } else {
        result.add(location(candidate, classSelectionRange(candidate)));
      }
    }
    result.sort(locationComparator);
    return result;
  }

  /**
   * Реализует ли класс (транзитивно по цепочке {@code &Расширяет}) хотя бы один
   * из интерфейсов. Покрывает случай абстрактного родителя: родитель объявляет
   * {@code &Реализует("Интерфейс")}, а наследник через {@code &Расширяет} считается
   * реализацией этого интерфейса (см. документацию extends — комбинирование
   * наследования и интерфейсов).
   */
  private boolean implementsAnyTransitively(DocumentContext candidate, Set<String> interfaceNames,
                                            ServerContext serverContext) {
    var visited = new HashSet<URI>();
    DocumentContext current = candidate;
    while (current != null && visited.add(current.getUri())) {
      var implemented = OScriptExtends.implementedInterfaceNames(current, metaAnnotationResolver);
      for (var name : implemented) {
        if (interfaceNames.contains(name.toLowerCase(Locale.ROOT))) {
          return true;
        }
      }
      current = OScriptExtends.parentClassName(current, metaAnnotationResolver)
        .flatMap(parent -> classResolver.resolveClassDocument(parent, serverContext))
        .orElse(null);
    }
    return false;
  }

  /**
   * Имя экспортного метода интерфейса под курсором (для перехода к одноимённым
   * реализациям), либо {@code null}, если курсор не на экспортном методе (тогда
   * переход — к самим реализующим классам). Конструктор {@code ПриСозданииОбъекта}
   * по соглашению не экспортный, поэтому отсекается фильтром экспортности
   * (а у реализаций — фильтром {@code isExport} при поиске одноимённого метода).
   */
  private static String methodNameAt(DocumentContext documentContext, ImplementationParams params) {
    var symbol = documentContext.getSymbolTree().getSymbolAtPosition(params.getPosition());
    if (symbol instanceof MethodSymbol method && method.isExport()) {
      return method.getName();
    }
    return null;
  }

  private static Range classSelectionRange(DocumentContext documentContext) {
    return documentContext.getSymbolTree().getConstructor()
      .map(MethodSymbol::getSelectionRange)
      .orElseGet(() -> documentContext.getSymbolTree().getModule().getSelectionRange());
  }

  private static Location location(DocumentContext documentContext, Range range) {
    return new Location(documentContext.getUri().toString(), range);
  }
}
