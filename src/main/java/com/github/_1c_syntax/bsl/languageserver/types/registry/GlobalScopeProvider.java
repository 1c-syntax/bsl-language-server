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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.scope.GlobalSymbolScope;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class GlobalScopeProvider {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-globals.json";
  private static final String OSCRIPT_RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-oscript-globals.json";

  private final Map<String, MemberDescriptor> functions;
  private final List<String> classes;
  private final List<String> keywords;
  private final List<PlatformVariable> platformVariables;
  /** lowercased имя функции (canonical + alias) → языковой скоуп. */
  private final Map<String, LanguageScope> functionScopes;
  /** lowercased имя класса-для-Новый → языковой скоуп. */
  private final Map<String, LanguageScope> classScopes;
  /** lowercased ключевое слово → языковой скоуп. */
  private final Map<String, LanguageScope> keywordScopes;
  /** lowercased имя платформенной переменной → языковой скоуп. */
  private final Map<String, LanguageScope> platformVariableScopes;
  /** lowercased имя глобального свойства (через registerGlobalProperty) → языковой скоуп. */
  private final Map<String, LanguageScope> globalPropertyScopes = new java.util.concurrent.ConcurrentHashMap<>();
  /** Имена library-модулей OneScript (lowercased) → TypeRef модуля. */
  private final Map<String, TypeRef> libraryModules = new java.util.concurrent.ConcurrentHashMap<>();
  /** Имена library-классов OneScript (lowercased) → сигнатуры конструктора. */
  private final Map<String, List<SignatureDescriptor>> libraryClasses = new java.util.concurrent.ConcurrentHashMap<>();
  /** Хранит исходные написания имён library-сущностей для отображения. */
  private final Map<String, String> libraryNamesDisplay = new java.util.concurrent.ConcurrentHashMap<>();
  /**
   * Имя library-сущности (lowercased) → имя библиотеки-источника (lowercased,
   * например, имя каталога с {@code lib.config} или {@code oscript_modules/<name>}).
   * Используется для фильтрации видимости по {@code #Использовать <libName>}.
   */
  private final Map<String, String> libraryEntryOrigin = new java.util.concurrent.ConcurrentHashMap<>();
  /**
   * Каноничные «составные» имена MD-объектов конфигурации в коллекционной
   * форме ({@code Справочники.Контрагенты}, {@code Documents.Документ1}).
   * Поддерживается no-dot completion: пользователь печатает {@code Докум} —
   * подсказывается и {@code Документы}, и {@code Документы.Документ1}.
   */
  private final java.util.Set<String> configurationQualifiedNames =
    java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

  public GlobalScopeProvider() {
    var loaded = load();
    this.functions = loaded.functions;
    this.classes = loaded.classes;
    this.keywords = loaded.keywords;
    this.platformVariables = loaded.platformVariables;
    this.functionScopes = loaded.functionScopes;
    this.classScopes = loaded.classScopes;
    this.keywordScopes = loaded.keywordScopes;
    this.platformVariableScopes = loaded.platformVariableScopes;
  }

  /**
   * Параллельный Symbol-фронт. Заполняется лениво при первом обращении к
   * глобальной области (см. {@link #ensureGlobalsPublished()}), потому что
   * @PostConstruct на workspace-scoped bean внутри scope.get() вызывает
   * рекурсивное создание других workspace-scoped beans (GlobalSymbolScope)
   * и WorkspaceScope падает с "Recursive update". См. plan-symbol-front.md.
   */
  @Autowired(required = false)
  private GlobalSymbolScope globalSymbolScope;

  private final java.util.concurrent.atomic.AtomicBoolean globalsPublished =
    new java.util.concurrent.atomic.AtomicBoolean(false);

  /**
   * Поиск symbol'а в глобальной области (globals + library entries).
   */
  public Optional<com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol> findGlobal(String name) {
    ensureGlobalsPublished();
    if (globalSymbolScope == null) {
      return Optional.empty();
    }
    return globalSymbolScope.findSymbol(name);
  }

  /**
   * То же, что {@link #findGlobal(String)}, но с фильтрацией по типу файла:
   * library-entries скрываются в BSL-файлах; функции и глобальные свойства
   * фильтруются по соответствующим скоупам.
   */
  public Optional<com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol> findGlobal(String name, FileType fileType) {
    var sym = findGlobal(name);
    if (sym.isEmpty() || fileType == null) {
      return sym;
    }
    var lc = name == null ? "" : name.toLowerCase(Locale.ROOT);
    // Library entries — только в OS-файлах.
    if (libraryEntryOrigin.containsKey(lc)) {
      return fileType == FileType.OS ? sym : Optional.empty();
    }
    // Функции — по скоупу функций.
    var fnScope = functionScopes.get(lc);
    if (fnScope != null && !fnScope.matches(fileType)) {
      return Optional.empty();
    }
    // Глобальные свойства (КодировкаТекста, Документы.X, ...) — по своему скоупу.
    var propScope = globalPropertyScopes.get(lc);
    if (propScope != null && !propScope.matches(fileType)) {
      return Optional.empty();
    }
    var varScope = platformVariableScopes.get(lc);
    if (varScope != null && !varScope.matches(fileType)) {
      return Optional.empty();
    }
    return sym;
  }

  private void ensureGlobalsPublished() {
    if (globalSymbolScope == null) {
      return;
    }
    if (globalsPublished.compareAndSet(false, true)) {
      publishGlobals();
    }
  }

  private void publishGlobals() {
    if (globalSymbolScope == null) {
      return;
    }
    // Регистрируем глобальные функции в GlobalSymbolScope как synthetic-методы.
    var alreadyRegistered = new java.util.HashSet<MemberDescriptor>();
    for (var entry : functions.entrySet()) {
      var descriptor = entry.getValue();
      if (!alreadyRegistered.add(descriptor)) {
        continue;
      }
      var symbol = new SyntheticSymbol(
        descriptor.name(),
        SyntheticKind.PLATFORM_GLOBAL_METHOD,
        descriptor.description(),
        descriptor.returnType()
      );
      globalSymbolScope.register(descriptor.name(), symbol, GlobalSymbolScope.Role.VALUE);
    }
    // Алиасы (ключи функций, не совпадающие с canonical name дескриптора).
    for (var entry : functions.entrySet()) {
      var descriptor = entry.getValue();
      var key = entry.getKey();
      if (!key.equalsIgnoreCase(descriptor.name())) {
        globalSymbolScope.findSymbol(descriptor.name()).ifPresent(symbol ->
          globalSymbolScope.register(key, symbol, GlobalSymbolScope.Role.VALUE));
      }
    }
    // Платформенные глобальные переменные (БиблиотекаКартинок, ПараметрыСеанса, …).
    for (var v : platformVariables) {
      var symbol = new SyntheticSymbol(
        v.name(),
        SyntheticKind.PLATFORM_GLOBAL_VARIABLE,
        v.description(),
        v.type()
      );
      globalSymbolScope.register(v.name(), symbol, GlobalSymbolScope.Role.VALUE);
      for (var alias : v.aliases()) {
        globalSymbolScope.register(alias, symbol, GlobalSymbolScope.Role.VALUE);
      }
    }
  }

  /**
   * @return неизменяемая коллекция имён платформенных глобальных переменных
   *         (canonical name каждой переменной, без алиасов).
   */
  public List<String> getPlatformVariableNames() {
    return platformVariables.stream().map(PlatformVariable::name).toList();
  }

  /**
   * То же, что {@link #getPlatformVariableNames()}, но с фильтрацией по типу файла.
   */
  public List<String> getPlatformVariableNames(FileType fileType) {
    if (fileType == null) {
      return getPlatformVariableNames();
    }
    return platformVariables.stream()
      .filter(v -> {
        var s = platformVariableScopes.get(v.name().toLowerCase(Locale.ROOT));
        return s == null || s.matches(fileType);
      })
      .map(PlatformVariable::name)
      .toList();
  }

  /**
   * Найти тип платформенной глобальной переменной по имени или алиасу.
   */
  public Optional<TypeRef> findPlatformVariableType(String name) {
    return findPlatformVariableType(name, null);
  }

  /**
   * То же, что {@link #findPlatformVariableType(String)}, но с фильтрацией по типу файла.
   */
  public Optional<TypeRef> findPlatformVariableType(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var lc = name.toLowerCase(Locale.ROOT);
    if (fileType != null) {
      var s = platformVariableScopes.get(lc);
      if (s != null && !s.matches(fileType)) {
        return Optional.empty();
      }
    }
    return platformVariables.stream()
      .filter(v -> v.name().equalsIgnoreCase(name)
        || v.aliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(lc)))
      .findFirst()
      .map(PlatformVariable::type);
  }

  /**
   * @return неизменяемая коллекция глобальных функций
   */
  public Collection<MemberDescriptor> getFunctions() {
    return functions.values();
  }

  /**
   * То же, что {@link #getFunctions()}, но с фильтрацией по типу файла.
   */
  public Collection<MemberDescriptor> getFunctions(FileType fileType) {
    if (fileType == null) {
      return getFunctions();
    }
    var result = new java.util.LinkedHashSet<MemberDescriptor>();
    for (var entry : functions.entrySet()) {
      var s = functionScopes.get(entry.getKey());
      if (s == null || s.matches(fileType)) {
        result.add(entry.getValue());
      }
    }
    return result;
  }

  /**
   * Поиск глобальной функции по имени (регистронезависимо, с учётом ru/en алиасов).
   */
  public Optional<MemberDescriptor> findFunction(String name) {
    return findFunction(name, null);
  }

  /**
   * То же, что {@link #findFunction(String)}, но с фильтрацией по типу файла.
   */
  public Optional<MemberDescriptor> findFunction(String name, FileType fileType) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    var lc = name.toLowerCase(Locale.ROOT);
    if (fileType != null) {
      var s = functionScopes.get(lc);
      if (s != null && !s.matches(fileType)) {
        return Optional.empty();
      }
    }
    return Optional.ofNullable(functions.get(lc));
  }

  /**
   * Зарегистрировать тип как глобальное свойство — его имя (и алиасы) становятся
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.МойМетод()},
   * {@code ФС.КаталогПустой()}. Каждое имя получает {@link SyntheticSymbol}
   * с типом-значением {@code ref}.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names) {
    registerGlobalProperty(ref, names, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #registerGlobalProperty(TypeRef, Collection)}, но с указанием
   * языкового скоупа. Скоуп сохраняется на каждом имени (lowercased) в
   * {@link #globalPropertyScopes}; при повторной регистрации с другим скоупом
   * результат повышается до {@link LanguageScope#BOTH}.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, LanguageScope scope) {
    registerGlobalProperty(ref, names, scope, "");
  }

  /**
   * То же, что {@link #registerGlobalProperty(TypeRef, Collection, LanguageScope)},
   * но с описанием, которое будет прикреплено к {@link SyntheticSymbol} для
   * последующего отображения в hover/completion.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names, LanguageScope scope, String description) {
    if (ref == null || names == null || names.isEmpty()) {
      return;
    }
    ensureGlobalsPublished();
    var canonical = ref.qualifiedName();
    var symbol = new SyntheticSymbol(canonical, SyntheticKind.PLATFORM_GLOBAL_PROPERTY,
      description == null ? "" : description, ref);
    if (globalSymbolScope == null) {
      return;
    }
    var effectiveScope = scope == null ? LanguageScope.BOTH : scope;
    for (var name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE);
      globalPropertyScopes.merge(name.toLowerCase(Locale.ROOT), effectiveScope, LanguageScope::merge);
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
  public void registerPlatformClass(TypeRef ref, Collection<String> names, LanguageScope scope, String description) {
    if (ref == null || names == null || names.isEmpty()) {
      return;
    }
    ensureGlobalsPublished();
    var symbol = new SyntheticSymbol(ref.qualifiedName(), SyntheticKind.CONFIGURATION_OBJECT,
      description == null ? "" : description, ref);
    if (globalSymbolScope == null) {
      return;
    }
    var effectiveScope = scope == null ? LanguageScope.BOTH : scope;
    for (var name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.TYPE_NAME);
      classScopes.merge(name.toLowerCase(Locale.ROOT), effectiveScope, LanguageScope::merge);
    }
  }

  /**
   * Имена всех зарегистрированных глобальных свойств (canonical-форма, без алиасов).
   */
  public Collection<String> getGlobalPropertyNames() {
    return getGlobalPropertyNames(null);
  }

  /**
   * То же, что {@link #getGlobalPropertyNames()}, но с фильтрацией по типу файла.
   */
  public Collection<String> getGlobalPropertyNames(FileType fileType) {
    if (globalSymbolScope == null) {
      return List.of();
    }
    return globalSymbolScope.streamSymbols()
      .filter(SyntheticSymbol.class::isInstance)
      .map(SyntheticSymbol.class::cast)
      .filter(s -> s.getSyntheticKind() == SyntheticKind.PLATFORM_GLOBAL_PROPERTY
        || s.getSyntheticKind() == SyntheticKind.CONFIGURATION_OBJECT)
      .map(SyntheticSymbol::getName)
      .filter(name -> matchesGlobalProperty(name, fileType))
      .distinct()
      .toList();
  }

  /**
   * Найти тип глобального свойства по имени (canonical или alias).
   */
  public Optional<TypeRef> findGlobalPropertyType(String name) {
    return findGlobalPropertyType(name, null);
  }

  /**
   * То же, что {@link #findGlobalPropertyType(String)}, но с фильтрацией по типу файла.
   * Возвращает {@link Optional#empty()}, если свойство не видно в данном {@code fileType}.
   */
  public Optional<TypeRef> findGlobalPropertyType(String name, FileType fileType) {
    if (!matchesGlobalProperty(name, fileType)) {
      return Optional.empty();
    }
    return findGlobal(name)
      .filter(SyntheticSymbol.class::isInstance)
      .map(s -> ((SyntheticSymbol) s).getValueType())
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN));
  }

  private boolean matchesGlobalProperty(String name, FileType fileType) {
    if (fileType == null || name == null) {
      return true;
    }
    var scope = globalPropertyScopes.get(name.toLowerCase(Locale.ROOT));
    return scope == null || scope.matches(fileType);
  }

  /**
   * @return имена платформенных классов, доступных в выражении {@code Новый}.
   */
  public List<String> getClasses() {
    return classes;
  }

  /**
   * То же, что {@link #getClasses()}, но с фильтрацией по типу файла.
   */
  public List<String> getClasses(FileType fileType) {
    if (fileType == null) {
      return classes;
    }
    return classes.stream()
      .filter(c -> {
        var s = classScopes.get(c.toLowerCase(Locale.ROOT));
        return s == null || s.matches(fileType);
      })
      .toList();
  }

  /**
   * @return ключевые слова языка для completion в no-dot контексте.
   */
  public List<String> getKeywords() {
    return keywords;
  }

  /**
   * То же, что {@link #getKeywords()}, но с фильтрацией по типу файла.
   */
  public List<String> getKeywords(FileType fileType) {
    if (fileType == null) {
      return keywords;
    }
    return keywords.stream()
      .filter(k -> {
        var s = keywordScopes.get(k.toLowerCase(Locale.ROOT));
        return s == null || s.matches(fileType);
      })
      .toList();
  }

  /**
   * Зарегистрировать имя глобального модуля OneScript-библиотеки
   * (записи {@code <module>} из {@code lib.config}). Имя становится доступным
   * в no-dot completion и резолвится в указанный {@link TypeRef} для
   * dot-completion'а.
   */
  public void registerLibraryModule(String name, TypeRef ref) {
    registerLibraryModule(name, ref, null);
  }

  /**
   * То же, что {@link #registerLibraryModule(String, TypeRef)}, но с явным
   * указанием имени библиотеки-источника (для последующей фильтрации видимости
   * по {@code #Использовать <libName>}).
   */
  public void registerLibraryModule(String name, TypeRef ref, String libOrigin) {
    if (name == null || name.isBlank() || ref == null) {
      return;
    }
    ensureGlobalsPublished();
    var key = name.toLowerCase(Locale.ROOT);
    libraryModules.put(key, ref);
    libraryNamesDisplay.putIfAbsent(key, name);
    if (libOrigin != null && !libOrigin.isBlank()) {
      libraryEntryOrigin.put(key, libOrigin.toLowerCase(Locale.ROOT));
    }
    if (globalSymbolScope != null) {
      var symbol = new SyntheticSymbol(name, SyntheticKind.LIBRARY_MODULE, "", ref);
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE);
    }
  }

  /**
   * Зарегистрировать имя класса OneScript-библиотеки (записи {@code <class>}
   * из {@code lib.config}) и сигнатуры его конструктора.
   */
  public void registerLibraryClass(String name, List<SignatureDescriptor> ctorSignatures) {
    registerLibraryClass(name, ctorSignatures, null);
  }

  /**
   * То же, что {@link #registerLibraryClass(String, List)}, но с явным
   * указанием имени библиотеки-источника.
   */
  public void registerLibraryClass(String name, List<SignatureDescriptor> ctorSignatures, String libOrigin) {
    if (name == null || name.isBlank()) {
      return;
    }
    ensureGlobalsPublished();
    var key = name.toLowerCase(Locale.ROOT);
    libraryClasses.put(key, ctorSignatures == null ? List.of() : List.copyOf(ctorSignatures));
    libraryNamesDisplay.putIfAbsent(key, name);
    if (libOrigin != null && !libOrigin.isBlank()) {
      libraryEntryOrigin.put(key, libOrigin.toLowerCase(Locale.ROOT));
    }
    if (globalSymbolScope != null) {
      var classRef = ctorSignatures != null && !ctorSignatures.isEmpty()
        ? ctorSignatures.get(0).returnType()
        : TypeRef.UNKNOWN;
      var symbol = new SyntheticSymbol(name, SyntheticKind.CONFIGURATION_OBJECT, "", classRef);
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.TYPE_NAME);
    }
  }

  /**
   * @return имена зарегистрированных library-модулей в исходном написании.
   */
  public Collection<String> getLibraryModules() {
    return libraryModules.keySet().stream()
      .map(k -> libraryNamesDisplay.getOrDefault(k, k))
      .toList();
  }

  /**
   * @return имена зарегистрированных library-классов в исходном написании.
   */
  public Collection<String> getLibraryClasses() {
    return libraryClasses.keySet().stream()
      .map(k -> libraryNamesDisplay.getOrDefault(k, k))
      .toList();
  }

  /**
   * Найти {@link TypeRef} library-модуля по имени.
   */
  public Optional<TypeRef> findLibraryModule(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(libraryModules.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Найти сигнатуры конструктора library-класса по имени.
   */
  public List<SignatureDescriptor> findLibraryClassConstructor(String name) {
    if (name == null || name.isBlank()) {
      return List.of();
    }
    return libraryClasses.getOrDefault(name.toLowerCase(Locale.ROOT), List.of());
  }

  /**
   * Удалить ранее зарегистрированный library-модуль по имени.
   */
  public void unregisterLibraryModule(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    var key = name.toLowerCase(Locale.ROOT);
    libraryModules.remove(key);
    if (!libraryClasses.containsKey(key)) {
      libraryNamesDisplay.remove(key);
      libraryEntryOrigin.remove(key);
    }
    if (globalSymbolScope != null) {
      globalSymbolScope.findSymbol(name).ifPresent(globalSymbolScope::unregister);
    }
  }

  /**
   * Удалить ранее зарегистрированный library-класс по имени.
   */
  public void unregisterLibraryClass(String name) {
    if (name == null || name.isBlank()) {
      return;
    }
    var key = name.toLowerCase(Locale.ROOT);
    libraryClasses.remove(key);
    if (!libraryModules.containsKey(key)) {
      libraryNamesDisplay.remove(key);
      libraryEntryOrigin.remove(key);
    }
    if (globalSymbolScope != null) {
      globalSymbolScope.findSymbol(name).ifPresent(globalSymbolScope::unregister);
    }
  }

  /**
   * @return имя библиотеки-источника для library-модуля или library-класса,
   * либо пустой Optional, если запись не зарегистрирована или происхождение
   * неизвестно.
   */
  public Optional<String> getLibraryEntryOrigin(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(libraryEntryOrigin.get(name.toLowerCase(Locale.ROOT)));
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
    return java.util.List.copyOf(configurationQualifiedNames);
  }

  /**
   * Очистить все ранее зарегистрированные library-сущности (например, перед
   * полной переиндексацией OneScript-библиотек workspace'а).
   */
  public void clearLibraryEntries() {
    libraryModules.clear();
    libraryClasses.clear();
    libraryNamesDisplay.clear();
    libraryEntryOrigin.clear();
    // GlobalSymbolScope чистит наполнение через TypeRegistry/owner-провайдеры.
    // Library-записи имеют роль VALUE/TYPE_NAME — точечно их различить здесь
    // нельзя, поэтому полную перечистку выполняет вышестоящий orchestrator
    // (например, OScriptLibraryIndex перед reindex).
  }

  private static Loaded load() {
    var bsl = loadFromResource(RESOURCE_PATH, LanguageScope.BSL);
    var os = loadFromResource(OSCRIPT_RESOURCE_PATH, LanguageScope.OS);
    return merge(bsl, os);
  }

  private static Loaded merge(Loaded a, Loaded b) {
    var functions = new LinkedHashMap<String, MemberDescriptor>(a.functions);
    var functionScopes = new java.util.HashMap<String, LanguageScope>(a.functionScopes);
    for (var e : b.functions.entrySet()) {
      functions.putIfAbsent(e.getKey(), e.getValue());
    }
    for (var e : b.functionScopes.entrySet()) {
      functionScopes.merge(e.getKey(), e.getValue(), LanguageScope::merge);
    }
    var classes = new ArrayList<String>(a.classes.size() + b.classes.size());
    var classScopes = new java.util.HashMap<String, LanguageScope>(a.classScopes);
    var seenClass = new java.util.HashSet<String>();
    for (var c : a.classes) {
      if (seenClass.add(c.toLowerCase(Locale.ROOT))) classes.add(c);
    }
    for (var c : b.classes) {
      if (seenClass.add(c.toLowerCase(Locale.ROOT))) classes.add(c);
    }
    for (var e : b.classScopes.entrySet()) {
      classScopes.merge(e.getKey(), e.getValue(), LanguageScope::merge);
    }
    var keywords = new ArrayList<String>(a.keywords.size() + b.keywords.size());
    var keywordScopes = new java.util.HashMap<String, LanguageScope>(a.keywordScopes);
    var seenKw = new java.util.HashSet<String>();
    for (var k : a.keywords) {
      if (seenKw.add(k.toLowerCase(Locale.ROOT))) keywords.add(k);
    }
    for (var k : b.keywords) {
      if (seenKw.add(k.toLowerCase(Locale.ROOT))) keywords.add(k);
    }
    for (var e : b.keywordScopes.entrySet()) {
      keywordScopes.merge(e.getKey(), e.getValue(), LanguageScope::merge);
    }
    var vars = new ArrayList<PlatformVariable>(a.platformVariables.size() + b.platformVariables.size());
    var varScopes = new java.util.HashMap<String, LanguageScope>(a.platformVariableScopes);
    var seenVar = new java.util.HashSet<String>();
    for (var v : a.platformVariables) {
      if (seenVar.add(v.name().toLowerCase(Locale.ROOT))) vars.add(v);
    }
    for (var v : b.platformVariables) {
      if (seenVar.add(v.name().toLowerCase(Locale.ROOT))) vars.add(v);
    }
    for (var e : b.platformVariableScopes.entrySet()) {
      varScopes.merge(e.getKey(), e.getValue(), LanguageScope::merge);
    }
    return new Loaded(
      Collections.unmodifiableMap(functions),
      List.copyOf(classes),
      List.copyOf(keywords),
      List.copyOf(vars),
      new java.util.concurrent.ConcurrentHashMap<>(functionScopes),
      new java.util.concurrent.ConcurrentHashMap<>(classScopes),
      new java.util.concurrent.ConcurrentHashMap<>(keywordScopes),
      new java.util.concurrent.ConcurrentHashMap<>(varScopes)
    );
  }

  private static Loaded loadFromResource(String resourcePath, LanguageScope scope) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> root = mapper.readValue(stream, Map.class);
      var functions = readFunctions(root);
      @SuppressWarnings("unchecked")
      var classes = (List<String>) root.getOrDefault("classes", Collections.emptyList());
      @SuppressWarnings("unchecked")
      var keywords = (List<String>) root.getOrDefault("keywords", Collections.emptyList());
      var variables = readVariables(root);
      var fnScopes = new java.util.HashMap<String, LanguageScope>();
      functions.keySet().forEach(k -> fnScopes.put(k, scope));
      var clsScopes = new java.util.HashMap<String, LanguageScope>();
      classes.forEach(c -> clsScopes.put(c.toLowerCase(Locale.ROOT), scope));
      var kwScopes = new java.util.HashMap<String, LanguageScope>();
      keywords.forEach(k -> kwScopes.put(k.toLowerCase(Locale.ROOT), scope));
      var varScopes = new java.util.HashMap<String, LanguageScope>();
      for (var v : variables) {
        varScopes.put(v.name().toLowerCase(Locale.ROOT), scope);
        v.aliases().forEach(a -> varScopes.put(a.toLowerCase(Locale.ROOT), scope));
      }
      return new Loaded(functions, List.copyOf(classes), List.copyOf(keywords), variables,
        fnScopes, clsScopes, kwScopes, varScopes);
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin globals resource: {}", resourcePath, e);
      return new Loaded(Collections.emptyMap(), List.of(), List.of(), List.of(),
        Map.of(), Map.of(), Map.of(), Map.of());
    }
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
      var descriptor = new MemberDescriptor(name, MemberKind.METHOD, description, returnType, signatures);
      result.put(name.toLowerCase(Locale.ROOT), descriptor);
      var aliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
      for (var alias : aliases) {
        result.put(alias.toLowerCase(Locale.ROOT), descriptor);
      }
    }
    return Collections.unmodifiableMap(result);
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
        params.add(new ParameterDescriptor(pname, TypeSet.EMPTY, optional, pdesc));
      }
      result.add(new SignatureDescriptor(params, returnType, description));
    }
    return result;
  }

  private record Loaded(
    Map<String, MemberDescriptor> functions,
    List<String> classes,
    List<String> keywords,
    List<PlatformVariable> platformVariables,
    Map<String, LanguageScope> functionScopes,
    Map<String, LanguageScope> classScopes,
    Map<String, LanguageScope> keywordScopes,
    Map<String, LanguageScope> platformVariableScopes
  ) {
  }

  /**
   * Описание платформенной глобальной переменной.
   */
  public record PlatformVariable(String name, List<String> aliases, String description, TypeRef type) {
  }
}
