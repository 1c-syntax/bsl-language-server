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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.registry.BslContextPlatformTypesProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
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

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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

  /**
   * Пробельный участок, внутри которого есть хотя бы один перенос строки: и одиночный
   * перенос, и пустая строка между абзацами (см. {@link #asListItemLines}). Записан без
   * вложенных квантификаторов — {@code (?:[ \t]*\R)*} внутри повторения даёт
   * экспоненциальный откат на длинном тексте.
   */
  private static final Pattern LINE_BREAKS = Pattern.compile("[ \\t]*\\R\\s*");

  private final Resources resources;
  private final LanguageServerConfiguration configuration;
  private final TypeRegistry typeRegistry;
  private final PlatformMetadataRenderer metadataRenderer;

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
      appendOpenStructureFields(sb, descriptor.returnTypes(), lang);
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
    // Описание возвращаемого значения отдельно не печатается: оно уже входит
    // в блок метаданных выше (PlatformMetadata.returnValueDescription).
    metadataRenderer.append(sb, descriptor.metadata());
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
   * Дописывает состав свойств «открытой структуры» маркдаун-списком — так же, как
   * hover переменной показывает поля {@code Структура}. Типы, у которых состав
   * свойств не объявлен конфигурацией (см. {@code TypeRegistry#isOpenStructure}),
   * пропускаются: у них полезно имя типа, а не перечисление сотен членов.
   */
  private void appendOpenStructureFields(StringBuilder sb, TypeSet types, Language lang) {
    if (types == null || types.isEmpty()) {
      return;
    }
    for (var ref : types.refs()) {
      var base = typeRegistry.openStructureBase(ref);
      if (base.isEmpty()) {
        continue;
      }
      // Члены базового платформенного типа (`Свойство`, `ИсходныйКлючЗаписи`) полями
      // не являются: конфигурация их не объявляла, и в списке они только мешают.
      var inherited = new HashSet<String>();
      for (var member : typeRegistry.getMembers(base.get(), FileType.BSL)) {
        inherited.add(member.name().toLowerCase(Locale.ROOT));
      }
      for (var member : typeRegistry.getMembers(ref, FileType.BSL)) {
        if (member.kind() != MemberKind.PROPERTY
          || inherited.contains(member.name().toLowerCase(Locale.ROOT))) {
          continue;
        }
        var description = member.displayDescription(lang);
        var keyMarker = BslContextPlatformTypesProvider.KEY_PARAMETER_MARKER.forLanguage(lang);
        var key = description.stripTrailing().endsWith(keyMarker);
        if (key) {
          description = description.stripTrailing();
          description = description.substring(0, description.length() - keyMarker.length());
        }

        // Ключевой параметр — курсивом: признак «по нему форма ищется среди уже
        // открытых» важнее прочего текста и должен читаться до описания.
        var name = member.displayName(lang);
        sb.append("\n* ").append(key ? "***" + name + "***" : "**" + name + "**");
        var typeLabel = renderTypeSet(member.returnTypes(), lang);
        if (!typeLabel.isEmpty()) {
          sb.append(": ").append(typeLabel);
        }
        var text = asListItemLines(description);
        if (!text.isBlank()) {
          sb.append(" — ").append(text);
        }
      }
    }
  }

  /**
   * Готовит многоабзацный текст к вставке внутрь пункта markdown-списка: переносы
   * сохраняются, но становятся «жёсткими» (два пробела в конце строки) и получают
   * отступ по ширине маркера. Без этого продолжение выходит из пункта и встаёт
   * отдельным абзацем, теряя привязку к своему полю; пустые строки между абзацами
   * убираются — в списке они смотрятся разрывом.
   */
  private static String asListItemLines(String text) {
    return LINE_BREAKS.matcher(text.strip()).replaceAll("  \n  ");
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
