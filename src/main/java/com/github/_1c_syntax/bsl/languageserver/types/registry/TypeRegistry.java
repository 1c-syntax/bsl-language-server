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

import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.AnyType;
import com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformType;
import com.github._1c_syntax.bsl.languageserver.types.model.PrimitiveType;
import com.github._1c_syntax.bsl.languageserver.types.model.Type;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.UnknownType;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Реестр известных типов.
 * <p>
 * Заменяет старый {@code KnownTypes}. Источники типов — реализации
 * {@link TypePackProvider} (специализация {@link PlatformTypesProvider} для
 * платформенных типов, динамические добавления через {@link #registerUserType}
 * для пользовательских/конфигурационных). Один тип может расширяться
 * несколькими источниками членов ({@link MemberSource}).
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class TypeRegistry {

  private final List<PlatformTypesProvider> platformProviders;
  /**
   * Параллельный Symbol-фронт: глобальные свойства и прочие глобальные символы.
   * Опционален, чтобы существующие unit-тесты, собирающие TypeRegistry вручную
   * без Spring, продолжали работать.
   */
  @Autowired(required = false)
  private GlobalScopeProvider globalScopeProvider;

  /** Интернированные TypeRef по канонической форме (kind + lowercased name). */
  private final Map<TypeRef, TypeRef> internedRefs = new ConcurrentHashMap<>();
  /** Алиасы (включая Ru/En) → канонический TypeRef. Ключ — lowercased имя. */
  private final Map<String, TypeRef> aliasIndex = new ConcurrentHashMap<>();
  /** Тип ↔ объект Type (hydrated). */
  private final Map<TypeRef, Type> types = new ConcurrentHashMap<>();
  /** Тип ↔ список источников членов (один тип может расширяться многими источниками). */
  private final Map<TypeRef, List<ScopedMemberSource>> memberSources = new ConcurrentHashMap<>();
  /** Тип ↔ языковой скоуп (BSL/OS/BOTH). Отсутствие записи трактуется как BOTH. */
  private final Map<TypeRef, LanguageScope> typeScopes = new ConcurrentHashMap<>();
  /** Тип ↔ описание (из JSON-пакета или динамической регистрации). Пусто если описания нет. */
  private final Map<TypeRef, String> descriptions = new ConcurrentHashMap<>();
  /** Тип ↔ список конструкторов (для платформенных классов из JSON-пакета). */
  private final Map<TypeRef, List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> constructors
    = new ConcurrentHashMap<>();
  /** Тип ↔ динамические источники конструкторов (например, OScript-класс из SymbolTree). */
  private final Map<TypeRef, List<java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>>>> constructorSources
    = new ConcurrentHashMap<>();

  /** Источник членов вместе с его языковым скоупом. */
  private record ScopedMemberSource(MemberSource source, LanguageScope scope) {
  }

  @PostConstruct
  void bootstrap() {
    if (platformProviders == null) {
      return;
    }
    for (var provider : platformProviders) {
      var scope = provider.getLanguageScope();
      for (var decl : provider.getTypes()) {
        registerPack(decl, scope);
      }
    }
  }

  /**
   * Интернировать ссылку на тип. Если такой тип уже зарегистрирован,
   * возвращает каноническую ссылку.
   */
  public TypeRef intern(TypeKind kind, String qualifiedName) {
    var candidate = new TypeRef(kind, qualifiedName);
    var existing = internedRefs.putIfAbsent(candidate, candidate);
    return existing != null ? existing : candidate;
  }

  /**
   * Найти тип по имени (регистронезависимо, с учётом Ru/En алиасов).
   */
  public Optional<TypeRef> resolve(String name) {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(aliasIndex.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * Найти тип по имени с фильтрацией по типу файла. Тип будет возвращён только
   * если его {@link LanguageScope} совместим с {@code fileType}.
   * Если {@code fileType == null} — фильтрация не применяется.
   */
  public Optional<TypeRef> resolve(String name, FileType fileType) {
    return resolve(name).filter(ref -> getLanguageScope(ref).matches(fileType));
  }

  /**
   * Языковой скоуп типа. По умолчанию (неизвестный тип / без записи) — {@link LanguageScope#BOTH}.
   */
  public LanguageScope getLanguageScope(TypeRef ref) {
    return typeScopes.getOrDefault(ref, LanguageScope.BOTH);
  }

  /**
   * Установить/повысить языковой скоуп типа. При наличии существующей записи
   * новый скоуп мержится: при различии повышается до {@link LanguageScope#BOTH}.
   */
  public void setLanguageScope(TypeRef ref, LanguageScope scope) {
    if (ref == null || scope == null) {
      return;
    }
    typeScopes.merge(ref, scope, LanguageScope::merge);
  }

  /**
   * Найти тип по точному совпадению канонического имени и kind'а.
   */
  public Optional<TypeRef> resolve(TypeKind kind, String qualifiedName) {
    var ref = new TypeRef(kind, qualifiedName);
    return Optional.ofNullable(internedRefs.get(ref));
  }

  /**
   * Получить hydrated {@link Type} по ссылке.
   */
  public Type get(TypeRef ref) {
    return types.getOrDefault(ref, UnknownType.INSTANCE);
  }

  /**
   * Получить полный набор членов типа — union по всем зарегистрированным
   * {@link MemberSource}'ам. Дубли по имени отбрасываются (побеждает первый
   * зарегистрированный источник).
   */
  public Collection<MemberDescriptor> getMembers(TypeRef ref) {
    return getMembers(ref, null);
  }

  /**
   * То же, что {@link #getMembers(TypeRef)}, но фильтрует источники по языковому скоупу.
   * При {@code fileType == null} фильтрация не применяется.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef ref, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    var sources = memberSources.get(ref);
    if (sources == null || sources.isEmpty()) {
      return Collections.emptyList();
    }
    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var scoped : sources) {
      if (fileType != null && scoped.scope() != null && !scoped.scope().matches(fileType)) {
        continue;
      }
      for (var member : scoped.source().getMembers()) {
        byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
      }
    }
    return new ArrayList<>(byName.values());
  }

  /**
   * Добавить дополнительный источник членов к существующему типу.
   * Позволяет, например, {@code ManagerModule.bsl} расширять платформенный
   * {@code СправочникМенеджер.X}.
   */
  public void registerMemberSource(TypeRef ref, MemberSource source) {
    registerMemberSource(ref, source, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #registerMemberSource(TypeRef, MemberSource)}, но дополнительно
   * привязывает источник к языковому скоупу. {@code null} трактуется как
   * {@link LanguageScope#BOTH}.
   */
  public void registerMemberSource(TypeRef ref, MemberSource source, LanguageScope scope) {
    var effective = scope == null ? LanguageScope.BOTH : scope;
    memberSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedMemberSource(source, effective));
  }

  /**
   * Зарегистрировать пользовательский тип (OneScript-класс, общий модуль и т.п.).
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration) {
    return registerUserType(qualifiedName, declaration, LanguageScope.BOTH);
  }

  /**
   * Зарегистрировать пользовательский тип с указанием языкового скоупа.
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration, LanguageScope scope) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.put(ref, new UserType(ref, declaration));
    addAlias(qualifiedName, ref);
    setLanguageScope(ref, scope == null ? LanguageScope.BOTH : scope);
    return ref;
  }

  /**
   * Зарегистрировать конфигурационный тип (Справочники.X, Документы.X и т.д.).
   * Конфигурационные типы всегда BSL-only.
   */
  public TypeRef registerConfigurationType(String qualifiedName) {
    var ref = intern(TypeKind.CONFIGURATION, qualifiedName);
    types.put(ref, new ConfigurationType(ref));
    addAlias(qualifiedName, ref);
    setLanguageScope(ref, LanguageScope.BSL);
    return ref;
  }

  /**
   * Зарегистрировать дополнительный алиас (английский вариант, синоним) для
   * уже зарегистрированного конфигурационного типа.
   */
  public void registerConfigurationTypeAlias(String alias, TypeRef ref) {
    addAlias(alias, ref);
  }

  /**
   * Зарегистрировать тип как глобальное свойство (его имя становится
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.Метод()}).
   * Регистрация идёт в {@link GlobalScopeProvider} — единая точка входа
   * для глобальных имён.
   */
  public void registerAsGlobalProperty(TypeRef ref) {
    registerAsGlobalProperty(ref, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #registerAsGlobalProperty(TypeRef)}, но дополнительно
   * пробрасывает скоуп в {@link GlobalScopeProvider}.
   */
  public void registerAsGlobalProperty(TypeRef ref, LanguageScope scope) {
    if (globalScopeProvider == null) {
      return;
    }
    var names = new java.util.LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerGlobalProperty(ref, names, scope, descriptions.getOrDefault(ref, ""));
  }

  /**
   * Описание типа из источника (JSON-пакета или динамической регистрации).
   * Возвращает пустую строку, если описание отсутствует.
   */
  public String getDescription(TypeRef ref) {
    if (ref == null) {
      return "";
    }
    return descriptions.getOrDefault(ref, "");
  }

  /**
   * Список конструкторов типа (для платформенных классов из JSON-пакета).
   * Возвращает пустой список, если конструкторов нет (например, для типов
   * без блока {@code constructors} в JSON или для system enums).
   */
  public List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> getConstructors(TypeRef ref) {
    if (ref == null) {
      return List.of();
    }
    var fromPack = constructors.getOrDefault(ref, List.<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>of());
    var sources = constructorSources.get(ref);
    if (sources == null || sources.isEmpty()) {
      return fromPack;
    }
    var result = new ArrayList<>(fromPack);
    for (var supplier : sources) {
      var sigs = supplier.get();
      if (sigs != null) {
        result.addAll(sigs);
      }
    }
    return result;
  }

  /**
   * Зарегистрировать динамический источник конструкторов для типа (например,
   * {@code ПриСозданииОбъекта} OneScript-класса из SymbolTree).
   * Источник вызывается каждый раз при запросе {@link #getConstructors(TypeRef)},
   * что обеспечивает hot-reload без ручной инвалидации.
   */
  public void registerConstructorSource(
    TypeRef ref,
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> source
  ) {
    if (ref == null || source == null) {
      return;
    }
    constructorSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>())).add(source);
  }

  /**
   * Зарегистрировать тип как платформенный класс с конструктором. Имя
   * становится доступным для completion после {@code Новый} (через
   * {@link GlobalScopeProvider#getClasses}) и резолвится в {@link SyntheticSymbol}
   * с ролью {@code TYPE_NAME} для hover/findGlobal. Вызывается автоматически
   * из {@link #registerPack} при непустых {@code constructors}.
   */
  private void registerAsPlatformClass(TypeRef ref, LanguageScope scope) {
    if (globalScopeProvider == null) {
      return;
    }
    var names = new java.util.LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerPlatformClass(ref, names, scope,
      descriptions.getOrDefault(ref, ""));
  }

  /**
   * Удалить пользовательский тип по qualifiedName (например, при закрытии
   * соответствующего документа).
   */
  public void unregisterUserType(String qualifiedName) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.remove(ref);
    memberSources.remove(ref);
    typeScopes.remove(ref);
    aliasIndex.remove(qualifiedName.toLowerCase(Locale.ROOT));
  }

  private void registerPack(TypePackProvider.TypeDecl decl) {
    registerPack(decl, LanguageScope.BOTH);
  }

  private void registerPack(TypePackProvider.TypeDecl decl, LanguageScope scope) {
    var ref = intern(decl.kind(), decl.qualifiedName());
    types.put(ref, hydrate(ref));
    addAlias(decl.qualifiedName(), ref);
    for (var alias : decl.aliases()) {
      addAlias(alias, ref);
    }
    if (decl.description() != null && !decl.description().isBlank()) {
      descriptions.put(ref, decl.description());
    }
    if (decl.constructors() != null && !decl.constructors().isEmpty()) {
      constructors.put(ref, List.copyOf(decl.constructors()));
      registerAsPlatformClass(ref, scope);
    }
    if (!decl.members().isEmpty()) {
      registerMemberSource(ref, decl::members, scope);
    }
    if (decl.exposedAsGlobal()) {
      registerAsGlobalProperty(ref, scope);
    }
    setLanguageScope(ref, scope == null ? LanguageScope.BOTH : scope);
  }

  private void addAlias(String name, TypeRef ref) {
    aliasIndex.put(name.toLowerCase(Locale.ROOT), ref);
  }

  private static Type hydrate(TypeRef ref) {
    return switch (ref.kind()) {
      case PRIMITIVE -> new PrimitiveType(ref);
      case PLATFORM -> new PlatformType(ref);
      case CONFIGURATION -> new ConfigurationType(ref);
      case USER -> new UserType(ref, new java.lang.ref.WeakReference<>(null));
      case ANY -> AnyType.INSTANCE;
      case UNKNOWN -> UnknownType.INSTANCE;
    };
  }
}
