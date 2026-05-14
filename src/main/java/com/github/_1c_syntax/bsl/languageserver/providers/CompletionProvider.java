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
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
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
import java.util.Locale;

/**
 * Провайдер для запросов {@code textDocument/completion}.
 * <p>
 * Поддерживает:
 * <ul>
 *   <li>dot-completion: после точки выводится union членов всех типов выражения слева;</li>
 *   <li>no-dot completion: глобальные функции, классы (в позиции после {@code Новый}),
 *       ключевые слова + локальные методы документа, отфильтрованные по префиксу.</li>
 * </ul>
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_completion">Completion Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class CompletionProvider {

  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * @return предложения автодополнения для указанной позиции
   */
  public List<CompletionItem> getCompletion(DocumentContext documentContext, CompletionParams params) {
    var position = params.getPosition();
    if (isDotCompletion(documentContext, position)) {
      return dotCompletion(documentContext, position);
    }
    return noDotCompletion(documentContext, position);
  }

  private List<CompletionItem> dotCompletion(DocumentContext documentContext, Position position) {
    // позиция выражения — символ перед точкой
    var beforeDot = new Position(position.getLine(), Math.max(0, position.getCharacter() - 2));
    var typeSet = typeService.findTypes(documentContext.getUri(), beforeDot);
    if (typeSet.isEmpty()) {
      typeSet = typeService.inferAtPosition(documentContext, beforeDot);
    }
    if (typeSet.isEmpty()) {
      // Fallback: голое имя OneScript library-модуля ("ИмяМодуля." без локального символа)
      var libModuleName = identifierBeforeDot(documentContext, position);
      if (libModuleName != null) {
        var libRef = globalScopeProvider.findLibraryModule(libModuleName);
        if (libRef.isPresent()) {
          typeSet = TypeSet.of(libRef.get());
        }
      }
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

  private static String identifierBeforeDot(DocumentContext documentContext, Position position) {
    try {
      var content = documentContext.getContent();
      if (content == null) {
        return null;
      }
      var lines = content.split("\\R", -1);
      if (position.getLine() >= lines.length) {
        return null;
      }
      var line = lines[position.getLine()];
      var col = Math.min(position.getCharacter(), line.length());
      var i = col;
      while (i > 0 && isIdentChar(line.charAt(i - 1))) {
        i--;
      }
      if (i == 0 || line.charAt(i - 1) != '.') {
        return null;
      }
      var dotIndex = i - 1;
      var start = dotIndex;
      while (start > 0 && isIdentChar(line.charAt(start - 1))) {
        start--;
      }
      if (start == dotIndex) {
        return null;
      }
      return line.substring(start, dotIndex);
    } catch (Exception e) {
      return null;
    }
  }

  private List<CompletionItem> noDotCompletion(DocumentContext documentContext, Position position) {
    var lineInfo = currentLineInfo(documentContext, position);
    if (lineInfo == null) {
      return List.of();
    }
    var prefix = lineInfo.prefix.toLowerCase(Locale.ROOT);
    var afterNew = isAfterNew(lineInfo.line, lineInfo.cursor - lineInfo.prefix.length());

    var items = new ArrayList<CompletionItem>();

    if (afterNew) {
      for (var className : globalScopeProvider.getClasses()) {
        if (matches(className, prefix)) {
          var item = new CompletionItem(className);
          item.setKind(CompletionItemKind.Class);
          items.add(item);
        }
      }
      for (var libClassName : globalScopeProvider.getLibraryClasses()) {
        if (matches(libClassName, prefix)) {
          var item = new CompletionItem(libClassName);
          item.setKind(CompletionItemKind.Class);
          items.add(item);
        }
      }
      return items;
    }

    // OneScript library namespaces (записи <module> из lib.config)
    for (var libModuleName : globalScopeProvider.getLibraryModules()) {
      if (matches(libModuleName, prefix)) {
        var item = new CompletionItem(libModuleName);
        item.setKind(CompletionItemKind.Module);
        items.add(item);
      }
    }

    // Global functions
    var seenFn = new java.util.HashSet<String>();
    for (var fn : globalScopeProvider.getFunctions()) {
      if (!seenFn.add(fn.name())) {
        continue;
      }
      if (matches(fn.name(), prefix)) {
        items.add(toCompletionItem(fn));
      }
    }

    // Local methods of current document
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (matches(method.getName(), prefix)) {
        var item = new CompletionItem(method.getName());
        item.setKind(method.isFunction() ? CompletionItemKind.Function : CompletionItemKind.Method);
        item.setInsertText(method.getName() + "(");
        items.add(item);
      }
    }

    // Local variables of current document
    for (var variable : documentContext.getSymbolTree().getVariables()) {
      if (matches(variable.getName(), prefix)) {
        var item = new CompletionItem(variable.getName());
        item.setKind(CompletionItemKind.Variable);
        items.add(item);
      }
    }

    // Keywords
    for (var keyword : globalScopeProvider.getKeywords()) {
      if (matches(keyword, prefix)) {
        var item = new CompletionItem(keyword);
        item.setKind(CompletionItemKind.Keyword);
        items.add(item);
      }
    }

    return items;
  }

  private static boolean matches(String name, String lowerPrefix) {
    if (lowerPrefix.isEmpty()) {
      return true;
    }
    return name.toLowerCase(Locale.ROOT).startsWith(lowerPrefix);
  }

  private static boolean isAfterNew(String line, int prefixStart) {
    var head = line.substring(0, Math.max(0, prefixStart)).stripTrailing().toLowerCase(Locale.ROOT);
    return head.endsWith("новый") || head.endsWith("new");
  }

  private static LineInfo currentLineInfo(DocumentContext documentContext, Position position) {
    try {
      var content = documentContext.getContent();
      if (content == null) {
        return null;
      }
      var lines = content.split("\\R", -1);
      if (position.getLine() >= lines.length) {
        return null;
      }
      var line = lines[position.getLine()];
      var col = Math.min(position.getCharacter(), line.length());
      var start = col;
      while (start > 0 && isIdentChar(line.charAt(start - 1))) {
        start--;
      }
      var prefix = line.substring(start, col);
      return new LineInfo(line, col, prefix);
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isIdentChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  private record LineInfo(String line, int cursor, String prefix) {
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
      // dot-completion also when typing prefix after dot: e.g. "x.Доб|" → walk back through ident
      var i = col;
      while (i > 0 && isIdentChar(line.charAt(i - 1))) {
        i--;
      }
      return i > 0 && line.charAt(i - 1) == '.';
    } catch (Exception e) {
      return false;
    }
  }

  private static CompletionItem toCompletionItem(MemberDescriptor member) {
    var item = new CompletionItem(member.name());
    if (member.kind() == MemberKind.METHOD) {
      item.setKind(CompletionItemKind.Function);
      item.setInsertText(member.name() + "(");
      if (member.signatures().size() > 1) {
        item.setDetail(member.signatures().size() + " вариантов синтаксиса");
      } else if (!member.description().isBlank()) {
        item.setDetail(member.description());
      }
    } else {
      item.setKind(CompletionItemKind.Variable);
      if (!member.description().isBlank()) {
        item.setDetail(member.description());
      }
    }
    return item;
  }

  private static List<CompletionItem> toCompletionItems(Collection<MemberDescriptor> members) {
    var items = new ArrayList<CompletionItem>(members.size());
    for (var member : members) {
      var item = new CompletionItem(member.name());
      if (member.kind() == MemberKind.METHOD) {
        item.setKind(CompletionItemKind.Method);
        item.setInsertText(member.name() + "(");
        if (member.signatures().size() > 1) {
          item.setDetail(member.signatures().size() + " вариантов синтаксиса");
        } else if (!member.description().isBlank()) {
          item.setDetail(member.description());
        }
      } else {
        item.setKind(CompletionItemKind.Property);
        if (!member.description().isBlank()) {
          item.setDetail(member.description());
        }
      }
      items.add(item);
    }
    return items;
  }
}
