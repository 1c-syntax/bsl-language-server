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

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Разрешение пользовательских аннотаций фреймворка «ОСень» через мета-аннотации.
 * <p>
 * Пользовательская аннотация определяется классом, конструктор которого помечен
 * {@code &Аннотация("Имя")} и базовой мета-аннотацией. Например, killjoy-алиас
 * {@code &Внедряемое} определён как:
 * <pre>
 *   &amp;Аннотация("Внедряемое")
 *   &amp;Пластилин
 *   Процедура ПриСозданииОбъекта(Значение = "", Тип = "")
 * </pre>
 * то есть {@code &Внедряемое} — это {@code &Пластилин}.
 * <p>
 * Индекс «имя аннотации → определение» уже строит {@link AnnotationRepository}
 * (через {@code AnnotationReferenceFinder}): он регистрирует конструкторы с
 * {@code &Аннотация} и инкрементально обновляется при правке/удалении документов.
 * Резолвер переиспользует его и разворачивает мета-аннотации транзитивно — по
 * образцу {@code РазворачивательАннотаций} из autumn-library/annotations, —
 * кэшируя получившийся плоский набор ролей на каждую аннотацию, чтобы не
 * пересчитывать цепочку на каждый запрос.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnMetaAnnotationResolver {

  /** Базовая мета-аннотация, регистрирующая пользовательскую аннотацию. */
  private static final String ANNOTATION_MARKER = "Аннотация";

  private final AnnotationRepository annotationRepository;

  /**
   * Кэш развёрнутых ролей: имя аннотации (lowercase) → транзитивный набор имён
   * (lowercase), в которые она разворачивается, включая её саму. Сбрасывается
   * при изменении состава зарегистрированных аннотаций.
   */
  private final Map<String, Set<String>> roleClosureCache = new ConcurrentHashMap<>();

  /**
   * Является ли аннотация {@code annotationName} базовой ролью {@code baseRole}
   * напрямую или через цепочку мета-аннотаций.
   *
   * @param annotationName имя проверяемой аннотации (как в коде)
   * @param baseRole       базовое имя роли (например, {@link AutumnAnnotations#INJECTION})
   */
  public boolean isRole(String annotationName, String baseRole) {
    return roleClosure(annotationName).contains(baseRole.toLowerCase(Locale.ROOT));
  }

  /**
   * Первая аннотация из списка, разворачивающаяся в указанную базовую роль.
   */
  public Optional<Annotation> findByRole(Iterable<Annotation> annotations, String baseRole) {
    for (var annotation : annotations) {
      if (isRole(annotation.getName(), baseRole)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  /**
   * @return {@code true}, если среди аннотаций есть разворачивающаяся в роль.
   */
  public boolean hasRole(Iterable<Annotation> annotations, String baseRole) {
    return findByRole(annotations, baseRole).isPresent();
  }

  /**
   * Значения параметра {@link AutumnAnnotations#VALUE_PARAMETER} всех аннотаций,
   * разворачивающихся в указанную роль (например, все прозвища желудя).
   */
  public List<String> valuesByRole(Iterable<Annotation> annotations, String baseRole) {
    var result = new ArrayList<String>();
    for (var annotation : annotations) {
      if (!isRole(annotation.getName(), baseRole)) {
        continue;
      }
      AutumnAnnotations.stringParameter(annotation, AutumnAnnotations.VALUE_PARAMETER)
        .filter(value -> !value.isBlank())
        .ifPresent(result::add);
    }
    return result;
  }

  /**
   * Сброс кэша развёрнутых ролей при изменении состава зарегистрированных
   * аннотаций (наполнение контекста, правка или удаление документа). Сам индекс
   * аннотаций поддерживает {@link AnnotationRepository}; пересчёт ролей —
   * ленивый, при следующем обращении.
   */
  @EventListener({
    ServerContextPopulatedEvent.class,
    DocumentContextContentChangedEvent.class,
    ServerContextDocumentRemovedEvent.class
  })
  public void invalidate() {
    roleClosureCache.clear();
  }

  private Set<String> roleClosure(String annotationName) {
    return roleClosureCache.computeIfAbsent(annotationName.toLowerCase(Locale.ROOT), key -> {
      var roles = new HashSet<String>();
      collectRoles(annotationName, roles);
      return roles;
    });
  }

  private void collectRoles(String annotationName, Set<String> accumulator) {
    if (!accumulator.add(annotationName.toLowerCase(Locale.ROOT))) {
      return;
    }
    annotationRepository.findByName(annotationName)
      .flatMap(AnnotationSymbol::getParent)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast)
      .ifPresent(constructor -> {
        for (var meta : constructor.getAnnotations()) {
          if (!ANNOTATION_MARKER.equalsIgnoreCase(meta.getName())) {
            collectRoles(meta.getName(), accumulator);
          }
        }
      });
  }
}
