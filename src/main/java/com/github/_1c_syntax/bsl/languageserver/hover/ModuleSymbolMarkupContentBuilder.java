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

import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Построитель контента для всплывающего окна для {@link ModuleSymbol}.
 */
@Component
@RequiredArgsConstructor
public class ModuleSymbolMarkupContentBuilder implements MarkupContentBuilder<ModuleSymbol> {

  private final Resources resources;
  private final DescriptionFormatter descriptionFormatter;

  @Override
  public MarkupContent getContent(ModuleSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // Местоположение модуля
    String moduleLocation = descriptionFormatter.getLocation(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, moduleLocation);

    // Информация о модуле из метаданных
    var moduleInfo = getModuleInfo(symbol);
    descriptionFormatter.addSectionIfNotEmpty(markupBuilder, moduleInfo);

    var content = markupBuilder.toString();
    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Module;
  }

  private String getModuleInfo(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var mdObject = documentContext.getMdObject();

    if (mdObject.isEmpty()) {
      return "";
    }

    var mdo = mdObject.get();
    if (!(mdo instanceof CommonModule commonModule)) {
      return "";
    }

    var moduleInfoBuilder = new StringJoiner("\n");

    // Комментарий
    var comment = commonModule.getComment();
    if (!comment.isBlank()) {
      moduleInfoBuilder.add(comment);
      moduleInfoBuilder.add("");
    }

    // Флаги доступности
    var flags = getModuleFlags(commonModule);

    if (!flags.isEmpty()) {
      var flagsHeader = "**" + getResourceString("availability") + ":** ";
      moduleInfoBuilder.add(flagsHeader + String.join(", ", flags));
      moduleInfoBuilder.add("");
    }

    // Режим повторного использования
    var returnValueReuse = commonModule.getReturnValuesReuse();
    var reuseKey = switch (returnValueReuse) {
      case DURING_REQUEST -> "duringRequest";
      case DURING_SESSION -> "duringSession";
      case DONT_USE, UNKNOWN -> "";
    };

    if (!reuseKey.isEmpty()) {
      var reuseHeader = "**" + getResourceString("returnValuesReuse") + ":** ";
      moduleInfoBuilder.add(reuseHeader + getResourceString(reuseKey));
    }

    return moduleInfoBuilder.toString();
  }

  private List<String> getModuleFlags(CommonModule commonModule) {
    var flags = new ArrayList<String>();

    if (commonModule.isServer()) {
      flags.add(getResourceString("server"));
    }
    if (commonModule.isClientManagedApplication()) {
      flags.add(getResourceString("clientManagedApplication"));
    }
    if (commonModule.isClientOrdinaryApplication()) {
      flags.add(getResourceString("clientOrdinaryApplication"));
    }
    if (commonModule.isExternalConnection()) {
      flags.add(getResourceString("externalConnection"));
    }
    if (commonModule.isServerCall()) {
      flags.add(getResourceString("serverCall"));
    }
    if (commonModule.isPrivileged()) {
      flags.add(getResourceString("privilegedMode"));
    }
    if (commonModule.isGlobal()) {
      flags.add(getResourceString("global"));
    }
    return flags;
  }

  private String getResourceString(String key) {
    return resources.getResourceString(getClass(), key);
  }
}
