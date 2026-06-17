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
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
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

  /**
   * Синтетический тип «глобальный контекст»: его члены — глобальные методы и
   * свойства, видимые в global scope без префикса (issue #3994). Системные
   * перечисления и прочие {@code exposedAsGlobal}-типы регистрируются как
   * свойства-члены этого типа (с {@code valueType} = сам тип). Имя
   * зарезервировано и не пересекается с инстанцируемыми типами 1С.
   */
  public static final TypeRef GLOBAL_CONTEXT = new TypeRef(TypeKind.PLATFORM, "ГлобальныйКонтекст");

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
  /**
   * Источники членов типов в разрезе языка (один тип может расширяться многими
   * источниками; порядок значим — {@link #registerMemberOverride} вставляет в начало).
   */
  private final Map<FileType, Map<TypeRef, List<MemberSource>>> memberSources = perFileType();

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

    private static final Comparator<MembersKey> NATURAL_ORDER = Comparator
      .comparing(MembersKey::ref)
      .thenComparing(MembersKey::fileType);

    @Override
    public int compareTo(MembersKey other) {
      return NATURAL_ORDER.compare(this, other);
    }
  }

  private record CachedMembers(long epoch, List<MemberDescriptor> members) {
  }

  /** Пустой контейнер с разрезами по всем языкам. */
  private static <V> Map<FileType, Map<TypeRef, V>> perFileType() {
    return Map.of(FileType.BSL, new ConcurrentHashMap<>(), FileType.OS, new ConcurrentHashMap<>());
  }
  /**
   * Типы, видимые в файлах каждого языка. Тип, не зарегистрированный ни в одном
   * разрезе, считается видимым везде (отсутствие знания — не повод фильтровать).
   */
  private final Map<FileType, Set<TypeRef>> visibleTypes = Map.of(
    FileType.BSL, ConcurrentHashMap.newKeySet(),
    FileType.OS, ConcurrentHashMap.newKeySet()
  );
  /** Описания типов в разрезе языка (первая регистрация выигрывает). */
  private final Map<FileType, Map<TypeRef, String>> descriptions = perFileType();
  /** Конструкторы типов в разрезе языка (повторные регистрации конкатенируются). */
  private final Map<FileType, Map<TypeRef, List<SignatureDescriptor>>> constructors = perFileType();
  /** Динамические источники конструкторов в разрезе языка (например, OScript-класс из SymbolTree). */
  private final Map<FileType, Map<TypeRef, List<Supplier<List<SignatureDescriptor>>>>> constructorSources = perFileType();

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
   * Двуязычные описания типов (ru + en) в разрезе языка — параллельный индекс к
   * {@link #descriptions}, который продолжает хранить scoped primary-форму
   * для legacy-логики. Заполняется из {@link TypePackProvider.TypeDecl#description()},
   * первая регистрация выигрывает.
   */
  private final Map<FileType, Map<TypeRef, BilingualString>> typeDescriptionsBilingual = perFileType();

  /**
   * Явная точка материализации workspace-scoped реестра. Тело пустое: значим
   * сам факт вызова метода на scoped-proxy — он создаёт target и прогоняет
   * {@code @PostConstruct} {@link #bootstrap()}, который push-моделью наполняет
   * {@link GlobalScopeProvider} платформенным глобальным скоупом. Вызывается из
   * {@code GlobalScopeProvider.ensureBootstrapped()}, чтобы первое в свежем
   * workspace-scope чтение глобального скоупа не увидело пустой реестр
   * (issue #3994). Заменяет прежний неявный {@code typeRegistry.resolve("")}
   * у потребителей.
   */
  public void ensureInitialized() {
    // no-op: материализация происходит за счёт самого вызова метода на proxy
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
   */
  public Optional<TypeRef> resolve(String name, FileType fileType) {
    return resolve(name).filter(ref -> isVisibleIn(ref, fileType));
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
  private boolean isVisibleIn(TypeRef ref, FileType fileType) {
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
   * Получить полный набор членов типа в разрезе языка — union по всем
   * зарегистрированным {@link MemberSource}'ам этого языка. Дубли по имени
   * отбрасываются (побеждает первый зарегистрированный источник).
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
    // Snapshot: список source'ов может модифицироваться параллельно через
    // registerMemberSource/registerMemberOverride (Phase B/C MetadataCollectionSpecializer
    // и др. workspace-scoped провайдеры). Список — CopyOnWriteArrayList,
    // снимок через List.copyOf дёшев и стабилен на время итерации.
    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var source : List.copyOf(resolveMemberSources(ref, fileType))) {
      for (var member : source.getMembers()) {
        byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
      }
    }
    // Неизменяемый список: память шарится между вызовами, случайная мутация
    // упадёт сразу (все потребители только итерируют).
    return List.copyOf(byName.values());
  }

  /**
   * Источники членов типа в разрезе языка с fallback на канонический псевдоним.
   *
   * @param ref      тип, для которого ищутся источники членов.
   * @param fileType язык файла-потребителя.
   * @return список источников; пустой, если их нет.
   */
  private List<MemberSource> resolveMemberSources(TypeRef ref, FileType fileType) {
    var byRef = memberSources.get(fileType);
    var sources = byRef.get(ref);
    if (sources == null || sources.isEmpty()) {
      var canonical = aliasIndex.get(ref.qualifiedName().toLowerCase(Locale.ROOT));
      if (canonical != null && !canonical.equals(ref)) {
        sources = byRef.get(canonical);
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
    memberSources.get(fileType).computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>())
      .add(source);
    membersEpoch.incrementAndGet();
  }

  /**
   * Аналог {@link #registerMemberSource}, но вставляет источник в НАЧАЛО списка,
   * чтобы при сборе членов через {@link #getMembers(TypeRef, FileType)} он выигрывал
   * dedup ({@code putIfAbsent} по имени). Используется для override returnType
   * у конкретного member'а уже зарегистрированного типа (например, подмена
   * {@code ОбъектМетаданныхКонфигурация.Документы} с общего
   * {@code КоллекцияОбъектовМетаданных} на специализированный
   * {@code КоллекцияОбъектовМетаданных.Документы}). Базовый источник остаётся
   * в реестре — другие members (Справочники, Перечисления, …) приходят оттуда.
   */
  public void registerMemberOverride(TypeRef ref, MemberSource source, FileType fileType) {
    memberSources.get(fileType).computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>())
      .addFirst(source);
    membersEpoch.incrementAndGet();
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
   *   <li>берёт members generic-типа через {@link #getMembers(TypeRef, FileType)};</li>
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
      var raw = getMembers(genericRef, fileType);
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
      var materialized = expandGenericMembers(genericRef, safeTypeBindings, safeExpansions, fileType);
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
                                                Map<String, List<String>> memberExpansions,
                                                FileType fileType) {
    if (memberExpansions.isEmpty()) {
      return List.of();
    }
    var safeTypeBindings = Map.copyOf(typeBindings);
    var safeExpansions = deepCopyExpansions(memberExpansions);
    return expandGenericMembers(genericRef, safeTypeBindings, safeExpansions, fileType);
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
                                                      Map<String, List<String>> memberExpansions,
                                                      FileType fileType) {
    var raw = getMembers(genericRef, fileType);
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
   * Описание типа из источника (JSON-пакета или динамической регистрации)
   * в разрезе указанного языка. Возвращает пустую строку, если описание отсутствует.
   *
   * @param ref      ссылка на тип.
   * @param fileType язык файла-потребителя.
   * @return описание или пустая строка.
   */
  public String getDescription(TypeRef ref, FileType fileType) {
    return descriptions.get(fileType).getOrDefault(ref, "");
  }

  /**
   * Зарегистрировать описание типа в разрезе языка. Повторная регистрация
   * того же языка игнорируется (первая выигрывает).
   *
   * @param ref      ссылка на тип.
   * @param text     текст описания.
   * @param fileType язык файла, в котором описание видимо.
   */
  public void registerDescription(TypeRef ref, String text, FileType fileType) {
    if (ref == null || text == null || text.isBlank()) {
      return;
    }
    descriptions.get(fileType).putIfAbsent(ref, text);
  }

  /**
   * Список конструкторов типа (для платформенных классов из JSON-пакета).
   * Возвращает пустой список, если конструкторов нет (например, для типов
   * без блока {@code constructors} в JSON или для system enums).
   */
  public List<SignatureDescriptor> getConstructors(
    TypeRef ref, FileType fileType
  ) {
    var result = new ArrayList<SignatureDescriptor>();
    var fromPack = constructors.get(fileType).get(ref);
    if (fromPack != null) {
      result.addAll(fromPack);
    }
    var sources = constructorSources.get(fileType).get(ref);
    if (sources != null) {
      for (var supplier : List.copyOf(sources)) {
        var sigs = supplier.get();
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
    List<SignatureDescriptor> ctors,
    FileType fileType
  ) {
    if (ref == null || ctors == null || ctors.isEmpty()) {
      return;
    }
    constructors.get(fileType).computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>())
      .addAll(ctors);
  }

  /**
   * Зарегистрировать динамический источник конструкторов для типа (например,
   * {@code ПриСозданииОбъекта} OneScript-класса из SymbolTree).
   * Источник вызывается каждый раз при запросе {@link #getConstructors(TypeRef, FileType)},
   * что обеспечивает hot-reload без ручной инвалидации.
   */
  public void registerConstructorSource(
    TypeRef ref,
    java.util.function.Supplier<List<SignatureDescriptor>> source,
    FileType fileType
  ) {
    if (ref == null || source == null) {
      return;
    }
    constructorSources.get(fileType).computeIfAbsent(ref, k -> new CopyOnWriteArrayList<>())
      .add(source);
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
    memberSources.values().forEach(byRef -> byRef.remove(ref));
    membersEpoch.incrementAndGet();
    visibleTypes.values().forEach(typed -> typed.remove(ref));
    aliasIndex.remove(qualifiedName.toLowerCase(Locale.ROOT));
  }

  private void registerPack(TypePackProvider.TypeDecl decl, FileType fileType) {
    var ref = intern(decl.kind(), decl.qualifiedName());
    types.put(ref, hydrate(ref));
    registerPackAliases(decl, ref);
    registerPackDescriptions(decl, ref, fileType);
    registerPackCallables(decl, ref, fileType);
    registerPackCollectionTraits(decl, ref);
    if (!decl.name().isEmpty()) {
      displayNames.putIfAbsent(ref, decl.name());
    }
    registerFileType(ref, fileType);
  }

  /**
   * Алиасы пака: BilingualString name покрывает ru+en — обе стороны должны
   * находиться в aliasIndex, чтобы lookup по любому написанию резолвился
   * в один TypeRef.
   */
  private void registerPackAliases(TypePackProvider.TypeDecl decl, TypeRef ref) {
    addAlias(decl.qualifiedName(), ref);
    if (decl.name().isEmpty()) {
      return;
    }
    var bnRu = decl.name().ru();
    var bnEn = decl.name().en();
    if (!bnRu.isEmpty()) {
      addAlias(bnRu, ref);
    }
    if (!bnEn.isEmpty()) {
      addAlias(bnEn, ref);
    }
  }

  /**
   * Описания пака: TypeRegistry хранит description как scoped-String
   * (ConfigurationTypesProvider и пр. передают одноязычные). Bilingual
   * TypeDecl.description раскрываем через primary для legacy-индекса;
   * en-сторону отдаёт displayDescription(ref, lang).
   */
  private void registerPackDescriptions(TypePackProvider.TypeDecl decl, TypeRef ref, FileType fileType) {
    if (decl.description() == null || decl.description().isEmpty()) {
      return;
    }
    registerDescription(ref, decl.description().primary(), fileType);
    typeDescriptionsBilingual.get(fileType).putIfAbsent(ref, decl.description());
  }

  /** Вызываемое пака: конструкторы, члены, exposedAsGlobal-публикация. */
  private void registerPackCallables(TypePackProvider.TypeDecl decl, TypeRef ref, FileType fileType) {
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
  }

  /** Коллекционные свойства пака: элементы по умолчанию, Для Каждого, индексатор, generic-параметры. */
  private void registerPackCollectionTraits(TypePackProvider.TypeDecl decl, TypeRef ref) {
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

  /**
   * Описание типа в указанной локали (для hover'а класса/конструктора) в разрезе
   * языка: когда тип имеет разные описания в BSL и OS (например,
   * {@code ТаблицаЗначений}), возвращается описание языка файла-потребителя.
   *
   * @param ref      ссылка на тип.
   * @param language локаль интерфейса LS.
   * @param fileType язык файла-потребителя.
   * @return описание; пустая строка, если подходящего описания нет.
   */
  public String getDescription(TypeRef ref, Language language, FileType fileType) {
    var bilingual = typeDescriptionsBilingual.get(fileType).get(ref);
    if (bilingual != null && !bilingual.isEmpty()) {
      return bilingual.forLanguage(language);
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
