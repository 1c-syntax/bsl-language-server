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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Тип параметра метода, объявленный вне его тела.
 * <p>
 * Источники в порядке убывания приоритета:
 * <ol>
 *   <li>документирующий комментарий самого метода — секция {@code // Параметры:},
 *   включая ссылки {@code // См. …}, в том числе вложенные в описания коллекций;</li>
 *   <li>контракт платформенного события — для параметров обработчиков;</li>
 *   <li>одноимённый параметр метода, на который метод ссылается через {@code // См. …}.</li>
 * </ol>
 * Про код тела ничего не знает: здесь только то, что автор объявил о параметре сам либо
 * что за него объявляет платформа.
 */
@Component
@RequiredArgsConstructor
public class DeclaredParameterTypes {

  private final SymbolTypeIndex symbolTypeIndex;
  private final EventContractsIndex eventContractsIndex;

  /**
   * Объявленный тип переменной, если она — параметр метода.
   *
   * @param variable переменная.
   * @return объявленные типы; пустой набор, если переменная не параметр либо тип не объявлен.
   */
  public TypeSet of(VariableSymbol variable) {
    if (!(variable.getScope() instanceof MethodSymbol method)) {
      return TypeSet.EMPTY;
    }
    var name = variable.getName();
    var parameters = method.getParameters();
    for (var i = 0; i < parameters.size(); i++) {
      var parameter = parameters.get(i);
      if (parameter.getName().equalsIgnoreCase(name)) {
        return of(method, parameter, name, i);
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Объявленный тип параметра по всем источникам, в порядке убывания приоритета.
   *
   * @param method     метод, которому принадлежит параметр.
   * @param parameter  описание параметра.
   * @param name       имя параметра.
   * @param paramIndex позиция параметра в объявлении метода.
   * @return объявленные типы; пустой набор, если ни один источник не сработал.
   */
  private TypeSet of(MethodSymbol method, ParameterDefinition parameter, String name, int paramIndex) {
    // getDeclaredParameterTypes разворачивает и См.-ссылки в описании параметра,
    // включая вложенные, — отдельный проход по ним не нужен.
    var declared = symbolTypeIndex.getDeclaredParameterTypes(parameter, method.getOwner());
    if (!declared.isEmpty()) {
      return declared;
    }
    var fromContract = fromEventContract(method, paramIndex);
    return fromContract.isEmpty() ? fromReferencedMethod(method, name) : fromContract;
  }

  /**
   * Тип параметра обработчика платформенного события из контракта.
   * <p>
   * Сопоставление строго <b>по позиции</b>: имена параметров обработчика задаёт автор, и
   * совпадать с именами в контракте они не обязаны. Если последний параметр контракта
   * помечен как принимающий переменное число аргументов, все параметры метода за ним
   * получают его тип — так устроен, например, конструктор класса OneScript
   * {@code ПриСозданииОбъекта(а, б, в, …)}.
   *
   * @param method     метод-обработчик.
   * @param paramIndex позиция параметра.
   * @return типы из контракта; пустой набор, если контракта нет или позиция вне его.
   */
  private TypeSet fromEventContract(MethodSymbol method, int paramIndex) {
    var contract = eventContractsIndex.getContract(method.getOwner(), method.getName()).orElse(null);
    if (contract == null || contract.signatures().isEmpty()) {
      return TypeSet.EMPTY;
    }
    var params = contract.signatures().get(0).parameters();
    if (params.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var index = Math.min(paramIndex, params.size() - 1);
    var param = params.get(index);
    if (paramIndex >= params.size() && !param.variadic()) {
      return TypeSet.EMPTY;
    }
    return param.types();
  }

  /**
   * Тип одноимённого параметра метода, на который ссылается документирующий комментарий
   * ({@code // См. Метод}). Работает в пределах одного модуля.
   *
   * @param method    метод со ссылкой.
   * @param paramName имя параметра.
   * @return типы одноимённого параметра; пустой набор, если ссылки нет либо она никуда
   *     не ведёт.
   */
  private TypeSet fromReferencedMethod(MethodSymbol method, String paramName) {
    var description = method.getDescription().orElse(null);
    if (description == null) {
      return TypeSet.EMPTY;
    }
    var links = description.getLinks();
    if (links == null || links.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var owner = method.getOwner();
    for (var link : links) {
      var target = localMethod(owner, link.link());
      if (target == null) {
        continue;
      }
      for (var targetParam : target.getParameters()) {
        if (targetParam.getName().equalsIgnoreCase(paramName)) {
          var types = symbolTypeIndex.getDeclaredParameterTypes(targetParam, owner);
          if (!types.isEmpty()) {
            return types;
          }
        }
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Метод модуля по имени.
   *
   * @param documentContext модуль.
   * @param methodName      имя метода; квалифицированное имя другого модуля не
   *                        поддерживается.
   * @return метод либо {@code null}, если такого в модуле нет.
   */
  @Nullable
  private static MethodSymbol localMethod(DocumentContext documentContext, @Nullable String methodName) {
    if (methodName == null || methodName.contains(".")) {
      return null;
    }
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(method -> method.getName().equalsIgnoreCase(methodName))
      .findFirst()
      .orElse(null);
  }
}
