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
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
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
 * Bootstrap-провайдер платформенных типов на основе JSON-ресурса,
 * упакованного вместе с bsl-language-server.
 * <p>
 * Содержит минимальный набор примитивов и ключевых коллекций — этого
 * достаточно для базового вывода типов из литералов и {@code Новый X()}.
 * Полный набор членов платформенных типов в дальнейшем будет приходить
 * из внешнего {@code platform-context} либо JSON синтакс-помощника
 * установленной платформы. Точка расширения — реализация
 * {@link PlatformTypesProvider} как отдельный Spring {@code @Component}.
 */
@Slf4j
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class BuiltinPlatformTypesProvider implements PlatformTypesProvider {

  private static final String RESOURCE_PATH =
    "com/github/_1c_syntax/bsl/languageserver/types/registry/builtin-platform-types.json";

  private final List<TypeDecl> types;

  public BuiltinPlatformTypesProvider() {
    this.types = loadFromResource();
  }

  @Override
  public Collection<TypeDecl> getTypes() {
    return types;
  }

  @SuppressWarnings("unchecked")
  private static List<TypeDecl> loadFromResource() {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
      List<Map<String, Object>> raw = mapper.readValue(stream, List.class);
      var result = new ArrayList<TypeDecl>(raw.size());
      for (var entry : raw) {
        var kindStr = (String) entry.getOrDefault("kind", "PLATFORM");
        var qualifiedName = (String) entry.get("name");
        var aliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
        var members = readMembers((List<Map<String, Object>>) entry.getOrDefault("members", Collections.emptyList()));
        result.add(new TypeDecl(TypeKind.valueOf(kindStr), qualifiedName, aliases, members));
      }
      return result;
    } catch (IOException e) {
      LOGGER.error("Failed to load builtin platform types resource: {}", RESOURCE_PATH, e);
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
        ? com.github._1c_syntax.bsl.languageserver.types.model.TypeRef.UNKNOWN
        : new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(
            com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM, returnTypeName);

      var rawSignatures = (List<Map<String, Object>>) m.get("signatures");
      var signatures = readSignatures(rawSignatures, returnType);
      members.add(new MemberDescriptor(
        name,
        com.github._1c_syntax.bsl.languageserver.types.model.MemberKind.valueOf(kindStr),
        description,
        returnType,
        signatures
      ));
    }
    return members;
  }

  @SuppressWarnings("unchecked")
  private static List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> readSignatures(
    List<Map<String, Object>> raw,
    com.github._1c_syntax.bsl.languageserver.types.model.TypeRef fallbackReturnType
  ) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    var result = new ArrayList<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>(raw.size());
    for (var sig : raw) {
      var description = (String) sig.getOrDefault("description", "");
      var returnTypeName = (String) sig.get("returnType");
      var returnType = returnTypeName == null
        ? fallbackReturnType
        : new com.github._1c_syntax.bsl.languageserver.types.model.TypeRef(
            com.github._1c_syntax.bsl.languageserver.types.model.TypeKind.PLATFORM, returnTypeName);
      var rawParams = (List<Map<String, Object>>) sig.getOrDefault("parameters", Collections.emptyList());
      var params = new ArrayList<com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor>(rawParams.size());
      for (var p : rawParams) {
        var pname = (String) p.get("name");
        var optional = Boolean.TRUE.equals(p.get("optional"));
        var pdesc = (String) p.getOrDefault("description", "");
        params.add(new com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor(
          pname,
          com.github._1c_syntax.bsl.languageserver.types.model.TypeSet.EMPTY,
          optional,
          pdesc
        ));
      }
      result.add(new com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor(
        params, returnType, description
      ));
    }
    return result;
  }
}
