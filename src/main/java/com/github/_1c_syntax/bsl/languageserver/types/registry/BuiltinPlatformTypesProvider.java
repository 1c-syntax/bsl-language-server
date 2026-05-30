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

  /** Размер пары ru/en-членов при склейке двуязычных дубликатов. */
  private static final int PAIR_SIZE = 2;

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
      // Тип(ы) возврата: union из массива `returnTypes` либо одиночный `returnType`.
      var returnTypes = readTypeSet(m, "returnTypes", "returnType");
      var returnType = returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);

      var rawSignatures = (List<Map<String, Object>>) m.get("signatures");
      var signatures = readSignatures(rawSignatures, returnType);
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
      // Если задан только `nameEn`, в `nameRu` остаётся пусто, и
      // `displayName(RU)` отдаст {@code name}. Если задан `nameRu` —
      // используется явно; иначе `displayName(RU)` тоже падает на name.
      var nameRu = stringField(m, "nameRu");
      var nameEn = stringField(m, "nameEn");
      if (!nameRu.isEmpty() || !nameEn.isEmpty()) {
        descriptor = descriptor.withLocalizedNames(nameRu, nameEn);
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
      members.add(descriptor);
    }
    return mergeBilingualPairs(members);
  }

  /**
   * Схлопывает соседние ru/en-пары моноязычных членов в один двуязычный
   * {@link MemberDescriptor}. В дампе платформы OneScript каждый член
   * перечислен дважды подряд — русским и английским именем (например,
   * {@code Добавить} и сразу за ним {@code Add}) с идентичной сигнатурой.
   * Это приводило к раздвоению в completion и к моноязычному hover/диагностике.
   * После склейки модель члена идентична двуязычной BSL-модели
   * ({@code builtin-platform-types.json}), и весь downstream (completion,
   * hover, диагностики) работает с одним представлением.
   * <p>
   * Правило склейки намеренно консервативно: сливаются только
   * <b>соседние</b> элементы, где первый — кириллический, второй —
   * латинский, оба моноязычны (en-слот пуст), совпадают {@link MemberKind}
   * и {@link #fingerprint(MemberDescriptor) сигнатурный отпечаток}.
   * Соседство (а не группировка по отпечатку) исключает ложное слияние
   * разных методов с совпадающим отпечатком; для уже двуязычных BSL-членов
   * (en-слот заполнен) правило не срабатывает — метод остаётся no-op.
   */
  private static List<MemberDescriptor> mergeBilingualPairs(List<MemberDescriptor> members) {
    if (members.size() < PAIR_SIZE) {
      return members;
    }
    var result = new ArrayList<MemberDescriptor>(members.size());
    var i = 0;
    while (i < members.size()) {
      var ru = members.get(i);
      if (i + 1 < members.size() && isBilingualPair(ru, members.get(i + 1))) {
        result.add(mergePair(ru, members.get(i + 1)));
        i += PAIR_SIZE;
      } else {
        result.add(ru);
        i++;
      }
    }
    return result;
  }

  private static boolean isBilingualPair(MemberDescriptor ru, MemberDescriptor en) {
    var bothMonolingual = ru.bilingualName().en().isEmpty() && en.bilingualName().en().isEmpty();
    var ruThenEn = isRussianName(ru.name()) && isEnglishName(en.name());
    return bothMonolingual
      && ru.kind() == en.kind()
      && ruThenEn
      && fingerprint(ru).equals(fingerprint(en));
  }

  /**
   * Имя русское, если содержит кириллицу. Русские идентификаторы платформы
   * могут включать латинские аббревиатуры ({@code ЧтениеJSON},
   * {@code ЗаписьXML}), поэтому отсутствие латиницы не требуется.
   */
  private static boolean isRussianName(String name) {
    return isCyrillic(name);
  }

  /** Имя английское, если содержит латиницу и не содержит кириллицы (чистый ASCII). */
  private static boolean isEnglishName(String name) {
    return isLatin(name) && !isCyrillic(name);
  }

  private static MemberDescriptor mergePair(MemberDescriptor ru, MemberDescriptor en) {
    return new MemberDescriptor(
      BilingualString.of(ru.name(), en.name()),
      ru.kind(),
      BilingualString.of(ru.description(), en.description()),
      ru.returnTypes(),
      mergeSignatures(ru.signatures(), en.signatures()),
      ru.sourceSymbol(),
      ru.generic(),
      ru.metadata(),
      ru.async() || en.async()
    );
  }

  /**
   * Сливает имена параметров двух наборов сигнатур (ru + en) в двуязычные.
   * Размеры гарантированно совпадают благодаря отпечатку; на случай
   * рассинхрона — возвращаются ru-сигнатуры без изменений.
   */
  private static List<SignatureDescriptor> mergeSignatures(
    List<SignatureDescriptor> ruSigs, List<SignatureDescriptor> enSigs
  ) {
    if (ruSigs.size() != enSigs.size()) {
      return ruSigs;
    }
    var result = new ArrayList<SignatureDescriptor>(ruSigs.size());
    for (var s = 0; s < ruSigs.size(); s++) {
      var ruSig = ruSigs.get(s);
      var enSig = enSigs.get(s);
      var ruParams = ruSig.parameters();
      var enParams = enSig.parameters();
      if (ruParams.size() != enParams.size()) {
        result.add(ruSig);
        continue;
      }
      var params = new ArrayList<ParameterDescriptor>(ruParams.size());
      for (var j = 0; j < ruParams.size(); j++) {
        var ruParam = ruParams.get(j);
        var enParam = enParams.get(j);
        params.add(new ParameterDescriptor(
          ruParam.name(), ruParam.types(), ruParam.optional(), ruParam.description(),
          ruParam.defaultValue(), BilingualString.of(ruParam.name(), enParam.name()))
          .withVariadic(ruParam.variadic()));
      }
      result.add(new SignatureDescriptor(params, ruSig.returnTypes(),
        BilingualString.of(ruSig.description(), enSig.description())));
    }
    return result;
  }

  private static String fingerprint(MemberDescriptor m) {
    var sb = new StringBuilder();
    sb.append(m.kind()).append('|');
    sb.append(m.returnType().qualifiedName()).append('|');
    sb.append(m.signatures().size());
    for (var sig : m.signatures()) {
      sb.append('#').append(sig.parameters().size());
    }
    return sb.toString();
  }

  private static boolean isCyrillic(String name) {
    return name.chars()
      .anyMatch(ch -> Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.CYRILLIC);
  }

  private static boolean isLatin(String name) {
    // Латиница = ASCII-буква (блок BASIC_LATIN = 0x00..0x7F).
    return name.chars().anyMatch(ch ->
      Character.isLetter(ch) && Character.UnicodeBlock.of(ch) == Character.UnicodeBlock.BASIC_LATIN);
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
    TypeRef fallbackReturnType
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
      // fallback на тип возврата метода.
      var returnTypes = readTypeSet(sig, "returnTypes", "returnType");
      if (returnTypes.refs().isEmpty() && !fallbackReturnType.equals(TypeRef.UNKNOWN)) {
        returnTypes = TypeSet.of(fallbackReturnType);
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
