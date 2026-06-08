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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
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
@WorkspaceScope
@RequiredArgsConstructor
public class AutumnMetaAnnotationResolver {

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
      if (isRole(annotation.getName(), baseRole)) {
        result.addAll(roleValues(annotation, baseRole));
      }
    }
    return result;
  }

  /**
   * Эффективные значения параметра {@link AutumnAnnotations#VALUE_PARAMETER} роли
   * {@code baseRole} с учётом разворачивания мета-аннотаций. Удобная обёртка над
   * {@link #roleParameterValues(Annotation, String, String)}.
   */
  public List<String> roleValues(Annotation usage, String baseRole) {
    return roleParameterValues(usage, baseRole, AutumnAnnotations.VALUE_PARAMETER);
  }

  /**
   * Эффективные значения параметра {@code parameterName} аннотации роли {@code baseRole}
   * для аннотации-использования с учётом разворачивания мета-аннотаций. Механизм единый
   * для любого параметра любой роли (имя желудя — {@code Значение}, тип коллекции —
   * {@code Тип}, прозвища и т.д.).
   * <p>
   * Источники:
   * <ol>
   *   <li>значения, статически зафиксированные на аннотации роли в определении алиаса
   *       (например, {@code &Лог} = {@code &Пластилин(Значение = "Лог")} → «Лог»;
   *       {@code &Контроллер} = {@code … &Прозвище("Контроллер")} → «Контроллер»);</li>
   *   <li>значение самой аннотации-использования, если она <i>прямо</i> является ролью
   *       ({@code &Желудь("X")}, {@code &Пластилин(Тип = "Массив")});</li>
   *   <li>декларативный перенос через {@code &ПсевдонимДля} (аналог Spring {@code @AliasFor}):
   *       параметр конструктора алиаса, помеченный
   *       {@code &ПсевдонимДля(Аннотация = «роль», Параметр = «parameterName»)}, переносит своё
   *       переданное значение (а при {@code ПереноситьЗначениеПоУмолчанию = Истина} — и значение
   *       по умолчанию) в этот параметр роли. Так разворачиваются killjoy-алиасы
   *       {@code &Внедряемое("X", Тип = "Массив")}/{@code &Компонент("X")}.</li>
   * </ol>
   * Для {@code &Контроллер("/маршрут")} значение «/маршрут» в имя желудя НЕ попадает —
   * параметр не помечен {@code &ПсевдонимДля}, поэтому имя берётся из мета {@code &Желудь}
   * (отсутствует → имя класса). Имена, вычисляемые алиасом динамически, статика не
   * выводит — берётся статически объявленное значение, см. issue #3960.
   */
  public List<String> roleParameterValues(Annotation usage, String baseRole, String parameterName) {
    var values = new ArrayList<String>();
    collectFixedParameterValues(usage.getName(), baseRole, parameterName, new HashSet<>(), values);
    if (baseRole.equalsIgnoreCase(usage.getName())) {
      // Дословно, как движок annotations (ПолучитьЗначениеПараметраАннотации): переданный
      // параметр переносится как есть, в т.ч. пустым. Различие «передан/не передан»
      // (а не «пуст/не пуст») потребитель отражает через findFirst().orElse(fallback).
      AutumnAnnotations.stringParameter(usage, parameterName).ifPresent(values::add);
    }
    collectAliasedParameterValues(usage, baseRole, parameterName, values);
    return values;
  }

  /** Значения, проброшенные в параметр роли декларативно через {@code &ПсевдонимДля}. */
  private void collectAliasedParameterValues(Annotation usage, String baseRole, String parameterName,
                                             List<String> out) {
    definitionConstructor(usage.getName()).ifPresent(definition -> {
      for (var parameter : definition.getParameters()) {
        AutumnAnnotations.find(parameter.getAnnotations(), AutumnAnnotations.ALIAS_FOR)
          .filter(alias -> aliasTargets(alias, baseRole, parameterName))
          .flatMap(alias -> aliasedValue(usage, parameter, alias))
          .ifPresent(out::add);
      }
    });
  }

  /** Нацелен ли {@code &ПсевдонимДля} на параметр {@code parameterName} аннотации, разворачивающейся в роль. */
  private boolean aliasTargets(Annotation alias, String baseRole, String parameterName) {
    var targetParameter = AutumnAnnotations.stringParameter(alias, AutumnAnnotations.ALIAS_TARGET_PARAMETER);
    if (!targetParameter.filter(parameterName::equalsIgnoreCase).isPresent()) {
      return false;
    }
    return AutumnAnnotations.stringParameter(alias, AutumnAnnotations.ALIAS_TARGET_ANNOTATION)
      .filter(target -> isRole(target, baseRole))
      .isPresent();
  }

  /** Переносимое значение параметра-псевдонима: переданное явно либо значение по умолчанию (при опт-ин). */
  private static Optional<String> aliasedValue(Annotation usage, ParameterDefinition parameter, Annotation alias) {
    var passed = AutumnAnnotations.stringParameter(usage, parameter.getName());
    if (passed.isPresent()) {
      // Параметр передан явно — движок переносит его значение дословно, даже пустым;
      // значение по умолчанию при этом НЕ берётся.
      return passed;
    }
    // Параметр не передан — значение по умолчанию переносится только при опт-ине.
    if (transfersDefault(alias)) {
      return defaultStringValue(parameter);
    }
    return Optional.empty();
  }

  private static boolean transfersDefault(Annotation alias) {
    return AutumnAnnotations.stringParameter(alias, AutumnAnnotations.ALIAS_TRANSFER_DEFAULT)
      .filter(AutumnAnnotations.TRUE_LITERAL::equalsIgnoreCase)
      .isPresent();
  }

  /** Строковое значение по умолчанию параметра (со снятием обрамляющих кавычек), если оно строковое. */
  private static Optional<String> defaultStringValue(ParameterDefinition parameter) {
    var defaultValue = parameter.getDefaultValue();
    if (defaultValue.type() != ParameterDefinition.ParameterType.STRING) {
      return Optional.empty();
    }
    var text = defaultValue.value();
    if (text.length() >= 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
      text = text.substring(1, text.length() - 1).replace("\"\"", "\"");
    }
    return Optional.of(text);
  }

  /** Статически зафиксированные на аннотациях роли значения параметра в цепочке определения алиаса. */
  private void collectFixedParameterValues(String annotationName, String baseRole, String parameterName,
                                           Set<String> visited, List<String> out) {
    if (!visited.add(annotationName.toLowerCase(Locale.ROOT))) {
      return;
    }
    definitionConstructor(annotationName).ifPresent(definition -> {
      for (var meta : definition.getAnnotations()) {
        if (AutumnAnnotations.ANNOTATION_MARKER.equalsIgnoreCase(meta.getName())) {
          continue;
        }
        if (baseRole.equalsIgnoreCase(meta.getName())) {
          AutumnAnnotations.stringParameter(meta, parameterName).ifPresent(out::add);
        }
        collectFixedParameterValues(meta.getName(), baseRole, parameterName, visited, out);
      }
    });
  }

  private Optional<MethodSymbol> definitionConstructor(String annotationName) {
    return annotationRepository.findByName(annotationName)
      .flatMap(AnnotationSymbol::getParent)
      .filter(MethodSymbol.class::isInstance)
      .map(MethodSymbol.class::cast);
  }

  /**
   * Сброс кэша развёрнутых ролей. Замыкания зависят только от классов-определений
   * аннотаций (конструктор с {@code &Аннотация}), поэтому кэш сбрасывается лишь
   * когда меняется именно такой класс — как в {@code AutumnBeanIndex}; правки
   * прочих {@code .os}- и любых {@code .bsl}-документов кэш не трогают. Пересчёт
   * ролей ленивый, при следующем обращении.
   */
  @EventListener(ServerContextPopulatedEvent.class)
  public void invalidateOnContextPopulated() {
    roleClosureCache.clear();
  }

  @EventListener
  public void invalidateOnDocumentChange(DocumentContextContentChangedEvent event) {
    var document = event.getSource();
    if (document.getFileType() == FileType.OS && isAnnotationDefinition(document)) {
      roleClosureCache.clear();
    }
  }

  /**
   * Удаление документа: вклад в {@link AnnotationRepository} уже снят, поэтому
   * сбрасываем кэш на удаление любого {@code .os} (определением аннотации он мог
   * быть, а интроспектировать удалённый документ уже нельзя). Удаление — событие
   * редкое, лишний ленивый пересчёт некритичен.
   */
  @EventListener
  public void invalidateOnDocumentRemoved(ServerContextDocumentRemovedEvent event) {
    if (isOScriptFile(event.getUri())) {
      roleClosureCache.clear();
    }
  }

  /**
   * Является ли {@code .os}-документ классом-определением пользовательской
   * аннотации «ОСени»: помечен ли его конструктор {@code ПриСозданииОбъекта}
   * маркером {@code &Аннотация}.
   *
   * @param document проверяемый документ
   * @return {@code true}, если документ определяет пользовательскую аннотацию
   */
  public boolean isAnnotationDefinition(DocumentContext document) {
    return document.getSymbolTree().getConstructor()
      .map(constructor ->
        AutumnAnnotations.find(constructor.getAnnotations(), AutumnAnnotations.ANNOTATION_MARKER).isPresent())
      .orElse(false);
  }

  private static boolean isOScriptFile(URI uri) {
    return uri.toString().toLowerCase(Locale.ROOT).endsWith(".os");
  }

  private Set<String> roleClosure(String annotationName) {
    return roleClosureCache.computeIfAbsent(annotationName.toLowerCase(Locale.ROOT), (String key) -> {
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
      .ifPresent((MethodSymbol constructor) -> {
        for (var meta : constructor.getAnnotations()) {
          if (!AutumnAnnotations.ANNOTATION_MARKER.equalsIgnoreCase(meta.getName())) {
            collectRoles(meta.getName(), accumulator);
          }
        }
      });
  }
}
