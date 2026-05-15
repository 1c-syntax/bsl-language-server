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

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.utils.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Поставщик платформенных типов на основе синтакс-помощника установленной
 * платформы 1С (через библиотеку {@code bsl-context}). Источник
 * {@link ContextProvider} берётся из {@link BslContextHolder} — общий
 * workspace-scoped кэш парсинга HBK.
 * <p>
 * Соответствие категорий bsl-context → {@link TypeKind} type-system v2:
 * <ul>
 *   <li>{@code PRIMITIVE_TYPE} → {@link TypeKind#PRIMITIVE} — примитивы языка
 *       (Строка, Число, Дата, Булево, Тип, Null, Неопределено, Произвольный);</li>
 *   <li>{@code TYPE} → {@link TypeKind#PLATFORM} — платформенные типы со
 *       свойствами и методами;</li>
 *   <li>{@code ENUM} → {@link TypeKind#PLATFORM} — системные перечисления;
 *       значения публикуются как {@link MemberKind#PROPERTY} с типом самого
 *       перечисления (для dot-completion'а);</li>
 *   <li>{@code GLOBAL_CONTEXT} и {@code LANGUAGE_KEYWORD} — типами не являются,
 *       пропускаются. Глобальный контекст и языковые keyword'ы потребляет
 *       {@link GlobalScopeProvider}.</li>
 * </ul>
 * <p>
 * Что из членов типа сейчас НЕ публикуется (type-system v2 моделирует только
 * {@link MemberKind#METHOD} / {@link MemberKind#PROPERTY}): события
 * ({@code ContextType.events()}). Также не публикуются метаданные
 * методов/свойств/конструкторов ({@code sinceVersion},
 * {@code deprecatedSinceVersion}, {@code recommendedReplacements},
 * {@code availabilities}, {@code accessMode}, {@code syntaxText},
 * {@code defaultValue} и т.п.) — они доступны в bsl-context, но
 * соответствующие LS-дескрипторы их пока не носят. См. {@code tmp/PLAN.md}
 * в bsl-context.
 */
@Slf4j
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class BslContextPlatformTypesProvider implements PlatformTypesProvider {

  private final BslContextHolder contextHolder;
  private final Lazy<List<TypeDecl>> cached;

  public BslContextPlatformTypesProvider(BslContextHolder contextHolder) {
    this.contextHolder = contextHolder;
    this.cached = new Lazy<>(() -> build(contextHolder.get().orElse(null)));
  }

  @Override
  public Collection<TypeDecl> getTypes() {
    return cached.getOrCompute();
  }

  @Override
  public LanguageScope getLanguageScope() {
    return LanguageScope.BSL;
  }

  private static List<TypeDecl> build(ContextProvider provider) {
    if (provider == null) {
      return List.of();
    }
    var contexts = provider.getContexts();
    var result = new ArrayList<TypeDecl>(contexts.size());
    for (var context : contexts) {
      var decl = toTypeDecl(context);
      if (decl != null) {
        result.add(decl);
      }
    }
    return List.copyOf(result);
  }

  private static TypeDecl toTypeDecl(Context context) {
    var kind = mapKind(context);
    if (kind == null) {
      return null;
    }
    var qualifiedName = context.name().getName();
    var aliases = aliasesOf(context.name());
    var members = collectMembers(context);
    var description = descriptionOf(context);
    var classRef = new TypeRef(kind, qualifiedName);
    var constructors = constructorsOf(context, classRef);
    return new TypeDecl(kind, qualifiedName, aliases, members,
      isExposedAsGlobal(context), description, constructors);
  }

  private static String descriptionOf(Context context) {
    if (context instanceof ContextType type) {
      return type.description();
    }
    return "";
  }

  /**
   * Маппит конструкторы платформенного типа из bsl-context
   * ({@link ContextConstructor}) в {@link SignatureDescriptor} type-system v2.
   * Возвращаемый тип конструктора — сам тип ({@code classRef}); параметры —
   * как у обычных методов.
   */
  private static List<SignatureDescriptor> constructorsOf(Context context, TypeRef classRef) {
    if (!(context instanceof ContextType type)) {
      return List.of();
    }
    var ctors = type.constructors();
    if (ctors == null || ctors.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<SignatureDescriptor>(ctors.size());
    for (var ctor : ctors) {
      var parameters = new ArrayList<ParameterDescriptor>(ctor.parameters().size());
      for (var parameter : ctor.parameters()) {
        parameters.add(toParameterDescriptor(parameter));
      }
      result.add(new SignatureDescriptor(parameters, classRef, ctor.description()));
    }
    return List.copyOf(result);
  }

  /**
   * Помечает тип как «возможный ресивер dot-выражения через глобальное имя»
   * по эвристике: внутри типа есть generic-property вида {@code <Имя X>}.
   * Это резервный сигнал для LS — основной путь связывания
   * {@code Справочники} ↔ {@code СправочникиМенеджер} идёт через
   * {@link GlobalScopeProvider} из свойств глобального контекста.
   */
  private static boolean isExposedAsGlobal(Context context) {
    if (!(context instanceof ContextType type)) {
      return false;
    }
    for (var p : type.properties()) {
      if (p.isGeneric()) {
        return true;
      }
    }
    return false;
  }

  private static TypeKind mapKind(Context context) {
    return switch (context.kind()) {
      case PRIMITIVE_TYPE -> TypeKind.PRIMITIVE;
      case TYPE, ENUM -> TypeKind.PLATFORM;
      // GLOBAL_CONTEXT и LANGUAGE_KEYWORD типами не являются — обрабатываются
      // отдельно (GlobalScopeProvider).
      case GLOBAL_CONTEXT, LANGUAGE_KEYWORD -> null;
    };
  }

  private static List<String> aliasesOf(ContextName name) {
    var alias = name.getAlias();
    if (alias == null || alias.isBlank() || alias.equalsIgnoreCase(name.getName())) {
      return List.of();
    }
    return List.of(alias);
  }

  private static List<MemberDescriptor> collectMembers(Context context) {
    if (context instanceof ContextType type) {
      var members = new ArrayList<MemberDescriptor>(
        type.methods().size() + type.properties().size());
      for (var property : type.properties()) {
        members.add(toMemberDescriptor(property));
      }
      for (var method : type.methods()) {
        members.add(toMemberDescriptor(method));
      }
      return List.copyOf(members);
    }
    if (context instanceof ContextEnum enumeration) {
      var values = enumeration.values();
      var members = new ArrayList<MemberDescriptor>(values.size());
      var enumRef = new TypeRef(TypeKind.PLATFORM, context.name().getName());
      for (var value : values) {
        members.add(toMemberDescriptor(value, enumRef));
      }
      return List.copyOf(members);
    }
    return Collections.emptyList();
  }

  static MemberDescriptor toMemberDescriptor(ContextProperty property) {
    var returnType = singleType(property.types());
    return new MemberDescriptor(
      property.name().getName(),
      MemberKind.PROPERTY,
      property.description(),
      returnType,
      List.of()
    );
  }

  static MemberDescriptor toMemberDescriptor(ContextMethod method) {
    var returnType = singleType(method.returnValues());
    var signatures = toSignatures(method.signatures(), returnType);
    return new MemberDescriptor(
      method.name().getName(),
      MemberKind.METHOD,
      method.description(),
      returnType,
      signatures
    );
  }

  private static MemberDescriptor toMemberDescriptor(ContextEnumValue value, TypeRef enumRef) {
    return new MemberDescriptor(
      value.name().getName(),
      MemberKind.PROPERTY,
      value.description(),
      enumRef,
      List.of()
    );
  }

  static List<SignatureDescriptor> toSignatures(
    List<ContextMethodSignature> signatures,
    TypeRef returnType
  ) {
    if (signatures == null || signatures.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<SignatureDescriptor>(signatures.size());
    for (var signature : signatures) {
      var parameters = new ArrayList<ParameterDescriptor>(signature.parameters().size());
      for (var parameter : signature.parameters()) {
        parameters.add(toParameterDescriptor(parameter));
      }
      // bsl-context хранит returnType один на метод (одна страница HBK —
      // один блок «Возвращаемое значение:»), поэтому он одинаков для
      // всех вариантов сигнатуры.
      result.add(new SignatureDescriptor(parameters, returnType, signature.description()));
    }
    return List.copyOf(result);
  }

  private static ParameterDescriptor toParameterDescriptor(ContextSignatureParameter parameter) {
    return new ParameterDescriptor(
      parameter.name().getName(),
      typeSet(parameter.types()),
      !parameter.isRequired(),
      parameter.description()
    );
  }

  static TypeRef singleType(List<Context> contexts) {
    if (contexts == null || contexts.isEmpty()) {
      return TypeRef.UNKNOWN;
    }
    var first = contexts.get(0);
    return new TypeRef(mapRefKind(first), first.name().getName());
  }

  private static TypeSet typeSet(List<Context> contexts) {
    if (contexts == null || contexts.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new ArrayList<TypeRef>(contexts.size());
    for (var c : contexts) {
      refs.add(new TypeRef(mapRefKind(c), c.name().getName()));
    }
    return TypeSet.of(refs);
  }

  private static TypeKind mapRefKind(Context context) {
    return switch (context.kind()) {
      case PRIMITIVE_TYPE -> TypeKind.PRIMITIVE;
      case TYPE, ENUM -> TypeKind.PLATFORM;
      case GLOBAL_CONTEXT, LANGUAGE_KEYWORD -> TypeKind.UNKNOWN;
    };
  }
}
