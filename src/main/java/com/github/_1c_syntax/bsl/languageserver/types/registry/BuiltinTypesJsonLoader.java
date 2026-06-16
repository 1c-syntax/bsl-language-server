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
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypePackProvider.TypeDecl;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Единый загрузчик встроенных JSON-паков платформенных типов
 * (BSL — {@code builtin-platform-types.json}, OneScript —
 * {@code builtin-oscript-platform-types.json}). Структура ресурса одна и та
 * же, поэтому и BSL-, и OScript-провайдеры
 * ({@link BuiltinPlatformTypesProvider}, {@link BuiltinOScriptPlatformTypesProvider})
 * используют один и тот же парсер — различаются лишь путём ресурса и
 * языком файлов, который проставляет сам провайдер.
 * <p>
 * Двуязычие членов выражается явными полями {@code nameRu}/{@code nameEn}
 * (как в BSL-модели и в bsl-context). Никакой склейки по порядку нет.
 */
@Slf4j
@UtilityClass
public class BuiltinTypesJsonLoader {

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
  static List<TypeDecl> load(String resourcePath) {
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
        if (nameEn.isEmpty() && !legacyAliases.isEmpty()) {
          nameEn = legacyAliases.getFirst();
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
        // Тип возврата конструктора — сам тип; используется как fallback для сигнатур.
        var constructors = readSignatures(rawCtors, TypeSet.of(classRef));
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
      // Тип(ы) возврата: union из массива `returnTypes` либо одиночный `returnType`.
      var returnTypes = readTypeSet(m, "returnTypes", "returnType");
      var returnType = returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);

      var rawSignatures = (List<Map<String, Object>>) m.get("signatures");
      // В fallback сигнатуре передаём весь union возврата метода, а не первый тип,
      // чтобы SignatureDescriptor.returnTypes() не терял варианты.
      var signatures = readSignatures(rawSignatures, returnTypes);
      var kind = MemberKind.valueOf(kindStr);
      var generic = Boolean.TRUE.equals(m.get("generic"));
      MemberDescriptor descriptor;
      if (kind == MemberKind.METHOD) {
        // returnTypes берём из JSON явно (в т.ч. union), а не выводим из сигнатур —
        // чтобы тип возврата сохранялся и у методов без описанных параметров.
        descriptor = new MemberDescriptor(name, MemberKind.METHOD, description, returnTypes,
          signatures, null, false, PlatformMetadata.EMPTY);
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
      // Явная одноязычность (задан только один из `nameRu`/`nameEn`) делает
      // член применимым лишь к своей локали — так выражаются англоязычные
      // [DeprecatedName]-алиасы OneScript без русской пары.
      // Член без явной локализации (только `name`) считается нейтральным
      // (значение перечисления вроде ANSI/MD5): оба слота заполняются именем,
      // чтобы он был применим к обеим локалям.
      var nameRu = stringField(m, "nameRu");
      var nameEn = stringField(m, "nameEn");
      if (!nameRu.isEmpty() || !nameEn.isEmpty()) {
        descriptor = descriptor.withBilingualName(BilingualString.of(nameRu, nameEn));
      } else if (name != null && !name.isEmpty()) {
        descriptor = descriptor.withBilingualName(BilingualString.of(name, name));
      }
      // Двуязычное описание: опциональные `descriptionRu`/`descriptionEn`.
      // Если заданы — приоритетнее моноязычного `description` (которое
      // попало в primary при сборке дескриптора выше).
      var descriptionRu = stringField(m, "descriptionRu");
      var descriptionEn = stringField(m, "descriptionEn");
      if (!descriptionRu.isEmpty() || !descriptionEn.isEmpty()) {
        var ru = descriptionRu.isEmpty() ? description : descriptionRu;
        descriptor = descriptor.withBilingualDescription(BilingualString.of(ru, descriptionEn));
      }
      if (kind == MemberKind.METHOD && Boolean.TRUE.equals(m.get("async"))) {
        descriptor = descriptor.withAsync(true);
      }
      // Члены builtin-platform-types.json — платформенный API → standardLibrary = true.
      members.add(descriptor.withStandardLibrary(true));
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
      recommended,
      availabilities,
      accessMode,
      returnValueDescription, notes,
      examples,
      seeAlso
    );
  }

  private static Set<Availability> readAvailabilities(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return Set.of();
    }
    var result = EnumSet.noneOf(Availability.class);
    for (var name : raw) {
      if (name.isBlank()) {
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

  /**
   * Читает набор типов из JSON: сперва массив {@code listKey} ({@code ["Число","Строка"]}),
   * иначе одиночное строковое поле {@code singleKey}. Пустые/отсутствующие → {@link TypeSet#EMPTY}.
   * Все имена трактуются как платформенные ({@link TypeKind#PLATFORM}); специализация
   * generic-плейсхолдеров происходит ниже по конвейеру.
   */
  private static TypeSet readTypeSet(Map<String, Object> raw, String listKey, String singleKey) {
    var names = new ArrayList<String>();
    if (raw.get(listKey) instanceof List<?> list) {
      for (var item : list) {
        if (item instanceof String s && !s.isBlank()) {
          names.add(s);
        }
      }
    }
    if (names.isEmpty() && raw.get(singleKey) instanceof String s && !s.isBlank()) {
      names.add(s);
    }
    if (names.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new ArrayList<TypeRef>(names.size());
    for (var name : names) {
      refs.add(new TypeRef(TypeKind.PLATFORM, name));
    }
    return TypeSet.of(refs);
  }

  @SuppressWarnings("unchecked")
  private static List<SignatureDescriptor> readSignatures(
    List<Map<String, Object>> raw,
    TypeSet fallbackReturnTypes
  ) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    var result = new ArrayList<SignatureDescriptor>(raw.size());
    for (var sig : raw) {
      var description = (String) sig.getOrDefault("description", "");
      var sigDescription = bilingualOrMono(description,
        stringField(sig, "descriptionRu"), stringField(sig, "descriptionEn"));
      // Тип(ы) возврата сигнатуры: union `returnTypes` / одиночный `returnType` /
      // fallback на полный union типов возврата метода (или сам тип — для конструктора).
      var returnTypes = readTypeSet(sig, "returnTypes", "returnType");
      if (returnTypes.isEmpty() && !fallbackReturnTypes.isEmpty()) {
        returnTypes = fallbackReturnTypes;
      }
      var rawParams = (List<Map<String, Object>>) sig.getOrDefault("parameters", Collections.emptyList());
      var params = new ArrayList<ParameterDescriptor>(rawParams.size());
      for (var p : rawParams) {
        var pname = (String) p.get("name");
        var optional = Boolean.TRUE.equals(p.get("optional"));
        var pdesc = (String) p.getOrDefault("description", "");
        var pdefault = (String) p.getOrDefault("defaultValue", "");
        var pNameRu = stringField(p, "nameRu");
        var pNameEn = stringField(p, "nameEn");
        var variadic = Boolean.TRUE.equals(p.get("variadic"));
        // Типы параметра: union `types` либо одиночный `type` (как в oscript-дампе).
        var paramTypes = readTypeSet(p, "types", "type");
        params.add(new ParameterDescriptor(pname, paramTypes, optional, pdesc, pdefault,
          BilingualString.of(pNameRu, pNameEn)).withVariadic(variadic));
      }
      result.add(new SignatureDescriptor(params, returnTypes, sigDescription));
    }
    return result;
  }

  /**
   * Двуязычное описание сигнатуры: при отсутствии {@code descriptionRu}/{@code descriptionEn}
   * — моноязычное {@code mono}; иначе ru-сторона берёт {@code descriptionRu} (или {@code mono},
   * если пусто), en-сторона — {@code descriptionEn}.
   */
  private static BilingualString bilingualOrMono(String mono, String descriptionRu, String descriptionEn) {
    if (descriptionRu.isEmpty() && descriptionEn.isEmpty()) {
      return BilingualString.of(mono);
    }
    var ru = descriptionRu.isEmpty() ? mono : descriptionRu;
    return BilingualString.of(ru, descriptionEn);
  }
}
