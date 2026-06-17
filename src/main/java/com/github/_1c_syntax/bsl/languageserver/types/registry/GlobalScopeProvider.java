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

import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextLanguageKeyword;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.context.platform.EnAttachments;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Workspace-scoped реестр глобальной области видимости:
 * глобальные функции/процедуры, имена платформенных классов
 * (доступные после {@code Новый}), ключевые слова BSL.
 * <p>
 * Источник — JSON-ресурс {@code builtin-globals.json}. Точка расширения —
 * подключение внешнего {@code platform-context} провайдера в будущем.
 */
@Slf4j
@Component
@WorkspaceScope
public class GlobalScopeProvider {

  private static final String RETURN_TYPE_FIELD = "returnType";
  private static final String ALIASES_FIELD = "aliases";

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-globals.json";
  private static final String OSCRIPT_RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-globals.json";
  private static final String KEYWORDS_RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-keywords.json";
  private static final String OSCRIPT_KEYWORDS_RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-keywords.json";

  /**
   * Данные каждого языка по отдельности — единственное хранилище глобалов:
   * снапшот загрузки ({@link FileType#BSL} ← bsl-context либо JSON-fallback,
   * {@link FileType#OS} ← oscript-JSON) плюс runtime-реестр имён глобальных
   * свойств ({@code LanguageData.globalContextNames}). Все lookup'ы читают
   * набор языка файла-потребителя.
   */
  private final Map<FileType, LanguageData> byFileType;
  /**
   * Хранилище типов: источник членов синтетического {@link TypeRegistry#GLOBAL_CONTEXT}.
   * Читается (не пишется) для резолва безпрефиксных имён — direction
   * {@code GlobalScopeProvider → TypeRegistry}, без цикла (issue #3994).
   */
  private final TypeRegistry typeRegistry;
  /** Эпоха-кэшированный name-индекс членов GLOBAL_CONTEXT (см. {@link #globalMember}). */
  private final AtomicReference<GlobalIndex> globalIndexRef = new AtomicReference<>();
  /**
   * URI документа-модуля → его тип-значение (обратный индекс к name-keyed записям).
   * Заполняется провайдерами регистрации модулей ({@code ConfigurationModuleMembersProvider}
   * для общих модулей, {@code OScriptModuleMembersProvider} для library-модулей) синхронно
   * рядом с регистрацией имени; читается выводом типа ресивера-модуля по {@code ModuleSymbol}
   * (у которого на руках URI, а не имя). Единая точка вместо обращения инференсера к
   * двум URI-ключевым индексам подсистем.
   */
  private final Map<URI, TypeRef> moduleTypeByUri = new ConcurrentHashMap<>();
  /**
   * Обратный индекс к {@link #moduleTypeByUri}: тип-значение модуля → URI документа,
   * объявившего этот тип. Заполняется синхронно вместе с {@link #moduleTypeByUri}
   * (см. {@link #indexModuleType}/{@link #removeModuleType}). Используется навигацией
   * по типу к объявившему модулю (общий модуль, модуль менеджера объекта,
   * library-модуль OneScript) — у потребителя на руках {@link TypeRef}, а не URI.
   */
  private final Map<TypeRef, URI> uriByModuleType = new ConcurrentHashMap<>();
  /**
   * Каноничные «составные» имена MD-объектов конфигурации в коллекционной
   * форме ({@code Справочники.Контрагенты}, {@code Documents.Документ1}).
   * Поддерживается no-dot completion: пользователь печатает {@code Докум} —
   * подсказывается и {@code Документы}, и {@code Документы.Документ1}.
   */
  private final Set<String> configurationQualifiedNames =
    Collections.newSetFromMap(new ConcurrentHashMap<>());

  /**
   * Загружает наполнение глобальной области:
   * <ul>
   *   <li>OneScript (OS) — всегда из ресурса {@link #OSCRIPT_RESOURCE_PATH};</li>
   *   <li>BSL — целиком из bsl-context, если платформа 1С установлена и парсинг
   *       прошёл (функции из глобального контекста, классы для {@code Новый} —
   *       из ContextType, ключевые слова — из {@code LANGUAGE_KEYWORD}
   *       категорий LITERAL/STATEMENT/OPERATOR/DECLARATION, платформенные
   *       переменные — из {@code ContextEnum}). JSON в этом случае не читается.
   *       Если bsl-context недоступен — fallback на ресурс {@link #RESOURCE_PATH}.</li>
   * </ul>
   *
   * @param bslContextHolder источник BSL-глобалов из синтакс-помощника
   *                         установленной платформы 1С (через {@code bsl-context}).
   *                         Если платформа найдена — её содержимое заменяет
   *                         встроенный {@code builtin-globals.json} для BSL-части
   *                         (OS-часть всегда из ресурса). Если платформа недоступна —
   *                         fallback на JSON-ресурс.
   */
  public GlobalScopeProvider(BslContextHolder bslContextHolder, TypeRegistry typeRegistry) {
    this.typeRegistry = typeRegistry;
    var os = loadFromResource(OSCRIPT_RESOURCE_PATH);
    var bsl = loadBsl(bslContextHolder);
    this.byFileType = Map.of(FileType.BSL, bsl, FileType.OS, os);
  }

  /**
   * Резолв безпрефиксного имени в член глобальной области — синтетического типа
   * {@link TypeRegistry#GLOBAL_CONTEXT} (глобальная функция-метод либо глобальное
   * свойство: перечисление, менеджер коллекции, общий/library-модуль). Быстрый
   * lookup по name-индексу, пересобираемому при смене эпохи членов
   * ({@link TypeRegistry#membersEpoch()}). Issue #3994: единая абстракция доступа
   * к глобальной области; {@link TypeRegistry} остаётся хранилищем типов.
   *
   * @param name     имя (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return член глобального контекста или {@link Optional#empty()}.
   */
  public Optional<MemberDescriptor> globalMember(@org.jspecify.annotations.Nullable String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var epoch = typeRegistry.membersEpoch();
    var index = globalIndexRef.get();
    if (index == null || index.epoch() != epoch) {
      index = new GlobalIndex(epoch, Map.of(
        FileType.BSL, globalNameIndex(FileType.BSL),
        FileType.OS, globalNameIndex(FileType.OS)));
      globalIndexRef.set(index);
    }
    return Optional.ofNullable(index.byName().get(fileType).get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Безпрефиксное имя как глобальная функция — метод-член
   * {@link TypeRegistry#GLOBAL_CONTEXT}.
   *
   * @param name     имя (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return метод-член или {@link Optional#empty()}, если имя не глобальная функция.
   */
  public Optional<MemberDescriptor> globalFunction(@org.jspecify.annotations.Nullable String name, FileType fileType) {
    return globalMember(name, fileType).filter(member -> member.kind() == MemberKind.METHOD);
  }

  /**
   * Безпрефиксное имя как глобальное свойство — свойство-член
   * {@link TypeRegistry#GLOBAL_CONTEXT} (перечисление, менеджер коллекции,
   * общий/library-модуль).
   *
   * @param name     имя (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return свойство-член или {@link Optional#empty()}, если имя не глобальное свойство.
   */
  public Optional<MemberDescriptor> globalProperty(@org.jspecify.annotations.Nullable String name, FileType fileType) {
    return globalMember(name, fileType).filter(member -> member.kind() == MemberKind.PROPERTY);
  }

  /**
   * Все глобальные свойства — свойства-члены {@link TypeRegistry#GLOBAL_CONTEXT}
   * (перечисления, менеджеры коллекций, общие/library-модули). Перечисляющий
   * аналог {@link #globalProperty} для потребителей, которым нужен весь набор
   * (completion). Issue #3994: enumerate-доступ к глобальной области — тоже через
   * эту абстракцию, а не прямым чтением {@code GLOBAL_CONTEXT}.
   *
   * @param fileType язык файла-потребителя.
   * @return свойства-члены глобального контекста (порядок — как у источника членов).
   */
  public List<MemberDescriptor> globalProperties(FileType fileType) {
    return globalMembersOfKind(fileType, MemberKind.PROPERTY);
  }

  /**
   * Все глобальные функции — методы-члены {@link TypeRegistry#GLOBAL_CONTEXT}.
   * Перечисляющий аналог {@link #globalFunction}.
   *
   * @param fileType язык файла-потребителя.
   * @return методы-члены глобального контекста.
   */
  public List<MemberDescriptor> globalFunctions(FileType fileType) {
    return globalMembersOfKind(fileType, MemberKind.METHOD);
  }

  private List<MemberDescriptor> globalMembersOfKind(FileType fileType, MemberKind kind) {
    var result = new ArrayList<MemberDescriptor>();
    for (var member : typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, fileType)) {
      if (member.kind() == kind) {
        result.add(member);
      }
    }
    return result;
  }

  private Map<String, MemberDescriptor> globalNameIndex(FileType fileType) {
    var map = new HashMap<String, MemberDescriptor>();
    for (var member : typeRegistry.getMembers(TypeRegistry.GLOBAL_CONTEXT, fileType)) {
      var ru = member.bilingualName().ru();
      var en = member.bilingualName().en();
      if (!ru.isBlank()) {
        map.putIfAbsent(ru.toLowerCase(Locale.ROOT), member);
      }
      if (!en.isBlank()) {
        map.putIfAbsent(en.toLowerCase(Locale.ROOT), member);
      }
    }
    return map;
  }

  /** Эпоха-кэшированный индекс имён членов GLOBAL_CONTEXT в разрезе языка. */
  private record GlobalIndex(long epoch, Map<FileType, Map<String, MemberDescriptor>> byName) {
  }

  /**
   * Двуязычный сниппет автодополнения для keyword'а (например,
   * {@code Если <?> Тогда\nИначеЕсли\nКонецЕсли;}) из набора указанного языка.
   * Источник для BSL — парные {@code .st}-файлы из {@code shlang_*.hbk};
   * если bsl-context недоступен или у keyword'а сниппета нет — {@link Optional#empty()}.
   * Поиск по lowercased имени, как ru, так и en.
   *
   * @param name     имя ключевого слова (регистронезависимо, ru/en)
   * @param fileType язык файла-потребителя
   * @return сниппет или {@link Optional#empty()}
   */
  public Optional<LanguageKeywordSnippet> findKeywordSnippet(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byFileType.get(fileType).keywordSnippets.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Описание keyword'а из набора указанного языка (например, для {@code Истина} —
   * «Литерал для указания значения типа Булево.») в указанной локали LS
   * (с fallback на другую локаль, если запрошенная пуста). Контекстно-зависимый
   * lookup: если у keyword'а есть описание для указанной родительской
   * конструкции (например, {@code "Функция"} или {@code "Процедура"} для
   * {@code Async}/{@code Знач}/{@code Возврат}) — возвращается оно; иначе
   * generic-описание. Для имён, существующих в обоих языках, описание другого
   * языка не подставляется.
   *
   * @param name          имя ключевого слова (регистронезависимо, ru/en)
   * @param language      локаль интерфейса LS
   * @param parentContext ru-имя родительской конструкции
   *                      (из {@link com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword#descriptionByParent});
   *                      {@code null} — generic-описание
   * @param fileType      язык файла-потребителя
   * @return локализованное описание ключевого слова или {@link Optional#empty()}
   */
  public Optional<String> findKeywordDescription(String name,
      Language language,
      String parentContext,
      FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var entry = byFileType.get(fileType).keywordDescriptions.get(name.toLowerCase(Locale.ROOT));
    if (entry == null) {
      return Optional.empty();
    }
    var description = entry.forContext(parentContext);
    if (description == null || description.isEmpty()) {
      return Optional.empty();
    }
    var localized = description.forLanguage(language);
    if (localized == null || localized.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(localized);
  }

  /**
   * Контекстно-зависимое описание keyword'а: основное описание
   * {@link #primary()} плюс набор описаний {@link #byParent()} по родительским
   * конструкциям (ru-имя родителя → пара ru/en описаний). Источник —
   * {@code PlatformLanguageKeyword.descriptionByParent()} из bsl-context.
   * <p>
   * Метод {@link #forContext(String)} возвращает описание для указанной
   * родительской конструкции; если такового нет — {@link #primary()}.
   *
   * @param primary  основное (контекстно-независимое) описание keyword'а
   * @param byParent описания по родительским конструкциям: ru-имя родителя → пара ru/en описаний
   */
  public record KeywordDescription(BilingualString primary, Map<String, BilingualString> byParent) {
    public static final KeywordDescription EMPTY = new KeywordDescription(BilingualString.EMPTY, Map.of());

    public KeywordDescription {
      byParent = byParent == null ? Map.of() : Map.copyOf(byParent);
    }

    public BilingualString forContext(String parentRuName) {
      if (parentRuName != null && !parentRuName.isBlank()) {
        var ctx = byParent.get(parentRuName);
        if (ctx != null && !ctx.isEmpty()) {
          return ctx;
        }
      }
      // Не нашли точного match по parent'у. byParent содержит реальные описания
      // из СП — они полезнее generic-заглушки primary вида «Часть конструкции
      // «Процедура»». Возвращаем первый непустой byParent (детерминированный
      // порядок — LinkedHashMap, заполняется по порядку обхода parent-страниц).
      for (var bi : byParent.values()) {
        if (!bi.isEmpty()) {
          return bi;
        }
      }
      return primary;
    }

    public boolean isEmpty() {
      return primary.isEmpty() && byParent.isEmpty();
    }
  }

  /**
   * Имена платформенных классов, доступных в выражении {@code Новый}
   * в файлах указанного языка.
   *
   * @param fileType язык файла-потребителя.
   * @return имена классов.
   */
  public List<String> getClasses(FileType fileType) {
    return byFileType.get(fileType).classes;
  }

  /**
   * Ключевые слова указанного языка для completion в no-dot контексте.
   *
   * @param fileType язык файла-потребителя.
   * @return ключевые слова.
   */
  public List<String> getKeywords(FileType fileType) {
    return byFileType.get(fileType).keywords;
  }

  /**
   * Связать URI документа-модуля с его типом-значением. Вызывается провайдерами
   * регистрации модулей синхронно рядом с регистрацией имени. Повторный вызов с тем
   * же URI перезаписывает тип (корректно отражает переименование модуля).
   */
  public void indexModuleType(URI uri, TypeRef ref) {
    var previous = moduleTypeByUri.put(uri, ref);
    if (previous != null && !previous.equals(ref)) {
      // Тип модуля сменился (переименование): чистим устаревшую обратную запись,
      // только если она всё ещё указывает на этот же URI.
      uriByModuleType.remove(previous, uri);
    }
    uriByModuleType.put(ref, uri);
  }

  /**
   * Снять связь URI→тип (при удалении документа/дерегистрации library-модуля).
   */
  public void removeModuleType(URI uri) {
    var ref = moduleTypeByUri.remove(uri);
    if (ref != null) {
      uriByModuleType.remove(ref, uri);
    }
  }

  /**
   * Тип-значение модуля по URI документа. Используется выводом типа ресивера-модуля
   * ({@code ModuleSymbol}), у которого есть URI, но нет имени для name-keyed lookup'а.
   */
  public Optional<TypeRef> moduleTypeByUri(URI uri) {
    return Optional.ofNullable(moduleTypeByUri.get(uri));
  }

  /**
   * URI документа-модуля, объявившего тип, по самому типу — обратная операция к
   * {@link #moduleTypeByUri(URI)}. Используется навигацией по выведенному типу к
   * объявившему его модулю (общий модуль, модуль менеджера объекта конфигурации,
   * library-модуль OneScript).
   *
   * @param ref тип-значение модуля.
   * @return URI документа, объявившего тип, либо {@code empty}, если тип не модульный
   *   (не зарегистрирован через {@link #indexModuleType}).
   */
  public Optional<URI> moduleUriByType(TypeRef ref) {
    return Optional.ofNullable(uriByModuleType.get(ref));
  }


  /**
   * Зарегистрировать каноничное составное имя MD-объекта конфигурации
   * (например, {@code Документы.Документ1} или {@code Documents.Документ1}).
   * Используется в no-dot completion.
   */
  public void registerConfigurationQualifiedName(String qualifiedName) {
    if (qualifiedName == null || qualifiedName.isBlank()) {
      return;
    }
    configurationQualifiedNames.add(qualifiedName);
  }

  /**
   * @return Каноничные составные имена MD-объектов в исходном написании.
   */
  public Collection<String> getConfigurationQualifiedNames() {
    return List.copyOf(configurationQualifiedNames);
  }


  /**
   * BSL-часть глобальной области: либо из bsl-context (если есть), либо
   * из {@link #RESOURCE_PATH} JSON (fallback). JSON не читается, когда
   * bsl-context дал результат — иначе данные дублируются.
   */
  private static LanguageData loadBsl(BslContextHolder bslContextHolder) {
    var providerOpt = bslContextHolder.get();
    if (providerOpt.isPresent()) {
      return buildBslFromContext(providerOpt.get());
    }
    return loadFromResource(RESOURCE_PATH);
  }

  private static LanguageData buildBslFromContext(ContextProvider provider) {
    var globalContext = provider.getGlobalContext();
    // Bilingual: если provider — Platform, тащим en-attachments для всех
    // глобальных функций (description, returnValueDescription, notes,
    // examples, seeAlso). MemberDescriptor станет bilingual.
    Function<Object, EnAttachments> enLookup =
      provider instanceof PlatformContextProvider pcp
        ? pcp::getEnAttachments
        : ctx -> EnAttachments.EMPTY;

    var functions = new LinkedHashMap<String, MemberDescriptor>();
    if (globalContext != null) {
      for (var method : globalContext.methods()) {
        var descriptor = BslContextPlatformTypesProvider.toMemberDescriptor(method, enLookup);
        putFunction(functions, method.name().getName(), descriptor);
        putFunction(functions, method.name().getAlias(), descriptor);
      }
    }

    var classes = new ArrayList<String>();
    var classSeen = new HashSet<String>();
    var keywords = new ArrayList<String>();
    var keywordSeen = new HashSet<String>();
    var keywordSnippets = new HashMap<String, LanguageKeywordSnippet>();
    var keywordDescriptions = new HashMap<String, KeywordDescription>();
    var variables = new ArrayList<PlatformVariable>();
    var enums = new ArrayList<PlatformVariable>();
    var variableSeen = new HashSet<String>();

    // Глобальные свойства (Документы, Справочники, БиблиотекаКартинок и т.п.) —
    // top-level имена, доступные без префикса.
    if (globalContext != null) {
      for (var property : globalContext.properties()) {
        addContextProperty(property, variableSeen, variables);
      }
    }

    for (var ctx : provider.getContexts()) {
      if (ctx instanceof ContextType type && !type.isGeneric()) {
        addNameWithAlias(classes, classSeen, type.name().getName(), type.name().getAlias());
      } else if (ctx instanceof ContextEnum enumeration) {
        addContextEnum(enumeration, variableSeen, enums);
      } else if (ctx instanceof ContextLanguageKeyword kw && KEYWORD_CATEGORIES.contains(kw.category())) {
        addContextKeyword(kw, keywords, keywordSeen, keywordSnippets, keywordDescriptions);
      }
    }

    return new LanguageData(
      Collections.unmodifiableMap(functions),
      List.copyOf(classes),
      List.copyOf(keywords),
      List.copyOf(variables),
      List.copyOf(enums),
      Map.copyOf(keywordSnippets),
      Map.copyOf(keywordDescriptions)
    );
  }

  /**
   * Глобальное свойство из СП (Документы, Справочники, БиблиотекаКартинок…):
   * тип берём первый из объявленных (типа СправочникиМенеджер) — для
   * dot-completion'а к коллекции. Generic-плейсхолдеры ({@code <Имя справочника>})
   * и дубли по lowercased имени пропускаются.
   */
  private static void addContextProperty(ContextProperty property,
                                         Set<String> variableSeen,
                                         List<PlatformVariable> variables) {
    if (property.isGeneric()) {
      return;
    }
    var name = property.name().getName();
    if (!variableSeen.add(name.toLowerCase(Locale.ROOT))) {
      return;
    }
    var alias = property.name().getAlias();
    var aliases = alias == null || alias.isBlank() ? List.<String>of() : List.of(alias);
    variables.add(new PlatformVariable(name, aliases, property.description(), firstTypeRef(property)));
  }

  /**
   * Системное перечисление платформы — публикуется в global scope через
   * отдельный список enums; классификация property-vs-enum у потребителей идёт
   * из типа-значения ({@code TypeRegistry.isEnumType}), issue #3994.
   */
  private static void addContextEnum(ContextEnum enumeration,
                                     Set<String> variableSeen,
                                     List<PlatformVariable> enums) {
    var name = enumeration.name().getName();
    if (!variableSeen.add(name.toLowerCase(Locale.ROOT))) {
      return;
    }
    var alias = enumeration.name().getAlias();
    var aliases = alias == null || alias.isBlank() ? List.<String>of() : List.of(alias);
    enums.add(new PlatformVariable(name, aliases, "", new TypeRef(TypeKind.PLATFORM, name)));
  }

  /**
   * Ключевое слово из СП: имя (ru + en) — в completion-список, сниппет и
   * описание (включая контекстно-зависимые {@code descriptionByParent}) —
   * в индексы по обоим написаниям.
   */
  private static void addContextKeyword(ContextLanguageKeyword kw,
                                        List<String> keywords,
                                        Set<String> keywordSeen,
                                        Map<String, LanguageKeywordSnippet> keywordSnippets,
                                        Map<String, KeywordDescription> keywordDescriptions) {
    var added = addNameWithAlias(keywords, keywordSeen, kw.name().getName(), kw.name().getAlias());
    if (!added) {
      return;
    }
    if (!kw.snippet().isEmpty()) {
      // Сниппет по обоим написаниям — пользователь может ввести любое.
      putByNameAndAlias(keywordSnippets, kw, kw.snippet());
    }
    var entry = keywordDescriptionOf(kw);
    if (!entry.isEmpty()) {
      putByNameAndAlias(keywordDescriptions, kw, entry);
    }
  }

  /** Кладёт значение в индекс по lowercased canonical-имени и en-алиасу keyword'а. */
  private static <V> void putByNameAndAlias(Map<String, V> index, ContextLanguageKeyword kw, V value) {
    index.put(kw.name().getName().toLowerCase(Locale.ROOT), value);
    var alias = kw.name().getAlias();
    if (alias != null && !alias.isBlank()) {
      index.put(alias.toLowerCase(Locale.ROOT), value);
    }
  }

  /**
   * Описание keyword'а: основное ru + en (en — из
   * {@link PlatformLanguageKeyword#descriptionEn()}) плюс контекстно-зависимые
   * описания по родительской конструкции (Async/Знач/Возврат имеют разное
   * описание в Функция vs Процедура — см. shlang_*.hbk).
   */
  private static KeywordDescription keywordDescriptionOf(ContextLanguageKeyword kw) {
    var enDesc = kw instanceof PlatformLanguageKeyword pk ? pk.descriptionEn() : "";
    var primary = BilingualString.of(kw.description() == null ? "" : kw.description(), enDesc);
    var byParent = kw instanceof PlatformLanguageKeyword pk
      ? byParentDescriptions(pk)
      : Map.<String, BilingualString>of();
    return new KeywordDescription(primary, byParent);
  }

  /** Двуязычные описания keyword'а по родительским конструкциям (ru-имя родителя → ru/en пара). */
  private static Map<String, BilingualString> byParentDescriptions(PlatformLanguageKeyword pk) {
    var ruByCtx = pk.descriptionByParent();
    var enByCtx = pk.descriptionByParentEn();
    var keys = new LinkedHashSet<String>();
    keys.addAll(ruByCtx.keySet());
    keys.addAll(enByCtx.keySet());
    var byParent = new LinkedHashMap<String, BilingualString>();
    for (var k : keys) {
      var bi = BilingualString.of(ruByCtx.getOrDefault(k, ""), enByCtx.getOrDefault(k, ""));
      if (!bi.isEmpty()) {
        byParent.put(k, bi);
      }
    }
    return byParent;
  }

  /**
   * Берёт первый тип из {@link ContextProperty#types()} и заворачивает в
   * {@link TypeRef}. Если список пуст — {@link TypeRef#UNKNOWN}.
   * Используется при публикации глобальных свойств — у property может быть
   * один или несколько объявленных типов, для resolve dot-completion'а
   * нам достаточно первого.
   */
  private static TypeRef firstTypeRef(ContextProperty property) {
    var types = property.types();
    if (types == null || types.isEmpty()) {
      return TypeRef.UNKNOWN;
    }
    var first = types.get(0);
    var kind = switch (first.kind()) {
      case PRIMITIVE_TYPE -> TypeKind.PRIMITIVE;
      case TYPE, COLLECTION, ENUM -> TypeKind.PLATFORM;
      case GLOBAL_CONTEXT, LANGUAGE_KEYWORD -> TypeKind.UNKNOWN;
    };
    return new TypeRef(kind, first.name().getName());
  }

  /**
   * Категории {@link LanguageKeywordCategory}, пригодные для no-dot completion
   * как «keywords». PRAGMA / ANNOTATION / PREPROCESSOR_INSTRUCTION
   * предваряются специальными префиксами ({@code &} / {@code #}) и
   * обрабатываются другим completion-flow.
   */
  private static final Set<LanguageKeywordCategory> KEYWORD_CATEGORIES =
    EnumSet.of(
      LanguageKeywordCategory.LITERAL,
      LanguageKeywordCategory.STATEMENT,
      LanguageKeywordCategory.OPERATOR,
      LanguageKeywordCategory.DECLARATION
    );

  /**
   * Добавляет ru-имя и en-алиас в общий список с дедупликацией по lowercase.
   * @return {@code true}, если хотя бы одно из имён было добавлено впервые
   *         (нужно для маппинга «ключ → snippet» — он привязывается только
   *         к новым именам).
   */
  private static boolean addNameWithAlias(List<String> sink,
                                          Set<String> seen,
                                          String name, String alias) {
    var added = false;
    if (!name.isBlank() && seen.add(name.toLowerCase(Locale.ROOT))) {
      sink.add(name);
      added = true;
    }
    if (!alias.isBlank() && seen.add(alias.toLowerCase(Locale.ROOT))) {
      sink.add(alias);
      added = true;
    }
    return added;
  }

  private static void putFunction(
    Map<String, MemberDescriptor> functions,
    String name,
    MemberDescriptor descriptor
  ) {
    if (name == null || name.isBlank()) {
      return;
    }
    functions.putIfAbsent(name.toLowerCase(Locale.ROOT), descriptor);
  }

  private static LanguageData loadFromResource(String resourcePath) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> root = mapper.readValue(stream, Map.class);
      var functions = readFunctions(root);
      @SuppressWarnings("unchecked")
      var classes = (List<String>) root.getOrDefault("classes", Collections.emptyList());
      @SuppressWarnings("unchecked")
      var keywords = new ArrayList<>(
        (List<String>) root.getOrDefault("keywords", Collections.emptyList()));
      var variables = readVariables(root);
      // Догружаем структурированные keywords (category/description/snippet)
      // из соседнего ресурса builtin-keywords.json — это JSON-fallback к
      // ContextLanguageKeyword из bsl-context, когда HBK не подключён.
      var keywordMeta = loadKeywordMetadata(keywordsResourceFor(resourcePath));
      var seenKeywords = new HashSet<String>();
      keywords.forEach(k -> seenKeywords.add(k.toLowerCase(Locale.ROOT)));
      for (var k : keywordMeta.keywords()) {
        if (seenKeywords.add(k.toLowerCase(Locale.ROOT))) {
          keywords.add(k);
        }
      }
      return new LanguageData(functions, List.copyOf(classes), List.copyOf(keywords), variables,
        List.of(),
        Map.copyOf(keywordMeta.snippets()), Map.copyOf(keywordMeta.descriptions()));
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin globals resource: {}", resourcePath, e);
      return new LanguageData(Collections.emptyMap(), List.of(), List.of(), List.of(),
        List.of(), Map.of(), Map.of());
    }
  }

  /** Имя соседнего keywords-ресурса для globals-ресурса. */
  private static String keywordsResourceFor(String globalsResourcePath) {
    if (OSCRIPT_RESOURCE_PATH.equals(globalsResourcePath)) {
      return OSCRIPT_KEYWORDS_RESOURCE_PATH;
    }
    return KEYWORDS_RESOURCE_PATH;
  }

  private static KeywordMetadata loadKeywordMetadata(String resourcePath) {
    return KeywordMetadataLoader.load(resourcePath, GlobalScopeProvider::isCompletionCategory);
  }

  /** Категория из JSON-fallback'a, попадающая в плоский completion-список. */
  private static boolean isCompletionCategory(String categoryStr) {
    return KEYWORD_CATEGORIES.stream().anyMatch(c -> c.name().equals(categoryStr));
  }

  /**
   * Глобальные члены для синтетического {@code GLOBAL_CONTEXT} (issue #3994) из
   * встроенного JSON-fallback: {@code functions} → методы-члены, {@code variables}
   * → свойства-члены. Двуязычное имя члена собирается из {@code name} + первого
   * {@code alias}, чтобы резолв работал по обоим написаниям. Временный мост на
   * время cutover'а — при схлопывании GlobalScopeProvider парсинг переедет в
   * выделенный loader, читаемый composer'ом напрямую.
   */
  @SuppressWarnings("unchecked")
  static List<MemberDescriptor> globalContextMembers(String resourcePath) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      Map<String, Object> root = mapper.readValue(stream, Map.class);
      var members = new ArrayList<MemberDescriptor>();
      for (var entry : (List<Map<String, Object>>) root.getOrDefault("functions", Collections.emptyList())) {
        var name = (String) entry.get("name");
        if (name != null) {
          members.add(functionMember(name, entry));
        }
      }
      for (var entry : (List<Map<String, Object>>) root.getOrDefault("variables", Collections.emptyList())) {
        var name = (String) entry.get("name");
        if (name != null && !name.isBlank()) {
          members.add(variableMember(name, entry));
        }
      }
      return members;
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin global members: {}", resourcePath, e);
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static MemberDescriptor functionMember(String name, Map<String, Object> entry) {
    var description = (String) entry.getOrDefault("description", "");
    var returnTypeName = (String) entry.get(RETURN_TYPE_FIELD);
    var returnType = returnTypeName == null
      ? TypeRef.UNKNOWN
      : new TypeRef(TypeKind.PLATFORM, returnTypeName);
    var signatures = readSignatures((List<Map<String, Object>>) entry.get("signatures"), returnType);
    var member = withFirstAliasName(
      MemberDescriptor.method(name, description, signatures).withStandardLibrary(true), entry);
    if (Boolean.TRUE.equals(entry.get("async"))) {
      member = member.withAsync(true);
    }
    var metadata = readGlobalMetadata(entry);
    if (!metadata.isEmpty()) {
      member = member.withMetadata(metadata);
    }
    return member;
  }

  private static MemberDescriptor variableMember(String name, Map<String, Object> entry) {
    var description = (String) entry.getOrDefault("description", "");
    var typeName = (String) entry.get("type");
    var typeRef = typeName == null || typeName.isBlank()
      ? TypeRef.UNKNOWN
      : new TypeRef(TypeKind.PLATFORM, typeName);
    return withFirstAliasName(MemberDescriptor.property(name, typeRef, description), entry);
  }

  @SuppressWarnings("unchecked")
  private static MemberDescriptor withFirstAliasName(MemberDescriptor member, Map<String, Object> entry) {
    var aliases = (List<String>) entry.getOrDefault(ALIASES_FIELD, Collections.emptyList());
    if (aliases.isEmpty() || aliases.get(0) == null || aliases.get(0).isBlank()) {
      return member;
    }
    return member.withBilingualName(BilingualString.of(member.name(), aliases.get(0)));
  }

  @SuppressWarnings("unchecked")
  private static List<PlatformVariable> readVariables(Map<String, Object> root) {
    var raw = (List<Map<String, Object>>) root.getOrDefault("variables", Collections.emptyList());
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<PlatformVariable>(raw.size());
    for (var entry : raw) {
      var name = (String) entry.get("name");
      if (name == null || name.isBlank()) {
        continue;
      }
      var description = (String) entry.getOrDefault("description", "");
      var typeName = (String) entry.get("type");
      var typeRef = typeName == null || typeName.isBlank()
        ? TypeRef.UNKNOWN
        : new TypeRef(TypeKind.PLATFORM, typeName);
      var aliases = (List<String>) entry.getOrDefault(ALIASES_FIELD, Collections.emptyList());
      result.add(new PlatformVariable(name, List.copyOf(aliases), description, typeRef));
    }
    return List.copyOf(result);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, MemberDescriptor> readFunctions(Map<String, Object> root) {
    var raw = (List<Map<String, Object>>) root.getOrDefault("functions", Collections.emptyList());
    var result = new LinkedHashMap<String, MemberDescriptor>();
    for (var entry : raw) {
      var name = (String) entry.get("name");
      if (name == null) {
        continue;
      }
      var description = (String) entry.getOrDefault("description", "");
      var returnTypeName = (String) entry.get(RETURN_TYPE_FIELD);
      var returnType = returnTypeName == null
        ? TypeRef.UNKNOWN
        : new TypeRef(TypeKind.PLATFORM, returnTypeName);
      var signatures = readSignatures(
        (List<Map<String, Object>>) entry.get("signatures"), returnType
      );
      var descriptor = MemberDescriptor.method(name, description, signatures)
        .withStandardLibrary(true);
      // Двуязычные имена: опциональные `nameRu` и `nameEn`. Если не заданы —
      // дескриптор остаётся с пустыми {@code nameRu}/{@code nameEn} (и
      // {@code displayName} падает на {@code name}).
      var nameRu = stringEntry(entry, "nameRu");
      var nameEn = stringEntry(entry, "nameEn");
      if (!nameRu.isEmpty() || !nameEn.isEmpty()) {
        descriptor = descriptor.withBilingualName(BilingualString.of(nameRu, nameEn));
      }
      if (Boolean.TRUE.equals(entry.get("async"))) {
        descriptor = descriptor.withAsync(true);
      }
      var metadata = readGlobalMetadata(entry);
      if (!metadata.isEmpty()) {
        descriptor = descriptor.withMetadata(metadata);
      }
      result.put(name.toLowerCase(Locale.ROOT), descriptor);
      var aliases = (List<String>) entry.getOrDefault(ALIASES_FIELD, Collections.emptyList());
      for (var alias : aliases) {
        result.put(alias.toLowerCase(Locale.ROOT), descriptor);
      }
    }
    return Collections.unmodifiableMap(result);
  }

  private static String stringEntry(Map<String, Object> raw, String key) {
    var v = raw.get(key);
    return v instanceof String s ? s : "";
  }

  /**
   * Читает опциональные версионные метаданные глобальной функции из JSON:
   * {@code sinceVersion}, {@code deprecatedSinceVersion},
   * {@code recommendedReplacements}. Нужны диагностикам устаревания и
   * недоступности-по-версии, чтобы они работали без установленного HBK.
   * Отсутствие всех полей → {@link PlatformMetadata#EMPTY}.
   */
  private static PlatformMetadata readGlobalMetadata(Map<String, Object> raw) {
    var sinceVersion = stringEntry(raw, "sinceVersion");
    var deprecatedSinceVersion = stringEntry(raw, "deprecatedSinceVersion");
    var recommended = stringListEntry(raw, "recommendedReplacements");
    if (sinceVersion.isEmpty() && deprecatedSinceVersion.isEmpty() && recommended.isEmpty()) {
      return PlatformMetadata.EMPTY;
    }
    return new PlatformMetadata(sinceVersion, deprecatedSinceVersion, recommended,
      Set.of(), null, "", "", List.of(), List.of());
  }

  /** Читает JSON-поле как список строк (нестроковые элементы пропускаются). */
  private static List<String> stringListEntry(Map<String, Object> raw, String key) {
    if (!(raw.get(key) instanceof List<?> list)) {
      return List.of();
    }
    var result = new ArrayList<String>(list.size());
    for (var item : list) {
      if (item instanceof String s) {
        result.add(s);
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static List<SignatureDescriptor> readSignatures(
    List<Map<String, Object>> raw, TypeRef fallbackReturnType
  ) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    var result = new ArrayList<SignatureDescriptor>(raw.size());
    for (var sig : raw) {
      var description = (String) sig.getOrDefault("description", "");
      var returnTypeName = (String) sig.get(RETURN_TYPE_FIELD);
      var returnType = returnTypeName == null
        ? fallbackReturnType
        : new TypeRef(TypeKind.PLATFORM, returnTypeName);
      var rawParams = (List<Map<String, Object>>) sig.getOrDefault("parameters", Collections.emptyList());
      var params = new ArrayList<ParameterDescriptor>(rawParams.size());
      for (var p : rawParams) {
        var pname = (String) p.get("name");
        var optional = Boolean.TRUE.equals(p.get("optional"));
        var pdesc = (String) p.getOrDefault("description", "");
        var pNameRu = stringEntry(p, "nameRu");
        var pNameEn = stringEntry(p, "nameEn");
        params.add(new ParameterDescriptor(pname, TypeSet.EMPTY, optional, pdesc, "",
          BilingualString.of(pNameRu, pNameEn)));
      }
      result.add(new SignatureDescriptor(params, returnType, description));
    }
    return result;
  }

  /**
   * Иммутабельный снапшот загруженных глобалов одного языка: функции, классы,
   * ключевые слова, платформенные переменные/перечисления, описания и сниппеты
   * ключевых слов.
   */
  private record LanguageData(
    Map<String, MemberDescriptor> functions,
    List<String> classes,
    List<String> keywords,
    List<PlatformVariable> platformVariables,
    List<PlatformVariable> platformEnums,
    Map<String, LanguageKeywordSnippet> keywordSnippets,
    Map<String, KeywordDescription> keywordDescriptions
  ) {
  }

  /**
   * Описание платформенной глобальной переменной.
   *
   * @param name        основное имя переменной
   * @param aliases     альтернативные имена (синонимы) переменной
   * @param description текстовое описание переменной
   * @param type        ссылка на тип значения переменной
   */
  public record PlatformVariable(String name, List<String> aliases, String description, TypeRef type) {
  }
}
