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
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.AnyType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.ConfigurationType;
import com.github._1c_syntax.bsl.languageserver.types.model.LanguageScope;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberSource;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformType;
import com.github._1c_syntax.bsl.languageserver.types.model.PrimitiveType;
import com.github._1c_syntax.bsl.languageserver.types.model.Type;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.UnknownType;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class TypeRegistry {

  private final List<PlatformTypesProvider> platformProviders;
  /**
   * Параллельный Symbol-фронт: глобальные свойства и прочие глобальные символы.
   * Опционален, чтобы существующие unit-тесты, собирающие TypeRegistry вручную
   * без Spring, продолжали работать.
   */
  @Autowired(required = false)
  private GlobalScopeProvider globalScopeProvider;

  /** Интернированные TypeRef по канонической форме (kind + lowercased name). */
  private final Map<TypeRef, TypeRef> internedRefs = new ConcurrentHashMap<>();
  /** Алиасы (включая Ru/En) → канонический TypeRef. Ключ — lowercased имя. */
  private final Map<String, TypeRef> aliasIndex = new ConcurrentHashMap<>();
  /** Тип ↔ объект Type (hydrated). */
  private final Map<TypeRef, Type> types = new ConcurrentHashMap<>();
  /** Тип ↔ список источников членов (один тип может расширяться многими источниками). */
  private final Map<TypeRef, List<ScopedMemberSource>> memberSources = new ConcurrentHashMap<>();
  /** Тип ↔ языковой скоуп (BSL/OS/BOTH). Отсутствие записи трактуется как BOTH. */
  private final Map<TypeRef, LanguageScope> typeScopes = new ConcurrentHashMap<>();
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
   * Индекс read-only свойств: тип → набор имён свойств (lowercased), у
   * которых {@link AccessMode#READ}. Заполняется при регистрации
   * {@link TypePackProvider.TypeDecl} провайдерами платформенных типов
   * (bsl-context / JSON-fallback). Используется как дешёвый источник истины
   * для диагностики присваивания в read-only свойство, без обращения к
   * {@link MemberSource} лямбдам и без захвата RWLock на {@code ServerContext}.
   * <p>
   * Конфигурационные типы (MD-объекты) сюда не попадают: у них нет
   * {@code accessMode} в метаданных, и они регистрируются через
   * {@code registerMemberSource} лениво.
   */
  private final Map<TypeRef, Set<String>> readOnlyMembersByType = new ConcurrentHashMap<>();
  /**
   * Дешёвый набор всех имён read-only свойств (lowercased) — для быстрого
   * pre-filter'а в диагностике: если имя присваиваемого свойства не входит
   * в этот набор, дальше идти не нужно. Заполняется параллельно с
   * {@link #readOnlyMembersByType}.
   */
  private final Set<String> readOnlyMemberNames = ConcurrentHashMap.newKeySet();
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
   */
  private final Map<TypeRef, BilingualString> typeDescriptionsBilingual = new ConcurrentHashMap<>();

  /** Источник членов вместе с его языковым скоупом. */
  private record ScopedMemberSource(MemberSource source, LanguageScope scope) {
  }

  /** Описание типа вместе с его языковым скоупом. */
  private record ScopedDescription(String text, LanguageScope scope) {
  }

  /** Набор конструкторов вместе с его языковым скоупом. */
  private record ScopedConstructors(
    List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> list,
    LanguageScope scope
  ) {
  }

  /** Динамический источник конструкторов вместе с его языковым скоупом. */
  private record ScopedConstructorSource(
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> supplier,
    LanguageScope scope
  ) {
  }

  @PostConstruct
  void bootstrap() {
    if (platformProviders == null) {
      return;
    }
    for (var provider : platformProviders) {
      var scope = provider.getLanguageScope();
      for (var decl : provider.getTypes()) {
        registerPack(decl, scope);
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
   * Найти тип по имени с фильтрацией по типу файла. Тип будет возвращён только
   * если его {@link LanguageScope} совместим с {@code fileType}.
   * Если {@code fileType == null} — фильтрация не применяется.
   */
  public Optional<TypeRef> resolve(String name, FileType fileType) {
    return resolve(name).filter(ref -> getLanguageScope(ref).matches(fileType));
  }

  /**
   * Языковой скоуп типа. По умолчанию (неизвестный тип / без записи) — {@link LanguageScope#BOTH}.
   */
  public LanguageScope getLanguageScope(TypeRef ref) {
    return typeScopes.getOrDefault(ref, LanguageScope.BOTH);
  }

  /**
   * Установить/повысить языковой скоуп типа. При наличии существующей записи
   * новый скоуп мержится: при различии повышается до {@link LanguageScope#BOTH}.
   */
  public void setLanguageScope(TypeRef ref, LanguageScope scope) {
    if (ref == null || scope == null) {
      return;
    }
    typeScopes.merge(ref, scope, LanguageScope::merge);
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
  public Collection<MemberDescriptor> getMembers(TypeRef ref, com.github._1c_syntax.bsl.languageserver.context.FileType fileType) {
    var sources = memberSources.get(ref);
    if ((sources == null || sources.isEmpty()) && ref != null) {
      var canonical = aliasIndex.get(ref.qualifiedName().toLowerCase(Locale.ROOT));
      if (canonical != null && !canonical.equals(ref)) {
        sources = memberSources.get(canonical);
      }
    }
    if (sources == null || sources.isEmpty()) {
      return Collections.emptyList();
    }
    var byName = new LinkedHashMap<String, MemberDescriptor>();
    for (var scoped : sources) {
      if (fileType != null && scoped.scope() != null && !scoped.scope().matches(fileType)) {
        continue;
      }
      for (var member : scoped.source().getMembers()) {
        byName.putIfAbsent(member.name().toLowerCase(Locale.ROOT), member);
      }
    }
    return new ArrayList<>(byName.values());
  }

  /**
   * Добавить дополнительный источник членов к существующему типу.
   * Позволяет, например, {@code ManagerModule.bsl} расширять платформенный
   * {@code СправочникМенеджер.X}.
   */
  public void registerMemberSource(TypeRef ref, MemberSource source) {
    registerMemberSource(ref, source, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #registerMemberSource(TypeRef, MemberSource)}, но дополнительно
   * привязывает источник к языковому скоупу. {@code null} трактуется как
   * {@link LanguageScope#BOTH}.
   */
  public void registerMemberSource(TypeRef ref, MemberSource source, LanguageScope scope) {
    var effective = scope == null ? LanguageScope.BOTH : scope;
    memberSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedMemberSource(source, effective));
  }

  /**
   * Зарегистрировать специализацию generic-типа по имени специализированного
   * типа. Если такого TypeRef ещё нет — интернируется с {@link TypeKind} как
   * у generic'а (чтобы инференсер и регистрация работали с одной и той же
   * парой {@code (kind, name)}). Если есть — используется существующий.
   * После этого делегирует в
   * {@link #registerSpecialization(TypeRef, TypeRef, Map, LanguageScope)}.
   *
   * @param specializedName qualifiedName целевого типа (например,
   *                        {@code "СправочникВыборка.МойСправочник"})
   * @param genericRef      generic-тип-источник
   * @param bindings        подстановки placeholder → имя заменителя
   * @param scope           языковой скоуп источника; {@code null} — {@link LanguageScope#BSL}
   * @return интернированный {@link TypeRef} специализированного типа
   */
  public TypeRef registerSpecialization(String specializedName, TypeRef genericRef,
                                        Map<String, String> bindings, LanguageScope scope) {
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
      setLanguageScope(specializedRef, scope == null ? LanguageScope.BSL : scope);
    }
    registerSpecialization(specializedRef, genericRef, bindings, scope);
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
   * Также индексируются read-only members специализированного типа
   * (см. {@link #indexReadOnlyMembers}).
   *
   * @param specializedRef  целевой TypeRef, который должен «наследовать»
   *                        members generic-типа
   * @param genericRef      generic-тип-источник (его qualifiedName обычно
   *                        содержит placeholder'ы {@code <X>})
   * @param bindings        placeholder → имя заменителя (например,
   *                        {@code "Имя справочника"} → {@code "МойСправочник"})
   * @param scope           языковой скоуп источника; {@code null} — {@link LanguageScope#BSL}
   */
  public void registerSpecialization(TypeRef specializedRef, TypeRef genericRef,
                                     Map<String, String> bindings, LanguageScope scope) {
    if (specializedRef == null || genericRef == null) {
      return;
    }
    var safeBindings = bindings == null ? Map.<String, String>of() : Map.copyOf(bindings);
    var effectiveScope = scope == null ? LanguageScope.BSL : scope;
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
        if (specialized.metadata().accessMode() == AccessMode.READ) {
          var lc = specialized.name().toLowerCase(Locale.ROOT);
          readOnlyMemberNames.add(lc);
          readOnlyMembersByType
            .computeIfAbsent(specializedRef, k -> ConcurrentHashMap.newKeySet())
            .add(lc);
        }
      }
      return result;
    };
    registerMemberSource(specializedRef, source, effectiveScope);
  }

  /**
   * Зарегистрировать пользовательский тип (OneScript-класс, общий модуль и т.п.).
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration) {
    return registerUserType(qualifiedName, declaration, LanguageScope.BOTH);
  }

  /**
   * Зарегистрировать пользовательский тип с указанием языкового скоупа.
   */
  public TypeRef registerUserType(String qualifiedName, SourceDefinedSymbol declaration, LanguageScope scope) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.put(ref, new UserType(ref, declaration));
    addAlias(qualifiedName, ref);
    setLanguageScope(ref, scope == null ? LanguageScope.BOTH : scope);
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
    setLanguageScope(ref, LanguageScope.BSL);
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
   * Зарегистрировать тип как глобальное свойство (его имя становится
   * ресивером dot-выражения: {@code Документы.Контрагенты},
   * {@code КодировкаТекста.UTF8}, {@code ОбщегоНазначения.Метод()}).
   * Регистрация идёт в {@link GlobalScopeProvider} — единая точка входа
   * для глобальных имён.
   */
  public void registerAsGlobalProperty(TypeRef ref) {
    registerAsGlobalProperty(ref, LanguageScope.BOTH, SyntheticKind.PLATFORM_GLOBAL_PROPERTY);
  }

  /**
   * То же, что {@link #registerAsGlobalProperty(TypeRef)}, но дополнительно
   * пробрасывает скоуп в {@link GlobalScopeProvider}.
   */
  public void registerAsGlobalProperty(TypeRef ref, LanguageScope scope) {
    registerAsGlobalProperty(ref, scope, SyntheticKind.PLATFORM_GLOBAL_PROPERTY);
  }

  /**
   * Та же регистрация, но с явным {@link SyntheticKind} — используется
   * при публикации системных перечислений ({@link SyntheticKind#PLATFORM_GLOBAL_ENUM}),
   * чтобы отличать их от обычных глобальных свойств.
   */
  public void registerAsGlobalProperty(TypeRef ref, LanguageScope scope, SyntheticKind syntheticKind) {
    registerAsGlobalProperty(ref, scope, syntheticKind, () -> null);
  }

  /**
   * То же + lazy-провайдер source-defined-символа (для общих модулей).
   * См. {@link GlobalScopeProvider#registerGlobalProperty(TypeRef, Collection, LanguageScope, String, SyntheticKind, Supplier)}.
   */
  public void registerAsGlobalProperty(TypeRef ref, LanguageScope scope, SyntheticKind syntheticKind,
                                       Supplier<Symbol> sourceSymbol) {
    if (globalScopeProvider == null) {
      return;
    }
    var names = new java.util.LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerGlobalProperty(ref, names, scope, getDescription(ref, fileTypeOf(scope)),
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
    if (ref == null) {
      return "";
    }
    var list = descriptions.get(ref);
    if (list == null || list.isEmpty()) {
      return "";
    }
    for (var sd : list) {
      if (fileType == null || sd.scope() == null || sd.scope().matches(fileType)) {
        return sd.text();
      }
    }
    return "";
  }

  /**
   * Зарегистрировать описание типа со скоупом. Допускается несколько описаний
   * на один TypeRef с разными скоупами (BSL/OS) — фильтрация при чтении.
   */
  public void registerDescription(TypeRef ref, String text, LanguageScope scope) {
    if (ref == null || text == null || text.isBlank()) {
      return;
    }
    var effective = scope == null ? LanguageScope.BOTH : scope;
    descriptions.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedDescription(text, effective));
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
    if (ref == null) {
      return List.of();
    }
    var result = new ArrayList<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>();
    var fromPack = constructors.get(ref);
    if (fromPack != null) {
      for (var scoped : fromPack) {
        if (fileType != null && scoped.scope() != null && !scoped.scope().matches(fileType)) {
          continue;
        }
        result.addAll(scoped.list());
      }
    }
    var sources = constructorSources.get(ref);
    if (sources != null) {
      for (var scoped : sources) {
        if (fileType != null && scoped.scope() != null && !scoped.scope().matches(fileType)) {
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
   * Зарегистрировать конструкторы типа со скоупом. Поддерживается несколько
   * вызовов на один TypeRef с разными скоупами (BSL/OS).
   */
  public void registerConstructors(
    TypeRef ref,
    List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor> ctors,
    LanguageScope scope
  ) {
    if (ref == null || ctors == null || ctors.isEmpty()) {
      return;
    }
    var effective = scope == null ? LanguageScope.BOTH : scope;
    constructors.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedConstructors(List.copyOf(ctors), effective));
  }

  /**
   * Зарегистрировать динамический источник конструкторов для типа (например,
   * {@code ПриСозданииОбъекта} OneScript-класса из SymbolTree).
   * Источник вызывается каждый раз при запросе {@link #getConstructors(TypeRef)},
   * что обеспечивает hot-reload без ручной инвалидации.
   */
  public void registerConstructorSource(
    TypeRef ref,
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> source
  ) {
    registerConstructorSource(ref, source, LanguageScope.BOTH);
  }

  /**
   * То же, что {@link #registerConstructorSource(TypeRef, java.util.function.Supplier)},
   * но привязывает источник к языковому скоупу. {@code null} ⇒ {@link LanguageScope#BOTH}.
   */
  public void registerConstructorSource(
    TypeRef ref,
    java.util.function.Supplier<List<com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor>> source,
    LanguageScope scope
  ) {
    if (ref == null || source == null) {
      return;
    }
    var effective = scope == null ? LanguageScope.BOTH : scope;
    constructorSources.computeIfAbsent(ref, k -> Collections.synchronizedList(new ArrayList<>()))
      .add(new ScopedConstructorSource(source, effective));
  }

  /**
   * Зарегистрировать тип как платформенный класс с конструктором. Имя
   * становится доступным для completion после {@code Новый} (через
   * {@link GlobalScopeProvider#getClasses}) и резолвится в {@link SyntheticSymbol}
   * с ролью {@code TYPE_NAME} для hover/findGlobal. Вызывается автоматически
   * из {@link #registerPack} при непустых {@code constructors}.
   */
  private void registerAsPlatformClass(TypeRef ref, LanguageScope scope) {
    if (globalScopeProvider == null) {
      return;
    }
    var names = new java.util.LinkedHashSet<String>();
    names.add(ref.qualifiedName());
    aliasIndex.forEach((alias, target) -> {
      if (target.equals(ref)) {
        names.add(alias);
      }
    });
    globalScopeProvider.registerPlatformClass(ref, names, scope,
      getDescription(ref, fileTypeOf(scope)));
  }

  private static FileType fileTypeOf(LanguageScope scope) {
    if (scope == null || scope == LanguageScope.BOTH) {
      return null;
    }
    return scope == LanguageScope.BSL ? FileType.BSL : FileType.OS;
  }

  /**
   * Удалить пользовательский тип по qualifiedName (например, при закрытии
   * соответствующего документа).
   */
  public void unregisterUserType(String qualifiedName) {
    var ref = intern(TypeKind.USER, qualifiedName);
    types.remove(ref);
    memberSources.remove(ref);
    typeScopes.remove(ref);
    aliasIndex.remove(qualifiedName.toLowerCase(Locale.ROOT));
  }

  private void registerPack(TypePackProvider.TypeDecl decl) {
    registerPack(decl, LanguageScope.BOTH);
  }

  private void registerPack(TypePackProvider.TypeDecl decl, LanguageScope scope) {
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
      registerDescription(ref, decl.description().primary(), scope);
      if (!decl.description().isEmpty()) {
        typeDescriptionsBilingual.putIfAbsent(ref, decl.description());
      }
    }
    if (decl.constructors() != null && !decl.constructors().isEmpty()) {
      registerConstructors(ref, decl.constructors(), scope);
      registerAsPlatformClass(ref, scope);
    }
    if (!decl.members().isEmpty()) {
      registerMemberSource(ref, decl::members, scope);
      indexReadOnlyMembers(ref, decl.members());
    }
    if (decl.exposedAsGlobal()) {
      var syntheticKind = decl.isEnum()
        ? SyntheticKind.PLATFORM_GLOBAL_ENUM
        : SyntheticKind.PLATFORM_GLOBAL_PROPERTY;
      registerAsGlobalProperty(ref, scope, syntheticKind);
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
    setLanguageScope(ref, scope == null ? LanguageScope.BOTH : scope);
  }

  /**
   * Возвращает имя типа для отображения в указанной локали LS. Если в
   * реестре есть двуязычное имя ({@link TypePackProvider.TypeDecl#name()}),
   * выбирает ru или en по {@code language}; иначе — {@code ref.qualifiedName()}.
   */
  public String displayName(TypeRef ref, Language language) {
    if (ref == null) {
      return "";
    }
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

  /** Описание типа в указанной локали (для hover'а класса/конструктора). */
  public String getDescription(TypeRef ref, Language language) {
    var bn = typeDescriptionsBilingual.get(ref);
    if (bn != null && !bn.isEmpty()) {
      return bn.forLanguage(language);
    }
    return getDescription(ref);
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
    if (ref == null) {
      return List.of();
    }
    return typeParameters.getOrDefault(ref, List.of());
  }

  /**
   * @return {@code true}, если в реестре зарегистрирован хотя бы один член
   *         с {@link AccessMode#READ}. Дешёвая проверка для early-exit'а в
   *         диагностиках, не имеющих смысла без read-only-данных
   *         (например, для JSON-fallback без accessMode).
   */
  public boolean hasAnyReadOnlyMember() {
    return !readOnlyMemberNames.isEmpty();
  }

  /**
   * Дешёвая проверка имени присваиваемого свойства: входит ли оно в число
   * имён, у которых ХОТЯ БЫ НА ОДНОМ платформенном типе режим доступа =
   * {@link AccessMode#READ}. Используется как pre-filter — отрицательный
   * ответ гарантирует, что присваивание точно не нарушает read-only.
   */
  public boolean isReadOnlyMemberName(String name) {
    return name != null && readOnlyMemberNames.contains(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Точная проверка: помечен ли member {@code name} на типе {@code typeRef}
   * как {@link AccessMode#READ}. Регистронезависимая. Возвращает
   * {@code false}, если тип не зарегистрирован или member на нём
   * не read-only.
   */
  public boolean isReadOnlyMember(TypeRef typeRef, String name) {
    if (typeRef == null || name == null) {
      return false;
    }
    var names = readOnlyMembersByType.get(typeRef);
    return names != null && names.contains(name.toLowerCase(Locale.ROOT));
  }

  /**
   * Регистрирует read-only члены типа {@code ref} в индексе. Имена
   * сохраняются в lowercased виде для регистронезависимого поиска.
   */
  private void indexReadOnlyMembers(TypeRef ref, Collection<MemberDescriptor> members) {
    for (var member : members) {
      if (member.metadata().accessMode() != AccessMode.READ) {
        continue;
      }
      var lc = member.name().toLowerCase(Locale.ROOT);
      readOnlyMemberNames.add(lc);
      readOnlyMembersByType
        .computeIfAbsent(ref, k -> ConcurrentHashMap.newKeySet())
        .add(lc);
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
