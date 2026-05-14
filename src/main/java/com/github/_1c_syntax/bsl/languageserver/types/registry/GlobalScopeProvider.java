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

  private final Map<String, MemberDescriptor> functions;
  private final List<String> classes;
  private final List<String> keywords;
  /** Имена library-модулей OneScript (lowercased) → TypeRef модуля. */
  private final Map<String, TypeRef> libraryModules = new java.util.concurrent.ConcurrentHashMap<>();
  /** Имена library-классов OneScript (lowercased) → сигнатуры конструктора. */
  private final Map<String, List<SignatureDescriptor>> libraryClasses = new java.util.concurrent.ConcurrentHashMap<>();
  /** Хранит исходные написания имён library-сущностей для отображения. */
  private final Map<String, String> libraryNamesDisplay = new java.util.concurrent.ConcurrentHashMap<>();

  public GlobalScopeProvider() {
    var loaded = load();
    this.functions = loaded.functions;
    this.classes = loaded.classes;
    this.keywords = loaded.keywords;
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
  }

  /**
   * @return неизменяемая коллекция глобальных функций
   */
  public Collection<MemberDescriptor> getFunctions() {
    return functions.values();
  }

  /**
   * Поиск глобальной функции по имени (регистронезависимо, с учётом ru/en алиасов).
   */
  public Optional<MemberDescriptor> findFunction(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(functions.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Зарегистрировать тип как глобальное свойство — его имя (и алиасы) становятся
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.МойМетод()},
   * {@code ФС.КаталогПустой()}. Каждое имя получает {@link SyntheticSymbol}
   * с типом-значением {@code ref}.
   */
  public void registerGlobalProperty(TypeRef ref, Collection<String> names) {
    if (ref == null || names == null || names.isEmpty()) {
      return;
    }
    ensureGlobalsPublished();
    var canonical = ref.qualifiedName();
    var symbol = new SyntheticSymbol(canonical, SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", ref);
    if (globalSymbolScope == null) {
      return;
    }
    for (var name : names) {
      if (name == null || name.isBlank()) {
        continue;
      }
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE);
    }
  }

  /**
   * Имена всех зарегистрированных глобальных свойств (canonical-форма, без алиасов).
   */
  public Collection<String> getGlobalPropertyNames() {
    if (globalSymbolScope == null) {
      return List.of();
    }
    return globalSymbolScope.streamSymbols()
      .filter(SyntheticSymbol.class::isInstance)
      .map(SyntheticSymbol.class::cast)
      .filter(s -> s.getSyntheticKind() == SyntheticKind.PLATFORM_GLOBAL_PROPERTY
        || s.getSyntheticKind() == SyntheticKind.CONFIGURATION_OBJECT)
      .map(SyntheticSymbol::getName)
      .distinct()
      .toList();
  }

  /**
   * Найти тип глобального свойства по имени (canonical или alias).
   */
  public Optional<TypeRef> findGlobalPropertyType(String name) {
    return findGlobal(name)
      .filter(SyntheticSymbol.class::isInstance)
      .map(s -> ((SyntheticSymbol) s).getValueType())
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN));
  }

  /**
   * @return имена платформенных классов, доступных в выражении {@code Новый}.
   */
  public List<String> getClasses() {
    return classes;
  }

  /**
   * @return ключевые слова языка для completion в no-dot контексте.
   */
  public List<String> getKeywords() {
    return keywords;
  }

  /**
   * Зарегистрировать имя глобального модуля OneScript-библиотеки
   * (записи {@code <module>} из {@code lib.config}). Имя становится доступным
   * в no-dot completion и резолвится в указанный {@link TypeRef} для
   * dot-completion'а.
   */
  public void registerLibraryModule(String name, TypeRef ref) {
    if (name == null || name.isBlank() || ref == null) {
      return;
    }
    ensureGlobalsPublished();
    var key = name.toLowerCase(Locale.ROOT);
    libraryModules.put(key, ref);
    libraryNamesDisplay.putIfAbsent(key, name);
    if (globalSymbolScope != null) {
      var symbol = new SyntheticSymbol(name, SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", ref);
      globalSymbolScope.register(name, symbol, GlobalSymbolScope.Role.VALUE);
    }
  }

  /**
   * Зарегистрировать имя класса OneScript-библиотеки (записи {@code <class>}
   * из {@code lib.config}) и сигнатуры его конструктора.
   */
  public void registerLibraryClass(String name, List<SignatureDescriptor> ctorSignatures) {
    if (name == null || name.isBlank()) {
      return;
    }
    ensureGlobalsPublished();
    var key = name.toLowerCase(Locale.ROOT);
    libraryClasses.put(key, ctorSignatures == null ? List.of() : List.copyOf(ctorSignatures));
    libraryNamesDisplay.putIfAbsent(key, name);
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
    }
    if (globalSymbolScope != null) {
      globalSymbolScope.findSymbol(name).ifPresent(globalSymbolScope::unregister);
    }
  }

  /**
   * Очистить все ранее зарегистрированные library-сущности (например, перед
   * полной переиндексацией OneScript-библиотек workspace'а).
   */
  public void clearLibraryEntries() {
    libraryModules.clear();
    libraryClasses.clear();
    libraryNamesDisplay.clear();
    // GlobalSymbolScope чистит наполнение через TypeRegistry/owner-провайдеры.
    // Library-записи имеют роль VALUE/TYPE_NAME — точечно их различить здесь
    // нельзя, поэтому полную перечистку выполняет вышестоящий orchestrator
    // (например, OScriptLibraryIndex перед reindex).
  }

  private static Loaded load() {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> root = mapper.readValue(stream, Map.class);
      var functions = readFunctions(root);
      @SuppressWarnings("unchecked")
      var classes = (List<String>) root.getOrDefault("classes", Collections.emptyList());
      @SuppressWarnings("unchecked")
      var keywords = (List<String>) root.getOrDefault("keywords", Collections.emptyList());
      return new Loaded(functions, List.copyOf(classes), List.copyOf(keywords));
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin globals resource: {}", RESOURCE_PATH, e);
      return new Loaded(Collections.emptyMap(), List.of(), List.of());
    }
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
    List<String> keywords
  ) {
  }
}
