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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Вывод типа внедряемой зависимости фреймворка «ОСень» (Autumn).
 * <p>
 * Аннотация {@code &Пластилин} помечает поле/параметр/сеттер как точку
 * внедрения желудя (компонента). Тип внедряемого значения определяется именем
 * желудя:
 * <pre>
 *   &amp;Пластилин("ИмяЖелудя")
 *   Перем ВнедренныйЖелудь;   // тип = желудь "ИмяЖелудя"
 * </pre>
 * Имя желудя берётся из параметра {@code Значение} (или первого позиционного),
 * иначе — из имени самой переменной/параметра. Параметр {@code Тип} с именем
 * прилепляемой коллекции (autumn-collections: {@code Массив}, {@code ТаблицаЗначений}
 * и т.п.) означает внедрение коллекции желудей — тогда типом становится тип,
 * который возвращает метод {@code Получить()} соответствующей прилепляемой
 * коллекции.
 * <p>
 * Имя желудя резолвится сначала через {@link AutumnBeanIndex} (переименованные
 * желуди, прозвища, {@code &Верховный}), затем — прямым резолвом имени типа
 * через {@link TypeRegistry} (поведение по умолчанию «имя желудя == имя
 * .os-класса»). Имя коллекции резолвится через {@link AutumnCollectionIndex},
 * а если у её {@code Получить()} нет описания возвращаемого значения — фоллбэк
 * на прямой резолв этого имени через {@link TypeRegistry} (для совпадающих имён
 * вроде {@code Массив}/{@code ТаблицаЗначений} это даёт корректный платформенный тип).
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class AutumnComponentInferencer {

  private final TypeRegistry typeRegistry;
  private final AutumnBeanIndex beanIndex;
  private final AutumnCollectionIndex collectionIndex;
  private final OScriptMetaAnnotationResolver metaAnnotationResolver;

  /**
   * Вывести тип внедряемой зависимости по аннотациям объявления.
   *
   * @param annotations  аннотации поля/параметра
   * @param fallbackName имя переменной/параметра — используется как имя желудя,
   *                     если оно не задано в аннотации
   * @param fileType     тип файла-владельца (для корректного резолва типа)
   * @return тип внедряемого желудя или {@link TypeSet#EMPTY}, если это не точка
   *         внедрения либо тип не разрешился
   */
  public TypeSet inferInjectedType(List<Annotation> annotations, String fallbackName, FileType fileType) {
    return metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.INJECTION)
      .map(injection -> injectedType(injection, fallbackName, fileType))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Внедряемый желудь точки с аннотацией {@code &Пластилин} — для навигации к производителю.
   * Имя желудя берётся из {@code Значение} (или первого позиционного), иначе — из имени
   * переменной/параметра; параметр {@code Тип} с именем коллекции помечает внедрение
   * прилепляемой коллекции (тогда целей навигации несколько — все подходящие желуди).
   *
   * @param annotations  аннотации поля/параметра
   * @param fallbackName имя переменной/параметра — используется как имя желудя, если оно не
   *                     задано в аннотации
   * @return внедряемый желудь либо пусто, если это не точка внедрения, имя желудя пустое или
   *         задан явно пустой {@code Тип=""} (в autumn — ошибка, точка не разрешается)
   */
  public Optional<InjectedBean> injectedBean(List<Annotation> annotations, String fallbackName) {
    return metaAnnotationResolver.findByRole(annotations, AutumnAnnotations.INJECTION)
      .flatMap(injection -> {
        var name = beanName(injection, fallbackName);
        var collectionName = collectionType(injection);
        // Пустое имя желудя или явный Тип="" (не «Желудь» и не коллекция) — точка внедрения не
        // разрешается, как и в inferInjectedType; иначе линза вела бы к ложным целям.
        if (name.isBlank() || collectionName.isBlank()) {
          return Optional.empty();
        }
        return Optional.of(new InjectedBean(
          name,
          !AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(collectionName)));
      });
  }

  /**
   * Внедряемый желудь: имя для резолва производителя и признак внедрения прилепляемой коллекции.
   *
   * @param name       Имя желудя (ключ резолва производителя/членов).
   * @param collection {@code true}, если внедряется прилепляемая коллекция (нужны все подходящие
   *                   желуди), иначе одиночный желудь.
   */
  public record InjectedBean(String name, boolean collection) {
  }

  private TypeSet injectedType(Annotation injection, String fallbackName, FileType fileType) {
    var collectionName = collectionType(injection);
    if (!AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(collectionName)) {
      // collectionName — имя прилепляемой коллекции (autumn-collections), а не
      // имя типа. Источник истины — AutumnCollectionIndex: тип возвращаемого
      // значения метода Получить() (из bsldoc). Если описания нет (или коллекция
      // не найдена), фоллбэк — прямой резолв имени через TypeRegistry: для
      // штатных коллекций имя совпадает с именем платформенного типа
      // (Массив, Соответствие, ТаблицаЗначений и т.п.).
      var fromCollection = collectionIndex.resolve(collectionName);
      if (!fromCollection.isEmpty()) {
        return fromCollection;
      }
      return typeRegistry.resolve(collectionName, fileType)
        .map(TypeSet::of)
        .orElse(TypeSet.EMPTY);
    }

    var beanName = beanName(injection, fallbackName);
    if (beanName.isBlank()) {
      return TypeSet.EMPTY;
    }

    // Имя желудя резолвится ТОЛЬКО через реестр желудей. В ОСени внедрение идёт
    // по имени желудя (&Желудь/&Завязь/&Прозвище), а не по имени типа, поэтому
    // fallback на TypeRegistry.resolve(имя) недопустим — он бы выдавал тип для
    // любого одноимённого класса, даже если желудя с таким именем нет.
    return beanIndex.resolve(beanName);
  }

  /**
   * Имя прилепляемой коллекции из параметра {@code Тип}; {@link AutumnAnnotations#BEAN_TYPE}
   * (значение по умолчанию), если параметр не задан или равен {@code Желудь} —
   * это означает обычное внедрение по имени, а не коллекцию.
   * <p>
   * Берётся с учётом разворачивания мета-аннотаций (зашитый/проброшенный через
   * {@code &ПсевдонимДля} {@code Тип}), как и имя желудя. Параметр {@code Тип}
   * задаётся только по имени (позиционно можно лишь {@code Значение}).
   */
  private String collectionType(Annotation injection) {
    // Как в autumn: Тип НЕ передан → «Желудь» (обычное внедрение, BEAN_TYPE); переданное
    // значение — как есть. Явный Тип="" в autumn — ошибка (не «Желудь» и не коллекция),
    // поэтому тип не выводится: пустое имя коллекции не резолвится → TypeSet.EMPTY.
    return metaAnnotationResolver
      .roleParameterValues(injection, AutumnAnnotations.INJECTION, AutumnAnnotations.TYPE_PARAMETER).stream()
      .findFirst()
      .orElse(AutumnAnnotations.BEAN_TYPE);
  }

  private String beanName(Annotation injection, String fallbackName) {
    // Имя желудя с учётом разворачивания мета-аннотаций: зашитое в мете
    // (&Лог = &Пластилин(Значение="Лог")) → проброшенное/прямое Значение
    // использования (&Внедряемое("X")/&Пластилин("X")) → иначе имя переменной.
    // Как в autumn (ПолучитьЗначениеПараметраАннотации): фоллбэк на имя члена — только
    // когда Значение НЕ передано; явно переданное "" остаётся "" (желудь "" не найдётся).
    return metaAnnotationResolver.roleValues(injection, AutumnAnnotations.INJECTION).stream()
      .findFirst()
      .orElse(fallbackName);
  }
}
