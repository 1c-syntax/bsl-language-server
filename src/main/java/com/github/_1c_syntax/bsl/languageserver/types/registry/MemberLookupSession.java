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

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Поиск имён по неизменяемым снимкам членов только на время одной задачи анализа. */
public final class MemberLookupSession {
  private static final int DIRECT_LOOKUP_LIMIT = 8;
  private static final ThreadLocal<MemberLookupSession> CURRENT = new ThreadLocal<>();
  private final Map<Collection<MemberDescriptor>, Map<Key, List<MemberDescriptor>>> snapshots = new IdentityHashMap<>();

  private record Key(MemberKind kind, String name) { }

  /** Открыть сессию задачи, если внешняя задача ещё не открыла свою; иначе вернуть {@code null}. */
  public static @Nullable Scope openIfAbsent() {
    return CURRENT.get() == null ? new MemberLookupSession().open() : null;
  }

  /** Установить общую сессию в потоке одной диагностической задачи. */
  public Scope open() {
    var previous = CURRENT.get();
    CURRENT.set(this);
    return new Scope(previous);
  }

  /** Восстанавливает сессию внешней задачи при вложенном вызове. */
  public static final class Scope implements AutoCloseable {
    private final @Nullable MemberLookupSession previous;

    private Scope(@Nullable MemberLookupSession previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) {
        CURRENT.remove();
      } else {
        CURRENT.set(previous);
      }
    }
  }

  /**
   * Все совпадения в исходном порядке; значения типов здесь не вычисляются.
   *
   * @param members неизменяемый снимок реестра.
   * @param kind вид члена.
   * @param name написание имени из запроса.
   * @return совпавшие дескрипторы.
   */
  public static List<MemberDescriptor> lookup(Collection<MemberDescriptor> members, MemberKind kind, String name) {
    var session = CURRENT.get();
    if (session == null || members.size() <= DIRECT_LOOKUP_LIMIT) {
      return select(members, kind, name);
    }
    Map<Key, List<MemberDescriptor>> names;
    synchronized (session.snapshots) {
      names = session.snapshots.computeIfAbsent(members, ignored -> new ConcurrentHashMap<>());
    }
    var key = new Key(kind, name);
    var ready = names.get(key);
    if (ready != null) {
      return ready;
    }
    // Дескрипторы отбираются вне замков. Другой поток может параллельно выполнить
    // первый отбор, но все последующие запросы используют готовый неизменяемый список.
    var selected = select(members, kind, name);
    var previous = names.putIfAbsent(key, selected);
    return previous == null ? selected : previous;
  }

  /**
   * Кандидаты для прохода, который сам проверяет имя и вид. Вне диагностической
   * сессии и для короткого списка сохраняется исходная коллекция без лишнего отбора.
   *
   * @param members неизменяемый снимок реестра.
   * @param kind требуемый вид.
   * @param name написание имени из запроса.
   * @return исходный снимок либо уже отобранные кандидаты.
   */
  public static Collection<MemberDescriptor> candidates(Collection<MemberDescriptor> members, MemberKind kind, String name) {
    return members.size() <= DIRECT_LOOKUP_LIMIT || CURRENT.get() == null ? members : lookup(members, kind, name);
  }

  /** Первый член с прежней семантикой регистра и двуязычных имён. */
  public static Optional<MemberDescriptor> first(Collection<MemberDescriptor> members, MemberKind kind, String name) {
    if (members.size() > DIRECT_LOOKUP_LIMIT && CURRENT.get() != null) {
      var found = lookup(members, kind, name);
      return found.isEmpty() ? Optional.empty() : Optional.of(found.getFirst());
    }
    for (var member : members) {
      if (member.kind() == kind && member.matches(name)) {
        return Optional.of(member);
      }
    }
    return Optional.empty();
  }

  private static List<MemberDescriptor> select(Collection<MemberDescriptor> members, MemberKind kind, String name) {
    var result = new ArrayList<MemberDescriptor>();
    for (var member : members) {
      if (member.kind() == kind && member.matches(name)) {
        result.add(member);
      }
    }
    return List.copyOf(result);
  }
}
