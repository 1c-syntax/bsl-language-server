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

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

  /**
   * Маппинг {@code kind}-значений JSON-пака (терминология bsl-context'а
   * {@code ContextKind}) в наш {@link TypeKind}:
   * <ul>
   *   <li>{@code "PRIMITIVE_TYPE"} → {@link TypeKind#PRIMITIVE}</li>
   *   <li>{@code "TYPE"}, {@code "ENUM"}, {@code "COLLECTION"} → {@link TypeKind#PLATFORM}</li>
   * </ul>
   * Различение enum от обычного типа делается отдельным флагом
   * {@code TypeDecl.isEnum}, потому что в {@code TypeKind} не введено
   * собственное значение ENUM (текущая ось «откуда тип», не «структура»).
   */
  private static TypeKind mapJsonKind(String kindStr) {
    return switch (kindStr) {
      case "PRIMITIVE_TYPE" -> TypeKind.PRIMITIVE;
      case "TYPE", "ENUM", "COLLECTION" -> TypeKind.PLATFORM;
      default -> throw new IllegalArgumentException("Unknown JSON kind: " + kindStr);
    };
  }

  @SuppressWarnings("unchecked")
  static List<TypeDecl> loadFromResource(String resourcePath) {
    var mapper = JsonMapper.builder().build();
    try (var stream = new ClassPathResource(resourcePath).getInputStream()) {
      List<Map<String, Object>> raw = mapper.readValue(stream, List.class);
      var result = new ArrayList<TypeDecl>(raw.size());
      for (var entry : raw) {
        var kindStr = (String) entry.getOrDefault("kind", "TYPE");
        var kind = mapJsonKind(kindStr);
        var isEnum = "ENUM".equals(kindStr);
        var qualifiedName = (String) entry.get("name");
        // JSON-схема: en-имя ожидается в `nameEn`, ru-имя — в `name` (или
        // явно в `nameRu`). Двуязычное имя пакуется в BilingualString.
        var nameRu = stringField(entry, "nameRu");
        var nameEn = stringField(entry, "nameEn");
        // Legacy `aliases` (одиночный en-вариант) пока поддерживаем — если
        // nameEn не задан, берём первый alias как en-имя; остальные aliases
        // отбрасываем (для JSON-fallback стандартный кейс — один alias).
        @SuppressWarnings("unchecked")
        var legacyAliases = (List<String>) entry.getOrDefault("aliases", Collections.emptyList());
        if (nameEn.isEmpty() && legacyAliases != null && !legacyAliases.isEmpty()) {
          nameEn = legacyAliases.get(0);
        }
        if (nameRu.isEmpty()) {
          nameRu = qualifiedName == null ? "" : qualifiedName;
        }
        var bilingualName = BilingualString.of(nameRu, nameEn);
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
        var forEachDescription = stringField(entry, "forEachDescription");
        var indexAccessDescription = stringField(entry, "indexAccessDescription");
        // Имена generic-плейсхолдеров не сохраняются в JSON отдельно —
        // выводятся структурно из qualifiedName через ContextNames
        // (тот же парсер, что использует bsl-context, чтобы LS и
        // JSON-fallback видели одни и те же placeholder'ы).
        var typeParameters = ContextNames.typeParameters(new ContextName(qualifiedName, ""));
        result.add(new TypeDecl(kind, bilingualName, members,
          exposedAsGlobal, description, constructors,
          List.copyOf(defaultElementTypes), supportsForEach, supportsIndexAccess,
          forEachDescription, indexAccessDescription, typeParameters, isEnum));
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
      var metadata = readMetadata(m, kind);
      if (!metadata.isEmpty()) {
        descriptor = descriptor.withMetadata(metadata);
      }
      // Двуязычные имена: опциональные JSON-поля `nameRu` и `nameEn`.
      // Если задан только `nameEn`, в `nameRu` остаётся пусто, и
      // `displayName(RU)` отдаст {@code name}. Если задан `nameRu` —
      // используется явно; иначе `displayName(RU)` тоже падает на name.
      var nameRu = stringField(m, "nameRu");
      var nameEn = stringField(m, "nameEn");
      if (!nameRu.isEmpty() || !nameEn.isEmpty()) {
        descriptor = descriptor.withLocalizedNames(nameRu, nameEn);
      }
      members.add(descriptor);
    }
    return members;
  }

  /**
   * Читает опциональные метаданные платформы из JSON-объекта члена.
   * Поддерживаются поля: {@code sinceVersion}, {@code deprecatedSinceVersion},
   * {@code recommendedReplacements} (list), {@code availabilities} (list имён
   * enum'а {@link Availability}, регистронезависимо), {@code accessMode}
   * (только для свойств: {@code "READ"} / {@code "READ_WRITE"}),
   * {@code returnValueDescription}, {@code notes}, {@code examples} (list),
   * {@code seeAlso} (list). Отсутствующие поля → {@link PlatformMetadata#EMPTY}.
   */
  @SuppressWarnings("unchecked")
  private static PlatformMetadata readMetadata(Map<String, Object> raw, MemberKind kind) {
    var sinceVersion = stringField(raw, "sinceVersion");
    var deprecatedSinceVersion = stringField(raw, "deprecatedSinceVersion");
    var recommended = (List<String>) raw.getOrDefault("recommendedReplacements", Collections.emptyList());
    var availRaw = (List<String>) raw.getOrDefault("availabilities", Collections.emptyList());
    var availabilities = readAvailabilities(availRaw);
    AccessMode accessMode = null;
    if (kind == MemberKind.PROPERTY) {
      var modeStr = (String) raw.get("accessMode");
      if (modeStr != null && !modeStr.isBlank()) {
        try {
          accessMode = AccessMode.valueOf(modeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
          // неизвестное значение — оставляем null
        }
      }
    }
    var returnValueDescription = stringField(raw, "returnValueDescription");
    var notes = stringField(raw, "notes");
    var examples = (List<String>) raw.getOrDefault("examples", Collections.emptyList());
    var seeAlso = (List<String>) raw.getOrDefault("seeAlso", Collections.emptyList());
    return new PlatformMetadata(
      sinceVersion, deprecatedSinceVersion,
      recommended == null ? List.of() : recommended,
      availabilities,
      accessMode,
      returnValueDescription, notes,
      examples == null ? List.of() : examples,
      seeAlso == null ? List.of() : seeAlso
    );
  }

  private static Set<Availability> readAvailabilities(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    var result = EnumSet.noneOf(Availability.class);
    for (var name : raw) {
      if (name == null || name.isBlank()) {
        continue;
      }
      try {
        result.add(Availability.valueOf(name.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        // неизвестное значение — пропускаем
      }
    }
    return result;
  }

  private static String stringField(Map<String, Object> raw, String key) {
    var value = raw.get(key);
    return value instanceof String s ? s : "";
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
        var pdefault = (String) p.getOrDefault("defaultValue", "");
        var pNameRu = stringField(p, "nameRu");
        var pNameEn = stringField(p, "nameEn");
        params.add(new ParameterDescriptor(pname, TypeSet.EMPTY, optional, pdesc, pdefault,
          BilingualString.of(pNameRu, pNameEn)));
      }
      result.add(new SignatureDescriptor(params, returnType, description));
    }
    return result;
  }
}
