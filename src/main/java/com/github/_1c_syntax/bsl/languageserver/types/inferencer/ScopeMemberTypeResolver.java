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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Тип имени, которое разрешается не в теле метода, а в областях вокруг него: в самом
 * модуле и в глобальной области.
 * <p>
 * Три случая:
 * <ul>
 *   <li><b>модуль как тип</b> — имя модуля в выражении ссылается на тип, членами которого
 *   стали его экспортные методы и переменные: общий модуль, модуль менеджера либо объекта,
 *   библиотечный модуль OneScript;</li>
 *   <li><b>член самого модуля</b> — имя без квалификации, доступное внутри модуля: реквизит
 *   объекта, платформенный метод набора записей, встроенный член класса OneScript;</li>
 *   <li><b>глобальная область</b> — платформенные глобалы, библиотечные и общие модули:
 *   все приходят сюда как глобальные свойства и функции.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ScopeMemberTypeResolver {

  private final TypeRegistry typeRegistry;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Тип, которым модуль выступает в выражении.
   * <p>
   * Берётся из обратного индекса «URI модуля → тип», который наполняют провайдеры
   * регистрации модулей, — обращаться к индексам подсистем напрямую не нужно.
   *
   * @param module символ модуля.
   * @return тип модуля; пустой набор, если модуль как тип не зарегистрирован.
   */
  public TypeSet moduleType(ModuleSymbol module) {
    return globalScopeProvider.moduleTypeRefByUri(module.getOwner().getUri())
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Тип члена самого модуля — того, к чему внутри модуля обращаются без квалификации.
   * <p>
   * В отличие от одноимённого поиска в фасаде типов берёт тип модуля только из готового
   * индекса, без обращения к метаданным: вывод типов идёт уже после его наполнения и при
   * построении дерева символов не вызывается.
   *
   * @param documentContext модуль, из которого идёт обращение.
   * @param name            имя члена.
   * @param kind            вид члена: метод для вызова {@code Имя(…)}, свойство для голого
   *                        имени.
   * @return типы значения члена; пусто, если у модуля нет своего типа либо члена с таким
   *     именем и видом в нём нет.
   */
  public Optional<TypeSet> selfMemberType(DocumentContext documentContext, String name, MemberKind kind) {
    return globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())
      .flatMap(ref -> typeRegistry.findMember(ref, kind, name, documentContext.getFileType()))
      .map(MemberDescriptor::returnTypes);
  }

  /**
   * Тип глобального свойства.
   * <p>
   * Только свойства: голое имя глобальной функции — это не значение, а имена типов для
   * {@code Новый} вообще не члены контекста.
   *
   * @param documentContext документ, из которого идёт обращение.
   * @param name            имя свойства.
   * @return типы значения; пустой набор, если свойства нет либо его тип неизвестен.
   */
  public TypeSet globalPropertyType(DocumentContext documentContext, String name) {
    return globalScopeProvider.globalProperty(name, documentContext.getFileType())
      .map(MemberDescriptor::returnTypes)
      .filter(types -> types.refs().stream().anyMatch(ref -> !ref.equals(TypeRef.UNKNOWN)))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Возвращаемые типы глобальной функции по имени — на случай, когда ссылка на символ
   * не нашлась либо не несёт типа.
   *
   * @param documentContext документ, из которого идёт вызов.
   * @param methodName      имя функции.
   * @return возвращаемые типы; пустой набор, если функции нет либо типы не объявлены.
   */
  public TypeSet globalFunctionType(DocumentContext documentContext, String methodName) {
    if (methodName.isBlank()) {
      return TypeSet.EMPTY;
    }
    return globalScopeProvider.globalFunction(methodName, documentContext.getFileType())
      .map(MemberDescriptor::returnTypes)
      .filter(types -> !types.isEmpty())
      .orElse(TypeSet.EMPTY);
  }
}
