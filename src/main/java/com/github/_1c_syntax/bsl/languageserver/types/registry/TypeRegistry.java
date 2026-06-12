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

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.AnyType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformType;
import com.github._1c_syntax.bsl.languageserver.types.model.PrimitiveType;
import com.github._1c_syntax.bsl.languageserver.types.model.Type;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.UnknownType;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.Placeholder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

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
@WorkspaceScope
@RequiredArgsConstructor
public class TypeRegistry {

  private final List<PlatformTypesProvider> platformProviders;
  /** Параллельный Symbol-фронт: глобальные свойства и прочие глобальные символы. */
  private final GlobalScopeProvider globalScopeProvider;
  /**
   * Индекс метаданных членов (read-only свойства + версионные члены) для
   * дешёвых pre-filter'ов диагностик. Workspace-scoped Spring-компонент;
   * заполняется при регистрации {@link TypePackProvider.TypeDecl} провайдерами
   * платформенных типов (bsl-context / JSON-fallback); конфигурационные MD-типы
   * сюда не попадают (у них нет accessMode/версий).
   */
  private final MemberMetadataIndex memberMetadataIndex;

  /** Интернированные TypeRef по канонической форме (kind + lowercased name). */
  private final Map<TypeRef, TypeRef> internedRefs = new ConcurrentHashMap<>();
  /** Алиасы (включая Ru/En) → канонический TypeRef. Ключ — lowercased имя. */
  private final Map<String, TypeRef> aliasIndex = new ConcurrentHashMap<>();
  /** Тип ↔ объект Type (hydrated). */
  private final Map<TypeRef, Type> types = new ConcurrentHashMap<>();
  /** Тип ↔ список источников членов (один тип может расширяться многими источниками). */
  private final Map<TypeRef, List<ScopedMemberSource>> memberSources = new ConcurrentHashMap<>();

  /**
   * Мемоизация {@link #getMembers(TypeRef, FileType)}. Сборка членов
   * (особенно переспециализация config/generic-типов) дорогая, а на горячем
   * пути (семантические токены, completion) повторяется для одного типа тысячи
   * раз. Инвалидация — через {@link #membersEpoch}: любая мутация
   * {@link #memberSources} (register/unregister) бампает счётчик, и устаревшие
   * записи пересобираются при следующем обращении. В steady-state (во время
   * запроса, без регистраций) memo стабилен.
   */
  private final AtomicLong membersEpoch = new AtomicLong();
  private final Map<MembersKey, CachedMembers> membersCache = new ConcurrentHashMap<>();

  private record MembersKey(TypeRef ref, FileType fileType) implements Comparable<MembersKey> {

    // fileType штатно бывает null: одноаргументный getMembers(ref) зовёт getMembers(ref, null)
    // («фильтрация по скоупу не применяется»). ref допускает null защитно (см. computeMembers).
    // Поэтому естественный порядок ключа — null-safe.
    private static final Comparator<MembersKey> NATURAL_ORDER = Comparator
      .comparing(MembersKey::ref, Comparator.nullsFirst(Comparator.naturalOrder()))
      .thenComparing(MembersKey::fileType, Comparator.nullsFirst(Comparator.naturalOrder()));

    @Override
    public int compareTo(MembersKey other) {
      return NATURAL_ORDER.compare(this, other);
    }
  }

  private record CachedMembers(long epoch, List<MemberDescriptor> members) {
  }
  /**
   * Типы, видимые в файлах каждого языка. Тип, не зарегистрированный ни в одном
   * разрезе, считается видимым везде (отсутствие знания — не повод фильтровать).
   */
  private final Map<FileType, Set<TypeRef>> visibleTypes = Map.of(
    FileType.BSL, ConcurrentHashMap.newKeySet(),
    FileType.OS, ConcurrentHashMap.newKeySet()
  );
  /** Тип ↔ список описаний с их скоупом. В одном типе разные описания для BSL/OS допускаются. */
  private final Map<TypeRef, List<ScopedDescription>> descriptions = new ConcurrentHashMap<>();
  /** Тип ↔ список наборов конструкторов с их скоупом. */
  private final Map<TypeRef, List<ScopedConstructors>> constructors = new ConcurrentHashMap<>();
  /** Тип ↔ динамические источники конструкторов (например, OScript-класс из SymbolTree). */
  private final Map<TypeRef, List<ScopedConstructorSource>> constructorSources
    = new ConcurrentHashMap<>();
  /**
   * Тип ↔ типы элементов «по умолчанию» для коллекции. Заполняется из
   * {@link TypePackProvider.TypeDecl#defaultElementTypes()} при регистрации.
   * Источник истины — bsl-context ({@code ContextCollection.collectionElementTypes()})
   * или builtin JSON. Используется инференсером для прокидывания element-типа
   * на TypeSet, чтобы {@code Для Каждого X Из Коллекция} давал X нужного типа
   * без аннотаций пользователя.
   */
  private final Map<TypeRef, List<TypeRef>> defaultElementTypes = new ConcurrentHashMap<>();
  /** Тип ↔ {@code supportsForEach} ({@code true} — обход {@code Для Каждого} разрешён). */
  private final Map<TypeRef, Boolean> supportsForEach = new ConcurrentHashMap<>();
  /** Тип ↔ {@code supportsIndexAccess} ({@code true} — индексатор {@code [...]} разрешён). */
  private final Map<TypeRef, Boolean> supportsIndexAccess = new ConcurrentHashMap<>();
  /** Тип ↔ текстовое описание обхода {@code Для Каждого} из синтакс-помощника. */
  private final Map<TypeRef, BilingualString> forEachDescriptions = new ConcurrentHashMap<>();
  /** Тип ↔ текстовое описание индексатора {@code [...]} из синтакс-помощника. */
  private final Map<TypeRef, BilingualString> indexAccessDescriptions = new ConcurrentHashMap<>();
  /**
   * Тип ↔ имена generic-плейсхолдеров (без угловых скобок). Заполняется
   * платформенным провайдером из {@link TypePackProvider.TypeDecl#typeParameters()}.
   * Источник истины — {@code Context.typeParameters()} в bsl-context.
   */
  private final Map<TypeRef, List<String>> typeParameters = new ConcurrentHashMap<>();

  /**
   * Двуязычные имена типов (для hover/inlay): для канонических TypeRef
   * храним {@link BilingualString} с ru + en написанием. Источник —
   * {@link TypeDecl#name()} платформенного провайдера. Пустые/
   * отсутствующие — fallback на {@link TypeRef#qualifiedName()}.
   */
  private final Map<TypeRef, BilingualString> displayNames = new ConcurrentHashMap<>();

  /**
   * Двуязычные описания типов (ru + en) — параллельный индекс к
   * {@link #descriptions}, который продолжает хранить scoped primary-форму
   * для legacy-логики. Заполняется из {@link TypePackProvider.TypeDecl#description()}.
   * Один тип может иметь описания с разными скоупами (BSL/OS) — фильтрация при чтении.
   */
  private final Map<TypeRef, List<ScopedBilingualDescription>> typeDescriptionsBilingual = new ConcurrentHashMap<>();

  /** Источник членов вместе с языком файлов, в которых он видим. */
  private record ScopedMemberSource(MemberSource source, FileType fileType) {
  }

  /** Описание типа вместе с языком файлов, в которых оно видимо. */
  private record ScopedDescription(String text, FileType fileType) {
  }

  /** Двуязычное описание типа вместе с языком файлов, в которых оно видимо. */
  private record ScopedBilingualDescription(BilingualString text, FileType fileType) {
  }

  /** Набор конструкторов вместе с языком файлов, в которых они видимы. */
  private record ScopedConstructors(
    List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> list,
    FileType fileType
  ) {
  }

  /** Динамический источник конструкторов вместе с языком файлов, в которых он видим. */
  private record ScopedConstructorSource(
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> supplier,
    FileType fileType
  ) {
  }

  @PostConstruct
  void bootstrap() {
    if (platformProviders == null) {
      return;
    }
    for (var provider : platformProviders) {
      var fileType = provider.getFileType();
      for (var decl : provider.getTypes()) {
        registerPack(decl, fileType);
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
   * Найти платформенный generic-тип по префиксу-семейству.
   * <p>
   * Платформа 1С регистрирует обобщённые типы вида
   * {@code "ДокументСсылка.<Имя документа>"}, {@code "СправочникОбъект.<Имя справочника>"}
   * и т.п. — конкретное имя плейсхолдера в угловых скобках различается для каждого
   * MDOType. Этот метод выбирает первый тип, чьё qualifiedName начинается с
   * {@code prefix + ".<"} (плейсхолдер) — обычно он один на семейство.
   *
   * @param prefix начальная часть qualifiedName до точки-плейсхолдера
   *               (например, {@code "ДокументСсылка"})
   * @return TypeRef generic-типа или {@link Optional#empty()}, если не зарегистрирован
   */
  public Optional<TypeRef> resolveGenericByPrefix(String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      return Optional.empty();
    }
    var needle = prefix.toLowerCase(Locale.ROOT) + ".<";
    for (var entry : aliasIndex.entrySet()) {
      if (entry.getKey().startsWith(needle)) {
        return Optional.of(entry.getValue());
      }
    }
    return Optional.empty();
  }

  /**
   * Найти ВСЕ зарегистрированные generic-типы, чьё qualifiedName начинается
   * с указанной family-core строки. Generic'ом считается тип с непустым
   * {@link #getTypeParameters(TypeRef)} (т.е. был помечен платформенным
   * провайдером как имеющий placeholder'ы — структурное определение,
   * без парсинга {@code .<...>} здесь).
   * <p>
   * Используется при регистрации MD-объекта для специализации всего семейства
   * дженериков сразу (СправочникСсылка, СправочникОбъект, СправочникМенеджер,
   * СправочникВыборка, СправочникСписок и т.п. — для Catalog'а).
   *
   * @param familyCore начальная часть имени до семейного суффикса
   *                   (например, {@code "Справочник"} матчит
   *                   {@code "СправочникСсылка.<Имя справочника>"},
   *                   {@code "СправочникВыборка.<Имя справочника>"} и т.п.)
   * @return список интернированных TypeRef'ов; пустой, если совпадений нет
   */
  public List<TypeRef> findAllGenericsByFamilyCore(String familyCore) {
    if (familyCore == null || familyCore.isEmpty()) {
      return List.of();
    }
    var needle = familyCore.toLowerCase(Locale.ROOT);
    var result = new ArrayList<TypeRef>();
    for (var ref : typeParameters.keySet()) {
      if (ref.qualifiedName().toLowerCase(Locale.ROOT).startsWith(needle)) {
        result.add(ref);
      }
    }
    return result;
  }

  /**
   * Найти тип по имени с фильтрацией по типу файла. Тип будет возвращён,
   * только если он видим в {@code fileType} (см. {@link #isVisibleIn}).
   * Если {@code fileType == null} — фильтрация не применяется.
   */
  public Optional<TypeRef> resolve(String name, FileType fileType) {
    return resolve(name).filter(ref -> fileType == null || isVisibleIn(ref, fileType));
  }

  /**
   * Видимость типа в данном типе файла. Тип без зарегистрированной языковой
   * принадлежности (ad-hoc TypeRef, неизвестное имя) считается видимым везде —
   * отсутствие знания не повод фильтровать.
   *
   * @param ref      ссылка на тип.
   * @param fileType тип файла-потребителя.
   * @return {@code true}, если тип видим в файлах данного типа.
   */
  public boolean isVisibleIn(TypeRef ref, FileType fileType) {
    if (visibleTypes.get(fileType).contains(ref)) {
      return true;
    }
    for (var typed : visibleTypes.values()) {
      if (typed.contains(ref)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Зарегистрировать видимость типа в типе файла. Повторные регистрации
   * аддитивны: тип, зарегистрированный и BSL-, и OS-источником, видим в обоих.
   *
   * @param ref      ссылка на тип.
   * @param fileType тип файла, в котором тип становится видимым.
   */
  public void registerFileType(TypeRef ref, FileType fileType) {
    visibleTypes.get(fileType).add(ref);
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
   * <p>
   * Fallback по имени: TypeRef в LS — это пара {@code (kind, qualifiedName)}, и
   * один и тот же тип может предъявляться с разными kind'ами в зависимости от
   * того, откуда он пришёл (например, specialize generic-типа платформы даёт
   * {@code (PLATFORM, "СправочникОбъект.X")}, а тот же тип, зарегистрированный
   * ConfigurationTypesProvider'ом, имеет kind {@code CONFIGURATION}). Чтобы
   * единый запрос {@code getMembers} находил источники независимо от kind'а,
   * сначала ищем точное совпадение по (kind, name), а если его нет —
   * резолвим по {@link #aliasIndex} по {@code qualifiedName} и пробуем
   * каноничный {@link TypeRef}.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef ref, FileType fileType) {
    var epoch = membersEpoch.get();
    var key = new MembersKey(ref, fileType);
    var cached = membersCache.get(key);
    if (cached != null && cached.epoch() == epoch) {
      return cached.members();
    }
    var members = computeMembers(ref, fileType);
    membersCache.put(key, new CachedMembers(epoch, members));
    return members;
  }

  private List<MemberDescriptor> computeMembers(TypeRef ref, FileType fileType) {
    var sources = resolveMemberSources(ref);
    if (sources.isEmpty()) {
      return List.of();
    }
    // Snapshot: список source'ов может модифицироваться параллельно через
    // registerMemberSource/registerMemberOverride (Phase B/C MetadataCollectionSpecializer
    // и др. workspace-scoped провайдеры). sources — CopyOnWriteArrayList,
    // снимок через List.copyOf дёшев и стабилен на время итерации.
    List<ScopedMemberSource> snapshot = List.copyOf(sources);
    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var scoped : snapshot) {
      if (fileType != null && scoped.fileType() != fileType) {
        continue;
      }
      for (var member : scoped.source().getMembers()) {
        byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
      }
    }
    // Неизменяемый список: память шарится между вызовами, случайная мутация
    // упадёт сразу (все потребители только итерируют).
    return List.copyOf(byName.values());
  }

  /**
   * Источники членов типа с fallback на канонический псевдоним.
   *
   * @param ref тип, для которого ищутся источники членов.
   * @return список источников; пустой, если их нет.
   */
  private List<ScopedMemberSource> resolveMemberSources(TypeRef ref) {
    var sources = memberSources.get(ref);
    if ((sources == null || sources.isEmpty()) && ref != null) {
      var canonical = aliasIndex.get(ref.qualifiedName().toLowerCase(Locale.ROOT));
      if (canonical != null && !canonical.equals(ref)) {
        sources = memberSources.get(canonical);
      }
    }
    return sources == null ? List.of() : sources;
  }

  /**
   * Сбросить memo {@link #getMembers}. Member-source'ы конфигурационных модулей и
   * OScript-библиотек лениво читают символьное дерево документа и меняют вывод при
   * правке без ре-регистрации источника — поэтому при любом изменении содержимого
   * документа memo надо инвалидировать.
   */
  @EventListener
  public void invalidateMembersCache(DocumentContextContentChangedEvent event) {
    membersEpoch.incrementAndGet();
  }

  /**
   * Добавить дополнительный источник членов к существующему типу с привязкой
   * к языку файла. Позволяет, например, {@code ManagerModule.bsl} расширять
   * платформенный {@code СправочникМенеджер.X}.
   *
   * @param ref      тип, к которому добавляется источник членов.
   * @param source   источник членов.
   * @param fileType язык файла, в котором члены источника видимы.
   */
  public void registerMemberSource(TypeRef ref, MemberSource source, FileType fileType) {
    memberSources.computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>())
      .add(new ScopedMemberSource(source, fileType));
    membersEpoch.incrementAndGet();
  }

  /**
   * Аналог {@link #registerMemberSource}, но вставляет источник в НАЧАЛО списка,
   * чтобы при сборе членов через {@link #getMembers(TypeRef)} он выигрывал
   * dedup ({@code putIfAbsent} по имени). Используется для override returnType
   * у конкретного member'а уже зарегистрированного типа (например, подмена
   * {@code ОбъектМетаданныхКонфигурация.Документы} с общего
   * {@code КоллекцияОбъектовМетаданных} на специализированный
   * {@code КоллекцияОбъектовМетаданных.Документы}). Базовый источник остаётся
   * в реестре — другие members (Справочники, Перечисления, …) приходят оттуда.
   */
  public void registerMemberOverride(TypeRef ref, MemberSource source, FileType fileType) {
    var list = memberSources.computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>());
    list.addFirst(new ScopedMemberSource(source, fileType));
  }

  /**
   * Зарегистрировать специализацию generic-типа по имени специализированного
   * типа. Если такого TypeRef ещё нет — интернируется с {@link TypeKind} как
   * у generic'а (чтобы инференсер и регистрация работали с одной и той же
   * парой {@code (kind, name)}). Если есть — используется существующий.
   * После этого делегирует в
   * {@link #registerSpecialization(TypeRef, TypeRef, Map, FileType)}.
   *
   * @param specializedName qualifiedName целевого типа (например,
   *                        {@code "СправочникВыборка.МойСправочник"})
   * @param genericRef      generic-тип-источник
   * @param bindings        подстановки placeholder → имя заменителя
   * @param fileType        язык файла, в котором специализация видима
   * @return интернированный {@link TypeRef} специализированного типа
   */
  public TypeRef registerSpecialization(String specializedName, TypeRef genericRef,
                                        Map<String, String> bindings, FileType fileType) {
    if (specializedName == null || specializedName.isBlank() || genericRef == null) {
      return TypeRef.UNKNOWN;
    }
    var existing = resolve(specializedName).orElse(null);
    var specializedRef = existing != null
      ? existing
      : intern(genericRef.kind(), specializedName);
    if (existing == null) {
      // Регистрируем как полноценный тип того же kind, что и generic, чтобы
      // инференсер (резолвящий типы по имени через aliasIndex / по паре
      // (kind, name)) находил тот же TypeRef и member-source'ы доходили
      // до getMembers.
      types.put(specializedRef, hydrate(specializedRef));
      addAlias(specializedName, specializedRef);
      registerFileType(specializedRef, fileType);
    }
    registerSpecialization(specializedRef, genericRef, bindings, fileType);
    return specializedRef;
  }

  /**
   * Зарегистрировать специализацию generic-типа: {@code specializedRef} —
   * целевой ссылочный тип ({@code СправочникСсылка.МойСправочник}),
   * {@code genericRef} — generic-тип ({@code СправочникСсылка.<Имя справочника>}),
   * {@code bindings} — подстановки placeholder'ов («Имя справочника» →
   * «МойСправочник»).
   * <p>
   * Регистрируется ленивый {@link MemberSource} для {@code specializedRef},
   * который при каждом запросе:
   * <ol>
   *   <li>берёт members generic-типа через {@link #getMembers(TypeRef)};</li>
   *   <li>отфильтровывает {@link MemberDescriptor#generic()} (слотовые
   *       члены вида {@code <Имя реквизита>});</li>
   *   <li>применяет {@link MemberDescriptor#specialize(Map)} к каждому
   *       члену — подставляет {@code bindings} в возвращаемые типы и
   *       сигнатуры.</li>
   * </ol>
   * <p>
   * Источник лениво пересобирается на каждый getMembers, чтобы реагировать
   * на смену языка интерфейса (имена members generic-типа меняются) и не
   * зависеть от порядка инициализации платформенных провайдеров.
   * Также индексируются read-only и версионные members специализированного типа
   * (см. {@link #indexMemberMetadata}).
   *
   * @param specializedRef  целевой TypeRef, который должен «наследовать»
   *                        members generic-типа
   * @param genericRef      generic-тип-источник (его qualifiedName обычно
   *                        содержит placeholder'ы {@code <X>})
   * @param bindings        placeholder → имя заменителя (например,
   *                        {@code "Имя справочника"} → {@code "МойСправочник"})
   * @param fileType        язык файла, в котором специализация видима
   */
  public void registerSpecialization(TypeRef specializedRef, TypeRef genericRef,
                                     Map<String, String> bindings, FileType fileType) {
    if (specializedRef == null || genericRef == null) {
      return;
    }
    var safeBindings = Map.copyOf(bindings);
    registerSpecializedDisplayName(specializedRef, genericRef, safeBindings);
    MemberSource source = () -> {
      var raw = getMembers(genericRef);
      if (raw.isEmpty()) {
        return List.of();
      }
      var result = new ArrayList<MemberDescriptor>(raw.size());
      for (var member : raw) {
        if (member.generic()) {
          continue;
        }
        var specialized = member.specialize(safeBindings);
        result.add(specialized);
        memberMetadataIndex.index(specializedRef, specialized);
      }
      return result;
    };
    registerMemberSource(specializedRef, source, fileType);
  }

  /**
   * Регистрирует материализацию generic-членов специализированного типа из
   * конфигурационно-зависимых данных. Для каждого generic-члена generic-типа,
   * чьё bilingual-имя содержит placeholder из {@code memberExpansions.keySet()},
   * порождается по одной материализованной копии на каждое значение из списка.
   * <p>
   * У материализованной копии:
   * <ul>
   *   <li>placeholder в ru- и en-имени заменён значением (en-сторона —
   *       по позиции placeholder'а);</li>
   *   <li>{@code generic = false};</li>
   *   <li>{@code returnType} и сигнатуры специализированы объединёнными
   *       {@code typeBindings ∪ {placeholder → value}};</li>
   *   <li>описание, {@link PlatformMetadata} ({@code accessMode},
   *       {@code availabilities}, {@code sinceVersion} …) — наследуются от
   *       template'а из HBK.</li>
   * </ul>
   * <p>
   * Используется для конфигурационно-зависимых детей, чьи имена платформа
   * моделирует как member-level placeholder: значения перечислений
   * ({@code <Имя значения>}), реквизиты/измерения/ресурсы регистров и т.п.
   *
   * @param specializedRef   специализированный тип-владелец
   * @param genericRef       generic-источник (member-template'ы)
   * @param typeBindings     type-level подстановки от родительской специализации
   * @param memberExpansions placeholder → список конкретных имён (из mdclasses)
   * @param fileType         язык файла, в котором члены видимы
   */
  public void registerMemberExpansion(TypeRef specializedRef, TypeRef genericRef,
                                      Map<String, String> typeBindings,
                                      Map<String, List<String>> memberExpansions,
                                      FileType fileType) {
    if (memberExpansions.isEmpty()) {
      return;
    }
    var safeTypeBindings = Map.copyOf(typeBindings);
    var safeExpansions = deepCopyExpansions(memberExpansions);
    MemberSource source = () -> {
      var materialized = expandGenericMembers(genericRef, safeTypeBindings, safeExpansions);
      // Индексируем как делает registerSpecialization: read-only/версионные
      // members проверяются через memberMetadataIndex.
      for (var member : materialized) {
        memberMetadataIndex.index(specializedRef, member);
      }
      return materialized;
    };
    registerMemberSource(specializedRef, source, fileType);
  }

  /**
   * Глубокая копия expansion-карты: внешний {@link Map#copyOf} даёт immutable
   * shell, но значения-{@link List} остаются исходными (caller может ими
   * управлять). Зафиксировать снимок целиком — каждый список тоже копируем.
   */
  private static Map<String, List<String>> deepCopyExpansions(Map<String, List<String>> raw) {
    var entries = raw.entrySet();
    var copy = LinkedHashMap.<String, List<String>>newLinkedHashMap(entries.size());
    for (var entry : entries) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Map.copyOf(copy);
  }

  /**
   * Снимок материализованных generic-членов: ленивая логика
   * {@link #registerMemberExpansion} один раз, без регистрации источника.
   * Нужен, когда specializedRef совпадает с genericRef (self-target expansion):
   * ленивый источник в этой раскладке самозамыкается через {@link #getMembers}.
   */
  public List<MemberDescriptor> expandedMembers(TypeRef genericRef,
                                                Map<String, String> typeBindings,
                                                Map<String, List<String>> memberExpansions) {
    if (memberExpansions.isEmpty()) {
      return List.of();
    }
    var safeTypeBindings = Map.copyOf(typeBindings);
    var safeExpansions = deepCopyExpansions(memberExpansions);
    return expandGenericMembers(genericRef, safeTypeBindings, safeExpansions);
  }

  /**
   * Разворачивает generic-членов {@code genericRef} в материализованные копии
   * по {@code memberExpansions}. См. {@link #registerMemberExpansion}.
   * <p>
   * Информация о placeholder'ах в именах членов берётся структурно из
   * bsl-context через {@link ContextNames#placeholders(String)} — парсинга
   * угловых скобок в LS нет.
   */
  private List<MemberDescriptor> expandGenericMembers(TypeRef genericRef,
                                                      Map<String, String> typeBindings,
                                                      Map<String, List<String>> memberExpansions) {
    var raw = getMembers(genericRef);
    if (raw.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<MemberDescriptor>();
    for (var template : raw) {
      if (template.generic()) {
        expandTemplate(template, memberExpansions, typeBindings, result);
      }
    }
    return result;
  }

  private static void expandTemplate(MemberDescriptor template,
                                     Map<String, List<String>> memberExpansions,
                                     Map<String, String> typeBindings,
                                     List<MemberDescriptor> sink) {
    var ruName = template.bilingualName().primary();
    var ruPlaceholders = ContextNames.placeholders(ruName);
    var ruMatch = ruPlaceholders.stream()
      .filter(p -> memberExpansions.containsKey(p.name()))
      .findFirst()
      .orElse(null);
    if (ruMatch == null) {
      return;
    }
    // en-сторона имени имеет placeholder'ы в том же порядке (структурно
    // парные ru/en — bsl-context их так и отдаёт).
    var enName = template.bilingualName().en();
    var enPlaceholders = enName.isEmpty() ? List.<Placeholder>of() : ContextNames.placeholders(enName);
    var ruIndex = ruPlaceholders.indexOf(ruMatch);
    var enMatch = ruIndex >= 0 && ruIndex < enPlaceholders.size() ? enPlaceholders.get(ruIndex) : null;
    for (var value : memberExpansions.get(ruMatch.name())) {
      sink.add(materializeGenericMember(template, ruMatch, enMatch, value, typeBindings));
    }
  }

  /**
   * Материализует одну копию generic-template'а: подставляет {@code value}
   * в placeholder ru/en-имени структурно (по позициям из bsl-context),
   * специализирует {@code returnType} + сигнатуры объединённым набором
   * bindings, снимает флаг {@code generic}.
   */
  private static MemberDescriptor materializeGenericMember(MemberDescriptor template,
                                                           Placeholder ruPlaceholder,
                                                           @Nullable Placeholder enPlaceholder,
                                                           String value,
                                                           Map<String, String> typeBindings) {
    var ruName = template.bilingualName().primary();
    var newRu = ruName.substring(0, ruPlaceholder.start()) + value + ruName.substring(ruPlaceholder.end());
    var enName = template.bilingualName().en();
    String newEn;
    if (enPlaceholder != null && !enName.isEmpty()) {
      newEn = enName.substring(0, enPlaceholder.start()) + value + enName.substring(enPlaceholder.end());
    } else {
      newEn = enName;
    }
    var combined = new HashMap<>(typeBindings);
    combined.put(ruPlaceholder.name(), value);
    return template
      .specialize(combined)
      .withBilingualName(BilingualString.of(newRu, newEn))
      .withGeneric(false);
  }

  /**
   * Двуязычное отображаемое имя специализированного типа: ru-сторона — уже
   * структурно специализированный {@code specializedRef.qualifiedName()}
   * ({@code СправочникСсылка.Контрагенты}), en-сторона — подстановка того же
   * MD-имени в en-написание display-имени generic'а
   * ({@code CatalogRef.<Catalog name>} → {@code CatalogRef.Контрагенты}).
   * Так конфигурационный тип в hover показывается на языке интерфейса.
   * Если у generic'а нет en-стороны display-имени — регистрация пропускается
   * (fallback {@link #displayName(TypeRef, Language)} на qualifiedName и так ru).
   */
  private void registerSpecializedDisplayName(TypeRef specializedRef, TypeRef genericRef,
                                              Map<String, String> bindings) {
    if (bindings.isEmpty()) {
      return;
    }
    var genericName = displayNames.get(genericRef);
    if (genericName == null || genericName.en().isEmpty()) {
      return;
    }
    // en-сторона имени имеет placeholder'ы в том же порядке, что и ru
    // (bsl-context выдаёт их структурно по позициям). Сопоставляем
    // ru-placeholder'ы из qualifiedName с en-placeholder'ами из en-display
    // позиционно и строим en-bindings для подстановки.
    var ruPlaceholders = genericRef.placeholders();
    var enRef = new TypeRef(genericRef.kind(), genericName.en());
    var enPlaceholders = enRef.placeholders();
    if (ruPlaceholders.size() != enPlaceholders.size()) {
      return;
    }
    var enBindings = HashMap.<String, String>newHashMap(enPlaceholders.size());
    for (var i = 0; i < ruPlaceholders.size(); i++) {
      var value = bindings.get(ruPlaceholders.get(i).name());
      if (value == null) {
        return;
      }
      enBindings.put(enPlaceholders.get(i).name(), value);
    }
    var en = TypeRef.specialize(enRef, enBindings).qualifiedName();
    displayNames.putIfAbsent(specializedRef,
      BilingualString.of(specializedRef.qualifiedName(), en));
  }

  /**
   * Зарегистрировать пользовательский тип (OneScript-класс, общий модуль и т.п.)
   * с указанием языка файла, в котором он видим.
   *
   * @param qualifiedName каноническое имя типа.
   * @param declaration   символ-объявление типа.
   * @param fileType      язык файла, в котором тип видим.
   * @return интернированный {@link TypeRef} зарегистрированного типа.
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration, FileType fileType) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.put(ref, new UserType(ref, declaration));
    addAlias(qualifiedName, ref);
    registerFileType(ref, fileType);
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
    registerFileType(ref, FileType.BSL);
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
   * Явно задать двуязычное отображаемое имя типа. Нужно конфигурационным
   * типам, которые регистрируются императивно (без {@link TypePackProvider.TypeDecl}
   * с готовым bilingual-именем): иначе {@link #displayName(TypeRef, Language)}
   * для них в EN отдаёт ru-написание qualifiedName.
   */
  public void registerDisplayName(TypeRef ref, BilingualString name) {
    if (name.isEmpty()) {
      return;
    }
    displayNames.putIfAbsent(ref, name);
  }

  /**
   * Зарегистрировать тип как глобальное свойство (его имя становится
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.Метод()}).
   * Регистрация идёт в {@link GlobalScopeProvider} — единая точка входа
   * для глобальных имён.
   */
  public void registerAsGlobalProperty(TypeRef ref, FileType fileType) {
    registerAsGlobalProperty(ref, fileType, SyntheticKind.PLATFORM_GLOBAL_PROPERTY);
  }

  /**
   * Та же регистрация, но с явным {@link SyntheticKind} — используется
   * при публикации системных перечислений ({@link SyntheticKind#PLATFORM_GLOBAL_ENUM}),
   * чтобы отличать их от обычных глобальных свойств.
   */
  public void registerAsGlobalProperty(TypeRef ref, FileType fileType, SyntheticKind syntheticKind) {
    registerAsGlobalProperty(ref, fileType, syntheticKind, () -> null);
  }

  /**
   * То же + lazy-провайдер source-defined-символа (для общих модулей).
   * См. {@link GlobalScopeProvider#registerGlobalProperty(TypeRef, Collection, FileType, String, SyntheticKind, Supplier)}.
   */
  public void registerAsGlobalProperty(TypeRef ref, FileType fileType, SyntheticKind syntheticKind,
                                       Supplier<Symbol> sourceSymbol) {
    var names = new LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerGlobalProperty(ref, names, fileType, getDescription(ref, fileType),
      syntheticKind, sourceSymbol);
  }

  /**
   * Описание типа из источника (JSON-пакета или динамической регистрации).
   * Возвращает пустую строку, если описание отсутствует.
   */
  public String getDescription(TypeRef ref) {
    return getDescription(ref, (FileType) null);
  }

  /**
   * Описание типа с фильтрацией по {@link FileType}. Возвращает первое описание,
   * чей скоуп совместим с переданным {@code fileType} ({@code null} ⇒ без фильтра,
   * возвращается первое зарегистрированное). Если ни одно описание не подходит — "".
   */
  public String getDescription(TypeRef ref, FileType fileType) {
    var list = descriptions.get(ref);
    if (list == null || list.isEmpty()) {
      return "";
    }
    for (var sd : list) {
      if (fileType == null || sd.fileType() == fileType) {
        return sd.text();
      }
    }
    return "";
  }

  /**
   * Зарегистрировать описание типа со скоупом. Допускается несколько описаний
   * на один TypeRef с разными скоупами (BSL/OS) — фильтрация при чтении.
   */
  public void registerDescription(TypeRef ref, String text, FileType fileType) {
    if (ref == null || text == null || text.isBlank()) {
      return;
    }
    descriptions.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedDescription(text, fileType));
  }

  /**
   * Список конструкторов типа (для платформенных классов из JSON-пакета).
   * Возвращает пустой список, если конструкторов нет (например, для типов
   * без блока {@code constructors} в JSON или для system enums).
   */
  public List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> getConstructors(TypeRef ref) {
    return getConstructors(ref, null);
  }

  /**
   * То же, что {@link #getConstructors(TypeRef)}, но фильтрует по {@link FileType}.
   * Конкатенирует все наборы (pack + динамические источники), чьи скоупы совместимы.
   */
  public List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> getConstructors(
    TypeRef ref, FileType fileType
  ) {
    var result = new ArrayList<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>();
    var fromPack = constructors.get(ref);
    if (fromPack != null) {
      for (var scoped : fromPack) {
        if (fileType != null && scoped.fileType() != fileType) {
          continue;
        }
        result.addAll(scoped.list());
      }
    }
    var sources = constructorSources.get(ref);
    if (sources != null) {
      for (var scoped : sources) {
        if (fileType != null && scoped.fileType() != fileType) {
          continue;
        }
        var sigs = scoped.supplier().get();
        if (sigs != null) {
          result.addAll(sigs);
        }
      }
    }
    return result;
  }

  /**
   * Зарегистрировать конструкторы типа с привязкой к языку файла.
   * Поддерживается несколько вызовов на один TypeRef с разными языками (BSL/OS).
   *
   * @param ref      тип, которому регистрируются конструкторы.
   * @param ctors    сигнатуры конструкторов.
   * @param fileType язык файла, в котором конструкторы видимы.
   */
  public void registerConstructors(
    TypeRef ref,
    List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> ctors,
    FileType fileType
  ) {
    if (ref == null || ctors == null || ctors.isEmpty()) {
      return;
    }
    constructors.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedConstructors(List.copyOf(ctors), fileType));
  }

  /**
   * Зарегистрировать динамический источник конструкторов для типа (например,
   * {@code ПриСозданииОбъекта} OneScript-класса из SymbolTree).
   * Источник вызывается каждый раз при запросе {@link #getConstructors(TypeRef)},
   * что обеспечивает hot-reload без ручной инвалидации.
   */
  public void registerConstructorSource(
    TypeRef ref,
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> source,
    FileType fileType
  ) {
    if (ref == null || source == null) {
      return;
    }
    constructorSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedConstructorSource(source, fileType));
  }

  /**
   * Зарегистрировать тип как платформенный класс с конструктором. Имя
   * становится доступным для completion после {@code Новый} (через
   * {@link GlobalScopeProvider#getClasses}) и резолвится в {@link SyntheticSymbol}
   * с ролью {@code TYPE_NAME} для hover/findGlobal. Вызывается автоматически
   * из {@link #registerPack} при непустых {@code constructors}.
   */
  private void registerAsPlatformClass(TypeRef ref, FileType fileType) {
    var names = new LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerPlatformClass(ref, names, fileType,
      getDescription(ref, fileType));
  }

  /**
   * Удалить пользовательский тип по qualifiedName (например, при закрытии
   * соответствующего документа).
   */
  public void unregisterUserType(String qualifiedName) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.remove(ref);
    memberSources.remove(ref);
    membersEpoch.incrementAndGet();
    visibleTypes.values().forEach(typed -> typed.remove(ref));
    aliasIndex.remove(qualifiedName.toLowerCase(Locale.ROOT));
  }

  private void registerPack(TypePackProvider.TypeDecl decl, FileType fileType) {
    var ref = intern(decl.kind(), decl.qualifiedName());
    types.put(ref, hydrate(ref));
    // BilingualString name покрывает ru+en — обе стороны должны находиться
    // в aliasIndex, чтобы lookup по любому написанию резолвился в один TypeRef.
    addAlias(decl.qualifiedName(), ref);
    if (!decl.name().isEmpty()) {
      var bnRu = decl.name().ru();
      var bnEn = decl.name().en();
      if (!bnRu.isEmpty()) {
        addAlias(bnRu, ref);
      }
      if (!bnEn.isEmpty()) {
        addAlias(bnEn, ref);
      }
    }
    if (decl.description() != null && !decl.description().isEmpty()) {
      // TypeRegistry хранит description как scoped-String (ConfigurationTypesProvider
      // и пр. передают одноязычные). Bilingual TypeDecl.description раскрываем
      // через primary для legacy-индекса; en-сторону отдаёт displayDescription(ref, lang).
      registerDescription(ref, decl.description().primary(), fileType);
      if (!decl.description().isEmpty()) {
        typeDescriptionsBilingual.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
          .add(new ScopedBilingualDescription(decl.description(), fileType));
      }
    }
    if (decl.constructors() != null && !decl.constructors().isEmpty()) {
      registerConstructors(ref, decl.constructors(), fileType);
      registerAsPlatformClass(ref, fileType);
    }
    if (!decl.members().isEmpty()) {
      registerMemberSource(ref, decl::members, fileType);
      indexMemberMetadata(ref, decl.members());
    }
    if (decl.exposedAsGlobal()) {
      var syntheticKind = decl.isEnum()
        ? SyntheticKind.PLATFORM_GLOBAL_ENUM
        : SyntheticKind.PLATFORM_GLOBAL_PROPERTY;
      registerAsGlobalProperty(ref, fileType, syntheticKind);
    }
    if (decl.defaultElementTypes() != null && !decl.defaultElementTypes().isEmpty()) {
      defaultElementTypes.put(ref, List.copyOf(decl.defaultElementTypes()));
    }
    if (decl.supportsForEach()) {
      supportsForEach.put(ref, Boolean.TRUE);
    }
    if (decl.supportsIndexAccess()) {
      supportsIndexAccess.put(ref, Boolean.TRUE);
    }
    if (!decl.forEachDescription().isEmpty()) {
      forEachDescriptions.put(ref, decl.forEachDescription());
    }
    if (!decl.indexAccessDescription().isEmpty()) {
      indexAccessDescriptions.put(ref, decl.indexAccessDescription());
    }
    if (!decl.typeParameters().isEmpty()) {
      typeParameters.put(ref, List.copyOf(decl.typeParameters()));
    }
    if (!decl.name().isEmpty()) {
      displayNames.putIfAbsent(ref, decl.name());
    }
    registerFileType(ref, fileType);
  }

  /**
   * Возвращает имя типа для отображения в указанной локали LS. Если в
   * реестре есть двуязычное имя ({@link TypePackProvider.TypeDecl#name()}),
   * выбирает ru или en по {@code language}; иначе — {@code ref.qualifiedName()}.
   */
  public String displayName(TypeRef ref, Language language) {
    var bn = displayNames.get(ref);
    if (bn == null) {
      var canonical = aliasIndex.get(ref.qualifiedName().toLowerCase(Locale.ROOT));
      if (canonical != null) {
        bn = displayNames.get(canonical);
      }
    }
    if (bn == null || bn.isEmpty()) {
      return ref.qualifiedName();
    }
    return bn.forLanguage(language);
  }

  /**
   * Типы элементов коллекции для указанного {@code ref}. Возвращает
   * {@link TypeSet#EMPTY}, если тип не зарегистрирован как коллекция либо
   * элементы гетерогенные.
   * <p>
   * Element-refs резолвятся через {@link #resolve(String)}, чтобы получить
   * канонические интернированные TypeRef'ы (одинаковые с теми, что
   * используются как ключи в индексах членов).
   */
  public TypeSet getDefaultElementTypes(TypeRef ref) {
    var raw = defaultElementTypes.get(ref);
    if (raw == null || raw.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var canonical = new ArrayList<TypeRef>(raw.size());
    for (var element : raw) {
      var resolved = resolve(element.qualifiedName()).orElse(element);
      canonical.add(resolved);
    }
    return TypeSet.of(canonical);
  }

  /** {@code true}, если у типа разрешён обход {@code Для Каждого}. */
  public boolean supportsForEach(TypeRef ref) {
    return Boolean.TRUE.equals(supportsForEach.get(ref));
  }

  /** {@code true}, если у типа разрешён индексатор {@code [...]}. */
  public boolean supportsIndexAccess(TypeRef ref) {
    return Boolean.TRUE.equals(supportsIndexAccess.get(ref));
  }

  /**
   * Текстовое описание обхода {@code Для Каждого} для типа-коллекции
   * (из синтакс-помощника платформы). Пустая строка, если описание не задано.
   */
  public String getForEachDescription(TypeRef ref) {
    return getForEachDescription(ref, Language.DEFAULT_LANGUAGE);
  }

  /** Описание обхода в указанной локали (с fallback на другую). */
  public String getForEachDescription(TypeRef ref, Language language) {
    return forEachDescriptions.getOrDefault(ref, BilingualString.EMPTY).forLanguage(language);
  }

  /**
   * Текстовое описание индексатора {@code [...]} для типа-коллекции
   * (из синтакс-помощника платформы). Пустая строка, если описание не задано.
   */
  public String getIndexAccessDescription(TypeRef ref) {
    return getIndexAccessDescription(ref, Language.DEFAULT_LANGUAGE);
  }

  /** Описание индексатора в указанной локали. */
  public String getIndexAccessDescription(TypeRef ref, Language language) {
    return indexAccessDescriptions.getOrDefault(ref, BilingualString.EMPTY).forLanguage(language);
  }

  /** Описание типа в указанной локали (для hover'а класса/конструктора), без фильтрации по типу файла. */
  public String getDescription(TypeRef ref, Language language) {
    var list = typeDescriptionsBilingual.get(ref);
    if (list != null) {
      for (var sd : List.copyOf(list)) {
        if (!sd.text().isEmpty()) {
          return sd.text().forLanguage(language);
        }
      }
    }
    return getDescription(ref);
  }

  /**
   * То же, что {@link #getDescription(TypeRef, Language)}, но с фильтрацией по
   * {@link FileType}: когда тип имеет разные описания в BSL и OS (например,
   * {@code ТаблицаЗначений}), возвращается описание, чей скоуп совместим с
   * {@code fileType}.
   *
   * @param ref      ссылка на тип.
   * @param language локаль интерфейса LS.
   * @param fileType тип файла-потребителя.
   * @return описание; пустая строка, если подходящего описания нет.
   */
  public String getDescription(TypeRef ref, Language language, FileType fileType) {
    var list = typeDescriptionsBilingual.get(ref);
    if (list != null) {
      for (var sd : List.copyOf(list)) {
        if (sd.fileType() == fileType && !sd.text().isEmpty()) {
          return sd.text().forLanguage(language);
        }
      }
    }
    return getDescription(ref, fileType);
  }

  /**
   * Имена generic-плейсхолдеров типа (без угловых скобок), в порядке
   * появления в qualifiedName. Для не-generic типов — пустой список.
   * Источник — {@link TypePackProvider.TypeDecl#typeParameters()}
   * (структурное представление из bsl-context).
   *
   * @param ref ссылка на тип
   * @return неизменяемый список имён placeholder'ов или пустой список
   */
  public List<String> getTypeParameters(TypeRef ref) {
    return typeParameters.getOrDefault(ref, List.of());
  }

  /**
   * @return {@code true}, если в реестре зарегистрирован хотя бы один член
   *         с {@link AccessMode#READ}. Дешёвая проверка для early-exit'а в
   *         диагностиках, не имеющих смысла без read-only-данных
   *         (например, для JSON-fallback без accessMode).
   */
  public boolean hasAnyReadOnlyMember() {
    return memberMetadataIndex.hasAnyReadOnly();
  }

  /**
   * Дешёвый pre-filter: входит ли {@code name} в число имён, у которых хотя бы
   * на одном типе задана версия появления/устаревания. Сам по себе ничего не
   * решает — после него обязателен точный резолв члена на конкретном
   * типе-владельце (иначе сработает однофамилец с другого типа).
   */
  public boolean isVersionedMemberName(@Nullable String name) {
    return name != null && memberMetadataIndex.isVersionedName(name);
  }

  /**
   * Дешёвая проверка имени присваиваемого свойства: входит ли оно в число
   * имён, у которых ХОТЯ БЫ НА ОДНОМ платформенном типе режим доступа =
   * {@link AccessMode#READ}. Используется как pre-filter — отрицательный
   * ответ гарантирует, что присваивание точно не нарушает read-only.
   */
  public boolean isReadOnlyMemberName(@Nullable String name) {
    return name != null && memberMetadataIndex.isReadOnlyName(name);
  }

  /**
   * Точная проверка: помечен ли member {@code name} на типе {@code typeRef}
   * как {@link AccessMode#READ}. Регистронезависимая. Возвращает
   * {@code false}, если тип не зарегистрирован или member на нём
   * не read-only.
   */
  public boolean isReadOnlyMember(@Nullable TypeRef typeRef, @Nullable String name) {
    return typeRef != null && name != null && memberMetadataIndex.isReadOnly(typeRef, name);
  }

  /**
   * Индексирует метаданные членов типа {@code ref} для дешёвых pre-filter'ов
   * диагностик: read-only свойства и версионные (sinceVersion/deprecated) члены.
   */
  private void indexMemberMetadata(TypeRef ref, Collection<MemberDescriptor> members) {
    for (var member : members) {
      memberMetadataIndex.index(ref, member);
    }
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
