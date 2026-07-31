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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SelfMemberClassifier;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationModuleMembersProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Единая точка для consumer'ов (hover, completion, signature help) для
 * получения информации о типах. Делегирует {@link SymbolTypeIndex}/
 * {@link ExpressionTypeInferencer}/{@link TypeRegistry}.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class TypeService implements SelfMemberClassifier {

  private final TypeRegistry typeRegistry;
  private final SymbolTypeIndex symbolTypeIndex;
  private final ExpressionTypeInferencer inferencer;
  private final ReferenceResolver referenceResolver;
  private final GlobalScopeProvider globalScopeProvider;
  private final DereferenceMemberMatcher dereferenceMatcher;

  /**
   * Тип значения, на которое указывает ссылка (контейнер символ+документ+диапазон):
   * source-defined символ → его тип-значение, synthetic → valueType,
   * platform-member → returnTypes.
   *
   * @param reference ссылка на символ.
   * @return набор типов значения ссылки; {@link TypeSet#EMPTY}, если тип не определяется.
   */
  public TypeSet typesAt(Reference reference) {
    if (reference.getSourceDefinedSymbol().isPresent()) {
      // Ссылка позиционна, поэтому и ответ позиционный: тип переменной берётся в точке
      // ссылки — с учётом присваиваний и вставок, случившихся на путях к ней.
      return inferencer.inferVariableAt(reference);
    }
    if (reference.symbol() instanceof PlatformMemberSymbol platformMember) {
      var returnTypes = platformMember.getDescriptor().returnTypes();
      if (returnTypes != null && !returnTypes.isEmpty()) {
        return returnTypes;
      }
    }
    return TypeSet.EMPTY;
  }

  /**
   * Тип переменной в указанной точке её документа — позиционный ответ там, где обращения
   * к переменной в этой точке нет и {@link Reference} не существует.
   *
   * @param variable переменная.
   * @param position точка в документе переменной.
   * @return набор типов в этой точке.
   */
  public TypeSet typesOfVariableAt(VariableSymbol variable, Position position) {
    return inferencer.inferVariableAt(variable, position);
  }

  /**
   * {@inheritDoc}
   * <p>
   * Делегирует в {@link #findSelfMember} с {@link MemberKind#PROPERTY}: self-тип
   * модуля и его реквизиты берутся из реестра типов (метаданные конфигурации),
   * а не из {@code SymbolTree} самого модуля, поэтому вызов безопасен и при
   * построении дерева символов (см. {@link SelfMemberClassifier}).
   */
  @Override
  public boolean isBareSelfProperty(DocumentContext documentContext, String name) {
    return findSelfMember(documentContext, name, MemberKind.PROPERTY).isPresent();
  }

  /**
   * Член self-типа текущего модуля (реквизит/платформенный метод объекта,
   * набора записей, менеджера, общего модуля, встроенный член OScript-класса),
   * доступный внутри модуля без квалификации. Self-тип — тот же, что и у
   * dot-completion ({@link GlobalScopeProvider#moduleTypeRefByUri(java.net.URI)});
   * если он для документа не зарегистрирован — всегда empty.
   *
   * @param documentContext документ, из которого происходит обращение.
   * @param name            имя члена (без учёта регистра, ru/en-написания).
   * @param kind            требуемый вид члена: {@link MemberKind#METHOD} для
   *                        вызова {@code Имя(...)}, {@link MemberKind#PROPERTY}
   *                        для голой ссылки на имя.
   * @return найденный член; empty, если self-типа нет или член не найден.
   */
  public Optional<MemberDescriptor> findSelfMember(DocumentContext documentContext, String name, MemberKind kind) {
    return selfTypeRef(documentContext)
      .flatMap(ref -> typeRegistry.findMember(ref, kind, name, documentContext.getFileType()));
  }

  /**
   * Self-тип модуля документа. Быстрый путь — кэш {@code moduleTypeRefByUri}; если он ещё
   * пуст (его наполняет {@code ConfigurationModuleMembersProvider.register} на
   * {@code DocumentContextContentChangedEvent} — уже ПОСЛЕ построения дерева, оно
   * строится внутри {@code DocumentContext.rebuild}, а событие AOP публикует после
   * возврата) — резолвим тип конфигурационного модуля <b>из метаданных напрямую</b>.
   * <p>
   * Без этого self-члены присваиваемых реквизитов ({@code Реквизит = …}) на первой
   * сборке дерева не распознавались бы, и {@code VariableSymbolComputer} заводил бы на
   * них фантомную DYNAMIC-переменную, затеняющую реквизит до первой правки файла.
   * Тот же приём, что у {@code EventHandlerResolver.resolveOwnerType}.
   */
  private Optional<TypeRef> selfTypeRef(DocumentContext documentContext) {
    var cached = globalScopeProvider.moduleTypeRefByUri(documentContext.getUri());
    if (cached.isPresent()) {
      return cached;
    }
    return documentContext.getMdObject()
      .flatMap(md -> ConfigurationModuleMembersProvider.selfTypeQualifiedName(documentContext.getModuleType(), md))
      .flatMap(typeRegistry::resolve);
  }

  /**
   * Тип <b>всего выражения</b>, охватывающего позицию (наименьшее объемлющее
   * {@code RULE_expression}), вычисленный инференсером по AST. Позиционно
   * <b>не</b>чувствителен <i>внутри</i> выражения: для {@code Г + А.Б.В} вернёт тип
   * суммы независимо от того, на каком сегменте курсор. Используется для вывода
   * типа выражения-аргумента вызова целиком.
   *
   * @param documentContext контекст документа.
   * @param position позиция внутри выражения.
   * @return тип охватывающего выражения; {@link TypeSet#EMPTY}, если не выводится.
   */
  public TypeSet expressionTypesAt(DocumentContext documentContext, Position position) {
    return ExpressionAtPosition.findExpressionTree(documentContext, position)
      .map(expression -> inferencer.infer(expression, documentContext))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Типы параметров метода (по порядку) — для signature help.
   *
   * @param method метод, чьи типы параметров нужны.
   * @return список наборов типов по параметрам, в порядке объявления.
   */
   public List<TypeSet> getParameterTypes(MethodSymbol method) {
     var owner = method.getOwner();
     return method.getParameters().stream()
       .map(parameter -> symbolTypeIndex.getDeclaredParameterTypes(parameter, owner))
       .toList();
   }

  /**
   * Объявленный тип возвращаемого значения метода — для signature help/hover.
   *
   * @param method метод.
   * @return набор типов возвращаемого значения; {@link TypeSet#EMPTY}, если не объявлен.
   */
  public TypeSet getDeclaredReturnTypes(MethodSymbol method) {
    return symbolTypeIndex.getDeclaredReturnTypes(method);
  }

  /**
   * Члены типа (методы + свойства) — для completion/hover/inferencer. Фильтрует:
   * <ul>
   *   <li>по языковому скоупу источника (источники, несовместимые с {@code fileType},
   *       пропускаются);</li>
   *   <li>generic-шаблоны ({@link MemberDescriptor#generic()} — placeholder'ы вида
   *       {@code <Имя картинки>}, {@code <Имя документа>}) — они хранятся в
   *       {@link TypeRegistry} как материал для materialization
   *       ({@code registerMemberExpansion}/{@code registerSpecialization}), но
   *       внешним потребителям бесполезны.</li>
   * </ul>
   *
   * @param typeRef тип, чьи члены нужны.
   * @param fileType тип файла-потребителя (BSL/OS) для фильтрации по скоупу.
   * @return члены типа без generic-шаблонов.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef typeRef, FileType fileType) {
    var raw = typeRegistry.getMembers(typeRef, fileType);
    if (raw.isEmpty()) {
      return raw;
    }
    var filtered = new ArrayList<MemberDescriptor>(raw.size());
    for (var member : raw) {
      if (!member.generic()) {
        filtered.add(member);
      }
    }
    return filtered;
  }

  /**
   * Члены типа, применимые к указанной локали скрипта — для автодополнения.
   * <p>
   * В отличие от {@link #getMembers(TypeRef, FileType)} (служит резолву имён и
   * выводу типов — там матч идёт по обоим написаниям), здесь член отсеивается,
   * если у него нет написания в запрошенной локали
   * ({@link MemberDescriptor#appliesTo(Language)}). Так из ru-автодополнения
   * уходят, например, англоязычные {@code [DeprecatedName]}-алиасы OneScript
   * без русской пары ({@code HTTPЗапрос.GetBodyAsBinary}). Отбор зависит только
   * от имени члена — никаких проверок устаревания и прочих свойств. Резолв
   * таких имён для диагностик сохраняется: {@link #getMembers(TypeRef, FileType)}
   * по-прежнему отдаёт их.
   *
   * @param typeRef  тип, чьи члены нужны.
   * @param fileType тип файла-потребителя (BSL/OS).
   * @param language локаль скрипта (ru/en) для отбора написаний.
   * @return члены, применимые к данной локали.
   */
  public Collection<MemberDescriptor> getMembers(TypeRef typeRef, FileType fileType, Language language) {
    var members = getMembers(typeRef, fileType);
    if (members.isEmpty()) {
      return members;
    }
    var result = new ArrayList<MemberDescriptor>(members.size());
    for (var member : members) {
      if (member.appliesTo(language)) {
        result.add(member);
      }
    }
    return result;
  }

  /**
   * Описание типа (текст для hover). Фильтрует описания по скоупу языка (BSL/OS) —
   * когда один и тот же {@link TypeRef} имеет разные описания в BSL и OS.
   *
   * @param typeRef тип.
   * @param fileType тип файла-потребителя (BSL/OS).
   * @return описание; пустая строка, если описание отсутствует.
   */
  public String getDescription(TypeRef typeRef, FileType fileType) {
    return typeRegistry.getDescription(typeRef, fileType);
  }

  /**
   * Описание типа в указанной локали LS с фильтрацией по типу файла (BSL/OS) —
   * когда один и тот же {@link TypeRef} имеет разные описания в BSL и OS.
   *
   * @param typeRef тип.
   * @param language локаль интерфейса LS.
   * @param fileType тип файла-потребителя.
   * @return описание; пустая строка, если описание отсутствует.
   */
  public String getDescription(TypeRef typeRef, Language language, FileType fileType) {
    return typeRegistry.getDescription(typeRef, language, fileType);
  }

  /**
   * «Страничные» метаданные самого типа из синтакс-помощника: доступность по
   * видам клиента, версии появления/устаревания с рекомендуемыми заменами,
   * «Замечание», «Пример», «См. также». Метаданные членов типа живут на
   * {@link MemberDescriptor#metadata()}.
   *
   * @param typeRef  тип.
   * @param fileType тип файла-потребителя (BSL/OS).
   * @return метаданные; {@link PlatformMetadata#EMPTY}, если источник их не дал.
   */
  public PlatformMetadata getTypeMetadata(TypeRef typeRef, FileType fileType) {
    return typeRegistry.getTypeMetadata(typeRef, fileType);
  }

  /**
   * Имя типа для отображения в указанной локали LS (ru/en, с fallback).
   *
   * @param typeRef тип.
   * @param language локаль интерфейса LS.
   * @return отображаемое имя типа.
   */
  public String displayName(TypeRef typeRef, Language language) {
    return typeRegistry.displayName(typeRef, language);
  }

  /**
   * Символ-источник, объявляющий тип (цель go-to-definition и hover), если такой
   * есть в исходниках рабочей области.
   * <p>
   * Покрывает типы, у которых есть объявляющий их модуль/класс:
   * <ul>
   *   <li>{@link TypeKind#USER} — пользовательские типы OneScript (классы и модули):
   *       объявление хранится в самом {@link UserType#getDeclaration()}. Для класса
   *       с конструктором предпочитается символ конструктора
   *       ({@code ПриСозданииОбъекта}) — см. ниже;</li>
   *   <li>{@link TypeKind#CONFIGURATION} — общие модули и модули менеджеров объектов
   *       конфигурации: документ-модуль находится обратным индексом
   *       {@link GlobalScopeProvider#uriByModuleTypeRef(TypeRef)}, а его символ —
   *       {@code getSymbolTree().getModule()}.</li>
   * </ul>
   * Для платформенных/примитивных типов ({@link TypeKind#PLATFORM},
   * {@link TypeKind#PRIMITIVE}) и служебных {@code Unknown}/{@code Any}
   * объявляющего исходник-символа нет — возвращается {@code empty}.
   * <p>
   * <b>Почему для OneScript-класса предпочтён конструктор, а не модуль.</b> У языка
   * нет отдельного оператора «объявление класса»: класс — это весь {@code .os}-файл,
   * а его {@link ModuleSymbol#getSelectionRange()} указывает на первый токен файла,
   * которым часто оказывается {@code Перем} (объявление поля). Потребитель —
   * {@code InlayHintLabelPart.location}, семантика которого в клиенте: выполнить
   * hover и go-to-definition <b>в позиции</b> ссылки. На {@code Перем} hover даёт
   * бесполезное описание ключевого слова, а go-to-definition не находит
   * source-defined символа и переход не срабатывает. Имя конструктора — это
   * source-defined метод: hover показывает его сигнатуру, а go-to-definition ведёт
   * к нему. Для класса без конструктора и не-USER типов цель — модуль/класс
   * (переход к началу файла).
   *
   * @param typeRef           тип, чей объявляющий символ нужен.
   * @param requestingContext контекст документа-потребителя; через него находится
   *                          {@code ServerContext} для резолва документа-модуля по URI.
   * @return объявляющий символ типа, либо {@code empty}, если у типа нет
   *   объявляющего символа в исходниках (платформенный/примитивный тип) или
   *   документ-модуль больше не загружен.
   */
  public Optional<SourceDefinedSymbol> definingSymbol(TypeRef typeRef, DocumentContext requestingContext) {
    return switch (typeRef.kind()) {
      case USER -> userTypeDeclaration(typeRef).map(TypeService::preferConstructor);
      case CONFIGURATION -> configurationModuleSymbol(typeRef, requestingContext);
      default -> Optional.empty();
    };
  }

  /**
   * Разрешить имя или квалифицированную ссылку в символ-определение того, что она
   * обозначает.
   * <p>
   * Ссылка не привязана к {@code См.}: это может быть метод, общий модуль, менеджер
   * справочника/документа и вообще любой тип, имеющий отражение в виде модуля.
   * <ul>
   *   <li>неквалифицированное имя ({@code Метод}) — метод того же модуля (функция
   *       или процедура);</li>
   *   <li>квалифицированная ссылка на член ({@code Модуль.Метод},
   *       {@code Справочники.X.Метод}, {@code Тип.Член}) — тем же обходом цепочки
   *       членов, что и резолв типа по строке
   *       ({@link SymbolTypeIndex#resolveReferenceSymbol}), единообразно для общих
   *       модулей, модулей менеджеров и прочих типов;</li>
   *   <li>имя типа целиком ({@code ОбщийМодуль}, {@code Справочники.Номенклатура}) —
   *       его определяющий символ ({@link #definingSymbol}).</li>
   * </ul>
   *
   * @param reference         имя/квалифицированная ссылка (например, текст
   *                          {@code См.}-ссылки без ключевого слова).
   * @param requestingContext документ, относительно которого резолвится ссылка.
   * @return символ-определение цели, либо {@code empty}.
   */
  public Optional<SourceDefinedSymbol> resolveDefinition(String reference, DocumentContext requestingContext) {
    if (reference.isBlank()) {
      return Optional.empty();
    }
    var fileType = requestingContext.getFileType();
    if (reference.indexOf('.') < 0) {
      // Метод того же модуля (функция или процедура) — по неквалифицированному имени.
      var localMethod = requestingContext.getSymbolTree()
        .getMethodSymbol(reference)
        .map(SourceDefinedSymbol.class::cast);
      if (localMethod.isPresent()) {
        return localMethod;
      }
    } else {
      // Квалифицированная ссылка на член (Модуль.Метод, Справочники.X.Метод,
      // Тип.Член) — тем же обходом цепочки, что и резолв типа по строке.
      var member = symbolTypeIndex.resolveReferenceSymbol(reference, fileType);
      if (member.isPresent()) {
        return member;
      }
    }
    // Ссылка на имя типа (общий модуль целиком, справочник и т.п.) — его
    // определяющий символ. Тот же fallback на имя типа, что и у резолва типа по строке.
    return resolve(reference, fileType).flatMap(typeRef -> definingSymbol(typeRef, requestingContext));
  }

  /**
   * Для символа-модуля OneScript-класса предпочесть символ конструктора
   * {@code ПриСозданииОбъекта}, если он есть; иначе вернуть сам символ-модуль
   * без изменений.
   */
  private static SourceDefinedSymbol preferConstructor(SourceDefinedSymbol declaration) {
    if (declaration instanceof ModuleSymbol module) {
      return module.getOwner().getSymbolTree().getConstructor()
        .map(SourceDefinedSymbol.class::cast)
        .orElse(declaration);
    }
    return declaration;
  }

  private Optional<SourceDefinedSymbol> userTypeDeclaration(TypeRef typeRef) {
    if (typeRegistry.get(typeRef) instanceof UserType userType) {
      return userType.getDeclaration();
    }
    return Optional.empty();
  }

  private Optional<SourceDefinedSymbol> configurationModuleSymbol(
    TypeRef typeRef,
    DocumentContext requestingContext
  ) {
    return globalScopeProvider.uriByModuleTypeRef(typeRef)
      .map(uri -> requestingContext.getServerContext().getDocument(uri))
      .<SourceDefinedSymbol>map(documentContext -> documentContext.getSymbolTree().getModule());
  }

  /**
   * URI исходного файла, в котором объявлен тип. Без загрузки {@code DocumentContext}'а —
   * годится для запросов без активного документа-потребителя (например, MCP-инструменты).
   *
   * @param typeRef тип.
   * @return URI модуля-объявления для {@link TypeKind#USER} и {@link TypeKind#CONFIGURATION};
   *   {@code empty} для платформенных/примитивных типов или если объявление недоступно.
   */
  public Optional<URI> definingUri(TypeRef typeRef) {
    return switch (typeRef.kind()) {
      case USER -> userTypeDeclaration(typeRef).map(symbol -> symbol.getOwner().getUri());
      case CONFIGURATION -> globalScopeProvider.uriByModuleTypeRef(typeRef);
      default -> Optional.empty();
    };
  }

  /**
   * Сигнатуры конструкторов типа (для платформенных классов из JSON-пакета).
   * Фильтрует по скоупу языка (BSL/OS).
   *
   * @param typeRef тип.
   * @param fileType тип файла-потребителя (BSL/OS).
   * @return сигнатуры конструкторов; пустой список, если конструкторов нет.
   */
  public List<SignatureDescriptor> getConstructors(
    TypeRef typeRef, FileType fileType
  ) {
    return typeRegistry.getConstructors(typeRef, fileType);
  }

  /**
   * Резолв типа по имени с учётом языкового скоупа (включая Ru/En алиасы и qualifiedName).
   *
   * @param name имя типа.
   * @param fileType тип файла-потребителя (BSL/OS).
   * @return найденный тип, либо empty.
   */
  public Optional<TypeRef> resolve(String name, FileType fileType) {
    return typeRegistry.resolve(name, fileType);
  }

  /**
   * Является ли тип перечислением (системным либо конфигурационным enum'ом)
   * в данном языке файла.
   *
   * @param typeRef тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @return {@code true}, если тип — перечисление.
   */
  public boolean isEnumType(TypeRef typeRef, FileType fileType) {
    return typeRegistry.isEnumType(typeRef, fileType);
  }

  /**
   * Похоже ли имя члена на версионируемое — дешёвый префиксный фильтр перед
   * полным резолвом члена (например, для отбора кандидатов на проверку
   * доступности по версии платформы).
   *
   * @param name имя члена.
   * @return {@code true}, если имя имеет версионный вид.
   */
  public boolean isVersionedMemberName(String name) {
    return typeRegistry.isVersionedMemberName(name);
  }

  /**
   * Есть ли среди зарегистрированных типов хотя бы один член, помеченный
   * «только для чтения» — глобальный гейт для диагностики присваивания.
   *
   * @return {@code true}, если хотя бы один read-only член известен.
   */
  public boolean hasAnyReadOnlyMember() {
    return typeRegistry.hasAnyReadOnlyMember();
  }

  /**
   * Поддерживает ли тип обход {@code Для Каждого} в данном языке файла.
   *
   * @param typeRef тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @return {@code true}, если тип итерируем.
   */
  public boolean supportsForEach(TypeRef typeRef, FileType fileType) {
    return typeRegistry.supportsForEach(typeRef, fileType);
  }

  /**
   * Поддерживает ли тип индексный доступ {@code [...]} в данном языке файла.
   *
   * @param typeRef тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @return {@code true}, если тип индексируем.
   */
  public boolean supportsIndexAccess(TypeRef typeRef, FileType fileType) {
    return typeRegistry.supportsIndexAccess(typeRef, fileType);
  }

  /**
   * Описание обхода {@code Для Каждого} для типа в указанной локали LS,
   * в данном языке файла.
   *
   * @param typeRef тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @param language локаль интерфейса LS.
   * @return описание; пустая строка, если его нет.
   */
  public String getForEachDescription(TypeRef typeRef, FileType fileType, Language language) {
    return typeRegistry.getForEachDescription(typeRef, fileType, language);
  }

  /**
   * Описание индексного доступа {@code [...]} для типа в указанной локали LS,
   * в данном языке файла.
   *
   * @param typeRef тип.
   * @param fileType язык файла-потребителя (BSL/OS).
   * @param language локаль интерфейса LS.
   * @return описание; пустая строка, если его нет.
   */
  public String getIndexAccessDescription(TypeRef typeRef, FileType fileType, Language language) {
    return typeRegistry.getIndexAccessDescription(typeRef, fileType, language);
  }

  /**
   * Найти член типа в позиции курсора (для hover/go-to-member по
   * выражениям без source-defined символа: цепочки accessor'ов,
   * платформенные типы, library-модули).
   *
   * @param documentContext контекст документа.
   * @param position позиция в документе.
   * @return описание найденного члена + тип-владелец и диапазон под курсором; empty, если члена нет.
   */
  public Optional<TypedMember> memberAt(DocumentContext documentContext, Position position) {
    return membersAt(documentContext, position).stream().findFirst();
  }

  /**
   * Вариант {@link #memberAt(DocumentContext, Position)} от уже известного
   * терминала — без спуска по AST к позиции (см. {@link #membersAt(DocumentContext, TerminalNode)}).
   */
  public Optional<TypedMember> memberAt(DocumentContext documentContext, TerminalNode terminal) {
    return membersAt(documentContext, terminal).stream().findFirst();
  }

  /**
   * То же, что {@link #memberAt(DocumentContext, Position)}, но возвращает
   * <b>все</b> члены-кандидаты, когда тип ресивера выведен как union из
   * нескольких типов (например, переменная присваивается значениями разных
   * типов в разных ветках). Потребителям, проверяющим метаданные члена
   * (устаревание, доступность по версии платформы), важен каждый возможный
   * тип-владелец: вызов небезопасен, если хотя бы один из них делает член
   * устаревшим/недоступным. Для глобальных функций/свойств список из одного
   * элемента. Порядок совпадает с {@link #memberAt} (первый элемент тот же).
   *
   * @param documentContext контекст документа.
   * @param position позиция в документе.
   * @return все члены-кандидаты в позиции; пустой список, если члена нет.
   */
  public List<TypedMember> membersAt(DocumentContext documentContext, Position position) {
    var terminal = identifierTerminalAt(documentContext, position).orElse(null);
    if (terminal == null) {
      return List.of();
    }
    return membersAt(documentContext, terminal);
  }

  /**
   * То же, что {@link #membersAt(DocumentContext, Position)}, но для случая,
   * когда терминал-идентификатор уже известен вызывающему (например, получен
   * при обходе AST). Избавляет от повторного спуска по дереву ради поиска
   * терминала по позиции — на больших модулях это доминирующая стоимость.
   * Не-идентификаторный терминал даёт пустой список (как и поиск по позиции).
   *
   * @param documentContext контекст документа.
   * @param terminal терминал-идентификатор члена/имени.
   * @return все члены-кандидаты для терминала; пустой список, если члена нет.
   */
  public List<TypedMember> membersAt(DocumentContext documentContext, TerminalNode terminal) {
    if (terminal.getSymbol().getType() != BSLParser.IDENTIFIER) {
      return List.of();
    }
    // Случай глобальной функции / свойства / library-модуля (например,
    // КодировкаТекста, ФС) — резолвится напрямую, без инференса ресивера.
    if (!isAccessorIdentifier(terminal)) {
      var bare = resolveBareName(terminal, documentContext);
      if (bare.isPresent()) {
        return List.of(bare.get());
      }
    }
    return dereferenceMatcher.matchAt(terminal, documentContext);
  }

  /**
   * Терминал-идентификатор в позиции курсора, либо empty, если AST недоступен
   * или под курсором не идентификатор.
   */
  private static Optional<TerminalNode> identifierTerminalAt(DocumentContext documentContext, Position position) {
    return Trees.findTerminalNodeContainsPosition(documentContext.getAst(), position)
      .filter(t -> t.getSymbol().getType() == BSLParser.IDENTIFIER);
  }

  /**
   * Резолв голого имени (не аксессора): глобальная функция (владелец = null),
   * глобальное свойство / library-модуль, либо неквалифицированный self-член
   * текущего модуля. Empty, если имя так не резолвится.
   */
  private Optional<TypedMember> resolveBareName(TerminalNode terminal, DocumentContext documentContext) {
    var bareName = terminal.getText();
    var fileType = documentContext.getFileType();
    // Глобальная функция.
    var globalFn = globalScopeProvider.globalFunction(bareName, fileType);
    if (globalFn.isPresent()) {
      return Optional.of(new TypedMember(null, globalFn.get(), Ranges.create(terminal), -1));
    }

    // Глобальное свойство (перечисление/менеджер коллекции/модуль); имена типов
    // для `Новый` (TYPE_NAME) глобальными свойствами не являются.
    var globalProp = globalScopeProvider.globalProperty(bareName, fileType)
      .map(member -> member.returnTypes().refs().stream()
        .filter(r -> !r.equals(TypeRef.UNKNOWN)).findFirst().orElse(TypeRef.UNKNOWN))
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN))
      .map((TypeRef ref) -> {
        var desc = typeRegistry.getDescription(ref, fileType);
        // owner == null: глобальное свойство, а не член ресивера (контракт TypedMember).
        return new TypedMember(null,
          MemberDescriptor.property(ref.qualifiedName(), ref, desc),
          Ranges.create(terminal));
      });
    if (globalProp.isPresent()) {
      return globalProp;
    }

    return resolveSelfMember(terminal, bareName, documentContext);
  }

  /**
   * Неквалифицированное обращение к члену self-типа текущего модуля (реквизит/
   * платформенный метод объекта/менеджера/набора записей/общего модуля,
   * встроенный член OScript-класса) — тот же self-тип, что и у dot-completion
   * ({@link GlobalScopeProvider#moduleTypeRefByUri(java.net.URI)}). Вид члена определяется
   * контекстом обращения: вызов ({@code Имя(...)}) — {@link MemberKind#METHOD},
   * иначе — {@link MemberKind#PROPERTY} (голый идентификатор без вызова не
   * может ссылаться на метод).
   */
  private Optional<TypedMember> resolveSelfMember(
    TerminalNode terminal, String name, DocumentContext documentContext
  ) {
    // Объявленная одноимённая переменная/параметр в области видимости перекрывает
    // self-член: голое имя тогда ссылается на переменную (её типизирует
    // reference-путь), а не на реквизит/метод объекта. Проверка живёт здесь, в
    // резолве, а не у каждого потребителя (диагностика, hover) — единый источник
    // правила затенения на этом пути.
    if (isShadowedByLocalVariable(terminal, documentContext, name)) {
      return Optional.empty();
    }
    var selfType = selfTypeRef(documentContext);
    if (selfType.isEmpty()) {
      return Optional.empty();
    }
    var expectedKind = isGlobalMethodCallName(terminal) ? MemberKind.METHOD : MemberKind.PROPERTY;
    return findSelfMember(documentContext, name, expectedKind)
      .map(member -> new TypedMember(selfType.get(), member, Ranges.create(terminal)));
  }

  /**
   * Голое имя {@code name} затенено объявленной переменной/параметром, видимым в
   * точке {@code terminal}: сперва — в охватывающем методе, иначе — на уровне
   * модуля. Такое имя ссылается на переменную, а не на self-член (тот же порядок
   * резолва имени в BSL, что и у индексатора self-членов в
   * {@code ReferenceIndexFiller}). Для настоящего self-члена пусто: голое
   * присваивание одноимённому реквизиту без {@code Перем} переменной не создаёт
   * (см. {@link SelfMemberClassifier}).
   */
  private static boolean isShadowedByLocalVariable(TerminalNode terminal, DocumentContext documentContext,
                                                   String name) {
    return terminal.getParent() instanceof ParserRuleContext ctx
      && documentContext.getSymbolTree().getVariableSymbolInScope(ctx, name).isPresent();
  }

  /** Терминал — имя безточечного вызова ({@code Имя(...)}, без ресивера). */
  private static boolean isGlobalMethodCallName(TerminalNode terminal) {
    return terminal.getParent() instanceof BSLParser.MethodNameContext methodName
      && methodName.getParent() instanceof BSLParser.GlobalMethodCallContext;
  }

  /**
   * Обращение к члену ({@code ресивер.член}), у которого тип ресивера выведен и
   * конкретен, но члена с таким именем нет ни на одном из типов-владельцев —
   * вероятная опечатка/несуществующий член. Возвращает {@code TypeSet}
   * ресивера (для подстановки имени типа в сообщение), либо empty, если в
   * позиции член найден, либо тип ресивера не выведен / содержит UNKNOWN/ANY.
   * <p>
   * Предполагает, что позиция указывает на аксессор члена — это контракт
   * вызывающего ({@code visitAccessProperty} / {@code visitMethodCall}).
   *
   * @param documentContext контекст документа.
   * @param position позиция аксессора члена.
   * @return типы ресивера (для имени типа в сообщении), либо empty, если член найден
   *     или тип ресивера не выведен / содержит UNKNOWN/ANY.
   */
  public Optional<TypeSet> unknownMemberReceiverAt(DocumentContext documentContext, Position position) {
    if (!membersAt(documentContext, position).isEmpty()) {
      return Optional.empty();
    }
    var receiver = receiverTypesAt(documentContext, position);
    return allConcrete(receiver) ? Optional.of(receiver) : Optional.empty();
  }

  /**
   * Вариант {@link #unknownMemberReceiverAt(DocumentContext, Position)} от уже
   * известного терминала-члена — без спуска по AST к позиции.
   * <p>
   * Тип ресивера выводится за один проход через dereference-инференс
   * ({@link DereferenceMemberMatcher#matchWithReceiverAt}). Если он пуст, ресивер
   * добирается по <b>завершающему идентификатору</b> ресивера (перед точкой),
   * резолвленному терминально через индекс ссылок. Тип охватывающего выражения
   * ({@code expressionTypesAt}) здесь <b>намеренно не используется</b>: он накрыл
   * бы всё {@code Ресивер.Член} и вернул тип операции (например {@code Булево} у
   * сравнения {@code Ресивер.Член = …}), из-за чего член проверялся бы у неверного
   * типа — ложное срабатывание. Такой fallback уместен лишь для висячей точки
   * ({@code Ресивер.|}) в completion, но не для диагностики с завершённым членом.
   */
  public Optional<TypeSet> unknownMemberReceiverAt(DocumentContext documentContext, TerminalNode terminal) {
    var match = dereferenceMatcher.matchWithReceiverAt(terminal, documentContext);
    if (!match.members().isEmpty()) {
      return Optional.empty();
    }
    var receiver = match.receiverTypes();
    if (receiver.isEmpty()) {
      receiver = receiverEndIdentifier(terminal)
        .flatMap(receiverEnd -> referenceResolver.findReference(documentContext.getUri(), receiverEnd))
        .map(this::typesAt)
        .orElse(TypeSet.EMPTY);
    }
    return allConcrete(receiver) ? Optional.of(receiver) : Optional.empty();
  }

  /**
   * Завершающий идентификатор ресивера для члена {@code Ресивер.Член}: последний
   * именованный сегмент перед точкой члена. По AST-цепочке доступа — предыдущий
   * {@code accessProperty} того же {@link BSLParser.ComplexIdentifierContext} (для
   * {@code А.Б.В|} это {@code Б}), либо головной {@code IDENTIFIER} для первого
   * члена ({@code А.Б|} → {@code А}). Пусто, если ресивер оканчивается вызовом или
   * индексом (не именованный символ — по индексу ссылок не резолвится).
   */
  private static Optional<TerminalNode> receiverEndIdentifier(TerminalNode member) {
    ParseTree node = member.getParent();
    while (node != null && !(node instanceof BSLParser.ModifierContext)) {
      node = node.getParent();
    }
    if (node == null || !(node.getParent() instanceof BSLParser.ComplexIdentifierContext chain)) {
      return Optional.empty();
    }
    var modifiers = chain.modifier();
    var index = modifiers.indexOf(node);
    if (index < 0) {
      return Optional.empty();
    }
    if (index == 0) {
      return Optional.ofNullable(chain.IDENTIFIER());
    }
    var previous = modifiers.get(index - 1).accessProperty();
    return previous == null ? Optional.empty() : Optional.ofNullable(previous.IDENTIFIER());
  }

  /**
   * Голый вызов {@code Имя(...)}, который не резолвится ни в глобальную функцию/
   * свойство/перечисление платформы или конфигурации, ни в source-defined символ
   * (метод/переменная текущего модуля). Вероятный вызов несуществующего метода.
   * <p>
   * Предполагает, что позиция указывает на имя голого вызова — это контракт
   * вызывающего ({@code visitGlobalMethodCall}).
   *
   * @param documentContext контекст документа.
   * @param position позиция имени голого вызова.
   * @return {@code true}, если имя не резолвится ни в глобал, ни в source-defined символ.
   */
  public boolean isUnknownGlobalAt(DocumentContext documentContext, Position position) {
    return membersAt(documentContext, position).isEmpty()
      && referenceResolver.findReference(documentContext.getUri(), position).isEmpty();
  }

  /**
   * Вариант {@link #isUnknownGlobalAt(DocumentContext, Position)} от уже
   * известного терминала-имени — без спуска по AST к позиции. Терминал
   * прокидывается и в {@link ReferenceResolver}, чтобы reference-finder'ы тоже
   * резолвили его подъёмом, а не спуском от корня.
   */
  public boolean isUnknownGlobalAt(DocumentContext documentContext, TerminalNode terminal) {
    return membersAt(documentContext, terminal).isEmpty()
      && referenceResolver.findReference(documentContext.getUri(), terminal).isEmpty();
  }

  /**
   * Тип ресивера доступа к члену в позиции — самодостаточная верхнеуровневая
   * точка входа. Пустой {@link TypeSet}, если в позиции нет доступа к члену.
   * <p>
   * Покрывает все формы без оркестрации со стороны консьюмера:
   * <ul>
   *   <li>курсор на члене ({@code Ресивер.Чле|н}, {@code Ресивер.Метод()}) —
   *       выводит тип левой части dereference (в т.ч. для именованного ресивера,
   *       т.к. инференс идентификатора сам идёт в индекс ссылок и global scope);</li>
   *   <li>висячая точка ({@code Ресивер.|}) — резолвит выражение-ресивер,
   *       заканчивающееся перед точкой.</li>
   * </ul>
   *
   * @param documentContext контекст документа.
   * @param position позиция доступа к члену (на члене или сразу за точкой).
   * @return типы ресивера; {@link TypeSet#EMPTY}, если в позиции нет доступа к члену.
   */
  public TypeSet receiverTypesAt(DocumentContext documentContext, Position position) {
    var viaMember = identifierTerminalAt(documentContext, position)
      .flatMap(terminal -> dereferenceMatcher.receiverTypesAt(documentContext, terminal))
      .orElse(TypeSet.EMPTY);
    if (!viaMember.isEmpty()) {
      return viaMember;
    }
    return receiverEndBeforeDot(documentContext, position)
      .map(receiverEnd -> receiverSegmentTypes(documentContext, receiverEnd))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Тип ресивера-сегмента, заканчивающегося в позиции {@code receiverEnd} (последний
   * символ ресивера перед висячей точкой). Позиционно-чувствительно: сначала индекс
   * ссылок (именованный символ/член в позиции, например переменная {@code Структура}
   * с её локальными полями), и лишь если пусто — тип охватывающего выражения (для
   * ресивера-результата вызова {@code Ф().|} и т.п.). Прямой {@code expressionTypesAt}
   * здесь нельзя: он накрыл бы незавершённое {@code Ресивер.Член} и не разрешил член.
   */
  private TypeSet receiverSegmentTypes(DocumentContext documentContext, Position receiverEnd) {
    var reference = referenceResolver.findReference(documentContext.getUri(), receiverEnd).orElse(null);
    var fromIndex = reference == null ? TypeSet.EMPTY : typesAt(reference);
    // Ссылка на переменную уже дала позиционный ответ — сравнивать не с чем, а расчёт
    // выражения в той же точке был бы вторым таким же проходом по методу.
    if (!fromIndex.isEmpty() && isVariableReference(reference)) {
      return fromIndex;
    }
    var fromExpression = expressionTypesAt(documentContext, receiverEnd);
    if (fromIndex.isEmpty()) {
      return fromExpression;
    }
    // Индекс ссылок отдаёт тип символа целиком, а инференс считает его в этой точке кода:
    // внутри ветки с проверкой типа набор сужен. Более узкий набор — точнее.
    if (narrowsSameTypes(fromExpression, fromIndex)) {
      return fromExpression;
    }
    // Индекс ссылок отдаёт ОБЪЯВЛЕННЫЙ тип члена, без уточнений, добытых на месте:
    // у `ТЗ.Колонки` это просто коллекция колонок, без самих колонок, а у
    // `ТЧ.ВыгрузитьКолонку("Цена")` — нетипизированный массив. Если инференс дал те
    // же типы, но с уточнениями, берём его: он строго информативнее.
    return refinesSameTypes(fromExpression, fromIndex) ? fromExpression : fromIndex;
  }

  /**
   * Ведёт ли ссылка на переменную или параметр.
   *
   * @param reference ссылка; {@code null} — ссылки нет.
   * @return {@code true}, если ссылка указывает на переменную.
   */
  private static boolean isVariableReference(@Nullable Reference reference) {
    return reference != null
      && reference.getSourceDefinedSymbol().orElse(null) instanceof VariableSymbol;
  }

  /**
   * Сужает ли {@code candidate} набор {@code base}: непустое строгое подмножество его
   * типов. Так выглядит результат проверки типа в коде — набор в этой точке уже, чем
   * объявленный у символа.
   *
   * @param candidate проверяемый набор.
   * @param base      набор, с которым сравнивается.
   * @return {@code true}, если кандидат — непустое строгое подмножество базы.
   */
  private static boolean narrowsSameTypes(TypeSet candidate, TypeSet base) {
    return !candidate.isEmpty()
      && candidate.size() < base.size()
      && base.refs().containsAll(candidate.refs());
  }

  /**
   * Уточняет ли {@code candidate} тот же набор типов, что и {@code base}: типы те же,
   * но у кандидата есть типы элементов или поля «открытого» объекта, которых нет у базы.
   */
  private static boolean refinesSameTypes(TypeSet candidate, TypeSet base) {
    if (!candidate.refs().equals(base.refs())) {
      return false;
    }
    for (var ref : candidate.refs()) {
      var richerElements = !candidate.getElementTypes(ref).isEmpty()
        && base.getElementTypes(ref).isEmpty();
      var richerFields = candidate.getLocalFields(ref).size() > base.getLocalFields(ref).size();
      if (richerElements || richerFields) {
        return true;
      }
    }
    return false;
  }

  /**
   * Если {@code position} стоит сразу за точкой доступа к члену (возможно, с уже
   * набранным префиксом члена) — позиция последнего символа ресивера перед точкой.
   * Используется для висячей точки, где завершённого dereference в AST ещё нет.
   */
  private static Optional<Position> receiverEndBeforeDot(DocumentContext documentContext, Position position) {
    var lines = documentContext.getContentList();
    if (position.getLine() >= lines.length) {
      return Optional.empty();
    }
    var line = lines[position.getLine()];
    var col = Math.clamp(position.getCharacter(), 0, line.length());
    var i = col;
    while (i > 0 && isIdentChar(line.charAt(i - 1))) {
      i--;
    }
    if (i == 0 || line.charAt(i - 1) != '.') {
      return Optional.empty();
    }
    var dotColumn = i - 1;
    if (dotColumn == 0) {
      return Optional.empty();
    }
    return Optional.of(new Position(position.getLine(), dotColumn - 1));
  }

  private static boolean isIdentChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  /** Все типы из набора конкретны (без {@link TypeRef#ANY} / {@link TypeRef#UNKNOWN}). */
  private static boolean allConcrete(TypeSet types) {
    var refs = types.refs();
    return !refs.isEmpty()
      && refs.stream().noneMatch(ref -> ref.equals(TypeRef.ANY) || ref.equals(TypeRef.UNKNOWN));
  }

  private static boolean isAccessorIdentifier(TerminalNode terminal) {
    var parent = terminal.getParent();
    if (parent instanceof BSLParser.AccessPropertyContext) {
      return true;
    }
    if (parent instanceof BSLParser.MethodNameContext
      && parent.getParent() instanceof BSLParser.MethodCallContext mc
      && mc.getParent() instanceof BSLParser.AccessCallContext) {
      return true;
    }
    return false;
  }

  /**
   * Найденный член типа в позиции курсора.
   *
   * @param owner       тип-владелец члена; {@code null} для глобальных функций/свойств
   * @param descriptor  описание члена
   * @param range       диапазон идентификатора-члена под курсором
   * @param callArgCount число фактических аргументов вызова; {@code -1} для не-вызова
   * @param argTypes    типы фактических аргументов (по порядку). Используется
   *                    для type-aware подбора перегрузки сигнатуры
   *                    ({@link com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection#pickIndexByTypes}).
   *                    Пустой список для не-вызова или если типы не удалось проинферить.
   */
  public record TypedMember(
    @Nullable TypeRef owner,
    MemberDescriptor descriptor,
    Range range,
    int callArgCount,
    List<TypeSet> argTypes
  ) {
    public TypedMember(@Nullable TypeRef owner, MemberDescriptor descriptor, Range range, int callArgCount) {
      this(owner, descriptor, range, callArgCount, List.of());
    }

    public TypedMember(@Nullable TypeRef owner, MemberDescriptor descriptor, Range range) {
      this(owner, descriptor, range, -1, List.of());
    }
  }

}
