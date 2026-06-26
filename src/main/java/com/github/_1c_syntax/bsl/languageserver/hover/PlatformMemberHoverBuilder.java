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

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.model.AccessMode;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.util.SignatureSelection;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
@RequiredArgsConstructor
public class PlatformMemberHoverBuilder {

  private final Resources resources;
  private final LanguageServerConfiguration configuration;
  private final TypeRegistry typeRegistry;

  private String tr(String key) {
    return resources.getResourceString(getClass(), key);
  }

  public MarkupContent build(TypeRef owner, MemberDescriptor descriptor, int callArgCount) {
    return build(owner, descriptor, callArgCount, List.of());
  }

  /**
   * Расширенная версия: учитывает {@code argTypes} (типы фактических
   * аргументов вызова) для type-aware подбора перегруженной сигнатуры.
   * Когда не пусто — приоритетно использует
   * {@link SignatureSelection#pickIndexByTypes}.
   */
  public MarkupContent build(TypeRef owner, MemberDescriptor descriptor, int callArgCount,
                             List<TypeSet> argTypes) {
    var sb = new StringBuilder();
    SignatureDescriptor chosen = null;
    boolean disclaim = false;
    int chosenIndex = -1;
    if (descriptor.kind() == MemberKind.METHOD && !descriptor.signatures().isEmpty()) {
      if (descriptor.signatures().size() > 1 && callArgCount >= 0) {
        // type-aware подбор приоритетен — если есть типы аргументов,
        // выбираем сигнатуру, лучше всего соответствующую им. При равенстве
        // или отсутствии типов — fallback к arity-based pick.
        if (argTypes != null && !argTypes.isEmpty()) {
          chosenIndex = SignatureSelection.pickIndexByTypes(descriptor.signatures(), argTypes);
        }
        if (chosenIndex < 0) {
          chosenIndex = SignatureSelection.pickIndexByArity(descriptor.signatures(), callArgCount);
        }
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
    var lang = configuration.getLanguage();
    if (descriptor.kind() == MemberKind.METHOD) {
      sb.append("```bsl\n");
      sb.append(descriptor.displayName(lang)).append('(');
      if (chosen != null) {
        sb.append(chosen.parameters().stream()
          .map(p -> p.displayName(lang))
          .collect(Collectors.joining(", ")));
      }
      sb.append(')');
      // Возвращаемый тип: предпочтительно полный union из
      // descriptor.returnTypes() (HBK может декларировать несколько
      // типов в "Тип:" возврата метода). Fallback: chosen.returnType()
      // и effectiveReturnType (для legacy-членов без TypeSet).
      var returnLabel = renderTypeSet(descriptor.returnTypes(), lang);
      if (returnLabel.isEmpty()) {
        TypeRef ret = (chosen != null && chosen.returnType() != null
          && !chosen.returnType().qualifiedName().isEmpty())
          ? chosen.returnType()
          : effectiveReturnType(descriptor);
        if (ret != null) {
          returnLabel = typeRegistry.displayName(ret, lang);
        }
      }
      if (!returnLabel.isEmpty()) {
        sb.append(": ").append(returnLabel);
      }
      sb.append("\n```\n");
    } else {
      sb.append("```bsl\n");
      sb.append(descriptor.displayName(lang));
      // Для свойств тоже отображаем union, если у дескриптора несколько
      // типов (например, composite-реквизит "Строка | Число").
      var propertyLabel = renderTypeSet(descriptor.returnTypes(), lang);
      if (!propertyLabel.isEmpty()) {
        sb.append(": ").append(propertyLabel);
      } else if (descriptor.returnType() != null
        && descriptor.returnType().qualifiedName() != null
        && !descriptor.returnType().qualifiedName().isEmpty()) {
        sb.append(": ").append(typeRegistry.displayName(descriptor.returnType(), lang));
      }
      sb.append("\n```\n");
    }
    if (owner != null) {
      sb.append("\n_").append(tr("memberOf")).append("_ `")
        .append(typeRegistry.displayName(owner, lang)).append('`');
    } else if (descriptor.kind() == MemberKind.METHOD) {
      sb.append("\n_").append(tr("globalFunction")).append('_');
    } else {
      sb.append("\n_").append(tr("globalProperty")).append('_');
    }
    if (descriptor.async()) {
      sb.append("\n\n_").append(tr("asyncMethod")).append('_');
    }
    var symDesc = descriptor.getSymbolDescription();
    if (symDesc.isDeprecated()) {
      sb.append("\n\n**").append(tr("deprecatedFlag")).append("**");
      if (!symDesc.getDeprecationInfo().isBlank()) {
        sb.append(' ').append(symDesc.getDeprecationInfo());
      }
    }
    // Для платформенных членов (sourceSymbol==null) — берём bilingual-описание
    // напрямую (учитывает en-локаль). Для source-defined — getSymbolDescription
    // приоритетно (BSL-doc-comment).
    if (descriptor.sourceSymbol() != null && !symDesc.getPurposeDescription().isBlank()) {
      sb.append("\n\n").append(symDesc.getPurposeDescription());
    } else {
      var desc = descriptor.displayDescription(lang);
      if (desc != null && !desc.isBlank()) {
        sb.append("\n\n").append(desc);
      }
    }
    if (chosen != null) {
      var chosenDesc = chosen.displayDescription(lang);
      if (chosenDesc != null && !chosenDesc.isBlank()) {
        sb.append("\n\n").append(chosenDesc);
      }
    }
    if (chosen != null && !chosen.parameters().isEmpty()) {
      sb.append("\n\n**").append(tr("parameters")).append("**\n");
      for (var p : chosen.parameters()) {
        HoverParameters.appendNameAndType(sb, p.displayName(lang), renderTypeSet(p.types(), lang), p.optional());
        if (!p.defaultValue().isBlank()) {
          sb.append(" _= ").append(p.defaultValue()).append('_');
        }
        var pDesc = p.displayDescription(lang);
        if (pDesc != null && !pDesc.isBlank()) {
          sb.append(" — ").append(pDesc);
        }
        sb.append('\n');
      }
    }
    appendMetadata(sb, descriptor.metadata());
    if (chosen != null && !chosen.description().isBlank()) {
      // returnValueDescription уже зашит в общий description выше
    }
    if (disclaim) {
      sb.append("\n\n_").append(tr("noMatchingSignature")).append('_');
    }
    if (descriptor.kind() == MemberKind.METHOD && descriptor.signatures().size() > 1) {
      sb.append("\n\n**").append(tr("allCallVariants")).append("**\n");
      for (int i = 0; i < descriptor.signatures().size(); i++) {
        var sig = descriptor.signatures().get(i);
        sb.append("- ");
        if (i == chosenIndex && !disclaim) {
          sb.append("**");
        }
        sb.append('`').append(descriptor.displayName(lang)).append('(')
          .append(sig.parameters().stream().map(p -> p.displayName(lang)).collect(Collectors.joining(", ")))
          .append(")`");
        // Полный union типов сигнатуры (sig.returnTypes); fallback на
        // descriptor.returnTypes если у сигнатуры пусто (legacy кейс).
        var sigLabel = renderTypeSet(sig.returnTypes(), lang);
        if (sigLabel.isEmpty()) {
          sigLabel = renderTypeSet(descriptor.returnTypes(), lang);
        }
        if (!sigLabel.isEmpty()) {
          sb.append(": ").append(sigLabel);
        }
        if (i == chosenIndex && !disclaim) {
          sb.append("**");
        }
        sb.append('\n');
      }
    }
    return new MarkupContent(MarkupKind.MARKDOWN, sb.toString());
  }

  /**
   * Отрисовывает блок платформенных метаданных: «доступно с …», «устарело с …»,
   * рекомендуемые замены, режим доступа, контексты исполнения, описание
   * возвращаемого значения, «Замечание», примеры, «См. также».
   * Если метаданные пусты — ничего не пишет.
   */
  private void appendMetadata(StringBuilder sb, PlatformMetadata md) {
    if (md == null || md.isEmpty()) {
      return;
    }
    if (!md.deprecatedSinceVersion().isBlank()) {
      sb.append("\n\n**").append(tr("deprecatedSince")).append("** ").append(md.deprecatedSinceVersion());
    }
    if (!md.sinceVersion().isBlank()) {
      sb.append("\n\n**").append(tr("sinceVersion")).append("** ").append(md.sinceVersion());
    }
    if (!md.recommendedReplacements().isEmpty()) {
      sb.append("\n\n**").append(tr("recommendedReplacements")).append("** ")
        .append(md.recommendedReplacements().stream()
          .map(r -> "`" + r + "`")
          .collect(Collectors.joining(", ")));
    }
    if (md.accessMode() == AccessMode.READ) {
      sb.append("\n\n**").append(tr("accessMode")).append("** ").append(tr("accessReadOnly"));
    } else if (md.accessMode() == AccessMode.READ_WRITE) {
      sb.append("\n\n**").append(tr("accessMode")).append("** ").append(tr("accessReadWrite"));
    }
    appendAvailabilities(sb, md.availabilities());
    var lang = configuration.getLanguage();
    var rv = md.returnValueDescription().forLanguage(lang);
    if (!rv.isBlank()) {
      sb.append("\n\n**").append(tr("returnValueDescription")).append("** ").append(rv);
    }
    var nt = md.notes().forLanguage(lang);
    if (!nt.isBlank()) {
      sb.append("\n\n**").append(tr("notes")).append("** ").append(nt);
    }
    appendBilingualList(sb, tr("example"), md.examples(), true, lang);
    appendBilingualList(sb, tr("seeAlso"), md.seeAlso(), false, lang);
  }

  private static void appendBilingualList(StringBuilder sb, String title,
                                          List<BilingualString> items, boolean asCodeBlock,
                                          Language lang) {
    if (items == null || items.isEmpty()) {
      return;
    }
    var resolved = new ArrayList<String>(items.size());
    for (var bi : items) {
      var s = bi.forLanguage(lang);
      if (s != null && !s.isBlank()) {
        resolved.add(s);
      }
    }
    appendList(sb, title, resolved, asCodeBlock);
  }

  private void appendAvailabilities(StringBuilder sb, Set<Availability> availabilities) {
    if (availabilities == null || availabilities.isEmpty()) {
      return;
    }
    sb.append("\n\n**").append(tr("availabilities")).append("** ");
    sb.append(availabilities.stream()
      .map(this::displayName)
      .collect(Collectors.joining(", ")));
  }

  private String displayName(Availability availability) {
    return tr("availability." + availability.name());
  }

  private static void appendList(StringBuilder sb, String title, List<String> items, boolean asCodeBlock) {
    if (items == null || items.isEmpty()) {
      return;
    }
    sb.append("\n\n**").append(title).append(":**");
    for (var item : items) {
      if (item == null || item.isBlank()) {
        continue;
      }
      if (asCodeBlock) {
        sb.append("\n\n```bsl\n").append(item).append("\n```");
      } else {
        sb.append("\n- ").append(item);
      }
    }
  }

  /**
   * Форматирует {@code TypeSet} как {@code "Тип1 | Тип2"}. Пустой набор —
   * пустая строка. Используется для рендеринга union-типов возврата и
   * union-свойств в одиночном блоке кода hover'а.
   */
  private String renderTypeSet(TypeSet types, Language lang) {
    if (types == null || types.isEmpty()) {
      return "";
    }
    return types.refs().stream()
      .map(r -> typeRegistry.displayName(r, lang))
      .filter(name -> name != null && !name.isEmpty())
      .collect(Collectors.joining(" | "));
  }

  @Nullable
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
