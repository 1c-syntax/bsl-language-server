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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import lombok.experimental.UtilityClass;

import java.util.Optional;

/**
 * Имена аннотаций фреймворка «ОСень» (Autumn) и помощники чтения их параметров.
 * <p>
 * Базовый набор имён; алиасы (killjoy) и пользовательские аннотации через
 * мета-аннотации разрешаются отдельно.
 */
@UtilityClass
public class AutumnAnnotations {

  /** Объявление компонента-желудя над конструктором. */
  public static final String COMPONENT = "Желудь";
  /** Класс-фабрика желудей (дуб); сам по себе тоже желудь. Размещается над конструктором. */
  public static final String OAK = "Дуб";
  /** Фабричный метод, создающий желудь; допустим только в классе-дубе. */
  public static final String FACTORY = "Завязь";
  /** Точка внедрения зависимости. */
  public static final String INJECTION = "Пластилин";
  /** Приоритетный желудь при конфликте имён/прозвищ. */
  public static final String PRIMARY = "Верховный";
  /** Дополнительное имя (алиас) желудя; повторяемая. */
  public static final String QUALIFIER = "Прозвище";
  /** Маркер класса-определения пользовательской аннотации ({@code &Аннотация("Имя")}). */
  public static final String ANNOTATION_MARKER = "Аннотация";
  /**
   * Псевдоним параметра конструктора композитной аннотации для параметра вложенной
   * мета-аннотации (annotations 1.5+, аналог Spring {@code @AliasFor}). Помечает
   * параметр {@code ПриСозданииОбъекта}: его значение переносится в указанную
   * мета-аннотацию при разворачивании.
   */
  public static final String ALIAS_FOR = "ПсевдонимДля";
  /** Параметр {@code &ПсевдонимДля} — имя целевой мета-аннотации. */
  public static final String ALIAS_TARGET_ANNOTATION = "Аннотация";
  /** Параметр {@code &ПсевдонимДля} — имя параметра целевой мета-аннотации. */
  public static final String ALIAS_TARGET_PARAMETER = "Параметр";
  /** Параметр {@code &ПсевдонимДля} — переносить ли значение по умолчанию (по умолчанию {@code Ложь}). */
  public static final String ALIAS_TRANSFER_DEFAULT = "ПереноситьЗначениеПоУмолчанию";
  /** Булев литерал {@code Истина}. */
  public static final String TRUE_LITERAL = "Истина";
  /**
   * Аннотация прилепляемой коллекции желудей (autumn-collections). Размещается над
   * конструктором класса-реализации коллекции; в параметре {@link #VALUE_PARAMETER}
   * задано имя коллекции (например, «Массив», «Соответствие»).
   */
  public static final String ATTACHABLE_COLLECTION = "ПрилепляемаяКоллекция";

  /** Первый параметр большинства аннотаций — имя желудя/значение. */
  public static final String VALUE_PARAMETER = "Значение";
  /** Параметр {@code Тип} аннотаций {@code &Пластилин}/{@code &Завязь}. */
  public static final String TYPE_PARAMETER = "Тип";
  /** Значение {@code Тип}, означающее «желудь как таковой». */
  public static final String BEAN_TYPE = "Желудь";
  /**
   * Имя экспортируемой функции прилепляемой коллекции — точка получения значения
   * для внедрения. Возвращаемый тип берётся из её bsldoc-описания.
   */
  public static final String ATTACHABLE_COLLECTION_GETTER = "Получить";

  /**
   * @return первая аннотация с указанным именем (регистронезависимо).
   */
  public static Optional<Annotation> find(Iterable<Annotation> annotations, String name) {
    for (var annotation : annotations) {
      if (name.equalsIgnoreCase(annotation.getName())) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  /**
   * Значение строкового параметра аннotaции по имени.
   * <p>
   * Безымянный (позиционный) параметр трактуется как параметр {@code Значение} —
   * именно так его разрешает движок аннотаций ОСени (autumn-library/annotations,
   * {@code ОпределениеАннотации.ПривестиИменаПараметров}). Поэтому позиционно
   * можно задать только {@code Значение}; {@code Тип} и прочие — лишь по имени.
   *
   * @return значение первого подходящего параметра, если это строковый литерал.
   */
  public static Optional<String> stringParameter(Annotation annotation, String parameterName) {
    for (var parameter : annotation.getParameters()) {
      var effectiveName = parameter.name().isEmpty() ? VALUE_PARAMETER : parameter.name();
      if (parameterName.equalsIgnoreCase(effectiveName) && parameter.value().isLeft()) {
        return Optional.of(parameter.value().getLeft());
      }
    }
    return Optional.empty();
  }
}
