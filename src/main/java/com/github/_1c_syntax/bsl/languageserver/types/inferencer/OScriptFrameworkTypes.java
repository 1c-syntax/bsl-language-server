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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.ExtendsAnnotations;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.OScriptExtends;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Типы, которые переменной задаёт не код и не комментарий, а фреймворк OneScript.
 * <p>
 * Два таких фреймворка:
 * <ul>
 *   <li><b>«ОСень»</b> — внедрение зависимости по аннотации {@code &Пластилин}: тип берётся
 *   из объявления внедряемого компонента. Аннотации несёт сам символ, поэтому так
 *   типизируются и поля модуля, и параметры конструктора либо завязи;</li>
 *   <li><b>библиотека {@code extends}</b> — наследование по аннотации {@code &Расширяет}.
 *   Типом поля-держателя родителя становится родительский класс, и {@code Родитель.Метод()}
 *   начинает разрешаться по членам родителя. Держателей два вида: явный, помеченный
 *   {@code &Родитель}, и неявное поле {@code _ОбъектРодитель}, которое фреймворк создаёт
 *   в собранном объекте, — в исходниках наследника его нет.</li>
 * </ul>
 * Всё перечисленное относится только к {@code .os}-файлам.
 */
@Component
@RequiredArgsConstructor
public class OScriptFrameworkTypes {

  private final TypeRegistry typeRegistry;
  private final AutumnComponentInferencer autumnComponentInferencer;
  private final OScriptExtends oScriptExtends;

  /**
   * Тип зависимости, внедряемой фреймворком «ОСень» по аннотации {@code &Пластилин}.
   *
   * @param variable переменная — поле модуля либо параметр.
   * @return тип внедряемого компонента; пустой набор, если внедрения нет.
   */
  public TypeSet injectedType(VariableSymbol variable) {
    var kind = variable.getKind();
    if (kind != VariableKind.MODULE && kind != VariableKind.PARAMETER) {
      return TypeSet.EMPTY;
    }
    return autumnComponentInferencer.inferInjectedType(
      variable.getAnnotations(), variable.getName(), variable.getOwner().getFileType());
  }

  /**
   * Тип поля-держателя родителя библиотеки {@code extends} — того, что помечено
   * {@code &Родитель}.
   *
   * @param variable переменная — поле модуля.
   * @return тип родительского класса; пустой набор, если переменная держателем не является
   *     либо наследование не объявлено.
   */
  public TypeSet parentHolderType(VariableSymbol variable) {
    if (variable.getKind() != VariableKind.MODULE) {
      return TypeSet.EMPTY;
    }
    var owner = variable.getOwner();
    if (owner.getFileType() != FileType.OS || !oScriptExtends.isParentHolder(variable)) {
      return TypeSet.EMPTY;
    }
    return parentClassType(owner);
  }

  /**
   * Тип неявного поля-держателя родителя {@code _ОбъектРодитель}, которого в исходниках
   * наследника нет — его создаёт фреймворк в собранном объекте.
   *
   * @param name            имя, встреченное в коде.
   * @param documentContext документ, в котором оно встречено.
   * @return тип родительского класса; пустой набор, если имя не то, файл не
   *     {@code .os}-класс либо тип родителя не выводится — тогда имя разрешают дальше как
   *     свойство самого модуля или как глобальное.
   */
  public TypeSet implicitParentFieldType(String name, DocumentContext documentContext) {
    if (!ExtendsAnnotations.IMPLICIT_PARENT_FIELD.equalsIgnoreCase(name)
      || documentContext.getFileType() != FileType.OS) {
      return TypeSet.EMPTY;
    }
    return parentClassType(documentContext);
  }

  /**
   * Тип родительского класса {@code .os}-документа, объявленного через {@code &Расширяет}
   * либо через мета-аннотации.
   *
   * @param documentContext документ-наследник.
   * @return тип родителя; пустой набор, если наследование не объявлено либо родитель не
   *     разрешается в зарегистрированный тип.
   */
  private TypeSet parentClassType(DocumentContext documentContext) {
    return oScriptExtends.parentClassName(documentContext)
      .flatMap(name -> typeRegistry.resolve(name, FileType.OS))
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
  }
}
