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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.types.oscript.ConventionalLibraryDiscovery.ConventionalLibrary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Обход дерева workspace в поисках OneScript-библиотек — ровно ОДИН проход по
 * файловой системе.
 * <p>
 * Раньше дерево обходилось дважды (отдельный поиск {@code lib.config} и отдельный
 * convention-обход); на больших конфигурациях каждый обход стоил секунды. Здесь
 * каждый каталог читается один раз ({@link DirContents}), и по этому листингу
 * сканер:
 * <ul>
 *   <li>находит {@code lib.config}-манифесты (на всю глубину {@value #MAX_DEPTH},
 *       спуск ими не прерывается — находятся и вложенные);</li>
 *   <li>для остальных каталогов спрашивает {@link ConventionalLibraryDiscovery},
 *       не является ли каталог convention/flat-библиотекой.</li>
 * </ul>
 * Решение, где НЕ применять convention-распознавание, принимает сам сканер (он и
 * так видит {@code lib.config} в листинге): каталог с {@code lib.config} и всё его
 * поддерево исключаются из convention/flat (их индексирует lib.config-путь), а
 * найденная convention/flat-библиотека считается завершённой — глубже как
 * отдельные библиотеки не регистрируются (но спуск продолжается ради вложенных
 * {@code lib.config}). Поэтому {@link ConventionalLibraryDiscovery} про
 * {@code lib.config} знать не нужно.
 */
@Component
@RequiredArgsConstructor
public class OScriptLibraryScanner {

  /** Максимальная глубина обхода. */
  static final int MAX_DEPTH = 6;

  private final OScriptLibraryRootResolver rootResolver;
  private final ConventionalLibraryDiscovery conventionalLibraryDiscovery;

  /**
   * Результат обхода: найденные {@code lib.config}-манифесты и
   * convention/flat-библиотеки.
   *
   * @param libConfigs             абсолютные нормализованные пути к {@code lib.config}
   * @param conventionalLibraries  convention/flat-библиотеки (без тех, что управляются
   *                               {@code lib.config})
   */
  public record ScanResult(List<Path> libConfigs, List<ConventionalLibrary> conventionalLibraries) {
  }

  /**
   * Обойти дерево workspace один раз.
   *
   * @param serverContext workspace-контекст
   * @return найденные манифесты и convention/flat-библиотеки
   */
  public ScanResult scan(ServerContext serverContext) {
    var libConfigs = new LinkedHashSet<Path>();
    var conventional = new ArrayList<ConventionalLibrary>();
    var visited = new HashSet<Path>();
    for (var root : rootResolver.getRoots(serverContext)) {
      if (Files.isDirectory(root)) {
        walk(root, visited, libConfigs, conventional, 0, false);
      }
    }
    return new ScanResult(new ArrayList<>(libConfigs), conventional);
  }

  /**
   * {@code suppressed} — текущее поддерево уже «занято» (лежит под
   * {@code lib.config}-каталогом или под уже зарегистрированной
   * convention/flat-библиотекой): convention/flat здесь не распознаём, но спуск
   * продолжаем ради вложенных {@code lib.config}.
   */
  private void walk(Path dir, Set<Path> visited, Set<Path> libConfigs,
                    List<ConventionalLibrary> conventional, int depth, boolean suppressed) {
    var normalized = dir.toAbsolutePath().normalize();
    if (!visited.add(normalized)) {
      return;
    }

    var contents = DirContents.read(normalized);
    // lib.config собирается на всю глубину MAX_DEPTH (файл лежит на уровне depth+1).
    if (contents.libConfig() != null && depth < MAX_DEPTH) {
      libConfigs.add(contents.libConfig());
    }

    // Каталог с lib.config (и всё его поддерево) не участвует в convention/flat —
    // его индексирует lib.config-путь.
    var conventionallySuppressed = suppressed || contents.libConfig() != null;
    var childSuppressed = conventionallySuppressed;
    var flatAtRoot = false;

    if (!conventionallySuppressed) {
      var classification = conventionalLibraryDiscovery.classify(normalized, contents);
      var library = classification.library();
      if (library != null) {
        conventional.add(library);
        if (classification.kind() == ConventionalLibraryDiscovery.Kind.CONVENTION) {
          // convention-каталог (Классы/Модули) — завершённая библиотека.
          childSuppressed = true;
        } else if (depth > 0) {
          // flat-каталог на вложенном уровне — тоже завершённая библиотека.
          childSuppressed = true;
        } else {
          // flat на корневом уровне: рядом может лежать каталог-библиотека,
          // подключаемая относительным #Использовать "<dir>", — обход не прерываем.
          flatAtRoot = true;
        }
      }
    }

    if (depth >= MAX_DEPTH) {
      return;
    }

    for (var child : contents.subdirs().entrySet()) {
      var name = child.getKey();
      // Не заходим в oscript_modules уже обходимого каталога — транзитивные
      // зависимости не должны переоткрываться как отдельные библиотеки. Корневой
      // workspace/oscript_modules/<lib> обрабатывается отдельно через
      // OScriptLibraryRootResolver (как отдельный корень).
      if (OScriptLibraryRootResolver.OSCRIPT_MODULES_DIRNAME.equals(name)) {
        continue;
      }
      // src flat-каталога уровня 0 уже включён в flat-библиотеку — как отдельную
      // библиотеку его не распознаём, но спускаемся ради вложенных lib.config.
      var childSup = childSuppressed || (flatAtRoot && ConventionalLibraryDiscovery.SRC_PREFIX.equals(name));
      walk(child.getValue(), visited, libConfigs, conventional, depth + 1, childSup);
    }
  }
}
