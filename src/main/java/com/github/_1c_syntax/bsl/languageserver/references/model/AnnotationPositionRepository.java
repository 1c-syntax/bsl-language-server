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
package com.github._1c_syntax.bsl.languageserver.references.model;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Снимки позиций аннотаций и их параметров в документах одной рабочей области. */
@Repository
@WorkspaceScope
public class AnnotationPositionRepository {
  private final Map<URI, Snapshot> snapshots = new ConcurrentHashMap<>();

  /**
   * Заменить снимок документа. Список позиций копируется и не зависит от дальнейших изменений исходного списка.
   *
   * @param document документ, которому принадлежат позиции.
   * @param positions позиции аннотаций и их параметров.
   */
  public void replace(DocumentContext document, List<AnnotationPosition> positions) {
    snapshots.put(document.getUri(), new Snapshot(document.getSymbolTree().getModule(), List.copyOf(positions)));
  }

  /**
   * Обновить модуль существующего снимка, сохранив позиции неизменённого текста.
   *
   * @param document документ с прежним текстом и актуальным деревом символов.
   */
  public void refreshModule(DocumentContext document) {
    snapshots.computeIfPresent(document.getUri(), (uri, snapshot) ->
      new Snapshot(document.getSymbolTree().getModule(), snapshot.positions()));
  }

  /**
   * Проверить наличие снимка, в том числе снимка без аннотаций.
   *
   * @param uri URI документа.
   * @return есть ли сохранённый снимок документа.
   */
  public boolean contains(URI uri) {
    return snapshots.containsKey(uri);
  }

  /**
   * Найти первое вхождение, содержащее указанную позицию.
   *
   * @param uri URI документа.
   * @param position позиция обращения.
   * @return позиция аннотации вместе с модулем из того же снимка либо пустой результат.
   */
  public Optional<Match> find(URI uri, Position position) {
    var snapshot = snapshots.get(uri);
    return snapshot == null ? Optional.empty() : snapshot.positions().stream()
      .filter(candidate -> Ranges.containsPosition(candidate.range(), position))
      .findFirst()
      .map(candidate -> new Match(snapshot.module(), candidate));
  }

  /**
   * Удалить снимок документа.
   *
   * @param uri URI удаляемого документа.
   */
  public void removeByUri(URI uri) {
    snapshots.remove(uri);
  }

  /** Диапазон обращения, диапазон выделения и имя аннотации с необязательным именем параметра. */
  public record AnnotationPosition(
    Range range, Range selectionRange, String annotationName, @Nullable String parameterName
  ) {
  }

  /** Вхождение и его модуль, прочитанные из одного снимка. */
  public record Match(ModuleSymbol module, AnnotationPosition position) {
  }

  private record Snapshot(ModuleSymbol module, List<AnnotationPosition> positions) {
  }
}
