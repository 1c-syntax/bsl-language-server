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
package com.github._1c_syntax.bsl.languageserver.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.NotFileFilter;
import org.apache.commons.io.filefilter.OrFileFilter;
import org.apache.commons.io.filefilter.PathMatcherFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Исключение путей по списку паттернов.
 * <p>
 * Поддерживаются простые имена сегментов (например, {@code .git}, {@code node_modules})
 * и glob-паттерны ({@code **\/.git/**}, {@code build/}, {@code *.tmp}).
 * Glob без префикса {@code **\/} или {@code /} автоматически дополняется до
 * {@code **\/<pattern>} — чтобы матчить на любой глубине абсолютного пути.
 */
@Slf4j
@UtilityClass
public class PathExclusionUtils {

  /** Суффикс «совпадает с любым потомком» в glob-паттерне (например, в {@code **\/.git/**}). */
  private static final String DESCENDANTS_SUFFIX = "/**";

  /**
   * Пара фильтров для {@code FileUtils.listFiles}: {@code true} — путь оставить.
   *
   * @param directoryFilter фильтр директорий
   * @param fileFilter      фильтр файлов
   */
  public record ExclusionFilters(IOFileFilter directoryFilter, IOFileFilter fileFilter) {
    /** Никого не исключать. */
    public static final ExclusionFilters NONE =
      new ExclusionFilters(TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
  }

  /**
   * Разовая проверка, попадает ли путь под исключения.
   * Для массового обхода каталога используйте {@link #filters(List)}.
   */
  public static boolean isExcluded(Path path, @Nullable List<String> patterns) {
    var exclusions = filters(patterns);
    if (exclusions == ExclusionFilters.NONE) {
      return false;
    }
    var file = path.toFile();
    return !exclusions.directoryFilter().accept(file)
      || !exclusions.fileFilter().accept(file);
  }

  /**
   * Строит фильтры для каталогов и для файлов, разделяя их так,
   * чтобы directory-only паттерны обрезали обход целыми поддеревьями.
   * Пустые/blank паттерны и невалидные glob игнорируются (с предупреждением в лог).
   */
  public static ExclusionFilters filters(@Nullable List<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return ExclusionFilters.NONE;
    }

    var dirExcluders = new ArrayList<IOFileFilter>();
    var fileExcluders = new ArrayList<IOFileFilter>();

    for (var raw : patterns) {
      if (raw == null || raw.isBlank()) {
        continue;
      }
      addExcluders(raw.trim().replace('\\', '/'), dirExcluders, fileExcluders);
    }

    if (dirExcluders.isEmpty() && fileExcluders.isEmpty()) {
      return ExclusionFilters.NONE;
    }

    return new ExclusionFilters(notExcludedFilter(dirExcluders), notExcludedFilter(fileExcluders));
  }

  /**
   * Классифицирует {@code pattern} по типу и добавляет соответствующий
   * {@link IOFileFilter} в нужный список исключений (для каталогов и/или для файлов).
   */
  private static void addExcluders(
    String pattern,
    List<IOFileFilter> dirExcluders,
    List<IOFileFilter> fileExcluders
  ) {
    // Простое имя сегмента — применяем и к каталогам, и к файлам.
    if (!pattern.contains("/") && !pattern.contains("*")) {
      var simpleName = new SegmentFileFilter(pattern);
      dirExcluders.add(simpleName);
      fileExcluders.add(simpleName);
      return;
    }

    // "build/" — только каталог.
    if (pattern.endsWith("/")) {
      tryGlobFilter(pattern.substring(0, pattern.length() - 1)).ifPresent(dirExcluders::add);
      return;
    }

    // "**/.git/**" — каталог обрезаем по короткой форме (".git"),
    // файлы — оригинальным паттерном на случай, если directory-фильтр уже пропустил.
    if (pattern.endsWith(DESCENDANTS_SUFFIX)) {
      tryGlobFilter(pattern.substring(0, pattern.length() - DESCENDANTS_SUFFIX.length()))
        .ifPresent(dirExcluders::add);
      tryGlobFilter(pattern).ifPresent(fileExcluders::add);
      return;
    }

    tryGlobFilter(pattern).ifPresent((IOFileFilter filter) -> {
      dirExcluders.add(filter);
      fileExcluders.add(filter);
    });
  }

  /**
   * Компилирует {@code glob} в {@link PathMatcherFileFilter}, дополняя префиксом
   * {@code **\/} для матчинга на любой глубине. Невалидные паттерны пропускаются
   * с предупреждением в лог.
   */
  private static Optional<IOFileFilter> tryGlobFilter(String glob) {
    var fullGlob = (glob.startsWith("**/") || glob.startsWith("/")) ? glob : ("**/" + glob);
    try {
      var matcher = FileSystems.getDefault().getPathMatcher("glob:" + fullGlob);
      return Optional.of(new PathMatcherFileFilter(forwardSlashAware(matcher)));
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Некорректный glob-паттерн исключения, пропуск: {}", glob, e);
      return Optional.empty();
    }
  }

  /**
   * На системах с {@code \\}-разделителем оборачивает {@code delegate} так, чтобы
   * входной путь предварительно приводился к виду с {@code /}-разделителем.
   * На Linux/macOS возвращает {@code delegate} как есть.
   */
  private static PathMatcher forwardSlashAware(PathMatcher delegate) {
    if ("/".equals(FileSystems.getDefault().getSeparator())) {
      return delegate;
    }
    return path -> delegate.matches(toForwardSlashPath(path));
  }

  /**
   * Пересобирает {@link Path} из его сегментов, объединённых через {@code /}.
   * Используется для приведения Windows-путей к виду, понимаемому glob-паттернами с {@code /}.
   */
  private static Path toForwardSlashPath(Path path) {
    if (path.getNameCount() == 0) {
      return path;
    }
    var names = new String[path.getNameCount()];
    for (var i = 0; i < names.length; i++) {
      names[i] = path.getName(i).toString();
    }
    return path.getFileSystem().getPath(String.join("/", names));
  }

  /**
   * Объединяет {@code excluders} через {@code OR} и инвертирует результат:
   * итоговый фильтр пропускает только те пути, которые не совпали ни с одним excluder.
   */
  private static IOFileFilter notExcludedFilter(List<IOFileFilter> excluders) {
    if (excluders.isEmpty()) {
      return TrueFileFilter.INSTANCE;
    }
    var excluded = excluders.size() == 1 ? excluders.get(0) : new OrFileFilter(excluders);
    return new NotFileFilter(excluded);
  }

  /** Фильтр-матчер по простому имени: путь принимается, если содержит сегмент с именем {@code name}. */
  private static final class SegmentFileFilter extends AbstractFileFilter {

    private final String name;

    /** @param name имя сегмента, по которому матчатся пути */
    private SegmentFileFilter(String name) {
      this.name = name;
    }

    @Override
    public boolean accept(File file) {
      var path = file.toPath();
      for (var i = 0; i < path.getNameCount(); i++) {
        if (path.getName(i).toString().equals(name)) {
          return true;
        }
      }
      return false;
    }
  }
}
