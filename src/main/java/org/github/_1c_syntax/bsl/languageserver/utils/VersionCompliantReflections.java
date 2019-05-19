/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package org.github._1c_syntax.bsl.languageserver.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.reflections.ReflectionUtils;
import org.reflections.Reflections;
import org.reflections.scanners.Scanner;
import org.reflections.scanners.SubTypesScanner;

import javax.annotation.Nullable;

import static org.reflections.ReflectionUtils.forName;

public class VersionCompliantReflections extends Reflections {

  private static final String SCANNER_CLASS_NAME = SubTypesScanner.class.getSimpleName();

  public VersionCompliantReflections(final String prefix, @Nullable final Scanner... scanners) {
    super(prefix, scanners);
  }

  @Override
  public void expandSuperTypes() {
    if (store.keySet().contains(SCANNER_CLASS_NAME)) {
      Multimap<String, String> mmap = store.get(SCANNER_CLASS_NAME);
      Sets.SetView<String> keys = Sets.difference(mmap.keySet(), Sets.newHashSet(mmap.values()));
      Multimap<String, String> expand = HashMultimap.create();
      for (String key : keys) {
        final Class<?> type = forName(key);
        if (type != null) {
          doExpandSuperTypes(expand, key, type);
        }
      }
      mmap.putAll(expand);
    }
  }

  private void doExpandSuperTypes(Multimap<String, String> mmap, String key, Class<?> type) {
    for (Class<?> supertype : ReflectionUtils.getSuperTypes(type)) {
      if (mmap.put(supertype.getName(), key)) {
        if (log != null) {
          log.debug("expanded subtype {} -> {}", supertype.getName(), key);
        }
        doExpandSuperTypes(mmap, supertype.getName(), supertype);
      }
    }
  }
}
