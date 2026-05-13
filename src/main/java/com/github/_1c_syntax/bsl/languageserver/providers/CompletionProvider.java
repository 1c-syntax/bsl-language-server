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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Провайдер для запросов {@code textDocument/completion}.
 * <p>
 * На текущий момент поддерживает только dot-completion: на позиции после
 * точки выводится union членов всех типов выражения слева. Сами типы
 * вычисляются {@link TypeService}, члены — {@link TypeService#getMembers}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_completion">Completion Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class CompletionProvider {

  private final TypeService typeService;

  /**
   * @return предложения автодополнения для указанной позиции
   */
  public List<CompletionItem> getCompletion(DocumentContext documentContext, CompletionParams params) {
    var position = params.getPosition();
    if (!isDotCompletion(documentContext, position)) {
      return List.of();
    }

    // позиция выражения — символ перед точкой
    var beforeDot = new Position(position.getLine(), Math.max(0, position.getCharacter() - 2));
    var typeSet = typeService.findTypes(documentContext.getUri(), beforeDot);
    if (typeSet.isEmpty()) {
      typeSet = typeService.inferAtPosition(documentContext, beforeDot);
    }
    if (typeSet.isEmpty()) {
      return List.of();
    }

    var members = new LinkedHashMap<String, MemberDescriptor>();
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref)) {
        members.putIfAbsent(member.name(), member);
      }
    }

    return toCompletionItems(members.values());
  }

  private static boolean isDotCompletion(DocumentContext documentContext, Position position) {
    try {
      var content = documentContext.getContent();
      if (content == null) {
        return false;
      }
      var lines = content.split("\\R", -1);
      if (position.getLine() >= lines.length) {
        return false;
      }
      var line = lines[position.getLine()];
      var col = Math.min(position.getCharacter(), line.length());
      return col > 0 && line.charAt(col - 1) == '.';
    } catch (Exception e) {
      return false;
    }
  }

  private static List<CompletionItem> toCompletionItems(Collection<MemberDescriptor> members) {
    var items = new ArrayList<CompletionItem>(members.size());
    for (var member : members) {
      var item = new CompletionItem(member.name());
      item.setKind(member.kind() == MemberKind.METHOD
        ? CompletionItemKind.Method
        : CompletionItemKind.Property);
      if (!member.description().isBlank()) {
        item.setDetail(member.description());
      }
      items.add(item);
    }
    return items;
  }
}
