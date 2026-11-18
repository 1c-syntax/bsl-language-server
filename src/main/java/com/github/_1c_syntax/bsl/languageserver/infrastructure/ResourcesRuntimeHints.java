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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Подсказки для native-image: ресурсы и resource bundle'ы проекта.
 * <p>
 * Ресурсы проекта (локализация, описания диагностик, справочники) регистрируются по маскам.
 * Resource bundle'ы находятся на classpath во время AOT-обработки по файлам локализаций, поэтому
 * новые бандлы регистрируются без правки этого класса.
 */
public class ResourcesRuntimeHints implements RuntimeHintsRegistrar {

  private static final String ROOT = "com/github/_1c_syntax/bsl/languageserver";
  private static final List<String> RESOURCE_EXTENSIONS = List.of("properties", "md", "json");
  private static final Pattern BUNDLE_FILE = Pattern.compile("(" + ROOT + "/.+)_(ru|en)\\.properties$");

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    RESOURCE_EXTENSIONS.forEach(extension -> hints.resources().registerPattern(ROOT + "/**/*." + extension));
    bundleBaseNames(classLoader).forEach(baseName -> hints.resources().registerResourceBundle(baseName));
  }

  private static Set<String> bundleBaseNames(@Nullable ClassLoader classLoader) {
    var resolver = new PathMatchingResourcePatternResolver(classLoader);
    try {
      return Arrays.stream(resolver.getResources("classpath*:" + ROOT + "/**/*.properties"))
        .map(ResourcesRuntimeHints::path)
        .map(BUNDLE_FILE::matcher)
        .filter(Matcher::find)
        .map(matcher -> matcher.group(1).replace('/', '.'))
        .collect(Collectors.toCollection(TreeSet::new));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot find resource bundles", e);
    }
  }

  private static String path(Resource resource) {
    try {
      return resource.getURL().getPath();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot resolve resource " + resource, e);
    }
  }
}
