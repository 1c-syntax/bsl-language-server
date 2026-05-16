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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Сборщик markdown-контента для hover'а по члену типа или глобальной функции
 * на основе {@link MemberDescriptor}.
 * <p>
 * Если {@code owner != null} — выводится строка {@code _member of_ <owner>};
 * если {@code owner == null} — описание глобальной функции/свойства без
 * привязки к контейнеру.
 */
@Component
public class PlatformMemberHoverBuilder {

  public MarkupContent build(TypeRef owner, MemberDescriptor descriptor, int callArgCount) {
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
    if (owner != null) {
      sb.append("\n_member of_ `").append(owner.qualifiedName()).append('`');
    } else if (descriptor.kind() == MemberKind.METHOD) {
      sb.append("\n_глобальная функция_");
    } else {
      sb.append("\n_глобальное свойство_");
    }
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
