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
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс метаданных членов для дешёвых pre-filter'ов диагностик: read-only
 * свойства (тип → имена и плоский набор имён) и версионные члены
 * ({@code sinceVersion} / {@code deprecatedSinceVersion}). Имена хранятся в
 * lowercased виде для регистронезависимого поиска; все lookup'ы — O(1)
 * (вызываются как pre-filter на каждом сайте обращения/присваивания).
 * <p>
 * Заполняется при регистрации {@link TypePackProvider.TypeDecl} провайдерами
 * платформенных типов (bsl-context / JSON-fallback); конфигурационные MD-типы
 * сюда не попадают (у них нет accessMode/версий).
 */
@Component
@WorkspaceScope
public class MemberMetadataIndex {

  private final Map<TypeRef, Set<String>> readOnlyByType = new ConcurrentHashMap<>();
  private final Set<String> readOnlyNames = ConcurrentHashMap.newKeySet();
  private final Set<String> versionedNames = ConcurrentHashMap.newKeySet();

  public void index(TypeRef ref, MemberDescriptor member) {
    var versioned = member.metadata().hasVersionInfo();
    var readOnly = member.metadata().accessMode() == AccessMode.READ;
    if (!versioned && !readOnly) {
      return;
    }
    var readOnlyOfType = readOnly
      ? readOnlyByType.computeIfAbsent(ref, k -> ConcurrentHashMap.newKeySet())
      : Set.<String>of();
    var bn = member.bilingualName();
    indexLocale(bn.ru(), versioned, readOnly, readOnlyOfType);
    indexLocale(bn.en(), versioned, readOnly, readOnlyOfType);
  }

  /** Регистрирует одно из двух написаний имени (ru/en) во всех релевантных индексах. */
  private void indexLocale(String name, boolean versioned, boolean readOnly,
                           Set<String> readOnlyOfType) {
    if (name.isEmpty()) {
      return;
    }
    var lc = name.toLowerCase(Locale.ROOT);
    if (versioned) {
      versionedNames.add(lc);
    }
    if (readOnly) {
      readOnlyNames.add(lc);
      readOnlyOfType.add(lc);
    }
  }

  public boolean hasAnyReadOnly() {
    return !readOnlyNames.isEmpty();
  }

  public boolean isReadOnlyName(String name) {
    return readOnlyNames.contains(name.toLowerCase(Locale.ROOT));
  }

  public boolean isReadOnly(TypeRef typeRef, String name) {
    var names = readOnlyByType.get(typeRef);
    return names != null && names.contains(name.toLowerCase(Locale.ROOT));
  }

  public boolean isVersionedName(String name) {
    return versionedNames.contains(name.toLowerCase(Locale.ROOT));
  }
}
