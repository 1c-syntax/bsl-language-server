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
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.scope.UseDirectiveScanner;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
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
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final LanguageServerConfiguration configuration;

  /**
   * Из коллекции членов оставить только те, чьё имя соответствует
   * настроенному {@link Language}. Алиас-пары (Ru/En) определяются по
   * одинаковому fingerprint (kind + параметры + returnType): они есть в
   * платформенных JSON-ах OneScript. Для членов без пары имя оставляем
   * как есть (например, значения перечислений вида {@code UTF8}).
   */
  private Collection<MemberDescriptor> filterMembersByLanguage(Collection<MemberDescriptor> members) {
    if (members.isEmpty()) {
      return members;
    }
    // Свойства (включая значения перечислений) не группируем — у них нет сигнатуры,
    // по которой можно надёжно определить алиас. Например, UTF8/ANSI у КодировкаТекста
    // имеют одинаковый fingerprint, но семантически независимы.
    // Методы группируем по сигнатуре: алиас-пары Ru↔En имеют идентичную сигнатуру.
    var byFingerprint = new LinkedHashMap<String, List<MemberDescriptor>>();
    var passthrough = new ArrayList<MemberDescriptor>();
    for (var m : members) {
      if (m.kind() != MemberKind.METHOD || m.signatures().isEmpty()) {
        passthrough.add(m);
        continue;
      }
      byFingerprint.computeIfAbsent(memberFingerprint(m), k -> new ArrayList<>()).add(m);
    }
    var result = new ArrayList<MemberDescriptor>(members.size());
    result.addAll(passthrough);
    for (var group : byFingerprint.values()) {
      if (group.size() == 1) {
        result.add(group.get(0));
        continue;
      }
      MemberDescriptor pick = null;
      for (var m : group) {
        if (isInConfiguredLanguage(m.name())) {
          pick = m;
          break;
        }
      }
      result.add(pick != null ? pick : group.get(0));
    }
    return result;
  }

  private static String memberFingerprint(MemberDescriptor m) {
    var sb = new StringBuilder();
    sb.append(m.kind()).append('|');
    sb.append(m.returnType() == null ? "" : m.returnType().qualifiedName()).append('|');
    sb.append(m.signatures().size());
    for (var sig : m.signatures()) {
      sb.append('#').append(sig.parameters().size());
      for (var p : sig.parameters()) {
        sb.append(';').append(p.optional()).append(',');
        sb.append(p.types() == null ? "" : p.types().refs());
      }
    }
    return sb.toString();
  }

  /**
   * Фильтр для plain-имён ({@code List<String>}) — классы, ключевые слова,
   * глобальные свойства и т.п. Алиас-пары определяются по тому, что разные
   * имена резолвятся к одному global symbol.
   */
  private List<String> filterNamesByLanguage(Collection<String> names, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    if (names.isEmpty()) {
      return List.of();
    }
    var byTarget = new LinkedHashMap<Object, List<String>>();
    var bareKey = new Object();
    for (var name : names) {
      var symbol = globalScopeProvider.findGlobal(name, fileType);
      Object key = symbol.isPresent() ? symbol.get() : bareKey;
      byTarget.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
    }
    var result = new ArrayList<String>(names.size());
    for (var entry : byTarget.entrySet()) {
      var group = entry.getValue();
      if (entry.getKey() == bareKey || group.size() == 1) {
        result.addAll(group);
        continue;
      }
      String pick = null;
      for (var name : group) {
        if (isInConfiguredLanguage(name)) {
          pick = name;
          break;
        }
      }
      result.add(pick != null ? pick : group.get(0));
    }
    return result;
  }

  /**
   * Имя считается совместимым с настроенным {@link Language}, если оно
   * не содержит «чужих» букв. Эвристика: кириллица → RU, латиница → EN.
   * Имена, состоящие только из не-букв (служебные/составные), не фильтруются.
   * Локальные пользовательские символы фильтру не подлежат — у пользователя свой язык.
   */
  private boolean isInConfiguredLanguage(String name) {
    if (name == null || name.isEmpty()) {
      return true;
    }
    var lang = configuration.getLanguage();
    boolean hasCyrillic = false;
    boolean hasLatin = false;
    for (int i = 0; i < name.length(); i++) {
      var ch = name.charAt(i);
      if (Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC) {
        hasCyrillic = true;
      } else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
        hasLatin = true;
      }
    }
    if (!hasCyrillic && !hasLatin) {
      return true;
    }
    if (lang == Language.RU) {
      return hasCyrillic || !hasLatin;
    }
    return hasLatin || !hasCyrillic;
  }

  private List<String> libraryEntryNames(OScriptLibraryIndex.EntryKind kind) {
    return oScriptLibraryIndex.findEntries(kind).stream()
      .map(OScriptLibraryIndex.LibraryEntry::qualifiedName)
      .distinct()
      .toList();
  }

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
    var dotInfo = dotCompletionInfo(documentContext, position);
    if (dotInfo == null) {
      return List.of();
    }
    var fileType = documentContext.getFileType();
    // позиция выражения — символ перед точкой
    var beforeDot = new Position(position.getLine(), Math.max(0, dotInfo.dotColumn - 1));
    var typeSet = typeService.findTypes(documentContext.getUri(), beforeDot);
    if (typeSet.isEmpty()) {
      typeSet = typeService.inferAtPosition(documentContext, beforeDot);
    }
    if (typeSet.isEmpty()) {
      typeSet = rescueDanglingDotType(documentContext, position, dotInfo);
    }
    if (typeSet.isEmpty()) {
      return List.of();
    }

    var members = new LinkedHashMap<String, MemberDescriptor>();
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref, fileType)) {
        members.putIfAbsent(member.name(), member);
      }
      // Декларированные ключи «открытого» объекта данных (Структура из
      // Новый Структура("К1, К2"), ТЗ с описанными колонками из JsDoc).
      // Поля идут перед members такого же имени, чтобы пользовательские
      // ключи приоритетнее дефолтных алиасов.
      var localFields = typeSet.getLocalFields(ref);
      for (var entry : localFields.entrySet()) {
        var fieldName = entry.getKey();
        var fieldTypes = entry.getValue();
        var fieldRef = fieldTypes.refs().stream().findFirst().orElse(null);
        members.putIfAbsent(fieldName, MemberDescriptor.property(fieldName, fieldRef, ""));
      }
    }

    var prefix = dotInfo.prefix.toLowerCase(Locale.ROOT);
    var filtered = filterMembersByLanguage(members.values()).stream()
      .filter(m -> matches(m.name(), prefix))
      .toList();
    return toCompletionItems(filtered);
  }

  private static DotCompletionInfo dotCompletionInfo(DocumentContext documentContext, Position position) {
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
      return new DotCompletionInfo(i - 1, line.substring(i, col));
    } catch (Exception e) {
      return null;
    }
  }

  private record DotCompletionInfo(int dotColumn, String prefix) {
  }

  private List<CompletionItem> noDotCompletion(DocumentContext documentContext, Position position) {
    var lineInfo = currentLineInfo(documentContext, position);
    if (lineInfo == null) {
      return List.of();
    }
    var prefix = lineInfo.prefix.toLowerCase(Locale.ROOT);
    var afterNew = isAfterNew(lineInfo.line, lineInfo.cursor - lineInfo.prefix.length());
    var fileType = documentContext.getFileType();

    var items = new ArrayList<CompletionItem>();

    // Per-document #Использовать gating. Строгая семантика OneScript:
    // library-сущность видна только если она объявлена в директиве
    // #Использовать <libName>. Без директив — ничего из библиотек не видно.
    // В BSL-файлах library-сущности скрыты целиком.
    var usedLibs = UseDirectiveScanner.usedLibraries(documentContext);
    var usedLibsLower = usedLibs.isEmpty()
      ? java.util.Set.<String>of()
      : usedLibs.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    java.util.function.Predicate<String> libVisible = name -> {
      var origin = oScriptLibraryIndex.findByName(name).map(OScriptLibraryIndex.LibraryEntry::libOrigin);
      if (origin.isEmpty()) {
        return true;
      }
      if (fileType == com.github._1c_syntax.bsl.languageserver.context.FileType.BSL) {
        return false;
      }
      return usedLibsLower.contains(origin.get().toLowerCase(Locale.ROOT));
    };

    if (afterNew) {
      for (var className : filterNamesByLanguage(globalScopeProvider.getClasses(fileType), fileType)) {
        if (matches(className, prefix)) {
          var item = new CompletionItem(className);
          item.setKind(CompletionItemKind.Class);
          typeService.resolve(className, fileType).ifPresent(ref -> {
            var ctors = typeService.getConstructors(ref);
            if (!ctors.isEmpty()) {
              var first = ctors.get(0);
              var paramList = first.parameters().stream()
                .map(p -> p.name())
                .collect(java.util.stream.Collectors.joining(", "));
              item.setDetail("(" + paramList + ")");
            }
            var desc = typeService.getDescription(ref);
            if (!desc.isEmpty()) {
              item.setDocumentation(desc);
            }
          });
          items.add(item);
        }
      }
      for (var libClassName : libraryEntryNames(OScriptLibraryIndex.EntryKind.CLASS)) {
        if (!libVisible.test(libClassName)) {
          continue;
        }
        if (matches(libClassName, prefix)) {
          var item = new CompletionItem(libClassName);
          item.setKind(CompletionItemKind.Class);
          items.add(item);
        }
      }
      return items;
    }

    // OneScript library modules (записи <module> из lib.config)
    for (var libModuleName : libraryEntryNames(OScriptLibraryIndex.EntryKind.MODULE)) {
      if (!libVisible.test(libModuleName)) {
        continue;
      }
      if (matches(libModuleName, prefix)) {
        var item = new CompletionItem(libModuleName);
        item.setKind(CompletionItemKind.Module);
        items.add(item);
      }
    }

    // Global property types (system enums: КодировкаТекста, НаправлениеСортировки и т.п.)
    for (var gpName : filterNamesByLanguage(globalScopeProvider.getGlobalPropertyNames(fileType), fileType)) {
      if (matches(gpName, prefix)) {
        var item = new CompletionItem(gpName);
        item.setKind(CompletionItemKind.Enum);
        items.add(item);
      }
    }

    // Каноничные составные имена MD-объектов конфигурации — только в BSL-файлах.
    if (fileType != com.github._1c_syntax.bsl.languageserver.context.FileType.OS) {
      for (var qualified : filterNamesByLanguage(globalScopeProvider.getConfigurationQualifiedNames(), fileType)) {
        if (matches(qualified, prefix)) {
          var item = new CompletionItem(qualified);
          item.setKind(CompletionItemKind.Module);
          items.add(item);
        }
      }
    }

    // Platform global variables (БиблиотекаКартинок, ПараметрыСеанса, …)
    for (var pv : filterNamesByLanguage(globalScopeProvider.getPlatformVariableNames(fileType), fileType)) {
      if (matches(pv, prefix)) {
        var item = new CompletionItem(pv);
        item.setKind(CompletionItemKind.Variable);
        items.add(item);
      }
    }

    // Global functions
    var seenFn = new java.util.HashSet<String>();
    for (var fn : filterMembersByLanguage(globalScopeProvider.getFunctions(fileType))) {
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
    for (var keyword : filterNamesByLanguage(globalScopeProvider.getKeywords(fileType), fileType)) {
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
    var symDesc = member.getSymbolDescription();
    var detail = symDesc.getPurposeDescription();
    if (detail.isBlank()) {
      detail = member.description();
    }
    if (member.kind() == MemberKind.METHOD) {
      item.setKind(CompletionItemKind.Function);
      item.setInsertText(member.name() + "(");
      if (member.signatures().size() > 1) {
        item.setDetail(formatSignaturesCount(member.signatures().size()));
      } else if (!detail.isBlank()) {
        item.setDetail(detail);
      }
    } else {
      item.setKind(CompletionItemKind.Variable);
      if (!detail.isBlank()) {
        item.setDetail(detail);
      }
    }
    applyDocumentation(item, symDesc);
    return item;
  }

  private static List<CompletionItem> toCompletionItems(Collection<MemberDescriptor> members) {
    var items = new ArrayList<CompletionItem>(members.size());
    for (var member : members) {
      var item = new CompletionItem(member.name());
      var symDesc = member.getSymbolDescription();
      var detail = symDesc.getPurposeDescription();
      if (detail.isBlank()) {
        detail = member.description();
      }
      if (member.kind() == MemberKind.METHOD) {
        item.setKind(CompletionItemKind.Method);
        item.setInsertText(member.name() + "(");
        if (member.signatures().size() > 1) {
          item.setDetail(formatSignaturesCount(member.signatures().size()));
        } else if (!detail.isBlank()) {
          item.setDetail(detail);
        }
      } else {
        item.setKind(CompletionItemKind.Property);
        if (!detail.isBlank()) {
          item.setDetail(detail);
        }
      }
      applyDocumentation(item, symDesc);
      items.add(item);
    }
    return items;
  }

  private static String formatSignaturesCount(int count) {
    var mod10 = count % 10;
    var mod100 = count % 100;
    String word;
    if (mod100 >= 11 && mod100 <= 14) {
      word = "вариантов";
    } else if (mod10 == 1) {
      word = "вариант";
    } else if (mod10 >= 2 && mod10 <= 4) {
      word = "варианта";
    } else {
      word = "вариантов";
    }
    return count + " " + word + " синтаксиса";
  }

  private static void applyDocumentation(
    CompletionItem item,
    com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolDescription symDesc
  ) {
    if (symDesc.isEmpty()) {
      return;
    }
    var sb = new StringBuilder();
    if (symDesc.isDeprecated()) {
      sb.append("**Устарело.**");
      if (!symDesc.getDeprecationInfo().isBlank()) {
        sb.append(' ').append(symDesc.getDeprecationInfo());
      }
      sb.append("\n\n");
    }
    if (!symDesc.getPurposeDescription().isBlank()) {
      sb.append(symDesc.getPurposeDescription());
    }
    if (sb.length() > 0) {
      item.setDocumentation(sb.toString());
    }
  }

  /**
   * Fallback для висячей точки ({@code MyClass.\n}): парсер уходит в error-recovery
   * и SymbolTree не содержит присваивания, поэтому штатный inference возвращает пусто.
   * Здесь мы:
   *   1. вставляем placeholder-идентификатор после точки,
   *   2. парсим патченный контент отдельным {@link BSLTokenizer},
   *   3. ищем последнее присваивание {@code receiver = Новый TypeName(...)} до позиции
   *      и резолвим имя типа через {@link TypeService#resolve(String, com.github._1c_syntax.bsl.languageserver.context.FileType)}.
   * Покрывает базовый сценарий: переменная, инициализированная конструктором.
   */
  private TypeSet rescueDanglingDotType(DocumentContext documentContext, Position position, DotCompletionInfo dotInfo) {
    var content = documentContext.getContent();
    if (content == null) {
      return TypeSet.EMPTY;
    }
    var lines = content.split("\\R", -1);
    if (position.getLine() >= lines.length) {
      return TypeSet.EMPTY;
    }
    var line = lines[position.getLine()];
    int dotCol = dotInfo.dotColumn;
    if (dotCol < 0 || dotCol >= line.length() || line.charAt(dotCol) != '.') {
      return TypeSet.EMPTY;
    }
    int end = dotCol;
    int start = dotCol;
    while (start > 0 && isIdentChar(line.charAt(start - 1))) {
      start--;
    }
    if (start == end) {
      return TypeSet.EMPTY;
    }
    var receiverName = line.substring(start, end);

    int offset = absoluteOffsetOf(content, position.getLine(), dotCol + 1);
    if (offset < 0 || offset > content.length()) {
      return TypeSet.EMPTY;
    }
    var placeholder = "__lsp_rescue__";
    var patched = content.substring(0, offset) + placeholder + content.substring(offset);

    BSLParser.FileContext ast;
    try {
      ast = new BSLTokenizer(patched).getAst();
    } catch (RuntimeException e) {
      return TypeSet.EMPTY;
    }

    var typeName = findConstructorTypeNameForVariable(ast, receiverName, position.getLine());
    if (typeName == null) {
      return TypeSet.EMPTY;
    }
    return typeService.resolve(typeName, documentContext.getFileType())
      .map(TypeSet::of)
      .orElse(TypeSet.EMPTY);
  }

  private static String findConstructorTypeNameForVariable(BSLParser.FileContext ast, String receiverName, int beforeLine) {
    String found = null;
    int foundLine = -1;
    for (var node : Trees.<ParserRuleContext>findAllRuleNodes(ast, BSLParser.RULE_assignment)) {
      if (!(node instanceof BSLParser.AssignmentContext assign)) {
        continue;
      }
      int line = assign.getStart().getLine() - 1;
      if (line > beforeLine) {
        continue;
      }
      var lValue = assign.lValue();
      if (lValue == null || lValue.IDENTIFIER() == null) {
        continue;
      }
      if (!lValue.IDENTIFIER().getText().equalsIgnoreCase(receiverName)) {
        continue;
      }
      var expr = assign.expression();
      if (expr == null) {
        continue;
      }
      var typeName = extractNewExpressionTypeName(expr);
      if (typeName == null) {
        continue;
      }
      if (line >= foundLine) {
        found = typeName;
        foundLine = line;
      }
    }
    return found;
  }

  private static String extractNewExpressionTypeName(ParserRuleContext expr) {
    for (var node : Trees.<ParserRuleContext>findAllRuleNodes(expr, BSLParser.RULE_newExpression)) {
      if (node instanceof BSLParser.NewExpressionContext ne && ne.typeName() != null) {
        return ne.typeName().getText();
      }
    }
    return null;
  }

  private static int absoluteOffsetOf(String content, int line, int col) {
    int currentLine = 0;
    int offset = 0;
    while (currentLine < line && offset < content.length()) {
      char c = content.charAt(offset);
      offset++;
      if (c == '\r') {
        currentLine++;
        if (offset < content.length() && content.charAt(offset) == '\n') {
          offset++;
        }
      } else if (c == '\n') {
        currentLine++;
      }
    }
    return offset + col;
  }
}
