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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
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
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
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
   * URI документа-модуля → его тип-значение (обратный индекс к name-keyed записям).
   * Заполняется провайдерами регистрации модулей ({@code ConfigurationModuleMembersProvider}
   * для общих модулей, {@code OScriptModuleMembersProvider} для library-модулей) синхронно
   * рядом с регистрацией имени; читается выводом типа ресивера-модуля по {@code ModuleSymbol}
   * (у которого на руках URI, а не имя). Единая точка вместо обращения инференсера к
   * двум URI-ключевым индексам подсистем.
   */
  private final Map<URI, TypeRef> moduleTypeByUri = new ConcurrentHashMap<>();
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
   * @param globalSymbolScope глобальная область символов, в которую публикуются
   *                          загруженные глобалы и register*-регистрации.
   */
  public GlobalScopeProvider(BslContextHolder bslContextHolder, GlobalSymbolScope globalSymbolScope) {
    var os = loadFromResource(OSCRIPT_RESOURCE_PATH);
    var bsl = loadBsl(bslContextHolder);
    this.byFileType = Map.of(FileType.BSL, bsl, FileType.OS, os);
    this.globalSymbolScope = globalSymbolScope;
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

  /** Параллельный Symbol-фронт. Заполняется лениво в {@link #ensureGlobalsPublished()}. */
  private final GlobalSymbolScope globalSymbolScope;

  /**
   * Источник данных о библиотеках OneScript (lib.config + oscript_modules).
   * Используется для фильтрации видимости symbol'ов из библиотек в BSL-файлах
   * и для согласования с {@code #Использовать &lt;libName&gt;}.
   * <p>
   * Field-injection с {@code required=false} здесь — обход циклической
   * зависимости: {@code OScriptLibraryIndex} → {@code OScriptModuleMembersProvider}
   * → {@code GlobalScopeProvider} (для регистрации library-классов/модулей).
   * Корректный фикс — перевести регистрацию на ApplicationEvent'ы, чтобы
   * {@code OScriptModuleMembersProvider} не зависел от
   * {@code GlobalScopeProvider}; это отдельная задача.
   */
  @Autowired(required = false)
  private @Nullable OScriptLibraryIndex oScriptLibraryIndex;

  private final AtomicBoolean globalsPublished = new AtomicBoolean(false);

  /**
   * Поиск symbol'а в глобальной области (globals + library entries) с фильтрацией
   * по типу файла: library-entries скрываются в BSL-файлах; функции и глобальные
   * свойства фильтруются по языку своего источника.
   */
  public Optional<Symbol> findGlobal(String name, FileType fileType) {
    ensureGlobalsPublished();
    // Выбор языкового варианта записи (BSL/OS) — в GlobalSymbolScope;
    // ниже остаётся только проверка видимости имени в данном типе файла.
    var sym = globalSymbolScope.findEntry(name, fileType).map(GlobalSymbolScope.Entry::symbol);
    if (sym.isEmpty()) {
      return sym;
    }
    var lc = name.toLowerCase(Locale.ROOT);
    // Library entries — только в OS-файлах.
    if (oScriptLibraryIndex != null && oScriptLibraryIndex.findByName(lc).isPresent()) {
      return fileType == FileType.OS ? sym : Optional.empty();
    }
    // Имя может быть зарегистрировано в нескольких категориях (функция, глобальное
    // свойство, платформенная переменная) одновременно с РАЗНЫМИ скоупами:
    // например, `КодировкаТекста` — это и OS-only глобальное свойство (из
    // oscript-pack), и BSL-only платформенная переменная (из bsl-context).
    // Имя видимо в данном fileType, если ХОТЯ БЫ ОДНА категория его так разрешает.
    // Если ни одной категории не зарегистрировано (классы, ключевые слова и т.п.) —
    // считаем символ доступным.
    var fnRegistered = byFileType.get(FileType.BSL).functions.containsKey(lc)
      || byFileType.get(FileType.OS).functions.containsKey(lc);
    var propRegistered = byFileType.get(FileType.BSL).globalContextNames.contains(lc)
      || byFileType.get(FileType.OS).globalContextNames.contains(lc);
    var varRegistered = byFileType.get(FileType.BSL).variableNames.contains(lc)
      || byFileType.get(FileType.OS).variableNames.contains(lc);
    if (!fnRegistered && !propRegistered && !varRegistered) {
      return sym;
    }
    boolean visible = (fnRegistered && byFileType.get(fileType).functions.containsKey(lc))
      || byFileType.get(fileType).globalContextNames.contains(lc)
      || byFileType.get(fileType).variableNames.contains(lc);
    return visible ? sym : Optional.empty();
  }

  /**
   * То же, что {@link #findGlobal(String, FileType)}, но возвращает запись с её
   * семантической ролью ({@link GlobalSymbolScope.Role#VALUE} или
   * {@link GlobalSymbolScope.Role#TYPE_NAME}). Нужно потребителям, которым важно
   * отличить голое имя класса ({@code Структура}) от глобал-значения
   * ({@code Справочники}, {@code ФС}, library-модули).
   */
  public Optional<GlobalSymbolScope.Entry> findGlobalEntry(String name, FileType fileType) {
    if (findGlobal(name, fileType).isEmpty()) {
      return Optional.empty();
    }
    return globalSymbolScope.findEntry(name, fileType);
  }

  private void ensureGlobalsPublished() {
    if (globalsPublished.compareAndSet(false, true)) {
      publishGlobals();
    }
  }

  private void publishGlobals() {
    // Регистрируем глобальные функции в GlobalSymbolScope как synthetic-методы.
    // Каждый язык публикует свой набор со своим скоупом: для имён, существующих
    // в обоих языках, появляются ДВА варианта записи — выбор при чтении по типу файла.
    var symbolsByDescriptor = new HashMap<MemberDescriptor, SyntheticSymbol>();
    for (var fileType : FileType.values()) {
      var data = byFileType.get(fileType);
      for (var entry : data.functions.entrySet()) {
        registerFunctionSymbol(symbolsByDescriptor, entry.getKey(), entry.getValue(), fileType);
      }
      // Платформенные глобальные переменные (БиблиотекаКартинок, ПараметрыСеанса, …)
      // и системные перечисления (КодировкаТекста, НаправлениеСортировки, …).
      publishPlatformGlobals(data.platformVariables, SyntheticKind.PLATFORM_GLOBAL_PROPERTY, fileType);
      publishPlatformGlobals(data.platformEnums, SyntheticKind.PLATFORM_GLOBAL_ENUM, fileType);
    }
  }

  /**
   * Публикует функцию в {@link GlobalSymbolScope} под ключом {@code key}
   * (canonical-имя или алиас), переиспользуя один {@link SyntheticSymbol}
   * на дескриптор через {@code cache}.
   */
  private void registerFunctionSymbol(Map<MemberDescriptor, SyntheticSymbol> cache,
                                      String key, MemberDescriptor descriptor, FileType fileType) {
    var symbol = cache.computeIfAbsent(descriptor, d -> new SyntheticSymbol(
      d.name(),
      SyntheticKind.PLATFORM_GLOBAL_METHOD,
      d.description(),
      d.returnType()
    ));
    var displayName = key.equalsIgnoreCase(descriptor.name()) ? descriptor.name() : key;
    globalSymbolScope.register(displayName, symbol, GlobalSymbolScope.Role.VALUE, fileType);
  }

  private void publishPlatformGlobals(List<PlatformVariable> globals, SyntheticKind kind, FileType fileType) {
    for (var v : globals) {
      var symbol = new SyntheticSymbol(
        v.name(),
        kind,
        v.description(),
        v.type()
      );
      globalSymbolScope.register(v.name(), symbol, GlobalSymbolScope.Role.VALUE, fileType);
      for (var alias : v.aliases()) {
        globalSymbolScope.register(alias, symbol, GlobalSymbolScope.Role.VALUE, fileType);
      }
    }
  }

  /**
   * Найти тип глобального свойства по имени (canonical или alias) в наборе
   * указанного языка. Покрывает только build-time источники с
   * {@link SyntheticKind#PLATFORM_GLOBAL_PROPERTY}. LIBRARY_MODULE и
   * configuration-registered записи сюда не попадают — для них используйте
   * umbrella-метод {@link #findGlobalContext(String, FileType)}.
   *
   * @param name     имя свойства (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return тип значения свойства или {@link Optional#empty()}.
   */
  public Optional<TypeRef> findGlobalProperty(String name, FileType fileType) {
    return findInList(byFileType.get(fileType).platformVariables, name);
  }

  /**
   * Имена платформенных глобальных свойств набора указанного языка
   * (canonical, без алиасов).
   *
   * @param fileType язык файла-потребителя.
   * @return имена свойств.
   */
  public List<String> getGlobalPropertyNames(FileType fileType) {
    return namesFromList(byFileType.get(fileType).platformVariables);
  }

  /**
   * Найти тип системного перечисления по имени (canonical или alias) в наборе
   * указанного языка. Покрывает только bsl-context-источники с
   * {@link SyntheticKind#PLATFORM_GLOBAL_ENUM}.
   *
   * @param name     имя перечисления (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return тип перечисления или {@link Optional#empty()}.
   */
  public Optional<TypeRef> findGlobalEnum(String name, FileType fileType) {
    return findInList(byFileType.get(fileType).platformEnums, name);
  }

  /**
   * Имена системных перечислений набора указанного языка (canonical, без алиасов).
   *
   * @param fileType язык файла-потребителя.
   * @return имена перечислений.
   */
  public List<String> getGlobalEnumNames(FileType fileType) {
    return namesFromList(byFileType.get(fileType).platformEnums);
  }

  private static Optional<TypeRef> findInList(List<PlatformVariable> list, String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var lc = name.toLowerCase(Locale.ROOT);
    return list.stream()
      .filter(v -> v.name().equalsIgnoreCase(name)
        || v.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(lc)))
      .findFirst()
      .map(PlatformVariable::type);
  }

  private static List<String> namesFromList(List<PlatformVariable> list) {
    return list.stream()
      .map(PlatformVariable::name)
      .toList();
  }

  /**
   * Глобальные функции набора указанного языка (уникальные дескрипторы,
   * без дубликатов по ru/en алиасам).
   *
   * @param fileType язык файла-потребителя.
   * @return дескрипторы глобальных функций.
   */
  public Collection<MemberDescriptor> getFunctions(FileType fileType) {
    return new LinkedHashSet<>(byFileType.get(fileType).functions.values());
  }

  /**
   * Поиск глобальной функции по имени в наборе указанного языка
   * (регистронезависимо, с учётом ru/en алиасов). Для имён, существующих
   * в обоих языках, возвращается дескриптор языка файла-потребителя.
   *
   * @param name     имя функции (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return дескриптор функции или {@link Optional#empty()}.
   */
  public Optional<MemberDescriptor> findFunction(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(byFileType.get(fileType).functions.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Зарегистрировать тип как глобальное свойство — его имя (и алиасы) становятся
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.МойМетод()},
   * {@code ФС.КаталогПустой()}. Каждое имя получает {@link SyntheticSymbol}
   * с типом-значением {@code ref}.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, FileType fileType) {
    registerGlobalProperty(ref, names, fileType, "");
  }

  /**
   * То же, что {@link #registerGlobalProperty(TypeRef, Collection, FileType)},
   * но с описанием, которое будет прикреплено к {@link SyntheticSymbol} для
   * последующего отображения в hover/completion.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, FileType fileType, String description) {
    registerGlobalProperty(ref, names, fileType, description, SyntheticKind.PLATFORM_GLOBAL_PROPERTY);
  }

  /**
   * Та же регистрация, но с явным {@link SyntheticKind} — используется
   * при публикации системных перечислений ({@link SyntheticKind#PLATFORM_GLOBAL_ENUM}),
   * чтобы отличать их в hover/completion/подсветке от обычных глобальных свойств.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, FileType fileType,
                                     String description, SyntheticKind syntheticKind) {
    registerGlobalProperty(ref, names, fileType, description, syntheticKind, () -> null);
  }

  /**
   * Та же регистрация, но с lazy-провайдером source-defined-символа.
   * Используется для общих модулей конфигурации (backing —
   * {@link com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol}),
   * чтобы supplier'ы (подсветка, hover) могли узнать, что синтетическое имя
   * соответствует source-defined-сущности.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, FileType fileType,
                                     String description, SyntheticKind syntheticKind,
                                     Supplier<Symbol> sourceSymbol) {
    if (ref == null || names == null || names.isEmpty()) {
      return;
    }
    ensureGlobalsPublished();
    var canonical = ref.qualifiedName();
    var symbol = new SyntheticSymbol(canonical, syntheticKind,
      description, ref, null, sourceSymbol);
    for (var name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE, fileType);
      byFileType.get(fileType).globalContextNames.add(name.toLowerCase(Locale.ROOT));
    }
  }

  /**
   * Зарегистрировать платформенный класс (имеет блок {@code constructors} в
   * JSON-пакете). Создаёт {@link SyntheticSymbol} с ролью
   * {@link GlobalSymbolScope.Role#TYPE_NAME} для каждого имени/алиаса,
   * чтобы hover/findGlobal на имени класса в {@code Новый <Класс>(...)} нашёл
   * символ с описанием класса. Сами сигнатуры конструкторов хранятся в
   * {@link TypeRegistry#getConstructors(TypeRef)}.
   */
  public void registerPlatformClass(TypeRef ref, Collection<String> names, FileType fileType, String description) {
    if (ref == null || names == null || names.isEmpty()) {
      return;
    }
    ensureGlobalsPublished();
    var symbol = new SyntheticSymbol(ref.qualifiedName(), SyntheticKind.TYPE_NAME,
      description, ref);
    for (var name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.TYPE_NAME, fileType);
    }
  }

  private static final Set<SyntheticKind> CONTEXT_KINDS = EnumSet.of(
    SyntheticKind.PLATFORM_GLOBAL_PROPERTY,
    SyntheticKind.PLATFORM_GLOBAL_ENUM,
    SyntheticKind.LIBRARY_MODULE
  );

  /**
   * Все VALUE-символы в global scope без фильтра по типу файла
   * (property + enum + library-module). Унифицированный вход для всего,
   * что можно использовать как ресивер dot-выражения. Имена классов для
   * {@code Новый} ({@link SyntheticKind#TYPE_NAME}, Role.TYPE_NAME) сюда не
   * попадают — они выдаются через {@link #getClasses()}.
   */
  public List<SyntheticSymbol> getGlobalContexts() {
    return globalSymbolScope.streamSymbols()
      .filter(SyntheticSymbol.class::isInstance)
      .map(SyntheticSymbol.class::cast)
      .filter(s -> CONTEXT_KINDS.contains(s.getSyntheticKind()))
      .distinct()
      .toList();
  }

  /**
   * То же, что {@link #getGlobalContexts()}, но с фильтрацией по типу файла.
   */
  public List<SyntheticSymbol> getGlobalContexts(FileType fileType) {
    return getGlobalContexts().stream()
      .filter(s -> matchesGlobalScope(s.getName(), fileType))
      .toList();
  }

  /**
   * @return canonical-имена всех VALUE-имён в global scope (property + enum + library-module).
   */
  public Collection<String> getGlobalContextNames() {
    return getGlobalContexts().stream().map(SyntheticSymbol::getName).toList();
  }

  /**
   * То же, что {@link #getGlobalContextNames()}, но с фильтрацией по типу файла.
   */
  public Collection<String> getGlobalContextNames(FileType fileType) {
    return getGlobalContexts(fileType).stream().map(SyntheticSymbol::getName).toList();
  }

  /**
   * Найти тип имени в global scope (любое VALUE-имя: property, enum, library-module)
   * с фильтрацией по типу файла. Унифицированная точка входа — для consumer'ов,
   * которым не важно различение property/enum.
   *
   * @param name     имя (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return тип-значение имени или {@link Optional#empty()}.
   */
  public Optional<TypeRef> findGlobalContext(String name, FileType fileType) {
    if (!matchesGlobalScope(name, fileType)) {
      return Optional.empty();
    }
    return findGlobalEntry(name, fileType)
      .filter(entry -> entry.role() == GlobalSymbolScope.Role.VALUE)
      .map(GlobalSymbolScope.Entry::symbol)
      .filter(SyntheticSymbol.class::isInstance)
      .map(s -> ((SyntheticSymbol) s).getValueType())
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN));
  }

  private boolean matchesGlobalScope(String name, FileType fileType) {
    if (name == null) {
      return true;
    }
    var lc = name.toLowerCase(Locale.ROOT);
    if (byFileType.get(fileType).globalContextNames.contains(lc)) {
      return true;
    }
    // не зарегистрировано ни в одном языке — не фильтруем
    return byFileType.values().stream().noneMatch(data -> data.globalContextNames.contains(lc));
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
   * Зарегистрировать synthetic-symbol для библиотечного модуля OneScript
   * (записи {@code <module>} из {@code lib.config}). Symbol становится
   * видимым через {@link #findGlobal(String)} с фильтрацией по {@link FileType}
   * (через {@link com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex}).
   * Источник {@link TypeRef} и {@code libOrigin} — {@code OScriptLibraryIndex}.
   */
  public void registerLibraryModule(String name, TypeRef ref) {
    if (name == null || name.isBlank() || ref == null) {
      return;
    }
    ensureGlobalsPublished();
    var symbol = new SyntheticSymbol(name, SyntheticKind.LIBRARY_MODULE, "", ref);
    globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE, FileType.OS);
  }

  /**
   * Зарегистрировать synthetic-symbol для библиотечного класса OneScript
   * (записи {@code <class>} из {@code lib.config}). Конструкторы хранятся в
   * {@link TypeRegistry} через {@code registerConstructorSource} (см.
   * {@code OScriptModuleMembersProvider}).
   */
  public void registerLibraryClass(String name, TypeRef classRef) {
    if (name == null || name.isBlank()) {
      return;
    }
    ensureGlobalsPublished();
    var ref = classRef;
    var symbol = new SyntheticSymbol(name, SyntheticKind.TYPE_NAME, "", ref);
    globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.TYPE_NAME, FileType.OS);
  }

  /**
   * Удалить synthetic-symbol library-модуля по имени.
   */
  public void unregisterLibraryModule(String name) {
    unregisterLibrarySymbol(name);
  }

  /**
   * Удалить synthetic-symbol library-класса по имени.
   */
  public void unregisterLibraryClass(String name) {
    unregisterLibrarySymbol(name);
  }

  private void unregisterLibrarySymbol(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    globalSymbolScope.findSymbol(name).ifPresent(globalSymbolScope::unregister);
  }

  /**
   * Связать URI документа-модуля с его типом-значением. Вызывается провайдерами
   * регистрации модулей синхронно рядом с регистрацией имени. Повторный вызов с тем
   * же URI перезаписывает тип (корректно отражает переименование модуля).
   */
  public void indexModuleType(URI uri, TypeRef ref) {
    moduleTypeByUri.put(uri, ref);
  }

  /**
   * Снять связь URI→тип (при удалении документа/дерегистрации library-модуля).
   */
  public void removeModuleType(URI uri) {
    moduleTypeByUri.remove(uri);
  }

  /**
   * Тип-значение модуля по URI документа. Используется выводом типа ресивера-модуля
   * ({@code ModuleSymbol}), у которого есть URI, но нет имени для name-keyed lookup'а.
   */
  public Optional<TypeRef> moduleTypeByUri(URI uri) {
    return Optional.ofNullable(moduleTypeByUri.get(uri));
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
    // top-level имена, доступные без префикса. Тип берём первый из объявленных
    // в СП (типа СправочникиМенеджер) — для dot-completion'а к коллекции.
    if (globalContext != null) {
      for (var property : globalContext.properties()) {
        if (property.isGeneric()) {
          continue; // generic-плейсхолдеры (<Имя справочника>) — не имена.
        }
        var name = property.name().getName();
        var alias = property.name().getAlias();
        var lc = name.toLowerCase(Locale.ROOT);
        if (!variableSeen.add(lc)) {
          continue;
        }
        var aliases = alias == null || alias.isBlank() ? List.<String>of() : List.of(alias);
        var typeRef = firstTypeRef(property);
        variables.add(new PlatformVariable(name, aliases, property.description(), typeRef));
      }
    }

    for (var ctx : provider.getContexts()) {
      if (ctx instanceof ContextType type && !type.isGeneric()) {
        addNameWithAlias(classes, classSeen, type.name().getName(), type.name().getAlias());
      } else if (ctx instanceof ContextEnum enumeration) {
        // Системное перечисление платформы — публикуется в global scope с
        // SyntheticKind.PLATFORM_GLOBAL_ENUM (через отдельный список enums).
        var name = enumeration.name().getName();
        var alias = enumeration.name().getAlias();
        var lc = name.toLowerCase(Locale.ROOT);
        if (variableSeen.add(lc)) {
          var aliases = alias == null || alias.isBlank() ? List.<String>of() : List.of(alias);
          var typeRef = new TypeRef(TypeKind.PLATFORM, name);
          enums.add(new PlatformVariable(name, aliases, "", typeRef));
        }
      } else if (ctx instanceof ContextLanguageKeyword kw) {
        if (KEYWORD_CATEGORIES.contains(kw.category())) {
          var added = addNameWithAlias(keywords, keywordSeen,
            kw.name().getName(), kw.name().getAlias());
          if (added && !kw.snippet().isEmpty()) {
            // Сниппет по обоим написаниям — пользователь может ввести любое.
            keywordSnippets.put(kw.name().getName().toLowerCase(Locale.ROOT), kw.snippet());
            if (kw.name().getAlias() != null && !kw.name().getAlias().isBlank()) {
              keywordSnippets.put(kw.name().getAlias().toLowerCase(Locale.ROOT), kw.snippet());
            }
          }
          if (added) {
            // Описание ru + en (en хранится в PlatformLanguageKeyword.descriptionEn).
            // Доступно по обоим написаниям имени.
            var enDesc = kw instanceof PlatformLanguageKeyword pk
              ? pk.descriptionEn() : "";
            var primary = BilingualString.of(
              kw.description() == null ? "" : kw.description(), enDesc);
            // Контекстно-зависимые описания (Async/Знач/Возврат и т.п. имеют
            // разное описание в Функция vs Процедура — см. shlang_*.hbk).
            var byParent = new LinkedHashMap<String, BilingualString>();
            if (kw instanceof PlatformLanguageKeyword pk) {
              var ruByCtx = pk.descriptionByParent();
              var enByCtx = pk.descriptionByParentEn();
              var keys = new LinkedHashSet<String>();
              keys.addAll(ruByCtx.keySet());
              keys.addAll(enByCtx.keySet());
              for (var k : keys) {
                var bi = BilingualString.of(
                  ruByCtx.getOrDefault(k, ""), enByCtx.getOrDefault(k, ""));
                if (!bi.isEmpty()) {
                  byParent.put(k, bi);
                }
              }
            }
            var entry = new KeywordDescription(primary, byParent);
            if (!entry.isEmpty()) {
              keywordDescriptions.put(kw.name().getName().toLowerCase(Locale.ROOT), entry);
              if (kw.name().getAlias() != null && !kw.name().getAlias().isBlank()) {
                keywordDescriptions.put(kw.name().getAlias().toLowerCase(Locale.ROOT), entry);
              }
            }
          }
        }
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
    if (name != null && !name.isBlank() && seen.add(name.toLowerCase(Locale.ROOT))) {
      sink.add(name);
      added = true;
    }
    if (alias != null && !alias.isBlank() && seen.add(alias.toLowerCase(Locale.ROOT))) {
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
      var aliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
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
      var returnTypeName = (String) entry.get("returnType");
      var returnType = returnTypeName == null
        ? TypeRef.UNKNOWN
        : new TypeRef(TypeKind.PLATFORM, returnTypeName);
      var signatures = readSignatures(
        (List<Map<String, Object>>) entry.get("signatures"), returnType
      );
      var descriptor = MemberDescriptor.method(name, description, signatures);
      // Двуязычные имена: опциональные `nameRu` и `nameEn`. Если не заданы —
      // дескриптор остаётся с пустыми {@code nameRu}/{@code nameEn} (и
      // {@code displayName} падает на {@code name}).
      var nameRu = stringEntry(entry, "nameRu");
      var nameEn = stringEntry(entry, "nameEn");
      if (!nameRu.isEmpty() || !nameEn.isEmpty()) {
        descriptor = descriptor.withLocalizedNames(nameRu, nameEn);
      }
      if (Boolean.TRUE.equals(entry.get("async"))) {
        descriptor = descriptor.withAsync(true);
      }
      var metadata = readGlobalMetadata(entry);
      if (!metadata.isEmpty()) {
        descriptor = descriptor.withMetadata(metadata);
      }
      result.put(name.toLowerCase(Locale.ROOT), descriptor);
      var aliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
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
      var returnTypeName = (String) sig.get("returnType");
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
   * Все данные глобальной области одного языка: иммутабельный снапшот загрузки
   * (функции, классы, ключевые слова, платформенные переменные/перечисления,
   * описания/сниппеты ключевых слов), производный индекс {@code variableNames}
   * и мутабельный runtime-реестр {@code globalContextNames} (имена глобальных
   * свойств, регистрируемые {@code registerGlobalProperty} в течение жизни
   * workspace: общие модули конфигурации, library-модули, публикации bootstrap'а).
   */
  private record LanguageData(
    Map<String, MemberDescriptor> functions,
    List<String> classes,
    List<String> keywords,
    List<PlatformVariable> platformVariables,
    List<PlatformVariable> platformEnums,
    Map<String, LanguageKeywordSnippet> keywordSnippets,
    Map<String, KeywordDescription> keywordDescriptions,
    Set<String> variableNames,
    Set<String> globalContextNames
  ) {

    /**
     * Конструктор от снапшота загрузки: производный индекс {@code variableNames}
     * (lowercased имена + алиасы платформенных переменных и перечислений,
     * для O(1)-проверки видимости в {@code findGlobal}) вычисляется здесь,
     * runtime-реестр {@code globalContextNames} создаётся пустым.
     */
    LanguageData(Map<String, MemberDescriptor> functions,
                 List<String> classes,
                 List<String> keywords,
                 List<PlatformVariable> platformVariables,
                 List<PlatformVariable> platformEnums,
                 Map<String, LanguageKeywordSnippet> keywordSnippets,
                 Map<String, KeywordDescription> keywordDescriptions) {
      this(functions, classes, keywords, platformVariables, platformEnums,
        keywordSnippets, keywordDescriptions,
        variableNames(platformVariables, platformEnums),
        ConcurrentHashMap.newKeySet());
    }

    private static Set<String> variableNames(List<PlatformVariable> variables, List<PlatformVariable> enums) {
      var result = new HashSet<String>();
      for (var list : List.of(variables, enums)) {
        for (var v : list) {
          result.add(v.name().toLowerCase(Locale.ROOT));
          for (var alias : v.aliases()) {
            result.add(alias.toLowerCase(Locale.ROOT));
          }
        }
      }
      return Set.copyOf(result);
    }
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
