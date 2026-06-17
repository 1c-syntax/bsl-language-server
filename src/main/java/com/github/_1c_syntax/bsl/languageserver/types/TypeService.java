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
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionAtPosition;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.UserType;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
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
public class TypeService {

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
    var sourceDefined = reference.getSourceDefinedSymbol();
    if (sourceDefined.isPresent()) {
      return inferencer.inferSymbol(sourceDefined.get());
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
     return method.getParameters().stream()
       .map(symbolTypeIndex::getDeclaredParameterTypes)
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
   * Символ-источник, объявивший тип, если такой есть в исходниках рабочей области.
   * <p>
   * Покрывает типы, у которых есть объявляющий их модуль/класс:
   * <ul>
   *   <li>{@link TypeKind#USER} — пользовательские типы OneScript (классы и модули):
   *       объявление хранится в самом {@link UserType#getDeclaration()};</li>
   *   <li>{@link TypeKind#CONFIGURATION} — общие модули и модули менеджеров объектов
   *       конфигурации: документ-модуль находится обратным индексом
   *       {@link GlobalScopeProvider#moduleUriByType(TypeRef)}, а его символ —
   *       {@code getSymbolTree().getModule()}.</li>
   * </ul>
   * Для платформенных/примитивных типов ({@link TypeKind#PLATFORM},
   * {@link TypeKind#PRIMITIVE}) и служебных {@code Unknown}/{@code Any}
   * объявляющего исходник-символа нет — возвращается {@code empty}.
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
      case USER -> userTypeDeclaration(typeRef);
      case CONFIGURATION -> configurationModuleSymbol(typeRef, requestingContext);
      default -> Optional.empty();
    };
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
    return globalScopeProvider.moduleUriByType(typeRef)
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
      case CONFIGURATION -> globalScopeProvider.moduleUriByType(typeRef);
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
    // Случай глобальной функции / свойства / library-модуля (например,
    // КодировкаТекста, ФС) — резолвится напрямую, без инференса ресивера.
    if (!isAccessorIdentifier(terminal)) {
      var bare = resolveBareName(terminal, documentContext);
      if (bare.isPresent()) {
        return List.of(bare.get());
      }
    }
    return dereferenceMatcher.matchAt(terminal, documentContext, position);
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
   * Резолв голого имени (не аксессора): глобальная функция (владелец = null)
   * либо глобальное свойство / library-модуль. Empty, если имя так не резолвится.
   */
  private Optional<TypedMember> resolveBareName(TerminalNode terminal, DocumentContext documentContext) {
    var bareName = terminal.getText();
    var fileType = documentContext.getFileType();
    // Глобальная функция — метод-член GLOBAL_CONTEXT.
    var globalFn = globalScopeProvider.globalFunction(bareName, fileType);
    if (globalFn.isPresent()) {
      return Optional.of(new TypedMember(null, globalFn.get(), Ranges.create(terminal), -1));
    }

    // Глобальное свойство (перечисление/менеджер коллекции/модуль) — свойство-член
    // GLOBAL_CONTEXT; имена типов для `Новый` (TYPE_NAME) членами не являются.
    return globalScopeProvider.globalProperty(bareName, fileType)
      .map(member -> member.returnTypes().refs().stream()
        .filter(r -> !r.equals(TypeRef.UNKNOWN)).findFirst().orElse(TypeRef.UNKNOWN))
      .filter(ref -> !ref.equals(TypeRef.UNKNOWN))
      .map(ref -> {
        var desc = typeRegistry.getDescription(ref, fileType);
        // owner == null: глобальное свойство, а не член ресивера (контракт TypedMember).
        return new TypedMember(null,
          MemberDescriptor.property(ref.qualifiedName(), ref, desc),
          Ranges.create(terminal));
      });
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
      .flatMap(terminal -> dereferenceMatcher.receiverTypesAt(documentContext, position, terminal))
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
    var fromIndex = referenceResolver.findReference(documentContext.getUri(), receiverEnd)
      .map(this::typesAt)
      .orElse(TypeSet.EMPTY);
    if (!fromIndex.isEmpty()) {
      return fromIndex;
    }
    return expressionTypesAt(documentContext, receiverEnd);
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
    var col = Math.min(position.getCharacter(), line.length());
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
