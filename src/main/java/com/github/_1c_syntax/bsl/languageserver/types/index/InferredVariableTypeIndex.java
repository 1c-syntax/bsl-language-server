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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.events.ConfigurationTypesRegisteredEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш выведенных типов переменных, разрезанный по URI документа.
 * <p>
 * Заполняется <b>лениво</b> из {@code ExpressionTypeInferencer.inferVariable}:
 * выведенный тип переменной мемоизируется, чтобы повторные обращения к ней
 * (на каждый член-доступ {@code Переменная.X} в документе) не переинферивались
 * с нуля. Без него полный проход семантических токенов по большому модулю
 * пересчитывает тип одного и того же ресивера сотни раз.
 * <p>
 * <b>Только инвалидация, никакого жадного наполнения:</b> на старте
 * {@code populateContext} пересобирает все документы, и AOP шлёт событие изменения
 * содержимого на каждый из них — обработчик лишь чистит соответствующий URI
 * (дешёвый no-op по пустому бакету), но не наполняет кэш. Реальное наполнение
 * происходит только при настоящих запросах инференса (семантические токены / hover
 * / диагностики по активным документам).
 * <p>
 * Ключ — сам {@link VariableSymbol}: его equals включает имя + документ +
 * позицию имени, поэтому одноимённые переменные из разных областей видимости —
 * разные ключи (коллизий по scope нет).
 * <p>
 * Инвалидация — per-URI через {@link AbstractDocumentLifecycleClearableIndex}
 * (изменение / очистка вторичных данных / закрытие / удаление документа удаляют
 * весь бакет этого URI). Кросс-документные зависимости типа (тип переменной зависит
 * от метода чужого модуля) при правке чужого модуля не сбрасываются — это та же
 * модель, что у {@link SymbolTypeIndex}.
 */
@Component
@WorkspaceScope
public class InferredVariableTypeIndex extends AbstractDocumentLifecycleClearableIndex {

  private final Map<URI, Map<VariableSymbol, TypeSet>> typesByUri = new ConcurrentHashMap<>();

  /**
   * Кэшированный тип переменной либо {@code null}, если ещё не вычислялся.
   *
   * @param variable переменная.
   * @return выведенный тип или {@code null} при промахе кэша.
   */
  @Nullable
  public TypeSet get(VariableSymbol variable) {
    var byVariable = typesByUri.get(uriOf(variable));
    return byVariable == null ? null : byVariable.get(variable);
  }

  /**
   * Запомнить выведенный тип переменной.
   *
   * @param variable переменная.
   * @param types    выведенный тип.
   */
  public void put(VariableSymbol variable, TypeSet types) {
    typesByUri.computeIfAbsent(uriOf(variable), k -> new ConcurrentHashMap<>()).put(variable, types);
  }

  /**
   * Удалить кэш по URI документа.
   *
   * @param uri URI документа.
   */
  @Override
  public void clear(URI uri) {
    typesByUri.remove(uri);
  }

  /**
   * Полный сброс кэша после регистрации конфигурационных типов: типы
   * параметров обработчиков платформенных событий разрешаются через
   * {@code EventContractsIndex}, который до регистрации возвращал пусто
   * (owner-тип модуля не в реестре). Кэшированные «пустые» инференсы
   * параметров надо пересчитать с уже заполненным реестром.
   */
  @EventListener
  public void handleConfigurationTypesRegistered(ConfigurationTypesRegisteredEvent event) {
    typesByUri.clear();
  }

  private static URI uriOf(VariableSymbol variable) {
    return variable.getOwner().getUri();
  }
}
