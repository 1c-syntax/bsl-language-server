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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.List;

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
 * иначе — из имени самой переменной/параметра. Параметр {@code Тип} с
 * коллекцией ({@code Массив}, {@code ТаблицаЗначений} и т.п.) означает внедрение
 * коллекции желудей — тогда типом становится сама коллекция.
 * <p>
 * Имя желудя резолвится сначала через {@link AutumnBeanIndex} (переименованные
 * желуди, прозвища, {@code &Верховный}), затем — прямым резолвом имени типа
 * через {@link TypeRegistry} (поведение по умолчанию «имя желудя == имя
 * .os-класса»).
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnComponentInferencer {

  private final TypeRegistry typeRegistry;
  private final AutumnBeanIndex beanIndex;
  private final AutumnMetaAnnotationResolver metaAnnotationResolver;

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

  private TypeSet injectedType(Annotation injection, String fallbackName, FileType fileType) {
    var collectionType = collectionType(injection);
    if (!AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(collectionType)) {
      // Внедряется прилепляемая коллекция желудей. ВНИМАНИЕ: collectionType — это
      // ИМЯ прилепляемой коллекции (autumn-collections), а не имя типа; прямой
      // резолв через TypeRegistry — временное приближение, см. issue #3959.
      return typeRegistry.resolve(collectionType, fileType)
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
   * Параметр {@code Тип} задаётся только по имени (позиционно можно лишь {@code Значение}).
   */
  private static String collectionType(Annotation injection) {
    return AutumnAnnotations.stringParameter(injection, AutumnAnnotations.TYPE_PARAMETER)
      .filter(type -> !type.isBlank())
      .orElse(AutumnAnnotations.BEAN_TYPE);
  }

  private String beanName(Annotation injection, String fallbackName) {
    // Имя желудя с учётом разворачивания мета-аннотаций: зашитое в мете
    // (&Лог = &Пластилин(Значение="Лог")) → проброшенное/прямое Значение
    // использования (&Внедряемое("X")/&Пластилин("X")) → иначе имя переменной.
    return metaAnnotationResolver.roleValues(injection, AutumnAnnotations.INJECTION).stream()
      .findFirst()
      .orElse(fallbackName);
  }
}
