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
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Fallback-провайдер платформенных типов из JSON-ресурса, упакованного
 * вместе с bsl-language-server. Используется, когда полноценный источник
 * через {@link BslContextPlatformTypesProvider} (синтакс-помощник
 * установленной платформы) недоступен — например, на CI или у пользователя
 * без 1С. Содержит минимальный набор примитивов и ключевых коллекций для
 * базового вывода типов из литералов и {@code Новый X()}.
 */
@Slf4j
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class BuiltinPlatformTypesProvider implements PlatformTypesProvider {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json";

  /**
   * Кэш десериализованных деклараций. JSON-ресурс упакован в jar и неизменен,
   * поэтому парсим его один раз на JVM (десятки workspace-контекстов в тестах
   * иначе перепарсивают одно и то же — ощутимый оверхед памяти/CPU).
   */
  private static final List<TypeDecl> CACHED_TYPES = List.copyOf(loadFromResource(RESOURCE_PATH));

  private final List<TypeDecl> types;
  private final BslContextHolder bslContextHolder;

  public BuiltinPlatformTypesProvider(BslContextHolder bslContextHolder) {
    this.bslContextHolder = bslContextHolder;
    this.types = CACHED_TYPES;
  }

  /**
   * Возвращает встроенный JSON-fallback только тогда, когда полноценный
   * {@code bsl-context}-источник недоступен (платформа 1С не установлена
   * либо парсинг HBK не удался). Если bsl-context дал данные —
   * {@link BslContextPlatformTypesProvider} полностью покрывает то же
   * множество типов, поэтому здесь возвращаем пустой список, чтобы
   * избежать дублей и устаревшей JSON-разметки.
   */
  @Override
  public Collection<TypeDecl> getTypes() {
    if (bslContextHolder.get().isPresent()) {
      return List.of();
    }
    return types;
  }

  @Override
  public LanguageScope getLanguageScope() {
    return LanguageScope.BSL;
  }

  @SuppressWarnings("unchecked")
  static List<TypeDecl> loadFromResource(String resourcePath) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      List<Map<String, Object>> raw = mapper.readValue(stream, List.class);
      var result = new ArrayList<TypeDecl>(raw.size());
      for (var entry : raw) {
        var kindStr = (String) entry.getOrDefault("kind", "PLATFORM");
        var kind = TypeKind.valueOf(kindStr);
        var qualifiedName = (String) entry.get("name");
        var aliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
        var members = readMembers((List<Map<String, Object>>) entry.getOrDefault("members", Collections.emptyList()));
        var exposedAsGlobal = Boolean.TRUE.equals(entry.get("exposedAsGlobal"));
        var description = (String) entry.getOrDefault("description", "");
        var classRef = new TypeRef(kind, qualifiedName);
        var rawCtors = (List<Map<String, Object>>) entry.get("constructors");
        var constructors = readSignatures(rawCtors, classRef);
        var elementTypeNames = (List<String>) entry.getOrDefault("elementTypes", Collections.emptyList());
        var defaultElementTypes = new ArrayList<TypeRef>(elementTypeNames.size());
        for (var name : elementTypeNames) {
          defaultElementTypes.add(new TypeRef(TypeKind.PLATFORM, name));
        }
        var supportsForEach = Boolean.TRUE.equals(entry.get("supportsForEach"));
        var supportsIndexAccess = Boolean.TRUE.equals(entry.get("supportsIndexAccess"));
        result.add(new TypeDecl(kind, qualifiedName, aliases, members,
          exposedAsGlobal, description, constructors,
          List.copyOf(defaultElementTypes), supportsForEach, supportsIndexAccess));
      }
      return result;
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin platform types resource: {}", resourcePath, e);
      return Collections.emptyList();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<MemberDescriptor> readMembers(List<Map<String, Object>> raw) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    var members = new ArrayList<MemberDescriptor>(raw.size());
    for (var m : raw) {
      var name = (String) m.get("name");
      var kindStr = (String) m.getOrDefault("kind", "METHOD");
      var description = (String) m.getOrDefault("description", "");
      var returnTypeName = (String) m.get("returnType");
      var returnType = returnTypeName == null
        ? TypeRef.UNKNOWN
        : new TypeRef(TypeKind.PLATFORM, returnTypeName);

      var rawSignatures = (List<Map<String, Object>>) m.get("signatures");
      var signatures = readSignatures(rawSignatures, returnType);
      var kind = MemberKind.valueOf(kindStr);
      if (kind == MemberKind.METHOD && signatures.isEmpty() && returnType != TypeRef.UNKNOWN) {
        // JSON указал returnType метода без signatures — синтезируем безпараметровую сигнатуру,
        // чтобы returnType метода был доступен инференсеру через MemberDescriptor.returnTypes.
        signatures = List.of(new SignatureDescriptor(List.of(), returnType, ""));
      }
      var generic = Boolean.TRUE.equals(m.get("generic"));
      MemberDescriptor descriptor;
      if (kind == MemberKind.METHOD) {
        descriptor = MemberDescriptor.method(name, description, signatures);
      } else if (generic) {
        descriptor = MemberDescriptor.genericProperty(name, returnType, description);
      } else {
        descriptor = MemberDescriptor.property(name, returnType, description);
      }
      members.add(descriptor);
    }
    return members;
  }

  @SuppressWarnings("unchecked")
  private static List<SignatureDescriptor> readSignatures(
    List<Map<String, Object>> raw,
    TypeRef fallbackReturnType
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
}
