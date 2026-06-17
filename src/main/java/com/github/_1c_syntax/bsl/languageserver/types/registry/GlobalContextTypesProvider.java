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
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.EnAttachments;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.utils.Lazy;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Composer глобального контекста (issue #3994): читает <b>источник</b>
 * (bsl-context либо встроенный JSON) и собирает синтетический тип
 * {@link TypeRegistry#GLOBAL_CONTEXT} — его члены суть глобальные методы и
 * свойства, видимые без префикса. {@code exposedAsGlobal}-типы (системные
 * перечисления и пр.) попадают сюда как свойства-члены с {@code valueType} =
 * сам тип.
 * <p>
 * Ключевой момент развязки: знание «эта сущность видна в глобальной области»
 * остаётся метаданными источника и применяется здесь, при сборке
 * {@code GLOBAL_CONTEXT}. Конкретный тип ({@code КодировкаТекста}) этого о себе
 * не знает, а {@link TypeRegistry} не эмитит ничего в глобальную область — он
 * лишь штатно регистрирует члены типа {@code GLOBAL_CONTEXT}, отданные этим
 * провайдером через {@link TypeDecl}.
 */
@Component
@WorkspaceScope
public class GlobalContextTypesProvider implements PlatformTypesProvider {

  private static final String PLATFORM_TYPES_RESOURCE =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json";

  private static final BilingualString GLOBAL_CONTEXT_NAME =
    BilingualString.of(TypeRegistry.GLOBAL_CONTEXT.qualifiedName(), "GlobalContext");

  private final BslContextHolder contextHolder;
  private final Lazy<List<TypeDecl>> cached;

  public GlobalContextTypesProvider(BslContextHolder contextHolder) {
    this.contextHolder = contextHolder;
    this.cached = new Lazy<>(this::build);
  }

  @Override
  public Collection<TypeDecl> getTypes() {
    return cached.getOrCompute();
  }

  @Override
  public FileType getFileType() {
    return FileType.BSL;
  }

  private List<TypeDecl> build() {
    var providerOpt = contextHolder.get();
    var members = providerOpt
      .map(GlobalContextTypesProvider::membersFromContext)
      .orElseGet(GlobalContextTypesProvider::membersFromBuiltin);
    if (members.isEmpty()) {
      return List.of();
    }
    return List.of(globalContextDecl(members));
  }

  private static TypeDecl globalContextDecl(List<MemberDescriptor> members) {
    return new TypeDecl(
      TypeRegistry.GLOBAL_CONTEXT.kind(),
      GLOBAL_CONTEXT_NAME,
      members,
      false,
      BilingualString.EMPTY,
      List.of(),
      List.of(),
      false,
      false,
      BilingualString.EMPTY,
      BilingualString.EMPTY,
      List.of(),
      false
    );
  }

  /**
   * Глобальные члены из bsl-context: методы и свойства собственно глобального
   * контекста плюс системные перечисления (top-level {@code ContextEnum}) как
   * свойства-члены с {@code valueType} = тип перечисления.
   */
  private static List<MemberDescriptor> membersFromContext(ContextProvider provider) {
    var enLookup = BslContextPlatformTypesProvider.enLookupOf(provider);
    var members = new ArrayList<MemberDescriptor>();
    var globalContext = provider.getGlobalContext();
    if (globalContext != null) {
      for (var method : globalContext.methods()) {
        members.add(BslContextPlatformTypesProvider.toMemberDescriptor(method, enLookup));
      }
      for (var property : globalContext.properties()) {
        members.add(BslContextPlatformTypesProvider.toMemberDescriptor(property, enLookup));
      }
    }
    for (var context : provider.getContexts()) {
      if (context instanceof ContextEnum enumeration) {
        members.add(enumAsProperty(
          enumeration.name().getName(),
          enumeration.name().getAlias(),
          new TypeRef(TypeKind.PLATFORM, enumeration.name().getName())));
      }
    }
    return members;
  }

  /**
   * Глобальные члены из встроенного JSON-fallback (платформа 1С недоступна).
   * Пока покрывает {@code exposedAsGlobal}-типы (перечисления, менеджеры) как
   * свойства-члены. Глобальные функции/переменные из {@code builtin-globals.json}
   * будут добавлены следующим инкрементом.
   */
  private static List<MemberDescriptor> membersFromBuiltin() {
    var members = new ArrayList<MemberDescriptor>();
    for (var decl : BuiltinTypesJsonLoader.load(PLATFORM_TYPES_RESOURCE)) {
      if (decl.exposedAsGlobal() && !decl.qualifiedName().contains("<")) {
        var ref = new TypeRef(decl.kind(), decl.qualifiedName());
        members.add(MemberDescriptor.property(decl.qualifiedName(), ref, decl.description().primary())
          .withBilingualName(decl.name()));
      }
    }
    return members;
  }

  private static MemberDescriptor enumAsProperty(String name, @Nullable String alias, TypeRef enumRef) {
    var bilingual = alias == null || alias.isBlank()
      ? BilingualString.of(name)
      : BilingualString.of(name, alias);
    return MemberDescriptor.property(name, enumRef, "").withBilingualName(bilingual);
  }
}
