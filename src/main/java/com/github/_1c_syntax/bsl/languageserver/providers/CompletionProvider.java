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
import com.github._1c_syntax.bsl.languageserver.completion.CompletionData;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
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
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.scope.UseDirectiveScanner;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import com.github._1c_syntax.bsl.support.CompatibilityMode;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionItemLabelDetails;
import org.eclipse.lsp4j.CompletionItemResolveSupportCapabilities;
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
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

  // sortText-«корзины» для no-dot completion. Клиент сортирует пункты по sortText
  // лексикографически, поэтому меньший префикс = выше в списке. Без sortText всё
  // сортируется по label, и локальные имена документа тонут среди сотен глобальных.
  // Порядок: локальные имена документа → глобальные функции/контексты → классы и
  // MD-имена → ключевые слова. Внутри корзины — стабильно по label.
  private static final String BUCKET_LOCAL = "1";
  private static final String BUCKET_GLOBAL = "2";
  private static final String BUCKET_TYPE = "3";
  private static final String BUCKET_KEYWORD = "4";
  // Корзина членов типа в dot-completion: пользовательские/декларированные поля
  // приоритетнее дефолтных членов того же типа.
  private static final String BUCKET_MEMBER_FIELD = "1";
  private static final String BUCKET_MEMBER_DEFAULT = "2";

  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;
  private final TypeRegistry typeRegistry;
  private final OScriptLibraryIndex oScriptLibraryIndex;
  private final LanguageServerConfiguration configuration;
  private final ClientCapabilitiesHolder clientCapabilitiesHolder;
  private final JsonMapper jsonMapper;

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

  // Кэшируется на initialize. Объявил ли клиент в completionItem.resolveSupport свойство
  // documentation — то есть готов лениво дотягивать описание через completionItem/resolve.
  // Если да — для членов dot-completion documentation откладывается (см. buildMemberItem),
  // иначе строится жадно, как раньше (клиент без resolve должен получить её сразу).
  private boolean documentationResolveSupport;

  // Кэшируется на initialize. Поддерживает ли клиент completionItem.labelDetailsSupport
  // (LSP 3.17). Если да — сигнатура метода и возвращаемый тип / тип свойства кладутся в
  // CompletionItemLabelDetails (detail/description) рядом с именем, а плоский detail не
  // заполняется, чтобы клиент не показывал ту же сигнатуру дважды. Иначе — прежнее поведение
  // с записью сигнатуры в плоский detail.
  private boolean labelDetailsSupport;

  // Кэшируется на initialize. Поддерживает ли клиент completionItem.commitCharactersSupport —
  // фиксацию пункта вводом «commit character». Если да — методам/функциям проставляем
  // commitCharacters ["("] (вставка открывающей скобки фиксирует вызываемый),
  // членам-объектам и переменным/модулям — ["."] (осмысленно дальнейшее обращение).
  // Если клиент не поддерживает — commitCharacters не задаём вовсе.
  private boolean commitCharactersSupport;

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
    documentationResolveSupport = completionItem
      .map(CompletionItemCapabilities::getResolveSupport)
      .map(CompletionItemResolveSupportCapabilities::getProperties)
      .map(properties -> properties.contains("documentation"))
      .orElse(Boolean.FALSE);
    labelDetailsSupport = completionItem
      .map(CompletionItemCapabilities::getLabelDetailsSupport)
      .orElse(Boolean.FALSE);
    commitCharactersSupport = completionItem
      .map(CompletionItemCapabilities::getCommitCharactersSupport)
      .orElse(Boolean.FALSE);
  }

  /**
   * Дедуп ru/en-написаний <b>имён типов</b> — классов для {@code Новый} и
   * составных имён MD-объектов конфигурации ({@code Справочники.Контрагенты}).
   * Разные написания одного типа резолвятся в один интернированный
   * {@link TypeRef} → группируются, из группы остаётся написание под настроенный
   * {@link Language}. Имя без резолва (не тип) проходит как есть, не группируясь
   *.
   */
  private List<String> filterTypeNamesByLanguage(Collection<String> names, Language language) {
    if (names.isEmpty()) {
      return List.of();
    }
    var byType = new LinkedHashMap<Object, List<String>>();
    for (var name : names) {
      // ru/en одного типа → один TypeRef; не-тип → уникальный ключ (своя группа).
      Object key = typeRegistry.resolve(name).<Object>map(ref -> ref).orElseGet(Object::new);
      byType.computeIfAbsent(key, k -> new ArrayList<>()).add(name);
    }
    var result = new ArrayList<String>(names.size());
    for (var group : byType.values()) {
      if (group.size() == 1) {
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

  /**
   * Разрешает отложенную документацию completion item ({@code completionItem/resolve}).
   * <p>
   * По data-ключу восстанавливается источник описания: для глобальной функции — она
   * сама по имени в глобальной области видимости, для члена типа — тип-владелец и член.
   * После чего {@code documentation} собирается тем же способом, что был бы при жадной
   * сборке. Поле {@code data} очищается для экономии трафика.
   * <p>
   * И {@code globalScopeProvider}, и {@code typeService} — workspace-scoped бины, поэтому
   * вызывающий обязан установить workspace-контекст текущего документа перед вызовом
   * (см. {@link #extractData(CompletionItem)} для получения {@code uri} документа).
   *
   * @param unresolved completion item, пришедший от клиента на разрешение.
   * @param data       ключ восстановления документации, извлечённый из item
   *                   через {@link #extractData(CompletionItem)}.
   * @return тот же item с проставленной {@code documentation} и очищенным {@code data}.
   */
  public CompletionItem resolveCompletionItem(CompletionItem unresolved, CompletionData data) {
    var functionName = data.getFunctionName();
    if (functionName != null) {
      globalScopeProvider.globalFunction(functionName, data.getFileType())
        .ifPresent(function -> applyDocumentation(unresolved, function, data.getScriptVariant()));
    } else {
      resolveMemberDocumentation(unresolved, data);
    }
    unresolved.setData(null);
    return unresolved;
  }

  /**
   * Восстанавливает {@code documentation} члена типа по ключу {@link CompletionData}
   * dot-варианта (тип-владелец + имя члена). Если ключ типа неполон — ничего не делает.
   *
   * @param unresolved completion item, которому проставляется документация.
   * @param data       ключ восстановления члена типа.
   */
  private void resolveMemberDocumentation(CompletionItem unresolved, CompletionData data) {
    var typeKind = data.getTypeKind();
    var typeQualifiedName = data.getTypeQualifiedName();
    var memberName = data.getMemberName();
    if (typeKind == null || typeQualifiedName == null || memberName == null) {
      return;
    }
    var ref = new TypeRef(typeKind, typeQualifiedName);
    typeService.getMembers(ref, data.getFileType(), data.getScriptVariant()).stream()
      .filter(member -> member.name().equals(memberName))
      .findFirst()
      .ifPresent(member -> applyDocumentation(unresolved, member, data.getScriptVariant()));
  }

  /**
   * Извлекает {@link CompletionData} из {@link CompletionItem#getData()}.
   * <p>
   * При прямом серверном вызове data — уже объект {@link CompletionData}; после
   * round-trip через клиента lsp4j отдаёт её как JSON (Map/JsonElement), поэтому
   * нетипизированное значение десериализуется через {@link JsonMapper}.
   *
   * @param item completion item, из которого извлекаются данные.
   * @return извлечённые данные либо {@code null}, если data отсутствует.
   */
  @Nullable
  public CompletionData extractData(CompletionItem item) {
    var rawData = item.getData();
    if (rawData == null) {
      return null;
    }
    if (rawData instanceof CompletionData completionData) {
      return completionData;
    }
    return jsonMapper.readValue(rawData.toString(), CompletionData.class);
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
    // Имена декларированных полей — для приоритетной корзины sortText: пользовательские
    // ключи должны ранжироваться выше дефолтных членов того же типа.
    var localFieldNames = new HashSet<String>();
    // Тип-владелец каждого члена — для отложенного восстановления документации в
    // completionItem/resolve. Локальные поля (ключи структуры/колонки ТЗ)
    // owner'а не получают: их описание (если есть, из JsDoc) лежит прямо в
    // MemberDescriptor и documentation строится сразу (eager), резолвить нечего.
    var owners = new LinkedHashMap<String, TypeRef>();
    for (TypeRef ref : typeSet.refs()) {
      for (var member : typeService.getMembers(ref, fileType, scriptVariant)) {
        if (members.putIfAbsent(member.name(), member) == null) {
          owners.put(member.name(), ref);
        }
      }
      // Декларированные ключи «открытого» объекта данных (Структура из
      // Новый Структура("К1, К2"), ТЗ с описанными колонками из JsDoc).
      // Поля идут перед members такого же имени, чтобы пользовательские
      // ключи приоритетнее дефолтных алиасов.
      var localFields = typeSet.getLocalFields(ref);
      for (var entry : localFields.entrySet()) {
        var fieldName = entry.getKey();
        var field = entry.getValue();
        var fieldRef = field.types().refs().stream().findFirst().orElse(null);
        if (members.putIfAbsent(fieldName,
          MemberDescriptor.property(fieldName, fieldRef, field.description())) == null) {
          localFieldNames.add(fieldName);
        }
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
    var items = toCompletionItems(filtered, owners, fileType, scriptVariant, target, documentContext.getUri());
    for (int i = 0; i < filtered.size(); i++) {
      var member = filtered.get(i);
      var bucket = localFieldNames.contains(member.name()) ? BUCKET_MEMBER_FIELD : BUCKET_MEMBER_DEFAULT;
      applySortText(items.get(i), bucket, isMemberDeprecated(member, target));
    }
    return items;
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
      for (var className : filterTypeNamesByLanguage(globalScopeProvider.getClasses(fileType), scriptVariant)) {
        if (isImplicitlyHiddenInCompletion(className) || isGenericTemplateName(className)) {
          continue;
        }
        if (matches(className, prefix)) {
          var item = buildPlatformClassCompletionItem(className, fileType, scriptVariant);
          applySortText(item, BUCKET_TYPE, false);
          items.add(item);
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
          applySortText(item, BUCKET_TYPE, false);
          items.add(item);
        }
      }
      return items;
    }

    // Каноничные составные имена MD-объектов конфигурации — только в BSL-файлах.
    if (fileType != FileType.OS) {
      for (var qualified : filterTypeNamesByLanguage(globalScopeProvider.getConfigurationQualifiedNames(), scriptVariant)) {
        if (isGenericTemplateName(qualified)) {
          continue;
        }
        if (matches(qualified, prefix)) {
          var item = new CompletionItem(qualified);
          item.setKind(CompletionItemKind.Module);
          applySortText(item, BUCKET_TYPE, false);
          applyCommitCharacters(item);
          items.add(item);
        }
      }
    }

    // Global contexts — все VALUE-имена глобальной области: свойства-члены
    // GLOBAL_CONTEXT (платформенные свойства, перечисления, коллекции, модули).
    // CompletionItemKind выводится из типа-значения. К library-сущностям
    // применяется library-gating (#Использовать / свой пакет / implicit) через
    // libraryNameVisible; платформенные и конфигурационные имена не ограничиваются.
    for (var ctx : globalScopeProvider.globalProperties(fileType)) {
      var name = ctx.name();
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
      item.setKind(completionKindForGlobalProperty(ctx, fileType));
      applySortText(item, BUCKET_GLOBAL, false);
      applyCommitCharacters(item);
      items.add(item);
    }

    // Global functions. Один и тот же двуязычный дескриптор зарегистрирован
    // под ru- и en-ключом, поэтому в values() встречается дважды — дедуп по
    // primary-имени через seenFn.
    var target = PlatformMemberVersions.targetCompatibilityMode(documentContext, configuration);
    var seenFn = new HashSet<String>();
    for (var fn : globalScopeProvider.globalFunctions(fileType)) {
      if (!seenFn.add(fn.name())) {
        continue;
      }
      var displayName = fn.displayName(scriptVariant);
      if (matches(displayName, prefix)) {
        var item = toCompletionItem(fn, fileType, scriptVariant, target, documentContext.getUri());
        applySortText(item, BUCKET_GLOBAL, isMemberDeprecated(fn, target));
        items.add(item);
      }
    }

    // Local methods of current document
    for (var method : documentContext.getSymbolTree().getMethods()) {
      if (matches(method.getName(), prefix)) {
        var item = new CompletionItem(method.getName());
        item.setKind(method.isFunction() ? CompletionItemKind.Function : CompletionItemKind.Method);
        applyCallableInsertText(item, method.getName(), !method.getParameters().isEmpty());
        applySourceMethodDetail(item, method);
        applySourceMethodDocumentation(item, method);
        if (method.isDeprecated()) {
          markDeprecatedItem(item);
        }
        applySortText(item, BUCKET_LOCAL, method.isDeprecated());
        applyCommitCharacters(item);
        items.add(item);
      }
    }

    // Local variables of current document
    for (var variable : documentContext.getSymbolTree().getVariables()) {
      if (matches(variable.getName(), prefix)) {
        var item = new CompletionItem(variable.getName());
        item.setKind(CompletionItemKind.Variable);
        applySortText(item, BUCKET_LOCAL, false);
        applyCommitCharacters(item);
        items.add(item);
      }
    }

    // Keywords: ru/en-написания не дедупятся — общей идентичности у кейвордов нет
    // (в отличие от имён типов), поэтому фильтр по языку к ним не применяется.
    for (var keyword : globalScopeProvider.getKeywords(fileType)) {
      if (matches(keyword, prefix)) {
        var item = new CompletionItem(keyword);
        item.setKind(CompletionItemKind.Keyword);
        applySortText(item, BUCKET_KEYWORD, false);
        items.add(item);
      }
    }

    return items;
  }

  /**
   * Иконка completion для свойства-члена GLOBAL_CONTEXT,
   * выведенная из типа-значения: перечисление → {@code Enum}; library-модуль
   * OneScript (модульный тип в OS-файле) → {@code Module}; иначе (платформенное
   * свойство, коллекция, общий модуль) → {@code Variable}.
   */
  private CompletionItemKind completionKindForGlobalProperty(MemberDescriptor member, FileType fileType) {
    var valueType = member.returnTypes().refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
    if (typeRegistry.isEnumType(valueType)) {
      return CompletionItemKind.Enum;
    }
    if (fileType == FileType.OS && globalScopeProvider.moduleUriByType(valueType).isPresent()) {
      return CompletionItemKind.Module;
    }
    return CompletionItemKind.Variable;
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

  /**
   * Сборка completion item глобальной функции (no-dot). Метки/виды/детали/вставка строятся
   * жадно. {@code documentation} тяжела для глобального контекста, поэтому при поддержке
   * клиентом lazy-resolve она вовсе не строится изначально, а откладывается в
   * {@code completionItem/resolve}: вместо текста в {@code data} кладётся ключ восстановления
   * функции по имени (см. {@link CompletionData}). Клиент без resolveSupport получает её сразу.
   */
  private CompletionItem toCompletionItem(MemberDescriptor member, FileType fileType,
                                          Language scriptVariant, CompatibilityMode target, URI uri) {
    var item = buildMemberItem(member, documentationResolveSupport,
      CompletionItemKind.Function, CompletionItemKind.Variable, scriptVariant, target);
    if (documentationResolveSupport) {
      item.setData(CompletionData.forFunction(uri, member.name(), fileType, scriptVariant));
    }
    return item;
  }

  private List<CompletionItem> toCompletionItems(Collection<MemberDescriptor> members,
                                                 Map<String, TypeRef> owners,
                                                 FileType fileType,
                                                 Language scriptVariant,
                                                 CompatibilityMode target,
                                                 URI uri) {
    var items = new ArrayList<CompletionItem>(members.size());
    for (var member : members) {
      var owner = owners.get(member.name());
      // documentation откладывается в resolve только когда член резолвим обратно по
      // owner-типу. Локальные поля (owner == null) резолвить нечем — documentation строится сразу.
      var deferDocumentation = documentationResolveSupport && owner != null;
      var item = buildMemberItem(member, deferDocumentation,
        CompletionItemKind.Method, CompletionItemKind.Property, scriptVariant, target);
      if (deferDocumentation) {
        item.setData(CompletionData.forMember(
          uri, owner.kind(), owner.qualifiedName(), member.name(), fileType, scriptVariant));
      }
      items.add(item);
    }
    return items;
  }

  /**
   * Сборка completion item для метода или свойства типа.
   * <p>
   * Разделение полей по LSP-конвенции:
   * <ul>
   *   <li>сигнатура {@code (param1, optional?)} и возвращаемый тип для методов, имя типа для
   *       свойств — техническая сводка. При поддержке клиентом {@code labelDetailsSupport}
   *       (LSP 3.17) она кладётся в {@code labelDetails} (detail/description) рядом с именем,
   *       иначе — в плоский {@code detail} (см. {@link #applyDetail}). Никогда не дублирует
   *       описание.</li>
   *   <li>{@code documentation} — содержательное описание (с deprecation-блоком, если есть).</li>
   * </ul>
   * Раньше {@code purposeDescription} писалось одновременно в {@code detail} и в
   * {@code documentation} — VS Code показывал его дважды в подсказке.
   *
   * @param deferDocumentation если {@code true}, {@code documentation} вовсе не строится:
   *                           она будет дотянута лениво в {@code completionItem/resolve}
   *                           (ключ восстановления в {@code data} проставляет вызывающий).
   *                           Если {@code false} — собирается жадно (прежнее поведение).
   */
  private CompletionItem buildMemberItem(MemberDescriptor member,
                                         boolean deferDocumentation,
                                         CompletionItemKind methodKind,
                                         CompletionItemKind propertyKind,
                                         Language scriptVariant,
                                         CompatibilityMode target) {
    var displayName = member.displayName(scriptVariant);
    var item = new CompletionItem(displayName);
    if (member.kind() == MemberKind.METHOD) {
      item.setKind(methodKind);
      applyCallableInsertText(item, displayName, memberHasParameters(member));
      applyMethodDetail(item, member, scriptVariant);
    } else {
      item.setKind(propertyKind);
      applyPropertyDetail(item, member, scriptVariant);
    }
    // documentation тяжела для широких типов (Глобальный контекст, union типов):
    // если её откладывают в completionItem/resolve, изначально не строим вовсе
    // (data-ключ проставит вызывающий). Иначе строим жадно (прежнее поведение).
    if (!deferDocumentation) {
      applyDocumentation(item, member, scriptVariant);
    }
    markDeprecated(item, member, target);
    applyCommitCharacters(item);
    return item;
  }

  /** Помечает item устаревшим, если {@code member} устарел для целевой версии. */
  private void markDeprecated(CompletionItem item, MemberDescriptor member, CompatibilityMode target) {
    if (isMemberDeprecated(member, target)) {
      markDeprecatedItem(item);
    }
  }

  /**
   * Проставляет {@code sortText} пункту автодополнения по схеме «корзина + флаг
   * устаревания + label». Клиент сортирует пункты лексикографически по
   * {@code sortText}: меньший префикс корзины поднимает группу выше, а
   * устаревшие члены внутри корзины опускаются вниз (флаг {@code 1} против
   * {@code 0}), даже если их имя лексикографически меньше неустаревшего соседа.
   * Внутри корзины при равном статусе порядок стабилен по {@code label}.
   *
   * @param item       пункт автодополнения, которому проставляется {@code sortText}.
   * @param bucket     префикс корзины ранжирования (см. {@code BUCKET_*}).
   * @param deprecated {@code true}, если член устарел и должен опускаться вниз корзины.
   */
  private static void applySortText(CompletionItem item, String bucket, boolean deprecated) {
    item.setSortText(bucket + (deprecated ? "1" : "0") + "_" + item.getLabel());
  }

  /**
   * Проставляет {@code commitCharacters} пункту автодополнения по его
   * {@link CompletionItemKind}, если клиент объявил поддержку
   * {@code completionItem.commitCharactersSupport}. Commit character —
   * символ, ввод которого фиксирует пункт и сразу вставляет его вместе с
   * этим символом. Набор подбирается по смыслу пункта: члены-объекты и
   * переменные/модули фиксируются точкой {@code "."} (после фиксации осмысленно
   * дальнейшее обращение к члену). Вызываемым (метод/функция/конструктор)
   * commit characters НЕ задаются: их {@code insertText} уже вставляет открывающую
   * скобку (см. {@link #applyCallableInsertText}), а commit character по спецификации
   * LSP добавляется после текста пункта — символ {@code "("} продублировал бы скобку
   * ({@code Имя((}) и сломал signatureHelp. Ключевым словам и прочим пунктам commit
   * characters также не задаются.
   *
   * @param item пункт автодополнения, которому проставляются commit characters.
   */
  private void applyCommitCharacters(CompletionItem item) {
    if (!commitCharactersSupport) {
      return;
    }
    var kind = item.getKind();
    if (kind == null) {
      return;
    }
    switch (kind) {
      // Вызываемым (метод/функция/конструктор) commit character "(" НЕ задаётся: их
      // insertText уже вставляет открывающую скобку (`Имя($0)` / `Имя(` / `Имя()`, см.
      // applyCallableInsertText), а commit character по спецификации LSP добавляется ПОСЛЕ
      // текста пункта. Получилась бы дублирующая скобка `Имя((` со сломанным signatureHelp.
      case Field, Property, Variable, Module -> item.setCommitCharacters(List.of("."));
      default -> {
        // методы/функции/конструкторы (скобку даёт insertText), ключевые слова, классы
        // и прочие пункты commit characters не получают
      }
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
        applyConstructorDetail(item, ctors, scriptVariant);
      }
      var desc = typeService.getDescription(ref, scriptVariant, fileType);
      if (!desc.isEmpty()) {
        setDocumentation(item, desc);
      }
    }
    applyCallableInsertText(item, className, ctorHasParameters);
    return item;
  }

  /**
   * Детали конструктора в позиции после {@code Новый}: сигнатура «{@code (Пар1, Пар2?)}» единственной
   * перегрузки либо счётчик вариантов при нескольких перегрузках — теми же
   * {@link #applyDetail(CompletionItem, String, String)} / {@link #formatParameterList} /
   * {@link #formatSignaturesCount}, что и {@link #applyMethodDetail} для методов, чтобы конструкторы
   * и платформенные методы выглядели одинаково (в т.ч. {@code labelDetails} при поддержке клиентом).
   * Тип возврата не показываем: результат конструктора — сам класс, дублирующий label.
   *
   * @param item          пункт автодополнения класса.
   * @param constructors  сигнатуры конструкторов класса (одна или несколько перегрузок).
   * @param scriptVariant язык отображаемых имён параметров.
   */
  private void applyConstructorDetail(CompletionItem item, List<SignatureDescriptor> constructors,
                                      Language scriptVariant) {
    if (constructors.size() > 1) {
      applyDetail(item, formatSignaturesCount(constructors.size(), scriptVariant), "");
      return;
    }
    applyDetail(item, formatParameterList(constructors.get(0), scriptVariant), "");
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

  /**
   * Заполняет детали пункта автодополнения для метода. При поддержке клиентом
   * {@code completionItem.labelDetailsSupport} сигнатура «{@code (Пар1, Пар2?)}» кладётся в
   * {@link CompletionItemLabelDetails#setDetail}, возвращаемый тип — в
   * {@link CompletionItemLabelDetails#setDescription}, а плоский {@code detail} не заполняется,
   * чтобы клиент не показывал сигнатуру дважды. Иначе сигнатура и тип возврата записываются в
   * плоский {@code detail} строкой «{@code (Пар1, Пар2?): Тип}» (прежнее поведение). Несколько
   * перегрузок передаются строкой-счётчиком вариантов синтаксиса.
   *
   * @param item          пункт автодополнения, которому проставляются детали.
   * @param member        описатель члена-метода, из которого берутся сигнатуры.
   * @param scriptVariant язык отображаемых имён параметров и типа возврата.
   */
  private void applyMethodDetail(CompletionItem item, MemberDescriptor member, Language scriptVariant) {
    var signatures = member.signatures();
    if (signatures.size() > 1) {
      var count = formatSignaturesCount(signatures.size(), scriptVariant);
      applyDetail(item, count, "");
      return;
    }
    if (signatures.isEmpty()) {
      return;
    }
    var signature = signatures.get(0);
    var paramList = formatParameterList(signature, scriptVariant);
    var returnTypeName = formatTypeName(signature.returnType(), scriptVariant);
    applyDetail(item, paramList, returnTypeName);
  }

  /**
   * Заполняет детали пункта автодополнения для свойства. При поддержке клиентом
   * {@code completionItem.labelDetailsSupport} имя типа члена кладётся в
   * {@link CompletionItemLabelDetails#setDescription}, а плоский {@code detail} не заполняется.
   * Иначе имя типа записывается в плоский {@code detail} (прежнее поведение).
   *
   * @param item          пункт автодополнения, которому проставляются детали.
   * @param member        описатель члена-свойства, из которого берётся тип.
   * @param scriptVariant язык отображаемого имени типа.
   */
  private void applyPropertyDetail(CompletionItem item, MemberDescriptor member, Language scriptVariant) {
    var typeName = formatTypeName(member.returnType(), scriptVariant);
    applyDetail(item, "", typeName);
  }

  /**
   * Раскладывает сигнатуру и тип по полям пункта автодополнения с учётом клиентских
   * capabilities. При поддержке {@code completionItem.labelDetailsSupport} {@code signature}
   * (например, «{@code (Пар1, Пар2?)}») идёт в {@link CompletionItemLabelDetails#setDetail}, а
   * {@code type} — в {@link CompletionItemLabelDetails#setDescription}; плоский {@code detail}
   * не трогается. Иначе оба склеиваются в плоский {@code detail} строкой
   * «{@code signature: type}», а если signature пустая — записывается только {@code type}.
   *
   * @param item      пункт автодополнения, которому проставляются детали.
   * @param signature сигнатура «{@code (...)}» либо строка-счётчик вариантов; может быть пустой.
   * @param type      возвращаемый тип метода или тип свойства; может быть пустым.
   */
  private void applyDetail(CompletionItem item, String signature, String type) {
    if (labelDetailsSupport) {
      if (signature.isBlank() && type.isBlank()) {
        return;
      }
      var labelDetails = new CompletionItemLabelDetails();
      if (!signature.isBlank()) {
        labelDetails.setDetail(signature);
      }
      if (!type.isBlank()) {
        labelDetails.setDescription(type);
      }
      item.setLabelDetails(labelDetails);
      return;
    }
    var sb = new StringBuilder(signature);
    if (!type.isBlank()) {
      if (sb.length() > 0) {
        sb.append(": ");
      }
      sb.append(type);
    }
    if (sb.length() > 0) {
      item.setDetail(sb.toString());
    }
  }

  /** Сигнатура «{@code (Пар1, Пар2?)}» с пометкой «?» у необязательных параметров. */
  private String formatParameterList(SignatureDescriptor signature, Language scriptVariant) {
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
    return sb.toString();
  }

  /**
   * Заполняет детали пункта автодополнения для локального (source-defined) метода: сигнатуру
   * «{@code (Пар1, Пар2?)}» и — для функций с задокументированным типом возврата — имя типа,
   * уложенные тем же {@link #applyDetail(CompletionItem, String, String)}, что и для платформенных
   * методов ({@link #applyMethodDetail}). Благодаря этому пользовательские и платформенные методы
   * выглядят в автодополнении одинаково.
   *
   * @param item   пункт автодополнения локального метода.
   * @param method символ локального метода (процедуры/функции) текущего документа.
   */
  private void applySourceMethodDetail(CompletionItem item, MethodSymbol method) {
    var paramList = formatSourceParameterList(method);
    var returnTypeName = method.isFunction() ? sourceReturnTypeName(method) : "";
    applyDetail(item, paramList, returnTypeName);
  }

  /**
   * Сигнатура «{@code (Пар1, Пар2?)}» по параметрам локального метода: имя параметра, необязательные
   * (с значением по умолчанию) помечаются «{@code ?}». Зеркалит {@link #formatParameterList} для
   * платформенных методов, отличаясь лишь источником данных
   * ({@link com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition}).
   *
   * @param method символ локального метода.
   * @return строка сигнатуры в круглых скобках.
   */
  private static String formatSourceParameterList(MethodSymbol method) {
    var sb = new StringBuilder();
    sb.append('(');
    var params = method.getParameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      var parameter = params.get(i);
      sb.append(parameter.getName());
      if (parameter.isOptional()) {
        // Необязательный параметр помечаем «?» после имени: ИмяПараметра?.
        sb.append('?');
      }
    }
    sb.append(')');
    return sb.toString();
  }

  /**
   * Имя типа возвращаемого значения локальной функции из её doc-comment'а
   * ({@code Возвращаемое значение:}). Несколько типов объединяются через «{@code  | }» (как в hover);
   * если тип не задокументирован — пустая строка (BSL не типизирован, иного источника нет).
   *
   * @param method символ локальной функции.
   * @return имя типа возврата либо пустая строка.
   */
  private static String sourceReturnTypeName(MethodSymbol method) {
    return method.getDescription()
      .map(MethodDescription::getReturnedValue)
      .map(types -> types.stream()
        .map(TypeDescription::name)
        .flatMap(name -> Arrays.stream(name.split(",")))
        .map(String::trim)
        .filter(name -> !name.isEmpty())
        .collect(Collectors.joining(" | ")))
      .orElse("");
  }

  /**
   * Документация пункта автодополнения для локального метода из его doc-comment'а: назначение и,
   * если метод устарел, причина устаревания (сам факт устаревания клиенту передаёт LSP-тег, см.
   * {@link #markDeprecatedItem}). Зеркалит {@link #applyDocumentation} для source-символов.
   *
   * @param item   пункт автодополнения локального метода.
   * @param method символ локального метода.
   */
  private void applySourceMethodDocumentation(CompletionItem item, MethodSymbol method) {
    method.getDescription().ifPresent(description -> {
      var sb = new StringBuilder();
      if (description.isDeprecated() && !description.getDeprecationInfo().isBlank()) {
        sb.append(description.getDeprecationInfo());
        if (!description.getPurposeDescription().isBlank()) {
          sb.append("\n\n");
        }
      }
      if (!description.getPurposeDescription().isBlank()) {
        sb.append(description.getPurposeDescription());
      }
      if (sb.length() > 0) {
        setDocumentation(item, sb.toString());
      }
    });
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