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
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
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

    // Fallback: type-driven hover для цепочек accessor'ов / platform members /
    // namespace-имен, у которых нет соответствующего SourceDefinedSymbol.
    return typeService.findMemberAt(documentContext, position)
      .map(member -> new Hover(renderMember(member.owner(), member.descriptor()), member.range()));
  }

  private static MarkupContent renderMember(TypeRef owner, MemberDescriptor descriptor) {
    var sb = new StringBuilder();
    if (descriptor.kind() == MemberKind.METHOD) {
      sb.append("```bsl\n");
      sb.append(descriptor.name()).append('(');
      if (!descriptor.signatures().isEmpty()) {
        var first = descriptor.signatures().get(0);
        sb.append(first.parameters().stream()
          .map(p -> p.name())
          .collect(Collectors.joining(", ")));
      }
      sb.append(')');
      var ret = effectiveReturnType(descriptor);
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
    if (descriptor.description() != null && !descriptor.description().isBlank()) {
      sb.append("\n\n").append(descriptor.description());
    }
    if (descriptor.kind() == MemberKind.METHOD && descriptor.signatures().size() > 1) {
      sb.append("\n\n**Перегрузки:**\n");
      for (var sig : descriptor.signatures()) {
        sb.append("- `").append(descriptor.name()).append('(')
          .append(sig.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")))
          .append(")`");
        if (sig.returnType() != null) {
          sb.append(": ").append(sig.returnType().qualifiedName());
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
