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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Сапплаер семантических токенов для идентификаторов, разрешающихся через
 * global scope ({@link GlobalScopeProvider}). Тип/модификатор токена выбираются
 * по {@link com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind}
 * найденного символа:
 * <ul>
 *   <li>{@code PLATFORM_GLOBAL_PROPERTY} ({@code Справочники}, {@code Метаданные},
 *       {@code ОбщегоНазначения}) → {@code Class + DefaultLibrary};</li>
 *   <li>{@code PLATFORM_GLOBAL_ENUM} ({@code КодировкаТекста}) →
 *       {@code Enum + DefaultLibrary}; значение перечисления первого уровня
 *       ({@code .UTF8}) → {@code EnumMember};</li>
 *   <li>{@code LIBRARY_MODULE} ({@code ФС}) → {@code Namespace}.</li>
 * </ul>
 * Идентификаторы, перекрытые локальной переменной/параметром, пропускаются —
 * локальный символ имеет приоритет.
 */
@Component
@RequiredArgsConstructor
public class GlobalScopeSemanticTokensSupplier implements SemanticTokensSupplier {

  private static final String[] DEFAULT_LIBRARY_MODIFIERS = {SemanticTokenModifiers.DefaultLibrary};
  private static final String[] DEFAULT_LIBRARY_ASYNC_MODIFIERS =
    {SemanticTokenModifiers.DefaultLibrary, SemanticTokenModifiers.Async};

  private final GlobalScopeProvider globalScopeProvider;
  private final TypeRegistry typeRegistry;
  private final SemanticTokensHelper helper;

  @Override
  public List<SemanticTokenEntry> getSemanticTokens(DocumentContext documentContext) {
    List<SemanticTokenEntry> entries = new ArrayList<>();
    var ast = documentContext.getAst();
    var fileType = documentContext.getFileType();
    var symbolTree = documentContext.getSymbolTree();

    // Идентификаторы в expression-позиции: `... = ПервыйОбщийМодуль.X(); А = Справочники.Y;`.
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_complexIdentifier)) {
      if (node instanceof BSLParser.ComplexIdentifierContext chain) {
        processIdentifier(entries, chain.IDENTIFIER(), chain.modifier(), fileType, symbolTree);
      }
    }
    // Идентификаторы в statement-позиции: `ПервыйОбщийМодуль.X();` без присваивания.
    // CallStatement — отдельный rule грамматики, с собственным IDENTIFIER+modifier-цепочкой.
    for (var node : Trees.findAllRuleNodes(ast, BSLParser.RULE_callStatement)) {
      if (node instanceof BSLParser.CallStatementContext callStmt) {
        processIdentifier(entries, callStmt.IDENTIFIER(), callStmt.modifier(), fileType, symbolTree);
      }
    }

    return entries;
  }

  private void processIdentifier(List<SemanticTokenEntry> entries,
                                 @Nullable TerminalNode identifier,
                                 List<? extends BSLParser.ModifierContext> modifiers,
                                 FileType fileType,
                                 SymbolTree symbolTree) {
    if (identifier == null) {
      return;
    }
    var name = identifier.getText();
    if (name.isBlank()) {
      return;
    }
    // Локальные имена (переменные/параметры) перекрывают глобальные — пропускаем.
    if (symbolTree != null && isLocalName(symbolTree, name)) {
      return;
    }
    // Глобальное VALUE-имя — свойство-член синтетического GLOBAL_CONTEXT
    // (issue #3994). Глобальные функции (METHOD) и имена типов для `Новый`
    // здесь не наш домен — их красят другие сапплаеры.
    var member = typeRegistry.globalMember(name, fileType)
      .filter(m -> m.kind() == MemberKind.PROPERTY);
    if (member.isEmpty()) {
      return;
    }
    var valueType = member.get().returnTypes().refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
    // 4-сторонняя классификация выводится из типа-значения (а не из отдельного
    // флага): перечисление → Enum; модульный тип (общий/library-модуль, есть в
    // URI-индексе) → Namespace; иначе платформенное свойство/коллекция → Class.
    if (typeRegistry.isEnumType(valueType)) {
      helper.addRange(entries, Ranges.create(identifier),
        SemanticTokenTypes.Enum, SemanticTokenModifiers.DefaultLibrary);
    } else if (globalScopeProvider.moduleUriByType(valueType).isPresent()) {
      helper.addRange(entries, Ranges.create(identifier), SemanticTokenTypes.Namespace);
    } else {
      helper.addRange(entries, Ranges.create(identifier),
        SemanticTokenTypes.Class, SemanticTokenModifiers.DefaultLibrary);
    }

    // Walk модификаторов — резолв member'ов с пошаговой проводкой по типам.
    // Покрашиваются: значения enum'а (.UTF8 → EnumMember), mdo-ссылки внутри
    // metadata-collection (.Контрагенты → Class), вызовы платформенных методов
    // (.ПустаяСсылка() → Method+DefaultLibrary).
    walkChain(entries, valueType, modifiers, fileType);
  }

  private void walkChain(List<SemanticTokenEntry> entries, TypeRef startType,
                         List<? extends BSLParser.ModifierContext> modifiers, FileType fileType) {
    if (modifiers == null || modifiers.isEmpty()) {
      return;
    }
    var current = startType;
    for (var modifier : modifiers) {
      if (current == null || current.equals(TypeRef.UNKNOWN)) {
        return;
      }
      if (modifier.accessProperty() != null) {
        current = handleProperty(entries, modifier.accessProperty(), current, fileType);
      } else if (modifier.accessCall() != null) {
        current = handleCall(entries, modifier.accessCall(), current, fileType);
      } else {
        return; // accessIndex и прочее — не отслеживаем тип элемента.
      }
    }
  }

  @Nullable
  private TypeRef handleProperty(List<SemanticTokenEntry> entries,
                                 BSLParser.AccessPropertyContext accessProperty,
                                 TypeRef ownerType, FileType fileType) {
    var idNode = accessProperty.IDENTIFIER();
    if (idNode == null) {
      return null;
    }
    var member = findMember(ownerType, idNode.getText(), MemberKind.PROPERTY, fileType);
    if (member == null) {
      return null;
    }
    var returnType = member.returnType();
    if (ownerType.equals(returnType)) {
      // self-typed property — значение enum (.UTF8).
      helper.addRange(entries, Ranges.create(idNode), SemanticTokenTypes.EnumMember);
    } else if (returnType.kind() == TypeKind.CONFIGURATION) {
      // mdo-ссылка (Справочники.Контрагенты).
      helper.addRange(entries, Ranges.create(idNode), SemanticTokenTypes.Class);
    }
    return returnType;
  }

  @Nullable
  private TypeRef handleCall(List<SemanticTokenEntry> entries,
                             BSLParser.AccessCallContext accessCall,
                             TypeRef ownerType, FileType fileType) {
    var methodCall = accessCall.methodCall();
    if (methodCall == null) {
      return null;
    }
    var methodName = methodCall.methodName();
    if (methodName == null || methodName.getStart() == null) {
      return null;
    }
    var member = findMember(ownerType, methodName.getStart().getText(),
      MemberKind.METHOD, fileType);
    if (member == null) {
      return null;
    }
    // Source-defined методы (общий модуль, модуль менеджера) красит
    // MethodCallSemanticTokensSupplier как Method+Static — не дублируем.
    if (!(member.sourceSymbol() instanceof SourceDefinedSymbol)) {
      var modifiers = member.async() ? DEFAULT_LIBRARY_ASYNC_MODIFIERS : DEFAULT_LIBRARY_MODIFIERS;
      helper.addRange(entries, Ranges.create(methodName), SemanticTokenTypes.Method, modifiers);
    }
    return member.returnType();
  }

  @Nullable
  private MemberDescriptor findMember(TypeRef ownerType, String memberName,
                                      MemberKind expectedKind, FileType fileType) {
    if (ownerType == null || memberName == null || memberName.isBlank()) {
      return null;
    }
    for (var m : typeRegistry.getMembers(ownerType, fileType)) {
      if (m.kind() == expectedKind && m.matches(memberName)) {
        return m;
      }
    }
    return null;
  }

  private static boolean isLocalName(SymbolTree symbolTree, String name) {
    return symbolTree.getVariableSymbol(name, symbolTree.getModule()).isPresent();
  }
}
