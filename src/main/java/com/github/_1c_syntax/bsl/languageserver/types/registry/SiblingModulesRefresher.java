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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Перепроверка деревьев символов модулей, дающих члены одного типа.
 * <p>
 * У типа бывает несколько модулей: модуль заимствованного объекта есть и в основной
 * конфигурации, и в расширении. Дерево каждого зависит от членов типа, а значит, и от
 * экспортных переменных другого модуля: голое присваивание такой переменной — обращение к
 * реквизиту, а не новая переменная. Дерево, построенное раньше, чем другой модуль объявил
 * переменную, само не исправится: текст документа не менялся, и перечитывать его некому.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class SiblingModulesRefresher {

  @Qualifier("asyncEventListenerExecutor")
  private final AsyncTaskExecutor executor;

  /** Тип, членами которого становятся члены модуля, по URI модуля. */
  private final Map<URI, TypeRef> typeByModule = new ConcurrentHashMap<>();

  /** Модули — источники членов типа, по типу. */
  private final Map<TypeRef, Set<URI>> modulesByType = new ConcurrentHashMap<>();

  /**
   * Запоминает модуль источником членов типа; прежний тип модуля, если он был другим, модуль
   * больше не числит.
   *
   * @param uri URI модуля.
   * @param ref тип, членами которого становятся члены модуля.
   */
  public void remember(URI uri, TypeRef ref) {
    var prev = typeByModule.put(uri, ref);
    if (prev != null && !prev.equals(ref)) {
      var previousModules = modulesByType.get(prev);
      if (previousModules != null) {
        previousModules.remove(uri);
      }
    }
    modulesByType.computeIfAbsent(ref, key -> ConcurrentHashMap.newKeySet()).add(uri);
  }

  /**
   * Перепроверяет деревья символов других модулей того же типа: строит заново те, что устарели
   * ({@link com.github._1c_syntax.bsl.languageserver.context.ServerContext#refreshSymbolTree}).
   * <p>
   * Перепроверка идёт отдельной задачей: ей нужны блокировки чужих документов, а вызывающий
   * поток может держать блокировку своего — ожидание замкнулось бы на встречное. Цепочка
   * перепроверок конечна: перестроенное дерево даёт те же экспортные члены, и перепроверка
   * модулей, начатая им, ничего не меняет.
   *
   * @param documentContext модуль, члены которого могли измениться.
   * @param ref             тип, членами которого они становятся.
   */
  public void refreshSiblingsOf(DocumentContext documentContext, TypeRef ref) {
    var modules = modulesByType.get(ref);
    if (modules == null) {
      return;
    }
    var uri = documentContext.getUri();
    var siblings = modules.stream().filter(module -> !module.equals(uri)).toList();
    if (siblings.isEmpty()) {
      return;
    }
    var serverContext = documentContext.getServerContext();
    executor.execute(() -> siblings.forEach((URI sibling) -> {
      var siblingDocument = serverContext.getDocument(sibling);
      if (siblingDocument != null) {
        serverContext.refreshSymbolTree(siblingDocument);
      }
    }));
  }
}
