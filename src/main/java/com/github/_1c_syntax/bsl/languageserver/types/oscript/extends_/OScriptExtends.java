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
package com.github._1c_syntax.bsl.languageserver.types.oscript.extends_;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.types.oscript.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Поддержка библиотеки наследования OneScript
 * <a href="https://github.com/nixel2007/extends">extends</a>
 * <p>
 * Наследование объявляется аннотацией {@code &Расширяет("ИмяРодителя")} над
 * конструктором класса {@code ПриСозданииОбъекта}:
 * <pre>
 *   &amp;Расширяет("Родитель")
 *   Процедура ПриСозданииОбъекта()
 *   КонецПроцедуры
 * </pre>
 * Имя родителя — то же, что используется в {@code Новый Родитель}: для
 * библиотечного класса это его {@code qualifiedName} из {@code lib.config},
 * для обычного {@code .os}-файла — basename.
 * <p>
 * Наследование распознаётся не только при прямом использовании {@code &Расширяет},
 * но и через <b>мета-аннотации</b> фреймворка «ОСень»: пользовательская аннотация,
 * определённая классом с {@code &Аннотация("Имя")} и {@code &Расширяет("Родитель")},
 * означает, что любой класс, помеченный {@code &Имя(...)}, наследует
 * {@code Родитель}. Так устроена, например, библиотека
 * <a href="https://github.com/autumn-library/autumn-data">autumn-data</a>:
 * <pre>
 *   // Определение аннотации &amp;ХранилищеСущностей:
 *   &amp;Аннотация("ХранилищеСущностей")
 *   &amp;Расширяет("ХранилищеСущностей")
 *   &amp;Желудь
 *   Процедура ПриСозданииОбъекта(Значение, ИсточникДанных = "")
 *
 *   // Использование — класс получает члены ХранилищеСущностей (ПолучитьОдно и т.п.):
 *   &amp;ХранилищеСущностей("Справочник")
 *   Процедура ПриСозданииОбъекта()
 * </pre>
 * Разворачивание мета-аннотаций делегируется {@link OScriptMetaAnnotationResolver}
 * (роль {@link ExtendsAnnotations#EXTENDS_ROLE}), который внедряется через конструктор.
 */
@Component
@RequiredArgsConstructor
public class OScriptExtends {

  /**
   * Имя аннотации-маркера обходимой коллекции OneScript {@code &Обходимое}
   * (англ. {@code &Iterable}). Ставится на конструктор {@code ПриСозданииОбъекта}
   * и означает, что класс поддерживает обход движковым {@code Для Каждого … Цикл}
   * (см. {@code UserIterableContextInstance} в OneScript). В отличие от
   * {@code &Расширяет}/{@code &Реализует}, это аннотация стандартной библиотеки
   * OneScript, а не библиотеки {@code extends}; разворачивание мета-аннотаций
   * к ней не применяется.
   */
  private static final String ITERABLE_ANNOTATION_RU = "Обходимое";
  private static final String ITERABLE_ANNOTATION_EN = "Iterable";

  /** Резолвер мета-аннотаций «ОСени» (внедряется через конструктор). */
  private final OScriptMetaAnnotationResolver metaAnnotationResolver;

  /**
   * Имена интерфейсов, которые класс объявляет реализуемыми через
   * {@code &Реализует("...")} на конструкторе {@code ПриСозданииОбъекта}
   * (с учётом мета-аннотаций). Имя интерфейса — то же, что используется в
   * {@code Новый Интерфейс}: qualifiedName library-класса или basename файла.
   *
   * @param documentContext контекст {@code .os}-документа
   * @return список имён реализуемых интерфейсов (возможно, пустой)
   */
  public List<String> implementedInterfaceNames(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return List.of();
    }
    var result = new ArrayList<String>();
    for (var value : metaAnnotationResolver.valuesByRole(constructorAnnotations(documentContext), ExtendsAnnotations.IMPLEMENTS_ROLE)) {
      if (value != null && !value.isBlank()) {
        result.add(value);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Является ли {@code .os}-документ интерфейсом — несёт ли его конструктор
   * {@code ПриСозданииОбъекта} аннотацию-маркер {@code &Интерфейс}
   * (с учётом мета-аннотаций).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return {@code true}, если документ — интерфейс
   */
  public boolean isInterface(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return false;
    }
    return metaAnnotationResolver.hasRole(constructorAnnotations(documentContext), ExtendsAnnotations.INTERFACE_ROLE);
  }

  /**
   * Является ли {@code .os}-документ обходимой коллекцией — несёт ли его
   * конструктор {@code ПриСозданииОбъекта} аннотацию-маркер {@code &Обходимое}
   * ({@code &Iterable}). Такой класс регистрируется в системе типов как
   * коллекция, обходимая через {@code Для Каждого … Цикл} (тип элемента при
   * этом остаётся неизвестным — как у {@code Массив}).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return {@code true}, если документ — обходимая коллекция
   */
  public boolean isIterable(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return false;
    }
    for (Annotation annotation : constructorAnnotations(documentContext)) {
      var name = annotation.getName();
      if (ITERABLE_ANNOTATION_RU.equalsIgnoreCase(name) || ITERABLE_ANNOTATION_EN.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Является ли переменная держателем экземпляра родителя: либо помечена
   * {@code &Родитель} (явный держатель с произвольным именем), либо это неявное
   * поле {@link ExtendsAnnotations#IMPLICIT_PARENT_FIELD}. Тип такого поля — родительский класс.
   *
   * @param variable переменная (как правило, поле модуля {@code .os}-класса)
   * @return {@code true}, если переменная хранит экземпляр родителя
   */
  public boolean isParentHolder(VariableSymbol variable) {
    if (ExtendsAnnotations.IMPLICIT_PARENT_FIELD.equalsIgnoreCase(variable.getName())) {
      return true;
    }
    for (Annotation annotation : variable.getAnnotations()) {
      if (ExtendsAnnotations.PARENT_FIELD.equalsIgnoreCase(annotation.getName())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Имя родительского класса для документа {@code .os}, объявленное аннотацией
   * наследования над конструктором {@code ПриСозданииОбъекта}. Учитывает
   * мета-аннотации (см. класс-уровневую документацию).
   *
   * @param documentContext контекст {@code .os}-документа
   * @return имя родителя или {@link Optional#empty()}, если файл не {@code .os}
   *         либо наследование не объявлено
   */
  public Optional<String> parentClassName(DocumentContext documentContext) {
    if (documentContext.getFileType() != FileType.OS) {
      return Optional.empty();
    }
    return parentFromAnnotations(constructorAnnotations(documentContext));
  }

  /**
   * Аннотации конструктора {@code ПриСозданииОбъекта} ({@code OnObjectCreate}) —
   * единственного метода, на котором библиотека {@code extends} объявляет
   * {@code &Расширяет}/{@code &Реализует}/{@code &Интерфейс}. Конструктор ищется
   * по имени среди методов, а не через {@code SymbolTree.getConstructor()}:
   * {@code ConstructorSymbol} создаётся только для классифицированных
   * OScript-классов, а наследование работает и для обычных {@code .os}-файлов.
   *
   * @param documentContext контекст {@code .os}-документа
   * @return аннотации конструктора либо пустой список, если конструктора нет
   */
  private static List<Annotation> constructorAnnotations(DocumentContext documentContext) {
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (Methods.isOscriptClassConstructorName(method.getName())) {
        return method.getAnnotations();
      }
    }
    return List.of();
  }

  /**
   * Имя родителя из аннотаций одного метода: роль {@link ExtendsAnnotations#EXTENDS_ROLE} —
   * прямой {@code &Расширяет} и мета-аннотации «ОСени», разворачивающиеся в неё.
   */
  private Optional<String> parentFromAnnotations(List<Annotation> annotations) {
    return metaAnnotationResolver.valuesByRole(annotations, ExtendsAnnotations.EXTENDS_ROLE).stream()
      .filter(value -> value != null && !value.isBlank())
      .findFirst();
  }
}
