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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.completion.CompletionMethodHint.Description;
import com.github._1c_syntax.bsl.languageserver.completion.CompletionMethodHint.DescriptionKey;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Форматирование пояснений completion; изменяемые кеши принадлежат одному вызову. */
@Component
public final class CompletionMethodHintRenderer {

  /**
   * Применить приоритеты и пояснения, сохранив имя, сигнатуру и текст вставки.
   *
   * @param ranking оценки и контексты текущего запроса.
   * @param items отобранные предложения, включая варианты без ограничений.
   * @param labelDetailsSupport поддерживает ли клиент подпись рядом с сигнатурой.
   * @return пояснения для документации; ключи сравниваются по идентичности пунктов.
   */
  public Map<CompletionItem, String> apply(CompletionContextRanker ranking, Iterable<CompletionItem> items,
                                          boolean labelDetailsSupport) {
    var language = ranking.language();
    Map<DescriptionKey, Description> descriptions = new HashMap<>();
    Map<CompletionItem, String> result = new IdentityHashMap<>();
    for (var item : items) {
      ranking.applyPriority(item);
      var hint = ranking.hint(item);
      if (hint != null) {
        var description = descriptions.computeIfAbsent(hint.descriptionKey(), ignored -> hint.render(language));
        var text = apply(description, item, labelDetailsSupport);
        if (!text.isBlank()) {
          result.put(item, text);
        }
      }
    }
    return result;
  }

  String apply(CompletionMethodHint hint, CompletionItem item, Language language, boolean labelDetailsSupport) {
    return apply(hint.render(language), item, labelDetailsSupport);
  }

  private static String apply(Description description, CompletionItem item, boolean labelDetailsSupport) {
    var label = description.serverLabel();
    if (label != null) {
      if (labelDetailsSupport) {
        prependServerLabel(item, label);
      } else {
        appendServerDetail(item, label);
      }
    }
    return description.text();
  }

  private static void prependServerLabel(CompletionItem item, String serverCall) {
    var labels = item.getLabelDetails();
    if (labels == null) {
      labels = new CompletionItemLabelDetails();
      item.setLabelDetails(labels);
    }
    var type = labels.getDescription();
    if (type == null || type.isBlank()) {
      labels.setDescription(serverCall);
    } else {
      labels.setDescription(serverCall + " · " + type);
    }
  }

  private static void appendServerDetail(CompletionItem item, String serverCall) {
    var detail = item.getDetail();
    if (detail == null || detail.isBlank()) {
      item.setDetail(serverCall);
    } else {
      item.setDetail(detail + " · " + serverCall);
    }
  }
}
