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
import java.lang.ref.WeakReference;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.AnyType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
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
import com.github._1c_syntax.bsl.context.api.ContextNames;
import com.github._1c_syntax.bsl.context.api.Placeholder;
import com.github._1c_syntax.utils.GenericInterner;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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
   * свойства, видимые в global scope без префикса. Системные
   * перечисления и прочие {@code exposedAsGlobal}-типы регистрируются как
   * свойства-члены этого типа (с {@code valueType} = сам тип). Имя
   * зарезервировано и не пересекается с инстанцируемыми типами 1С.
   * <p>
   * Деталь хранилища: доступ к глобальной области у потребителей — через
   * {@link GlobalScopeProvider} (геттеры {@code globalFunction}/{@code globalProperty}/…),
   * а не по этой ссылке. Видимость пакетная — её знают лишь реестр, поставщики
   * членов глобального контекста и {@code GlobalScopeProvider}.
   */
  static final TypeRef GLOBAL_CONTEXT = new TypeRef(TypeKind.PLATFORM, "ГлобальныйКонтекст");

  /**
   * Языки файлов, вычисленные один раз: {@link FileType#values()} клонирует внутренний
   * массив на каждый вызов, а перебор языков идёт при каждой точечной инвалидации кэша.
   */
  private static final FileType[] FILE_TYPES = FileType.values();

  private final List<PlatformTypesProvider> platformProviders;
  /**
   * Индекс метаданных членов (read-only свойства + версионные члены) для
   * дешёвых pre-filter'ов диагностик. Workspace-scoped Spring-компонент;
   * заполняется при регистрации {@link TypePackProvider.TypeDecl} провайдерами
   * платформенных типов (bsl-context / JSON-fallback); конфигурационные MD-типы
   * сюда не попадают (у них нет accessMode/версий).
   */
  private final MemberMetadataIndex memberMetadataIndex;

  /**
   * Состав определяемых типов конфигурации. Своего {@link TypeRef} у определяемого типа
   * нет — за именем стоит набор, поэтому в {@link #aliasIndex} ему места нет
   * (см. {@link #resolveSet(String)}).
   */
  private final DefinedTypesIndex definedTypes;

  /** Интернер TypeRef: канонический инстанс на пару (kind, qualifiedName). */
  private final GenericInterner<TypeRef> refInterner = new GenericInterner<>();
  /** Алиасы (включая Ru/En) → канонический TypeRef. Ключ — lowercased имя. */
  private final Map<String, TypeRef> aliasIndex = new ConcurrentHashMap<>();

  /**
   * Запомненные ответы {@link #resolveGenericByPrefix(String)}, включая промахи.
   * Сбрасывается при любом изменении {@link #aliasIndex}.
   */
  private final Map<String, Optional<TypeRef>> genericByPrefix = new ConcurrentHashMap<>();
  /** Тип ↔ объект Type (hydrated). */
  private final Map<TypeRef, Type> types = new ConcurrentHashMap<>();
  /**
   * Источники членов типов в разрезе языка (один тип может расширяться многими
   * источниками; порядок значим — {@link #registerMemberOverride} вставляет в начало).
   * <p>
   * Значение хранится компактно: для типа с единственным источником (доминирующий
   * случай — на реальной конфигурации из ~671k типов ~561k имеют ровно один источник)
   * — сам {@link MemberSource} без обёртки-списка; для типа с несколькими источниками
   * — неизменяемый {@code MemberSource[]}. Массив/одиночка вместо
   * {@code CopyOnWriteArrayList} на каждый тип убирает три служебных объекта на запись
   * (список, его lock и backing-массив). Обновления атомарны по ключу через
   * {@link ConcurrentHashMap#merge}; опубликованное значение неизменяемо, поэтому
   * читатели ({@link #resolveMemberSources}) видят согласованный снимок без блокировки.
   */
  private final Map<FileType, Map<TypeRef, Object>> memberSources = perFileType();

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
  /**
   * Пер-типовые поколения memo членов: точечная инвалидация ({@link #invalidateMembers})
   * инкрементирует поколение ключа, а {@link #getMembers} штампует им запись кэша и
   * отвергает публикацию из устаревшего поколения. Защищает от гонки, когда параллельный
   * незавершённый {@code computeMembers} публикует устаревший результат уже ПОСЛЕ
   * инвалидации (эпоха при точечной инвалидации не двигается). Ключи заводятся только для
   * реально инвалидированных типов — для нетронутых поколение по умолчанию {@code 0}.
   */
  private final Map<MembersKey, Long> membersGeneration = new ConcurrentHashMap<>();

  private record MembersKey(TypeRef ref, FileType fileType) implements Comparable<MembersKey> {

    private static final Comparator<MembersKey> NATURAL_ORDER = Comparator
      .comparing(MembersKey::ref)
      .thenComparing(MembersKey::fileType);

    @Override
    public int compareTo(MembersKey other) {
      return NATURAL_ORDER.compare(this, other);
    }
  }

  private record CachedMembers(long epoch, long generation, List<MemberDescriptor> members) {
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
  /**
   * «Страничные» метаданные типов в разрезе языка: доступность, версии
   * появления/устаревания, «Замечание», «Пример», «См. также». Заполняются из
   * {@link TypePackProvider.TypeDecl#metadata()}, первая регистрация выигрывает.
   */
  private final Map<FileType, Map<TypeRef, PlatformMetadata>> typeMetadata = perFileType();
  /** Конструкторы типов в разрезе языка (повторные регистрации конкатенируются). */
  private final Map<FileType, Map<TypeRef, List<SignatureDescriptor>>> constructors = perFileType();
  /** Динамические источники конструкторов в разрезе языка (например, OScript-класс из SymbolTree). */
  private final Map<FileType, Map<TypeRef, List<Supplier<List<SignatureDescriptor>>>>> constructorSources = perFileType();

  /**
   * Коллекционные свойства типов: типы элементов, признаки {@code Для Каждого}
   * и индексатора с описаниями. Вынесены в отдельный индекс — реестр только
   * делегирует к нему и канонизирует типы элементов через {@link #resolve(String)}.
   */
  private final CollectionTraitsIndex collectionTraits = new CollectionTraitsIndex();
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
   * Типы, состав свойств которых задан конфигурацией и достаточно компактен, чтобы
   * показывать его списком (см. {@link #registerOpenStructure}).
   */
  private final Map<TypeRef, TypeRef> openStructures = new ConcurrentHashMap<>();

  /**
   * Происхождение конкретного типа: из какого дженерика он вырос
   * ({@code СправочникСсылка.Контрагенты} → {@code СправочникСсылка.<Имя справочника>}).
   * Заполняется {@link #registerSpecialization}; первая регистрация выигрывает —
   * конкретный тип рождается из одного дженерика, а повторный вызов лишь добавляет
   * ещё один источник членов.
   */
  private final Map<TypeRef, TypeRef> specializedFrom = new ConcurrentHashMap<>();

  /**
   * Расширения типа: чьи члены в него подмешаны, в порядке регистрации
   * (см. {@link #registerExtension}). Отвечает на вопрос «что вообще навешано на
   * этот тип» — без него это выясняется только чтением кода-регистратора.
   */
  private final Map<TypeRef, List<TypeRef>> extensions = new ConcurrentHashMap<>();

  /**
   * Двуязычные описания типов (ru + en) в разрезе языка — параллельный индекс к
   * {@link #descriptions}, который продолжает хранить scoped primary-форму
   * для legacy-логики. Заполняется из {@link TypePackProvider.TypeDecl#description()},
   * первая регистрация выигрывает.
   */
  private final Map<FileType, Map<TypeRef, BilingualString>> typeDescriptionsBilingual = perFileType();

  /**
   * Члены, которых у типа не существует, — по языку файла (см.
   * {@link #registerMemberSuppression}). Хранятся именами, а не дескрипторами:
   * подавление объявляется до того, как члены собраны.
   */
  private final Map<FileType, Map<TypeRef, Set<String>>> memberSuppressions = perFileType();

  /** Пустой контейнер с разрезами по всем языкам. */
  private static <V> Map<FileType, Map<TypeRef, V>> perFileType() {
    return Map.of(FileType.BSL, new ConcurrentHashMap<>(), FileType.OS, new ConcurrentHashMap<>());
  }

  /**
   * Явная точка материализации workspace-scoped реестра. Тело пустое: значим
   * сам факт вызова метода на scoped-proxy — он создаёт target и прогоняет
   * {@code @PostConstruct} {@link #bootstrap()} (регистрацию платформенных типов).
   * Нужен потребителям, читающим реестр в свежем workspace-scope, чтобы первое
   * чтение не увидело пустой реестр.
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
    // Единый источник членов GLOBAL_CONTEXT из типов-глобал-свойств. Override
    // (в начало списка) — эти члены перекрывают одноимённые из других источников
    // GLOBAL_CONTEXT.
    registerMemberOverride(GLOBAL_CONTEXT, () -> globalPropertyMembers(FileType.BSL), FileType.BSL);
    registerMemberOverride(GLOBAL_CONTEXT, () -> globalPropertyMembers(FileType.OS), FileType.OS);

    // «Произвольный»/«Arbitrary» — вершина решётки типов. Канонизируем имя в
    // TypeRef.ANY, чтобы по всему движку универсальный тип распознавался
    // сравнением с ANY (без проверки имени). Отображается как «Произвольный».
    aliasIndex.put("произвольный", TypeRef.ANY);
    aliasIndex.put("arbitrary", TypeRef.ANY);
    registerDisplayName(TypeRef.ANY, BilingualString.of("Произвольный", "Arbitrary"));
  }

  /**
   * Интернировать ссылку на тип. Если такой тип уже зарегистрирован,
   * возвращает каноническую ссылку.
   */
  public TypeRef intern(TypeKind kind, String qualifiedName) {
    return refInterner.intern(new TypeRef(kind, qualifiedName));
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
   * Найти типы, скрытые за именем: у обычного имени это один тип, у определяемого —
   * его состав.
   * <p>
   * Определяемый тип — не тип, а именованное описание типов: собственного
   * {@link TypeRef} у него нет и быть не может, потому что за именем стоит набор.
   * Вложенные определяемые типы раскрываются вглубь, до настоящих типов; имена,
   * за которыми типа нет, отбрасываются.
   *
   * @param name имя типа: платформенное, конфигурационное либо определяемого типа.
   * @return типы за этим именем; {@link TypeSet#EMPTY}, если имя не зарегистрировано.
   */
  public TypeSet resolveSet(String name) {
    if (name.isEmpty()) {
      return TypeSet.EMPTY;
    }
    if (definedTypes.knows(name)) {
      return definedTypes.compositionOf(name, this::asTypeSet);
    }
    return asTypeSet(name);
  }

  /**
   * Запомнить состав определяемого типа конфигурации.
   *
   * @param qualifiedName полное имя определяемого типа ({@code ОпределяемыйТип.Сумма}).
   * @param composition   имена типов, из которых он собран.
   */
  public void registerDefinedType(String qualifiedName, List<String> composition) {
    definedTypes.register(qualifiedName, composition);
  }

  private TypeSet asTypeSet(String name) {
    return resolve(name).map(TypeSet::of).orElse(TypeSet.EMPTY);
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
    // Ответ зависит только от содержимого индекса имён, а тот меняется лишь при
    // регистрации типов — поэтому запоминается, включая промахи. Без этого перебор всего
    // индекса шёл на каждое разрешаемое имя: в профиле analyze по cpm это была вторая
    // строка сверху.
    return genericByPrefix.computeIfAbsent(prefix.toLowerCase(Locale.ROOT), this::findGenericByPrefix);
  }

  /**
   * Ищет generic-тип перебором индекса имён.
   *
   * @param needlePrefix начало имени в нижнем регистре.
   * @return тип семейства; {@link Optional#empty()}, если такого нет.
   */
  private Optional<TypeRef> findGenericByPrefix(String needlePrefix) {
    var needle = needlePrefix + ".<";
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
   * Найти <b>зарегистрированный</b> тип по точному совпадению kind'а и
   * канонического имени. Возвращает ссылку, только если тип присутствует в
   * хранилище типов (был зарегистрирован), а не просто интернирован.
   */
  public Optional<TypeRef> resolve(TypeKind kind, String qualifiedName) {
    var ref = new TypeRef(kind, qualifiedName);
    return types.containsKey(ref) ? Optional.of(ref) : Optional.empty();
  }

  /**
   * Получить hydrated {@link Type} по ссылке.
   */
  public Type get(TypeRef ref) {
    return types.getOrDefault(ref, UnknownType.INSTANCE);
  }

  /**
   * Получить полный набор членов типа в разрезе языка — union по всем
   * зарегистрированным {@link MemberSource}'ам этого языка. Дубли по паре
   * (вид члена {@link MemberKind}, имя без учёта регистра) отбрасываются
   * (побеждает первый источник в порядке резолва, а не обязательно первый
   * зарегистрированный — см. {@link #registerMemberOverride}) — член одного
   * вида не вытесняет одноимённый член другого вида.
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
    var generation = membersGeneration.getOrDefault(key, 0L);
    var cached = membersCache.get(key);
    if (cached != null && cached.epoch() == epoch && cached.generation() == generation) {
      return cached.members();
    }
    var members = computeMembers(ref, fileType);
    // Штампуем поколением, снятым ДО вычисления: если во время computeMembers прошла
    // точечная инвалидация (bump поколения), запись окажется устаревшей и будет
    // отвергнута следующим чтением — гонка «публикация устаревшего результата» закрыта.
    membersCache.put(key, new CachedMembers(epoch, generation, members));
    return members;
  }

  /**
   * Первый член типа {@code ref} заданного вида {@code kind} с именем {@code name}
   * (ru/en-написание, без учёта регистра). Единая точка резолва «член по (тип, вид,
   * имя)» для self-member-потребителей ({@code SelfMemberResolverImpl},
   * {@code TypeService#findSelfMember}, {@code ExpressionTypeInferencer}), чтобы
   * семантика сопоставления не расходилась по копиям.
   *
   * @param ref      тип-владелец.
   * @param kind     требуемый вид члена.
   * @param name     имя члена (ru/en, без учёта регистра).
   * @param fileType язык файла-потребителя.
   * @return найденный член; empty, если такого нет.
   */
  public Optional<MemberDescriptor> findMember(TypeRef ref, MemberKind kind, String name, FileType fileType) {
    return getMembers(ref, fileType).stream()
      .filter(member -> member.kind() == kind && member.matches(name))
      .findFirst();
  }

  /** Типы-перечисления (источник пометил {@code isEnum}), в разрезе языка. */
  private final Map<FileType, Set<TypeRef>> enumTypes = Map.of(
    FileType.BSL, ConcurrentHashMap.newKeySet(),
    FileType.OS, ConcurrentHashMap.newKeySet());

  /**
   * Является ли тип системным/платформенным перечислением в данном языке файла.
   * Read-проекция для потребителей (например, раскраска
   * {@code GLOBAL_CONTEXT}-свойства как {@code Enum} vs {@code Class}).
   *
   * @param ref      проверяемый тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @return {@code true}, если тип помечен источником этого языка как перечисление.
   */
  public boolean isEnumType(@Nullable TypeRef ref, FileType fileType) {
    return ref != null && enumTypes.get(fileType).contains(ref);
  }

  /**
   * Типы, видимые как свойства-члены {@link #GLOBAL_CONTEXT}, в разрезе языка.
   * Маркер-множество — аналог {@link #enumTypes}: признак «тип виден в глобальной
   * области» хранится здесь, у хранилища типов.
   */
  private final Map<FileType, Set<TypeRef>> globalPropertyTypes = Map.of(
    FileType.BSL, ConcurrentHashMap.newKeySet(),
    FileType.OS, ConcurrentHashMap.newKeySet());

  /**
   * Явный source-символ типа-глобал-свойства — для типов, не несущих declaration
   * сами (в отличие от {@link UserType}). {@link WeakReference} — символ не
   * удерживается.
   */
  private final Map<TypeRef, WeakReference<SourceDefinedSymbol>> globalPropertySymbols =
    new ConcurrentHashMap<>();

  /**
   * Пометить тип как глобальное свойство ({@link #GLOBAL_CONTEXT}-член) для языка
   * без отдельного source-символа (тип либо не имеет его, либо несёт сам — см.
   * {@link UserType}). Член собирается лениво из реестра (имя/bilingual из
   * displayName, value-type = ref).
   *
   * @param ref      тип-глобал-свойство.
   * @param fileType язык, в котором он виден без префикса.
   */
  public void registerGlobalPropertyType(TypeRef ref, FileType fileType) {
    if (globalPropertyTypes.get(fileType).add(ref)) {
      membersEpoch.incrementAndGet();
    }
  }

  /**
   * То же, но с явным source-символом — для типов, не несущих declaration сами.
   * Символ удерживается слабо ({@link WeakReference}).
   *
   * @param ref         тип-глобал-свойство.
   * @param fileType    язык, в котором он виден без префикса.
   * @param declaration символ-источник, объявивший тип.
   */
  public void registerGlobalPropertyType(TypeRef ref, FileType fileType, SourceDefinedSymbol declaration) {
    globalPropertySymbols.put(ref, new WeakReference<>(declaration));
    if (globalPropertyTypes.get(fileType).add(ref)) {
      membersEpoch.incrementAndGet();
    }
    // Повторная пометка (правка уже зарегистрированного модуля) обновляет только
    // symbol-источник; инвалидацию memo GLOBAL_CONTEXT-члена и name-индекса, куда
    // символ уже вошёл, выполняет вызывающий провайдер точечно.
  }

  /**
   * Снять пометку глобального свойства с типа.
   *
   * @param ref      тип.
   * @param fileType язык.
   */
  public void unregisterGlobalPropertyType(TypeRef ref, FileType fileType) {
    globalPropertyTypes.get(fileType).remove(ref);
    globalPropertySymbols.remove(ref);
    membersEpoch.incrementAndGet();
  }

  /**
   * Члены {@link #GLOBAL_CONTEXT} из типов, помеченных глобальными свойствами,
   * для языка. Регистрируется как override (см. {@code bootstrap}) — перекрывает
   * одноимённые члены из других источников {@link #GLOBAL_CONTEXT}.
   */
  private List<MemberDescriptor> globalPropertyMembers(FileType fileType) {
    var refs = globalPropertyTypes.get(fileType);
    var result = new ArrayList<MemberDescriptor>(refs.size());
    for (var ref : refs) {
      var member = MemberDescriptor.property(ref.qualifiedName(), ref, "");
      var display = displayNames.get(ref);
      if (display != null && !display.isEmpty()) {
        member = member.withBilingualName(display);
      }
      var declaration = globalPropertyDeclaration(ref);
      if (declaration != null) {
        member = member.withSourceSymbol(declaration);
      }
      result.add(member);
    }
    return result;
  }

  private @Nullable SourceDefinedSymbol globalPropertyDeclaration(TypeRef ref) {
    if (get(ref) instanceof UserType userType) {
      var declaration = userType.getDeclaration().orElse(null);
      if (declaration != null) {
        return declaration;
      }
    }
    var weak = globalPropertySymbols.get(ref);
    return weak == null ? null : weak.get();
  }

  /**
   * Имя резолвится в платформенный/конфигурационный тип с конструктором —
   * т.е. это имя типа для {@code Новый}/типовой позиции ({@code Структура},
   * {@code ТаблицаЗначений}), а не глобальное значение. Ось type-name отдельно
   * от членов {@link #GLOBAL_CONTEXT}.
   *
   * @param name     имя (регистронезависимо, ru/en).
   * @param fileType язык файла-потребителя.
   * @return {@code true}, если имя — конструируемый тип.
   */
  public boolean isConstructibleTypeName(@Nullable String name, FileType fileType) {
    return resolve(name).map(ref -> !getConstructors(ref, fileType).isEmpty()).orElse(false);
  }

  private List<MemberDescriptor> computeMembers(TypeRef ref, FileType fileType) {
    // Snapshot: список source'ов может модифицироваться параллельно через
    // registerMemberSource/registerMemberOverride (Phase B/C MetadataCollectionSpecializer
    // и др. workspace-scoped провайдеры). Список — CopyOnWriteArrayList,
    // снимок через List.copyOf дёшев и стабилен на время итерации.
    // Ключ дедупликации — (kind, имя): метод и свойство с одинаковым именем —
    // разные члены (например, self-completion может видеть self-метод и
    // одноимённую self-переменную типа), один не должен вытеснять другой.
    var suppressed = resolveSuppressions(ref, fileType);
    var byNameAndKind = new LinkedHashMap<MemberKey, MemberDescriptor>();
    for (var source : List.copyOf(resolveMemberSources(ref, fileType))) {
      for (var member : source.getMembers()) {
        if (isSuppressed(member, suppressed)) {
          continue;
        }
        byNameAndKind.putIfAbsent(
          new MemberKey(member.kind(), member.name().toLowerCase(Locale.ROOT)), member);
      }
    }
    // Неизменяемый список: память шарится между вызовами, случайная мутация
    // упадёт сразу (все потребители только итерируют).
    return List.copyOf(byNameAndKind.values());
  }

  /**
   * Подавлен ли член (см. {@link #registerMemberSuppression}). Сравнение идёт по
   * дескриптору, а не по строке имени, — иначе подавление сняло бы член только в
   * том написании, которым его назвали.
   */
  private static boolean isSuppressed(MemberDescriptor member, Set<String> suppressed) {
    for (var name : suppressed) {
      if (member.matches(name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ключ дедупликации членов в {@link #computeMembers}: вид члена + имя без учёта регистра.
   * Package-private (а не private) ради прямого юнит-теста {@code compareTo}.
   */
  record MemberKey(MemberKind kind, String lowercaseName) implements Comparable<MemberKey> {
    @Override
    public int compareTo(MemberKey other) {
      var byKind = kind.compareTo(other.kind);
      return byKind != 0 ? byKind : lowercaseName.compareTo(other.lowercaseName);
    }
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
    if (sources == null) {
      var canonical = canonicalOf(ref);
      if (canonical != null) {
        sources = byRef.get(canonical);
      }
    }
    return asSourceList(sources);
  }

  /**
   * Подавления для типа — тем же путём, что и источники членов.
   * <p>
   * Путь обязан совпадать: если ref не канонический, источники находятся по канону, а
   * подавления — нет, и подавленный член вернулся бы в ответ через «боковую» ссылку.
   */
  private Set<String> resolveSuppressions(TypeRef ref, FileType fileType) {
    var byRef = memberSuppressions.get(fileType);
    var names = byRef.get(ref);
    if (names == null) {
      var canonical = canonicalOf(ref);
      if (canonical != null) {
        names = byRef.get(canonical);
      }
    }
    return names == null ? Set.of() : names;
  }

  /** Канонический ref по имени; {@code null}, если он же и передан либо алиаса нет. */
  private @Nullable TypeRef canonicalOf(TypeRef ref) {
    var canonical = aliasIndex.get(ref.qualifiedName().toLowerCase(Locale.ROOT));
    return canonical != null && !canonical.equals(ref) ? canonical : null;
  }

  /**
   * Разложить компактное значение {@link #memberSources} в список источников:
   * {@code null} → пусто, одиночный {@link MemberSource} → список из одного,
   * {@code MemberSource[]} → неизменяемый список-обёртка. Пустого значения не бывает —
   * {@link #appendSource}/{@link #prependSource} всегда дают ≥1 элемент, а единственное
   * удаление ({@code memberSources...remove(ref)}) снимает ключ целиком.
   */
  private static List<MemberSource> asSourceList(@Nullable Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof MemberSource single) {
      return List.of(single);
    }
    return List.of((MemberSource[]) value);
  }

  /** Дописать источник в конец компактного значения (одиночка → массив). */
  private static Object appendSource(Object current, Object added) {
    var add = (MemberSource) added;
    if (current instanceof MemberSource single) {
      return new MemberSource[]{single, add};
    }
    var array = (MemberSource[]) current;
    var updated = Arrays.copyOf(array, array.length + 1);
    updated[array.length] = add;
    return updated;
  }

  /** Вставить источник в начало компактного значения (override выигрывает dedup по имени). */
  private static Object prependSource(Object current, Object added) {
    var add = (MemberSource) added;
    if (current instanceof MemberSource single) {
      return new MemberSource[]{add, single};
    }
    var array = (MemberSource[]) current;
    var updated = new MemberSource[array.length + 1];
    updated[0] = add;
    System.arraycopy(array, 0, updated, 1, array.length);
    return updated;
  }

  /**
   * Точечно сбросить memo {@link #getMembers} для одного типа во всех языках —
   * без сдвига глобальной эпохи (кэши прочих типов остаются валидными).
   * Применяется при правке содержимого документа, чьи member-source'ы читают
   * только этот тип (BSL-модуль как источник членов своего типа-обёртки).
   * <p>
   * Инвалидация — через инкремент пер-типового поколения ({@link #membersGeneration}),
   * а не удаление записи: это отвергает и уже закэшированный результат, и устаревший
   * результат параллельного незавершённого {@code computeMembers}, который допишется
   * в кэш уже после инвалидации. Запись выселяется перезаписью при следующем чтении.
   *
   * @param ref тип, memo членов которого нужно пересобрать.
   */
  public void invalidateMembers(TypeRef ref) {
    for (var fileType : FILE_TYPES) {
      membersGeneration.merge(new MembersKey(ref, fileType), 1L, Long::sum);
    }
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
    memberSources.get(fileType).merge(ref, source, TypeRegistry::appendSource);
    membersEpoch.incrementAndGet();
  }

  /**
   * Объявить, что членов с такими именами у типа не существует.
   * <p>
   * Источник умеет только <b>перекрыть</b> член, но не убрать: {@code registerMemberOverride}
   * встаёт в начало списка, а дедуп берёт первое вхождение. Когда член приходит из объявления
   * платформы, а применимость его зависит от конфигурации, перекрывать нечем — нужно убрать.
   * Так у неиерархического справочника не существует {@code Родитель} и {@code ЭтоГруппа},
   * хотя платформа объявляет их у всего семейства справочников.
   * <p>
   * Подавление сравнивает имена через {@link MemberDescriptor#matches(String)}, то есть
   * снимает член в обоих написаниях: подавлять по строке было бы ошибкой — английское имя
   * пережило бы подавление.
   *
   * @param ref         тип, у которого членов нет.
   * @param memberNames имена подавляемых членов (ru либо en, регистр не важен).
   * @param fileType    язык файла-потребителя.
   */
  public void registerMemberSuppression(TypeRef ref, Collection<String> memberNames, FileType fileType) {
    if (memberNames.isEmpty()) {
      return;
    }
    memberSuppressions.get(fileType)
      .computeIfAbsent(ref, key -> ConcurrentHashMap.newKeySet())
      .addAll(memberNames);
    membersEpoch.incrementAndGet();
  }

  /**
   * Аналог {@link #registerMemberSource}, но вставляет источник в НАЧАЛО списка,
   * чтобы при сборе членов через {@link #getMembers(TypeRef, FileType)} он выигрывал
   * dedup ({@code putIfAbsent} по паре (вид члена, имя)). Используется для override returnType
   * у конкретного member'а уже зарегистрированного типа (например, подмена
   * {@code ОбъектМетаданныхКонфигурация.Документы} с общего
   * {@code КоллекцияОбъектовМетаданных} на специализированный
   * {@code КоллекцияОбъектовМетаданных.Документы}). Базовый источник остаётся
   * в реестре — другие members (Справочники, Перечисления, …) приходят оттуда.
   */
  public void registerMemberOverride(TypeRef ref, MemberSource source, FileType fileType) {
    memberSources.get(fileType).merge(ref, source, TypeRegistry::prependSource);
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
   *   <li>применяет {@link MemberDescriptor#specialize(Map, UnaryOperator)} к каждому
   *       члену — подставляет {@code bindings} в возвращаемые типы и
   *       сигнатуры.</li>
   * </ol>
   * <p>
   * Источник лениво пересобирается на каждый getMembers, чтобы реагировать
   * на смену языка интерфейса (имена members generic-типа меняются) и не
   * зависеть от порядка инициализации платформенных провайдеров.
   * Также индексируются read-only и версионные members специализированного типа
   * (см. {@link #indexMemberMetadata}).
   * <p>
   * Переносятся <b>только members</b>. Коллекционные свойства (типы элементов,
   * {@code Для Каждого}, индексатор) сознательно не копируются: «источник — коллекция»
   * не значит «target — коллекция». Если специализация должна остаться коллекцией,
   * позовите {@link #inheritCollectionTraits(TypeRef, TypeRef, FileType)} явно.
   * <p>
   * Подмешать в тип члены другого типа <b>без</b> подстановки — это уже не
   * специализация, а расширение: см. {@link #registerExtension}.
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
    specializedFrom.putIfAbsent(specializedRef, genericRef);
    copyMembers(specializedRef, genericRef, safeBindings, fileType);
  }

  /**
   * Зарегистрировать расширение типа: подмешать в {@code target} члены
   * {@code source}'а как есть, без подстановки placeholder'ов.
   * <p>
   * Операция отличается от специализации не механикой, а смыслом: специализация
   * <b>порождает</b> конкретный тип из дженерика, а расширение <b>дополняет</b> уже
   * существующий тип. Так собираются формы (базовый тип + расширение по основному
   * реквизиту + расширение вида элемента), строка динамического списка и объектный
   * контекст модуля обычной формы — у одного target'а таких источников несколько.
   * <p>
   * Связь запоминается и доступна через {@link #extensionsOf(TypeRef)}: иначе узнать,
   * что на типе висит, можно только чтением кода-регистратора.
   *
   * @param target   тип, который дополняется.
   * @param source   тип, чьи члены подмешиваются.
   * @param fileType язык файла, в котором расширение видимо.
   */
  public void registerExtension(TypeRef target, TypeRef source, FileType fileType) {
    if (target == null || source == null) {
      return;
    }
    extensions.computeIfAbsent(target, key -> new CopyOnWriteArrayList<>()).add(source);
    copyMembers(target, source, Map.of(), fileType);
  }

  /**
   * Дженерик, из которого вырос конкретный тип.
   *
   * @param ref конкретный тип.
   * @return дженерик; пусто, если тип специализацией не порождался.
   */
  public Optional<TypeRef> genericOf(TypeRef ref) {
    return Optional.ofNullable(specializedFrom.get(ref));
  }

  /**
   * Типы, подмешанные в этот тип расширением, в порядке регистрации.
   *
   * @param ref тип.
   * @return расширения; пустой список, если их нет.
   */
  public List<TypeRef> extensionsOf(TypeRef ref) {
    return List.copyOf(extensions.getOrDefault(ref, List.of()));
  }

  /** Общая механика специализации и расширения: ленивый перенос членов источника. */
  private void copyMembers(TypeRef target, TypeRef source, Map<String, String> bindings, FileType fileType) {
    MemberSource memberSource = () -> {
      var raw = getMembers(source, fileType);
      if (raw.isEmpty()) {
        return List.of();
      }
      var result = new ArrayList<MemberDescriptor>(raw.size());
      for (var member : raw) {
        if (member.generic()) {
          continue;
        }
        var specialized = member.specialize(bindings, this::canonicalRef);
        result.add(specialized);
        memberMetadataIndex.index(target, specialized);
      }
      return result;
    };
    registerMemberSource(target, memberSource, fileType);
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
        expandTemplate(template, memberExpansions, typeBindings, result, this::canonicalRef);
      }
    }
    return result;
  }

  private static void expandTemplate(MemberDescriptor template,
                                     Map<String, List<String>> memberExpansions,
                                     Map<String, String> typeBindings,
                                     List<MemberDescriptor> sink,
                                     UnaryOperator<TypeRef> canonicalizer) {
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
      sink.add(materializeGenericMember(template, ruMatch, enMatch, value, typeBindings, canonicalizer));
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
                                                           Map<String, String> typeBindings,
                                                           UnaryOperator<TypeRef> canonicalizer) {
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
      .specialize(combined, canonicalizer)
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
   * <p>
   * Инвариант «одно qualifiedName ↔ один {@link TypeRef}»: если имя уже
   * зарезолвлено в канонический тип (например, платформенную специализацию
   * {@code ОтчётОбъект.<Имя>}, которую завёл {@link #registerSpecialization}
   * с kind'ом generic'а = {@link TypeKind#PLATFORM}), — <b>переиспользуем</b> его,
   * а не плодим отдельный {@link TypeKind#CONFIGURATION}-ref с тем же именем.
   * Иначе второй ref перекрыл бы первый в {@code aliasIndex}, а member-source'ы
   * разбрелись бы по двум разным ref'ам: {@link #getMembers} собирает источники
   * строго по своему ref (см. {@code resolveMemberSources}) и не досбирает чужие,
   * из-за чего, например, события и встроенные реквизиты платформенного типа стали
   * бы недостижимы после того, как модуль объекта до-регистрировал свои члены. Тот
   * же resolve-or-intern уже делает {@link #registerSpecialization(String, TypeRef,
   * Map, FileType)} — так обе стороны сходятся на один ref независимо от порядка
   * регистрации.
   */
  public TypeRef registerConfigurationType(String qualifiedName) {
    var existing = resolve(qualifiedName).orElse(null);
    if (existing != null) {
      // Имя уже занято каноническим типом — садимся на него, лишь дорегистрируя
      // BSL-видимость (конфигурационные члены всегда BSL). Не перетираем types-запись
      // существующего типа: его kind/описание/конструкторы остаются как есть.
      registerFileType(existing, FileType.BSL);
      return existing;
    }
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
   * «Страничные» метаданные типа из синтакс-помощника в разрезе указанного
   * языка: доступность по видам клиента, версии появления/устаревания с
   * рекомендуемыми заменами, «Замечание», «Пример», «См. также».
   *
   * @param ref      ссылка на тип.
   * @param fileType язык файла-потребителя.
   * @return метаданные либо {@link PlatformMetadata#EMPTY}, если источник их не дал.
   */
  public PlatformMetadata getTypeMetadata(TypeRef ref, FileType fileType) {
    return typeMetadata.get(fileType).getOrDefault(ref, PlatformMetadata.EMPTY);
  }

  /**
   * Зарегистрировать «страничные» метаданные типа в разрезе языка. Повторная
   * регистрация того же языка игнорируется (первая выигрывает), пустые
   * метаданные не сохраняются.
   *
   * @param ref      ссылка на тип.
   * @param metadata метаданные типа.
   * @param fileType язык файла, в котором метаданные видимы.
   */
  public void registerTypeMetadata(TypeRef ref, PlatformMetadata metadata, FileType fileType) {
    if (metadata.isEmpty()) {
      return;
    }
    typeMetadata.get(fileType).putIfAbsent(ref, metadata);
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
    genericByPrefix.clear();
    collectionTraits.remove(ref);
  }

  private void registerPack(TypePackProvider.TypeDecl decl, FileType fileType) {
    var ref = intern(decl.kind(), decl.qualifiedName());
    types.put(ref, hydrate(ref));
    if (decl.isEnum()) {
      enumTypes.get(fileType).add(ref);
    }
    registerPackAliases(decl, ref);
    registerPackDescriptions(decl, ref, fileType);
    registerPackMetadata(decl, ref, fileType);
    registerPackCallables(decl, ref, fileType);
    collectionTraits.registerPack(decl, ref, fileType);
    registerPackTypeParameters(decl, ref);
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

  /** Страничные метаданные пака: доступность, версии, замечание, примеры, «См. также». */
  private void registerPackMetadata(TypePackProvider.TypeDecl decl, TypeRef ref, FileType fileType) {
    registerTypeMetadata(ref, decl.metadata(), fileType);
  }

  /** Вызываемое пака: конструкторы, члены, exposedAsGlobal-публикация. */
  private void registerPackCallables(TypePackProvider.TypeDecl decl, TypeRef ref, FileType fileType) {
    if (decl.constructors() != null && !decl.constructors().isEmpty()) {
      registerConstructors(ref, decl.constructors(), fileType);
    }
    if (!decl.members().isEmpty()) {
      registerMemberSource(ref, decl::members, fileType);
      indexMemberMetadata(ref, decl.members());
    }
  }

  /** Generic-параметры пака: имена плейсхолдеров в qualifiedName типа. */
  private void registerPackTypeParameters(TypePackProvider.TypeDecl decl, TypeRef ref) {
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
    return collectionTraits.defaultElementTypes(ref, element -> resolve(element.qualifiedName()).orElse(element));
  }

  /**
   * Пометить пользовательский тип (OneScript-класс) как коллекцию, обходимую
   * через {@code Для Каждого} — или снять признак. Это императивный аналог
   * регистрации пака для USER-типов, у которых нет
   * {@link TypePackProvider.TypeDecl}: источник истины — аннотация
   * {@code &Обходимое} на {@code ПриСозданииОбъекта} (см.
   * {@code OScriptIterable#isIterable}).
   * <p>
   * Тип элемента при этом не задаётся: в исходниках OneScript он нигде не
   * объявлен (итератор возвращает нетипизированное значение), поэтому
   * {@code Для Каждого X Из Коллекция} даёт {@code X} типа «любой» — ровно как
   * у платформенного {@code Массив}.
   * <p>
   * Метод идемпотентен и пригоден для hot-reload: повторный вызов с {@code true}
   * ничего не меняет, вызов с {@code false} снимает ранее выставленный признак
   * (например, если из класса убрали {@code &Обходимое}).
   *
   * @param ref      ссылка на пользовательский тип.
   * @param iterable {@code true} — пометить коллекцией; {@code false} — снять признак.
   * @param fileType языковой скоуп, в котором действует признак.
   */
  public void setUserTypeIterable(TypeRef ref, boolean iterable, FileType fileType) {
    collectionTraits.setIterable(ref, iterable, fileType);
  }

  /**
   * Скопировать коллекционные свойства типа-источника на его специализацию:
   * типы элементов, признаки {@code Для Каждого} и индексатора вместе с их
   * текстовыми описаниями.
   * <p>
   * {@link #registerSpecialization(TypeRef, TypeRef, Map, FileType)} переносит только
   * members — коллекционные свойства приходят из {@link TypePackProvider.TypeDecl} и
   * специализации не достаются. Без явного копирования специализация коллекции
   * (например, {@code ВсеЭлементыФормы.<форма>}) переставала бы обходиться
   * {@code Для Каждого} и индексироваться.
   * <p>
   * Уже заданные у {@code target} свойства не перетираются: собственная регистрация
   * специализации приоритетнее унаследованной.
   *
   * @param target   специализированный тип-получатель.
   * @param source   тип-источник (обычно generic, от которого сделана специализация).
   * @param fileType языковой скоуп.
   */
  public void inheritCollectionTraits(TypeRef target, TypeRef source, FileType fileType) {
    collectionTraits.inherit(target, source, fileType);
  }

  /**
   * Задать типы элементов коллекции явно. Нужно там, где элемент известен точнее,
   * чем у обобщённого типа: у специализации коллекции ({@code ДокументТабличнаяЧасть.X.Y})
   * элемент — своя строка с колонками, а не обобщённая {@code Строка табличной части}.
   * <p>
   * Первая регистрация выигрывает, как и при наследовании
   * ({@link #inheritCollectionTraits}), поэтому вызывать надо до него.
   *
   * @param ref          ссылка на тип-коллекцию.
   * @param elementTypes типы элементов; пустой список ничего не меняет.
   */
  public void registerDefaultElementTypes(TypeRef ref, List<TypeRef> elementTypes) {
    collectionTraits.registerDefaultElementTypes(ref, elementTypes);
  }

  /** {@code true}, если у типа разрешён обход {@code Для Каждого} в данном языке файла. */
  public boolean supportsForEach(TypeRef ref, FileType fileType) {
    return collectionTraits.supportsForEach(ref, fileType);
  }

  /**
   * Пометить тип как «открытую структуру» — набор именованных свойств, объявленный
   * конфигурацией. У такого типа состав свойств информативен сам по себе, поэтому
   * потребители вправе показывать его целиком (например, списком в hover'е), а не
   * ограничиваться именем типа.
   * <p>
   * Помечать имеет смысл только компактные наборы: у типа с сотнями свойств
   * развёрнутый список бесполезен.
   *
   * @param ref          ссылка на тип.
   * @param inheritedFrom базовый платформенный тип, от которого {@code ref}
   *                     унаследовал members. Его члены полями не считаются:
   *                     конфигурация их не объявляла.
   */
  public void registerOpenStructure(TypeRef ref, TypeRef inheritedFrom) {
    openStructures.put(ref, inheritedFrom);
  }

  /**
   * Базовый тип открытой структуры (см. {@link #registerOpenStructure}).
   *
   * @param ref ссылка на тип.
   * @return базовый тип; пусто, если тип открытой структурой не помечен.
   */
  public Optional<TypeRef> openStructureBase(TypeRef ref) {
    return Optional.ofNullable(openStructures.get(ref));
  }

  /** {@code true}, если у типа разрешён индексатор {@code [...]} в данном языке файла. */
  public boolean supportsIndexAccess(TypeRef ref, FileType fileType) {
    return collectionTraits.supportsIndexAccess(ref, fileType);
  }

  /**
   * Текстовое описание обхода {@code Для Каждого} для типа-коллекции
   * (из синтакс-помощника платформы) в данном языке файла. Пустая строка,
   * если описание не задано.
   */
  public String getForEachDescription(TypeRef ref, FileType fileType) {
    return getForEachDescription(ref, fileType, Language.DEFAULT_LANGUAGE);
  }

  /** Описание обхода в указанной локали (с fallback на другую) в данном языке файла. */
  public String getForEachDescription(TypeRef ref, FileType fileType, Language language) {
    return collectionTraits.forEachDescription(ref, fileType, language);
  }

  /**
   * Текстовое описание индексатора {@code [...]} для типа-коллекции
   * (из синтакс-помощника платформы) в данном языке файла. Пустая строка,
   * если описание не задано.
   */
  public String getIndexAccessDescription(TypeRef ref, FileType fileType) {
    return getIndexAccessDescription(ref, fileType, Language.DEFAULT_LANGUAGE);
  }

  /** Описание индексатора в указанной локали в данном языке файла. */
  public String getIndexAccessDescription(TypeRef ref, FileType fileType, Language language) {
    return collectionTraits.indexAccessDescription(ref, fileType, language);
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
    genericByPrefix.clear();
  }

  /**
   * Каноническая ссылка на тип с этим именем — та, что зарегистрирована в реестре.
   * <p>
   * Структурная специализация шаблона ({@code СправочникСсылка.<Имя>} →
   * {@code СправочникСсылка.Справочник1}) сохраняет вид шаблона, а шаблоны приходят из
   * синтакс-помощника платформенными. Без приведения за одним именем оказываются две
   * ссылки разного вида, и объединение наборов их не схлопывает.
   *
   * @param ref ссылка после структурной специализации.
   * @return зарегистрированная ссылка с этим именем; исходная, если такого имени в
   *     реестре нет.
   */
  TypeRef canonicalRef(TypeRef ref) {
    return resolve(ref.qualifiedName()).orElse(ref);
  }

  private static Type hydrate(TypeRef ref) {
    return switch (ref.kind()) {
      case PRIMITIVE -> new PrimitiveType(ref);
      case PLATFORM -> new PlatformType(ref);
      case CONFIGURATION -> new ConfigurationType(ref);
      case USER -> new UserType(ref, new WeakReference<>(null));
      case ANY -> AnyType.INSTANCE;
      case UNKNOWN -> UnknownType.INSTANCE;
    };
  }
}
