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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Состав определяемых типов конфигурации.
 * <p>
 * Определяемый тип — не тип, а именованное описание типов: за именем
 * {@code ОпределяемыйТип.Сумма} стоит набор, поэтому собственного {@link TypeRef}
 * у него нет и в индексе алиасов ему места нет — состав живёт здесь.
 * <p>
 * Состав хранится <b>именами</b>, а не ссылками: определяемый тип читается из
 * метаданных наравне с прочими объектами, и то, на что он ссылается, к этому моменту
 * может быть ещё не зарегистрировано. Имена превращаются в типы на чтении.
 * <p>
 * Индекс конкурентный, внешней синхронизации не требует. Определяемые типы — свойство
 * конкретной конфигурации, поэтому индекс живёт по экземпляру на workspace.
 */
@Component
@WorkspaceScope
public class DefinedTypesIndex {

  /** Lowercased имя определяемого типа ↔ имена типов, из которых он собран. */
  private final Map<String, List<String>> compositions = new ConcurrentHashMap<>();

  /**
   * Запомнить состав определяемого типа.
   *
   * @param qualifiedName полное имя определяемого типа ({@code ОпределяемыйТип.Сумма}).
   * @param composition   имена типов, из которых он собран.
   */
  public void register(String qualifiedName, List<String> composition) {
    compositions.put(qualifiedName.toLowerCase(Locale.ROOT), List.copyOf(composition));
  }

  /**
   * Есть ли за этим именем определяемый тип.
   *
   * @param name имя типа.
   * @return {@code true}, если имя принадлежит определяемому типу.
   */
  public boolean knows(String name) {
    return compositions.containsKey(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Раскрыть имя определяемого типа в типы, из которых он собран. Определяемый тип
   * может ссылаться на другой определяемый — и на себя, поэтому уже раскрытые имена
   * запоминаются: иначе такая ссылка увела бы разбор в бесконечность.
   *
   * @param name    имя определяемого типа.
   * @param resolve поиск типа по имени.
   * @return состав; {@link TypeSet#EMPTY}, если имя не принадлежит определяемому типу.
   */
  public TypeSet compositionOf(String name, Function<String, TypeSet> resolve) {
    var key = name.toLowerCase(Locale.ROOT);
    var composition = compositions.get(key);
    if (composition == null) {
      return TypeSet.EMPTY;
    }
    var unfolded = new HashSet<String>();
    unfolded.add(key);
    return unfold(composition, unfolded, resolve);
  }

  private TypeSet unfold(List<String> composition, Set<String> unfolded, Function<String, TypeSet> resolve) {
    var result = TypeSet.EMPTY;
    for (var name : composition) {
      var key = name.toLowerCase(Locale.ROOT);
      var nested = compositions.get(key);
      if (nested == null) {
        result = result.union(resolve.apply(name));
        continue;
      }
      if (unfolded.add(key)) {
        result = result.union(unfold(nested, unfolded, resolve));
      }
    }
    return result;
  }
}
