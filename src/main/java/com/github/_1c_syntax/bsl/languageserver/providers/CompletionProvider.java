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

import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.events.LanguageServerInitializeRequestReceivedEvent;
import com.github._1c_syntax.bsl.languageserver.types.PlatformMemberVersions;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.scope.UseDirectiveScanner;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemTag;
import org.eclipse.lsp4j.CompletionItemTagSupportCapabilities;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

  private static final String TRIGGER_PARAMETER_HINTS_COMMAND = "editor.action.triggerParameterHints";

  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final LanguageServerConfiguration configuration;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;

  // Кэшируется на initialize. snippetSupport — gate для вставки `Метод($0)` сниппета и
  // прикрепления `editor.action.triggerParameterHints` к completion item.
  private boolean snippetSupport;

  // Кэшируется на initialize. Поддерживает ли клиент CompletionItemTag.Deprecated —
  // если нет, помечаем устаревший член legacy-флагом setDeprecated.
  private boolean deprecatedTagSupport;

  // Кэшируется на initialize. Поддерживает ли клиент markdown в documentation completion item.
  // Если да — documentation отдаётся как MarkupContent(MARKDOWN), иначе голой строкой
  // (plaintext) с вырезанной markdown-разметкой, чтобы клиент не показывал звёздочки буквально.
  private boolean markdownDocumentationSupport;

  @EventListener(LanguageServerInitializeRequestReceivedEvent.class)
  public void handleInitializeEvent() {
    var completionItem = clientCapabilitiesHolder.getCapabilities()
      .map(ClientCapabilities::getTextDocument)
      .map(TextDocumentClientCapabilities::getCompletion)
      .map(CompletionCapabilities::getCompletionItem);
    snippetSupport = completionItem
      .map(CompletionItemCapabilities::getSnippetSupport)
      .orElse(Boolean.FALSE);
    deprecatedTagSupport = completionItem
      .map(CompletionItemCapabilities::getTagSupport)
      .map(CompletionItemTagSupportCapabilities::getValueSet)
      .filter(valueSet -> valueSet.contains(CompletionItemTag.Deprecated))
      .isPresent();
    markdownDocumentationSupport = completionItem
      .map(CompletionItemCapabilities::getDocumentationFormat)
      .map(formats -> formats.contains(MarkupKind.MARKDOWN))
      .orElse(Boolean.FALSE);
  }

  /**
   * Фильтр для plain-имён ({@code List<String>}) — классы, ключевые слова,
   * глобальные свойства и т.п. Алиас-пары определяются по тому, что разные
   * имена резолвятся к одному global symbol.
   */
  private List<String> filterNamesByLanguage(Collection<String> names, FileType fileType, Language language) {
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
        if (isInConfiguredLanguage(name, language)) {
          pick = name;
          break;
        }
      }
      result.add(pick != null ? pick : group.get(0));
    }
    return result;
  }

  /**
   * Имя считается совместимым с настроенным {@link Language}.
   * Ключевой инвариант 1С: русские идентификаторы могут содержать
   * латинские аббревиатуры ({@code ЧтениеJSON}, {@code ЗаписьXML},
   * {@code HTTPСоединение}), а английские — всегда чистый ASCII.
   * Поэтому наличие кириллицы однозначно относит имя к RU, и эвристика
   * строится только на её присутствии. Имена без букв
   * (служебные/составные) не фильтруются.
   */
  private static boolean isInConfiguredLanguage(String name, Language language) {
    if (name.isEmpty()) {
      return true;
    }
    var hasCyrillic = name.chars().anyMatch(CompletionProvider::isCyrillic);
    var hasLatin = name.chars().anyMatch(CompletionProvider::isAsciiLetter);
    if (!hasCyrillic && !hasLatin) {
      return true;
    }
    if (language == Language.RU) {
      return hasCyrillic;
    }
    return !hasCyrillic;
  }

  private static boolean isCyrillic(int ch) {
    return Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC;
  }

  private static boolean isAsciiLetter(int ch) {
    return Character.isLetter(ch) && Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.BASIC_LATIN;
  }

  /**
   * Видна ли library-запись в no-dot completion текущего документа.
   * <p>
   * Запись видна, если:
   * <ul>
   *   <li>файл — не BSL (в BSL OneScript-library-сущности скрыты целиком);</li>
   *   <li>её библиотека объявлена в {@code #Использовать} ИЛИ это та же
   *       библиотека-«пакет» (тот же корень), что и редактируемый файл
   *       ({@code ownLibOrigin});</li>
   *   <li>implicit-запись из чужой библиотеки скрывается при выключенном
   *       {@code oscript.showImplicitLibraryEntriesInCompletion}; из своего
   *       пакета implicit-запись видна всегда.</li>
   * </ul>
   */
  private boolean libraryEntryVisible(OScriptLibraryIndex.LibraryEntry entry,
                                      FileType fileType,
                                      Set<String> usedLibsLower,
                                      Optional<String> ownLibOrigin) {
    if (fileType == FileType.BSL) {
      return false;
    }
    var origin = entry.libOrigin();
    if (origin.isBlank()) {
      return true;
    }
    var originLower = origin.toLowerCase(Locale.ROOT);
    boolean samePackage = ownLibOrigin.map(originLower::equals).orElse(false);
    if (entry.implicit()
      && !configuration.getOscriptOptions().isShowImplicitLibraryEntriesInCompletion()
      && !samePackage) {
      return false;
    }
    return samePackage || usedLibsLower.contains(originLower);
  }

  /**
   * Видно ли имя из global scope с точки зрения library-gating. Если имя не
   * относится к зарегистрированной library-записи — видно (платформенные и
   * конфигурационные имена не ограничиваем).
   */
  private boolean libraryNameVisible(String name,
                                     FileType fileType,
                                     Set<String> usedLibsLower,
                                     Optional<String> ownLibOrigin) {
    return oScriptLibraryIndex.findByName(name)
      .map(entry -> libraryEntryVisible(entry, fileType, usedLibsLower, ownLibOrigin))
      .orElse(true);
  }

  /**
   * Скрывать ли имя из no-dot completion из-за {@code implicit}-флага.
   * Если по имени в {@link OScriptLibraryIndex} нет записи или она не помечена
   * implicit — ничего не скрываем. Если помечена и
   * {@code oscript.showImplicitLibraryEntriesInCompletion = false} — скрываем.
   */
  private boolean isImplicitlyHiddenInCompletion(String name) {
    if (configuration.getOscriptOptions().isShowImplicitLibraryEntriesInCompletion()) {
      return false;
    }
    return oScriptLibraryIndex.findByName(name)
      .map(OScriptLibraryIndex.LibraryEntry::implicit)
      .orElse(false);
  }

  /**
   * Generic-имена платформенных типов (e.g. {@code СправочникСсылка.<Имя справочника>},
   * {@code ПерерасчетЗапись.<Имя перерасчета>}) — это шаблоны для specialization,
   * не самостоятельные классы. В completion подставлять их буквально нельзя:
   * у пользователя в коде такое имя — синтаксическая ошибка.
   * <p>
   * Детект placeholder'ов идёт через bsl-context ({@link ContextNames#placeholders}),
   * а не через парсинг {@code <>} в LS — единая точка истины для имён generic'ов.
   */
  private static boolean isGenericTemplateName(String name) {
    return !ContextNames.placeholders(name).isEmpty();
  }

  /**
   * @return предложения автодополнения для указанной позиции, обёрнутые в {@link CompletionList}.
   *     {@code isIncomplete = false}: список содержит все валидные кандидаты для текущего префикса
   *     — клиент может фильтровать дальше локально, повторно к серверу обращаться не обязан.
   */
  public CompletionList getCompletion(DocumentContext documentContext, CompletionParams params) {
    var position = params.getPosition();
    var items = isDotCompletion(documentContext, position)
      ? dotCompletion(documentContext, position)
      : noDotCompletion(documentContext, position);
    return new CompletionList(false, items);
  }

  private List<CompletionItem> dotCompletion(DocumentContext documentContext, Position position) {
    var dotInfo = dotCompletionInfo(documentContext, position);
    if (dotInfo == null) {
      return List.of();
    }
    var fileType = documentContext.getFileType();
    // тип ресивера слева от точки
    var typeSet = typeService.receiverTypesAt(documentContext, position);
    if (typeSet.isEmpty()) {
      return List.of();
    }

    var scriptVariant = documentContext.getScriptVariantLanguage();
    var members = new LinkedHashMap<String, MemberDescriptor>();
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref, fileType, scriptVariant)) {
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
    var target = PlatformMemberVersions.targetCompatibilityMode(documentContext, configuration);
    var filtered = members.values().stream()
      .filter(m -> matches(m.displayName(scriptVariant), prefix))
      // События платформы — обработчики, программист их не вызывает; в
      // автодополнении только мешают (это callback-точки в модулях).
      .filter(m -> m.kind() != MemberKind.EVENT)
      // Член, недоступный в целевой версии платформы (sinceVersion новее target),
      // в автодополнении предлагать не нужно — его вызов помечает
      // UnavailableMemberCall. Устаревшие при этом остаются (показываются
      // зачёркнутыми).
      .filter(m -> !PlatformMemberVersions.firesUnavailable(m.metadata().sinceVersion(), target))
      .toList();
    return toCompletionItems(filtered, scriptVariant, target);
  }

  /**
   * Член устарел: платформенный — если устарел для целевой версии платформы
   * ({@code target >= deprecatedSinceVersion}, как в {@code DeprecatedMethodCall});
   * source-член — по пометке устаревания в doc-комментарии. Sentinel-версия
   * oscript ({@code "*"}) срабатывает всегда.
   */
  private static boolean isMemberDeprecated(MemberDescriptor member, CompatibilityMode target) {
    return PlatformMemberVersions.firesDeprecated(member.metadata().deprecatedSinceVersion(), target)
      || member.getSymbolDescription().isDeprecated();
  }

  @Nullable
  private static DotCompletionInfo dotCompletionInfo(DocumentContext documentContext, Position position) {
    try {
      var lines = documentContext.getContentList();
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
      return new DotCompletionInfo(line.substring(i, col));
    } catch (Exception e) {
      return null;
    }
  }

  private record DotCompletionInfo(String prefix) {
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
    // сторонняя library-сущность видна только если её библиотека объявлена в
    // директиве #Использовать <libName>. Без директив сторонние библиотеки не
    // видны. В BSL-файлах library-сущности скрыты целиком.
    var usedLibs = UseDirectiveScanner.usedLibraries(documentContext);
    var usedLibsLower = usedLibs.isEmpty()
      ? Set.<String>of()
      : usedLibs.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    // Библиотека-«пакет» редактируемого файла: если сам документ является
    // зарегистрированной library-записью, его соседи по тому же корню видны
    // в completion без #Использовать (как implicit, так и explicit).
    var ownLibOrigin = oScriptLibraryIndex.findByUri(Absolute.uri(documentContext.getUri()))
      .map(OScriptLibraryIndex.LibraryEntry::libOrigin)
      .filter(o -> o != null && !o.isBlank())
      .map(o -> o.toLowerCase(Locale.ROOT));

    var scriptVariant = documentContext.getScriptVariantLanguage();
    if (afterNew) {
      for (var className : filterNamesByLanguage(globalScopeProvider.getClasses(fileType), fileType, scriptVariant)) {
        if (isImplicitlyHiddenInCompletion(className) || isGenericTemplateName(className)) {
          continue;
        }
        if (matches(className, prefix)) {
          items.add(buildPlatformClassCompletionItem(className, fileType, scriptVariant));
        }
      }
      for (var classEntry : oScriptLibraryIndex.findEntries(OScriptLibraryIndex.EntryKind.CLASS)) {
        if (!libraryEntryVisible(classEntry, fileType, usedLibsLower, ownLibOrigin)) {
          continue;
        }
        var libClassName = classEntry.qualifiedName();
        if (matches(libClassName, prefix)) {
          var item = new CompletionItem(libClassName);
          item.setKind(CompletionItemKind.Class);
          // Данных о конструкторе библиотечного класса здесь нет — сохраняем курсор между скобок.
          applyCallableInsertText(item, libClassName, true);
          items.add(item);
        }
      }
      return items;
    }

    // Каноничные составные имена MD-объектов конфигурации — только в BSL-файлах.
    if (fileType != FileType.OS) {
      for (var qualified : filterNamesByLanguage(globalScopeProvider.getConfigurationQualifiedNames(), fileType, scriptVariant)) {
        if (isGenericTemplateName(qualified)) {
          continue;
        }
        if (matches(qualified, prefix)) {
          var item = new CompletionItem(qualified);
          item.setKind(CompletionItemKind.Module);
          items.add(item);
        }
      }
    }

    // Global contexts — все VALUE-имена в global scope (property, enum, library-module).
    // CompletionItemKind выбирается по фактическому SyntheticKind. К library-сущностям
    // применяется library-gating (#Использовать / свой пакет / implicit) через
    // libraryNameVisible; платформенные и конфигурационные имена не ограничиваются.
    for (var ctx : globalScopeProvider.getGlobalContexts(fileType)) {
      var name = ctx.getName();
      if (!matches(name, prefix)) {
        continue;
      }
      if (isGenericTemplateName(name)) {
        continue;
      }
      if (!libraryNameVisible(name, fileType, usedLibsLower, ownLibOrigin)) {
        continue;
      }
      var item = new CompletionItem(name);
      item.setKind(completionKindForSynthetic(ctx.getSyntheticKind()));
      items.add(item);
    }

    // Global functions. Один и тот же двуязычный дескриптор зарегистрирован
    // под ru- и en-ключом, поэтому в values() встречается дважды — дедуп по
    // primary-имени через seenFn.
    var target = PlatformMemberVersions.targetCompatibilityMode(documentContext, configuration);
    var seenFn = new java.util.HashSet<String>();
    for (var fn : globalScopeProvider.getFunctions(fileType)) {
      if (!seenFn.add(fn.name())) {
        continue;
      }
      var displayName = fn.displayName(scriptVariant);
      if (matches(displayName, prefix)) {
        items.add(toCompletionItem(fn, scriptVariant, target));
      }
    }

    // Local methods of current document
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (matches(method.getName(), prefix)) {
        var item = new CompletionItem(method.getName());
        item.setKind(method.isFunction() ? CompletionItemKind.Function : CompletionItemKind.Method);
        applyCallableInsertText(item, method.getName(), !method.getParameters().isEmpty());
        if (method.isDeprecated()) {
          markDeprecatedItem(item);
        }
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
    for (var keyword : filterNamesByLanguage(globalScopeProvider.getKeywords(fileType), fileType, scriptVariant)) {
      if (matches(keyword, prefix)) {
        var item = new CompletionItem(keyword);
        item.setKind(CompletionItemKind.Keyword);
        items.add(item);
      }
    }

    return items;
  }

  /**
   * Подобрать {@link CompletionItemKind} для имени из global scope по его
   * {@link SyntheticKind}.
   */
  private static CompletionItemKind completionKindForSynthetic(SyntheticKind kind) {
    return switch (kind) {
      case PLATFORM_GLOBAL_ENUM -> CompletionItemKind.Enum;
      case LIBRARY_MODULE -> CompletionItemKind.Module;
      default -> CompletionItemKind.Variable;
    };
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

  @Nullable
  private static LineInfo currentLineInfo(DocumentContext documentContext, Position position) {
    try {
      var lines = documentContext.getContentList();
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
      var lines = documentContext.getContentList();
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

  private CompletionItem toCompletionItem(MemberDescriptor member, Language scriptVariant, CompatibilityMode target) {
    return buildMemberItem(member, CompletionItemKind.Function, CompletionItemKind.Variable, scriptVariant, target);
  }

  private List<CompletionItem> toCompletionItems(Collection<MemberDescriptor> members, Language scriptVariant,
                                                 CompatibilityMode target) {
    var items = new ArrayList<CompletionItem>(members.size());
    for (var member : members) {
      items.add(buildMemberItem(member, CompletionItemKind.Method, CompletionItemKind.Property, scriptVariant, target));
    }
    return items;
  }

  /**
   * Сборка completion item для метода или свойства типа.
   * <p>
   * Разделение полей по LSP-конвенции:
   * <ul>
   *   <li>{@code detail} — техническая сводка: сигнатура {@code (param1, [optional])}
   *       и возвращаемый тип для методов; имя типа для свойств. Никогда не дублирует описание.</li>
   *   <li>{@code documentation} — содержательное описание (с deprecation-блоком, если есть).</li>
   * </ul>
   * Раньше {@code purposeDescription} писалось одновременно в {@code detail} и в
   * {@code documentation} — VS Code показывал его дважды в подсказке.
   */
  private CompletionItem buildMemberItem(MemberDescriptor member,
                                         CompletionItemKind methodKind,
                                         CompletionItemKind propertyKind,
                                         Language scriptVariant,
                                         CompatibilityMode target) {
    var displayName = member.displayName(scriptVariant);
    var item = new CompletionItem(displayName);
    if (member.kind() == MemberKind.METHOD) {
      item.setKind(methodKind);
      applyCallableInsertText(item, displayName, memberHasParameters(member));
      var detail = methodDetail(member, scriptVariant);
      if (!detail.isBlank()) {
        item.setDetail(detail);
      }
    } else {
      item.setKind(propertyKind);
      var detail = propertyDetail(member, scriptVariant);
      if (!detail.isBlank()) {
        item.setDetail(detail);
      }
    }
    applyDocumentation(item, member, scriptVariant);
    markDeprecated(item, member, target);
    return item;
  }

  /** Помечает item устаревшим, если {@code member} устарел для целевой версии. */
  private void markDeprecated(CompletionItem item, MemberDescriptor member, CompatibilityMode target) {
    if (isMemberDeprecated(member, target)) {
      markDeprecatedItem(item);
    }
  }

  /**
   * Помечает completion item устаревшим: при поддержке клиентом тегов —
   * {@link CompletionItemTag#Deprecated}, иначе legacy-флагом
   * {@link CompletionItem#setDeprecated}. Клиент рисует такой пункт
   * зачёркнутым. Применяется ко всем устаревшим членам автодополнения —
   * платформенным, глобальным функциям, членам конфигурации и
   * пользовательским методам oscript-классов.
   */
  private void markDeprecatedItem(CompletionItem item) {
    if (deprecatedTagSupport) {
      item.setTags(List.of(CompletionItemTag.Deprecated));
    } else {
      item.setDeprecated(Boolean.TRUE);
    }
  }

  /**
   * Поведение по конвенции LSP-серверов (TypeScript LS, gopls, rust-analyzer, Pyright):
   * <ul>
   *   <li>Метод без параметров — вставляем готовые скобки «{@code Метод()}» и оставляем
   *       курсор сразу после них: вводить нечего, а signatureHelp поднимать незачем.</li>
   *   <li>Метод с параметрами и клиент поддерживает {@code completionItem.snippetSupport} —
   *       вставляем «{@code Метод($0)}» как сниппет: курсор окажется между скобок,
   *       и сразу даём {@code editor.action.triggerParameterHints}, чтобы клиент
   *       поднял signatureHelp без дополнительного нажатия.</li>
   *   <li>Метод с параметрами без {@code snippetSupport} — фолбэк «{@code Метод(}»: символ
   *       {@code (} тоже trigger character для signatureHelp
   *       ({@link com.github._1c_syntax.bsl.languageserver.BSLLanguageServer}),
   *       но закрывающую скобку пользователь поставит сам.</li>
   * </ul>
   *
   * @param hasParameters есть ли у вызываемого хотя бы один параметр. Когда данных о
   *                      сигнатуре нет либо перегрузок несколько — передаётся {@code true},
   *                      чтобы сохранить поведение с курсором между скобок.
   */
  /**
   * Строит completion-item платформенного класса в позиции после {@code Новый}.
   * Курсор оставляем между скобок, если у конструктора есть параметры либо
   * перегрузок несколько; для единственного беспараметрового конструктора —
   * после закрытой скобки {@code ()}.
   */
  private CompletionItem buildPlatformClassCompletionItem(String className, FileType fileType,
                                                          Language scriptVariant) {
    var item = new CompletionItem(className);
    item.setKind(CompletionItemKind.Class);
    // Без данных о конструкторе сохраняем поведение с курсором между скобок.
    var ctorHasParameters = true;
    var refOpt = typeService.resolve(className, fileType);
    if (refOpt.isPresent()) {
      var ref = refOpt.get();
      var ctors = typeService.getConstructors(ref, fileType);
      if (!ctors.isEmpty()) {
        // Несколько перегрузок конструктора → консервативно оставляем курсор между скобок:
        // первый вариант может быть беспараметровым, а следующий — принимать аргументы
        // (например, Новый HTTPЗапрос() и (Адрес, Заголовки)).
        ctorHasParameters = ctors.size() > 1 || !ctors.get(0).parameters().isEmpty();
        var paramList = ctors.get(0).parameters().stream()
          .map(p -> p.displayName(scriptVariant))
          .collect(java.util.stream.Collectors.joining(", "));
        item.setDetail("(" + paramList + ")");
      }
      var desc = typeService.getDescription(ref, scriptVariant, fileType);
      if (!desc.isEmpty()) {
        setDocumentation(item, desc);
      }
    }
    applyCallableInsertText(item, className, ctorHasParameters);
    return item;
  }

  private void applyCallableInsertText(CompletionItem item, String name, boolean hasParameters) {
    if (!hasParameters) {
      item.setInsertText(name + "()");
      return;
    }
    if (snippetSupport) {
      item.setInsertText(name + "($0)");
      item.setInsertTextFormat(InsertTextFormat.Snippet);
      item.setCommand(new Command("Trigger Parameter Hints", TRIGGER_PARAMETER_HINTS_COMMAND));
    } else {
      item.setInsertText(name + "(");
    }
  }

  /**
   * Есть ли у метода/функции хотя бы один параметр. Решение принимается строго по данным:
   * беспараметровым считается метод ровно с одной сигнатурой и пустым списком параметров.
   * Несколько перегрузок или отсутствие сигнатур (параметры неизвестны) трактуются
   * консервативно как «параметры есть» — курсор останется между скобок.
   */
  private static boolean memberHasParameters(MemberDescriptor member) {
    var signatures = member.signatures();
    if (signatures.size() != 1) {
      return true;
    }
    return !signatures.get(0).parameters().isEmpty();
  }

  private String methodDetail(MemberDescriptor member, Language scriptVariant) {
    var signatures = member.signatures();
    if (signatures.size() > 1) {
      return formatSignaturesCount(signatures.size(), scriptVariant);
    }
    if (signatures.isEmpty()) {
      return "";
    }
    return formatSignature(signatures.get(0), scriptVariant);
  }

  private String formatSignature(SignatureDescriptor signature, Language scriptVariant) {
    var sb = new StringBuilder();
    sb.append('(');
    var params = signature.parameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      var p = params.get(i);
      var paramName = p.displayName(scriptVariant);
      sb.append(paramName);
      if (p.optional()) {
        // Необязательный параметр помечаем «?» после имени: ИмяПараметра?.
        sb.append('?');
      }
    }
    sb.append(')');
    var returnTypeName = formatTypeName(signature.returnType(), scriptVariant);
    if (!returnTypeName.isEmpty()) {
      sb.append(": ").append(returnTypeName);
    }
    return sb.toString();
  }

  private String propertyDetail(MemberDescriptor member, Language scriptVariant) {
    return formatTypeName(member.returnType(), scriptVariant);
  }

  /**
   * Короткое имя типа в языке {@code scriptVariant}. Берётся двуязычное
   * отображаемое имя из реестра ({@code Строка}/{@code String},
   * {@code Массив}/{@code Array}), затем — последний сегмент (для
   * квалифицированных имён вида {@code СправочникСсылка.Контрагенты}).
   */
  private String formatTypeName(TypeRef ref, Language scriptVariant) {
    if (ref == null || ref.kind() == TypeKind.UNKNOWN || ref.equals(TypeRef.UNKNOWN)) {
      return "";
    }
    var displayName = typeService.displayName(ref, scriptVariant);
    var dot = displayName.lastIndexOf('.');
    return dot < 0 ? displayName : displayName.substring(dot + 1);
  }

  private static String formatSignaturesCount(int count, Language scriptVariant) {
    if (scriptVariant == Language.EN) {
      return count + (count == 1 ? " overload" : " overloads");
    }
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

  private void applyDocumentation(CompletionItem item, MemberDescriptor member, Language scriptVariant) {
    var symDesc = member.getSymbolDescription();
    // У source-defined членов (пользовательские методы/свойства) описание —
    // это doc-comment в коде пользователя, он на языке проекта as-is.
    // У платформенных/конфигурационных членов source-символа нет, поэтому
    // берём bilingual-описание в языке ScriptVariant, иначе документация
    // всегда оставалась бы на русском (primary).
    var purpose = member.getSourceSymbol()
      .map(symbol -> symDesc.getPurposeDescription())
      .filter(doc -> !doc.isBlank())
      .orElseGet(() -> member.displayDescription(scriptVariant));
    var sb = new StringBuilder();
    // Сам факт устаревания клиенту сообщает родной механизм LSP (markDeprecated:
    // CompletionItemTag.Deprecated или legacy-флаг deprecated) — текстовая пометка
    // в documentation его бы дублировала. В документацию попадает только причина
    // устаревания, которую тегом не передать.
    if (symDesc.isDeprecated() && !symDesc.getDeprecationInfo().isBlank()) {
      sb.append(symDesc.getDeprecationInfo());
      if (!purpose.isBlank()) {
        sb.append("\n\n");
      }
    }
    if (!purpose.isBlank()) {
      sb.append(purpose);
    }
    if (sb.length() > 0) {
      setDocumentation(item, sb.toString());
    }
  }

  /**
   * Проставляет documentation completion item с учётом клиентских capabilities.
   * Если клиент поддерживает markdown в documentation completion item — текст отдаётся
   * как {@link MarkupContent} с {@link MarkupKind#MARKDOWN}. Иначе документация отдаётся
   * голой строкой (plaintext) с вырезанной markdown-разметкой, иначе клиент покажет
   * управляющие символы (например, {@code **}) буквально.
   *
   * @param item     completion item, которому проставляется документация.
   * @param markdown текст документации в формате markdown (без экранирования).
   */
  private void setDocumentation(CompletionItem item, String markdown) {
    if (markdownDocumentationSupport) {
      item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, markdown));
    } else {
      item.setDocumentation(stripMarkdownEmphasis(markdown));
    }
  }

  /**
   * Убирает markdown-разметку жирного начертания ({@code **...**}) из текста для
   * plaintext-клиентов. В формируемой документации {@code **} используется только как
   * обрамление жирного, поэтому достаточно удалить все вхождения {@code **}.
   *
   * @param value исходный текст с markdown-разметкой.
   * @return текст без обрамляющих {@code **}.
   */
  private static String stripMarkdownEmphasis(String value) {
    return value.replace("**", "");
  }
}