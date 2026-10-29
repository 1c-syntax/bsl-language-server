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
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Collection;

/** Контексты метода и наблюдавшиеся пути вызова; состояние одного пункта completion. */
final class CompletionMethodHint {
  private enum CallPath {LOCAL, SERVER, UNKNOWN, UNAVAILABLE}
  private final Set<ExecutionContext> contexts = EnumSet.noneOf(ExecutionContext.class);
  private final Set<CallPath> paths = EnumSet.noneOf(CallPath.class);
  private boolean knownContexts = true;

  void include(Collection<ExecutionContext> declared) { contexts.addAll(declared); }
  void unknownContexts() { knownContexts = false; }
  void unknownCall() { paths.add(CallPath.UNKNOWN); }
  void unavailableCall() { paths.add(CallPath.UNAVAILABLE); }
  void knownCall(boolean server) { paths.add(server ? CallPath.SERVER : CallPath.LOCAL); }
  void unknownReceiver() { unknownContexts(); unknownCall(); }

  /** В списке остаётся серверная стрелка, а пояснение добавляется после документации. */
  String apply(CompletionItem item, Language language, boolean labelDetailsSupport) {
    var explanation = new ArrayList<String>();
    if (knownContexts) {
      explanation.add(tr(language, "contexts").formatted(contextNames(language)));
    }
    appendCallDescription(explanation, language);
    if (paths.contains(CallPath.SERVER)) {
      var label = tr(language, paths.size() > 1 ? "possibleServerCall" : "serverCall");
      if (labelDetailsSupport) {
        prependServerLabel(item, label);
      } else {
        appendServerDetail(item, label);
      }
    }
    return String.join("\n\n", explanation);
  }

  private String contextNames(Language language) {
    if (contexts.isEmpty()) {
      return tr(language, "noContexts");
    }
    return contexts.stream().filter(context -> !context.isFileMode() || !contexts.contains(context.optionContext()))
      .map(context -> tr(language, "context." + context.name())).distinct().collect(Collectors.joining(", "));
  }

  private void appendCallDescription(List<String> explanation, Language language) {
    if (paths.contains(CallPath.SERVER)) {
      explanation.add(tr(language, paths.size() > 1 ? "possibleServerCall.detail" : "serverCall.detail"));
    } else if (only(CallPath.UNAVAILABLE)) {
      explanation.add(tr(language, "unavailableCall.detail"));
    } else if (only(CallPath.LOCAL)) {
      explanation.add(tr(language, "localCall.detail"));
    } else {
      // Смешанные или неизвестные пути не доказывают локальный вызов либо запрет.
    }
  }

  private boolean only(CallPath path) {
    return paths.size() == 1 && paths.contains(path);
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

  private static String tr(Language language, String key) {
    return Resources.getResourceString(language, CompletionContextRanker.class, key);
  }
}
