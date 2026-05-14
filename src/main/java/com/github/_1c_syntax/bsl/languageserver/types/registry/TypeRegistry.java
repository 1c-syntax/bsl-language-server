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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.AnyType;
import com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType;
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

  /** Интернированные TypeRef по канонической форме (kind + lowercased name). */
  private final Map<TypeRef, TypeRef> internedRefs = new ConcurrentHashMap<>();
  /** Алиасы (включая Ru/En) → канонический TypeRef. Ключ — lowercased имя. */
  private final Map<String, TypeRef> aliasIndex = new ConcurrentHashMap<>();
  /** Тип ↔ объект Type (hydrated). */
  private final Map<TypeRef, Type> types = new ConcurrentHashMap<>();
  /** Тип ↔ список источников членов (один тип может расширяться многими источниками). */
  private final Map<TypeRef, List<MemberSource>> memberSources = new ConcurrentHashMap<>();
  /** Имена-неймспейсы (lowercased) — типы, чьё имя само используется как namespace-ресивер (например, {@code КодировкаТекста.UTF8}). */
  private final Map<String, TypeRef> namespaceIndex = new ConcurrentHashMap<>();

  @PostConstruct
  void bootstrap() {
    if (platformProviders == null) {
      return;
    }
    for (var provider : platformProviders) {
      for (var decl : provider.getTypes()) {
        registerPack(decl);
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
    var sources = memberSources.get(ref);
    if (sources == null || sources.isEmpty()) {
      return Collections.emptyList();
    }
    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var source : sources) {
      for (var member : source.getMembers()) {
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
    memberSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>())).add(source);
  }

  /**
   * Зарегистрировать пользовательский тип (OneScript-класс, общий модуль и т.п.).
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.put(ref, new UserType(ref, declaration));
    addAlias(qualifiedName, ref);
    return ref;
  }

  /**
   * Зарегистрировать конфигурационный тип (Справочники.X, Документы.X и т.д.).
   */
  public TypeRef registerConfigurationType(String qualifiedName) {
    var ref = intern(TypeKind.CONFIGURATION, qualifiedName);
    types.put(ref, new ConfigurationType(ref));
    addAlias(qualifiedName, ref);
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
   * Пометить тип как namespace-приёмник (его имя может выступать ресивером
   * dot-выражения: {@code Документы.Контрагенты}, {@code КодировкаТекста.UTF8}).
   * Имя и все его алиасы попадают в namespace-индекс.
   */
  public void registerNamespace(TypeRef ref) {
    var canonical = ref.qualifiedName();
    namespaceIndex.put(canonical.toLowerCase(Locale.ROOT), ref);
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        namespaceIndex.put(alias, ref);
      }
    });
  }

  /**
   * Удалить пользовательский тип по qualifiedName (например, при закрытии
   * соответствующего документа).
   */
  public void unregisterUserType(String qualifiedName) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.remove(ref);
    memberSources.remove(ref);
    aliasIndex.remove(qualifiedName.toLowerCase(Locale.ROOT));
  }

  private void registerPack(TypePackProvider.TypeDecl decl) {
    var ref = intern(decl.kind(), decl.qualifiedName());
    types.put(ref, hydrate(ref));
    addAlias(decl.qualifiedName(), ref);
    for (var alias : decl.aliases()) {
      addAlias(alias, ref);
    }
    if (!decl.members().isEmpty()) {
      registerMemberSource(ref, decl::members);
    }
    if (decl.namespace()) {
      namespaceIndex.put(decl.qualifiedName().toLowerCase(Locale.ROOT), ref);
      for (var alias : decl.aliases()) {
        namespaceIndex.put(alias.toLowerCase(Locale.ROOT), ref);
      }
    }
  }

  /**
   * Найти namespace-тип по имени (регистронезависимо). Namespace-типы — те,
   * чьё имя само может выступать как ресивер dot-выражения, например
   * {@code КодировкаТекста.UTF8}.
   */
  public Optional<TypeRef> resolveNamespace(String name) {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(namespaceIndex.get(name.toLowerCase(Locale.ROOT)));
  }

  /**
   * @return имена зарегистрированных namespace-типов в каноническом написании.
   */
  public Collection<String> getNamespaceNames() {
    return namespaceIndex.values().stream()
      .map(TypeRef::qualifiedName)
      .distinct()
      .toList();
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
