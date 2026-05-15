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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.hover.MarkupContentBuilder;
import com.github._1c_syntax.bsl.languageserver.references.ReferenceResolver;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Провайдер для отображения всплывающих подсказок при наведении курсора.
 * <p>
 * Обрабатывает запросы {@code textDocument/hover}.
 *
 * @see <a href="https://microsoft.github.io/language-server-protocol/specifications/specification-current/#textDocument_hover">Hover Request specification</a>
 */
@Component
@RequiredArgsConstructor
public final class HoverProvider {

  private final ReferenceResolver referenceResolver;
  private final TypeService typeService;
  private final Map<SymbolKind, MarkupContentBuilder<Symbol>> markupContentBuilders;

  /**
   * Получить информацию для отображения при наведении курсора на символ.
   *
   * @param documentContext Контекст документа
   * @param params Параметры запроса hover
   * @return Информация для отображения во всплывающей подсказке
   */
  public Optional<Hover> getHover(DocumentContext documentContext, HoverParams params) {
    Position position = params.getPosition();

    var symbolBased = referenceResolver.findReference(documentContext.getUri(), position)
      .flatMap((Reference reference) -> {
        var symbol = reference.symbol();
        var range = reference.selectionRange();

        return Optional.ofNullable(markupContentBuilders.get(symbol.getSymbolKind()))
          .map(markupContentBuilder -> markupContentBuilder.getContent(symbol))
          .map(content -> new Hover(content, range));
      });
    if (symbolBased.isPresent()) {
      return symbolBased;
    }

    // Hover для имени класса в `Новый ИмяКласса(...)`.
    var ctorHover = newExpressionHover(documentContext, position);
    if (ctorHover.isPresent()) {
      return ctorHover;
    }

    // Fallback: type-driven hover для цепочек accessor'ов / platform members /
    // namespace-имен, у которых нет соответствующего SourceDefinedSymbol.
    return typeService.findMemberAt(documentContext, position)
      .map(member -> new Hover(
        renderMember(member.owner(), member.descriptor(), member.callArgCount()), member.range()));
  }

  private Optional<Hover> newExpressionHover(DocumentContext documentContext, Position position) {
    BSLParser.FileContext ast;
    try {
      ast = documentContext.getAst();
    } catch (NullPointerException e) {
      return Optional.empty();
    }
    if (ast == null) {
      return Optional.empty();
    }
    var nex = findInnermostNewExpression(ast, position);
    if (nex.isEmpty()) {
      return Optional.empty();
    }
    var typeNameCtx = nex.get().typeName();
    if (typeNameCtx == null) {
      return Optional.empty();
    }
    // Hover актуален только когда позиция на имени класса (не внутри скобок аргументов).
    if (!encloses(typeNameCtx, position)) {
      return Optional.empty();
    }
    var typeName = typeNameCtx.getText();
    var fileType = documentContext.getFileType();
    var ref = typeService.resolve(typeName, fileType).orElse(null);
    if (ref == null) {
      return Optional.empty();
    }
    var ctors = typeService.getConstructors(ref);
    if (ctors.isEmpty()) {
      return Optional.empty();
    }
    int argCount = countNewExpressionArgs(nex.get());
    boolean disclaim = false;
    int chosenIndex = SignatureSelection.pickIndexByArity(ctors, argCount);
    SignatureDescriptor chosen;
    if (chosenIndex < 0) {
      chosen = ctors.get(0);
      disclaim = true;
    } else {
      chosen = ctors.get(chosenIndex);
    }
    var range = tokenRange(typeNameCtx);
    return Optional.of(new Hover(renderConstructor(typeName, ref, chosen, ctors, disclaim), range));
  }

  private static int countNewExpressionArgs(BSLParser.NewExpressionContext nex) {
    var doCall = nex.doCall();
    if (doCall == null) {
      return 0;
    }
    var list = doCall.callParamList();
    if (list == null) {
      return 0;
    }
    var ps = list.callParam();
    if (ps == null || ps.isEmpty()) {
      return 0;
    }
    // Учитываем trailing empty (`Foo(a, )` — 2 параметра по AST, но фактически 1 значимый).
    int n = ps.size();
    var last = ps.get(n - 1);
    if (last.getChildCount() == 0) {
      n--;
    }
    return n;
  }

  private static Optional<BSLParser.NewExpressionContext> findInnermostNewExpression(
    ParseTree node, Position position
  ) {
    BSLParser.NewExpressionContext best = null;
    if (node instanceof BSLParser.NewExpressionContext nex && encloses(nex, position)) {
      best = nex;
    }
    for (int i = 0; i < node.getChildCount(); i++) {
      var child = node.getChild(i);
      if (child instanceof ParserRuleContext prc && !encloses(prc, position)) {
        continue;
      }
      var inner = findInnermostNewExpression(child, position);
      if (inner.isPresent()) {
        best = inner.get();
      }
    }
    return Optional.ofNullable(best);
  }

  private static boolean encloses(ParserRuleContext ctx, Position position) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    if (start == null || stop == null) {
      return false;
    }
    var range = Ranges.create(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
    return Ranges.containsPosition(range, position);
  }

  private static Range tokenRange(ParserRuleContext ctx) {
    var start = ctx.getStart();
    var stop = ctx.getStop();
    return Ranges.create(start.getLine() - 1, start.getCharPositionInLine(),
      stop.getLine() - 1, stop.getCharPositionInLine() + stop.getText().length());
  }

  private MarkupContent renderConstructor(
    String typeName,
    TypeRef ref,
    SignatureDescriptor chosen,
    java.util.List<SignatureDescriptor> ctors,
    boolean disclaim
  ) {
    var sb = new StringBuilder();
    sb.append("```bsl\nНовый ").append(typeName).append('(');
    sb.append(chosen.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")));
    sb.append(')').append("\n```\n");
    sb.append("\n_конструктор типа_ `").append(ref.qualifiedName()).append('`');
    var classDesc = typeService.getDescription(ref);
    if (!classDesc.isBlank()) {
      sb.append("\n\n").append(classDesc);
    }
    if (chosen.description() != null && !chosen.description().isBlank()) {
      sb.append("\n\n").append(chosen.description());
    }
    if (!chosen.parameters().isEmpty()) {
      sb.append("\n\n**Параметры:**\n");
      for (var p : chosen.parameters()) {
        sb.append("- `").append(p.name()).append('`');
        if (p.optional()) {
          sb.append(" _(необязательный)_");
        }
        if (p.description() != null && !p.description().isBlank()) {
          sb.append(" — ").append(p.description());
        }
        sb.append('\n');
      }
    }
    if (disclaim) {
      sb.append("\n\n_Не найдено описание, подходящее под текущий вызов конструктора._");
    }
    if (ctors.size() > 1) {
      sb.append("\n\n**Все варианты конструктора:**\n");
      for (var sig : ctors) {
        sb.append("- `Новый ").append(typeName).append('(')
          .append(sig.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")))
          .append(")`");
        if (sig.description() != null && !sig.description().isBlank()) {
          sb.append(" — ").append(sig.description());
        }
        sb.append('\n');
      }
    }
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  private static MarkupContent renderMember(TypeRef owner, MemberDescriptor descriptor, int callArgCount) {
    var sb = new StringBuilder();
    SignatureDescriptor chosen = null;
    boolean disclaim = false;
    int chosenIndex = -1;
    if (descriptor.kind() == MemberKind.METHOD && !descriptor.signatures().isEmpty()) {
      if (descriptor.signatures().size() > 1 && callArgCount >= 0) {
        chosenIndex = SignatureSelection.pickIndexByArity(descriptor.signatures(), callArgCount);
        if (chosenIndex < 0) {
          chosen = descriptor.signatures().get(0);
          chosenIndex = 0;
          disclaim = true;
        } else {
          chosen = descriptor.signatures().get(chosenIndex);
        }
      } else {
        chosen = descriptor.signatures().get(0);
        chosenIndex = 0;
      }
    }
    if (descriptor.kind() == MemberKind.METHOD) {
      sb.append("```bsl\n");
      sb.append(descriptor.name()).append('(');
      if (chosen != null) {
        sb.append(chosen.parameters().stream()
          .map(p -> p.name())
          .collect(Collectors.joining(", ")));
      }
      sb.append(')');
      TypeRef ret = (chosen != null && chosen.returnType() != null
        && !chosen.returnType().qualifiedName().isEmpty())
        ? chosen.returnType()
        : effectiveReturnType(descriptor);
      if (ret != null) {
        sb.append(": ").append(ret.qualifiedName());
      }
      sb.append("\n```\n");
    } else {
      sb.append("```bsl\n");
      sb.append(descriptor.name());
      if (descriptor.returnType() != null
        && descriptor.returnType().qualifiedName() != null
        && !descriptor.returnType().qualifiedName().isEmpty()) {
        sb.append(": ").append(descriptor.returnType().qualifiedName());
      }
      sb.append("\n```\n");
    }
    sb.append("\n_member of_ `").append(owner.qualifiedName()).append('`');
    var symDesc = descriptor.getSymbolDescription();
    if (symDesc.isDeprecated()) {
      sb.append("\n\n**Устарело.**");
      if (!symDesc.getDeprecationInfo().isBlank()) {
        sb.append(' ').append(symDesc.getDeprecationInfo());
      }
    }
    if (!symDesc.getPurposeDescription().isBlank()) {
      sb.append("\n\n").append(symDesc.getPurposeDescription());
    } else if (descriptor.description() != null && !descriptor.description().isBlank()) {
      sb.append("\n\n").append(descriptor.description());
    }
    if (chosen != null && chosen.description() != null && !chosen.description().isBlank()) {
      sb.append("\n\n").append(chosen.description());
    }
    if (chosen != null && !chosen.parameters().isEmpty()) {
      sb.append("\n\n**Параметры:**\n");
      for (var p : chosen.parameters()) {
        sb.append("- `").append(p.name()).append('`');
        if (p.optional()) {
          sb.append(" _(необязательный)_");
        }
        if (p.description() != null && !p.description().isBlank()) {
          sb.append(" — ").append(p.description());
        }
        sb.append('\n');
      }
    }
    if (disclaim) {
      sb.append("\n\n_Не найдено описание, подходящее под текущий вызов метода._");
    }
    if (descriptor.kind() == MemberKind.METHOD && descriptor.signatures().size() > 1) {
      sb.append("\n\n**Все варианты вызова:**\n");
      for (int i = 0; i < descriptor.signatures().size(); i++) {
        var sig = descriptor.signatures().get(i);
        sb.append("- ");
        if (i == chosenIndex && !disclaim) {
          sb.append("**");
        }
        sb.append('`').append(descriptor.name()).append('(')
          .append(sig.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")))
          .append(")`");
        if (sig.returnType() != null && !sig.returnType().qualifiedName().isEmpty()) {
          sb.append(": ").append(sig.returnType().qualifiedName());
        }
        if (i == chosenIndex && !disclaim) {
          sb.append("**");
        }
        sb.append('\n');
      }
    }
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  private static TypeRef effectiveReturnType(MemberDescriptor descriptor) {
    if (descriptor.returnType() != null
      && !descriptor.returnType().qualifiedName().isEmpty()) {
      return descriptor.returnType();
    }
    if (!descriptor.signatures().isEmpty()) {
      var sig = descriptor.signatures().get(0);
      if (sig.returnType() != null) {
        return sig.returnType();
      }
    }
    return null;
  }

}
