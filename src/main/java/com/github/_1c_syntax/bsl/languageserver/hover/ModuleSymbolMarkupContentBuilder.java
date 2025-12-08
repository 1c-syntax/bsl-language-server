/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import com.github._1c_syntax.bsl.mdo.support.ReturnValueReuse;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.StringJoiner;

/**
 * Построитель контента для всплывающего окна для {@link ModuleSymbol}.
 */
@Component
@RequiredArgsConstructor
public class ModuleSymbolMarkupContentBuilder implements MarkupContentBuilder<ModuleSymbol> {

  private final Resources resources;

  @Override
  public MarkupContent getContent(ModuleSymbol symbol) {
    var markupBuilder = new StringJoiner("\n");

    // Сигнатура модуля
    String signature = buildSignature(symbol);
    addSectionIfNotEmpty(markupBuilder, signature);

    // Местоположение модуля
    String location = buildLocation(symbol);
    addSectionIfNotEmpty(markupBuilder, location);

    // Информация о модуле из метаданных
    String moduleInfo = buildModuleInfo(symbol);
    addSectionIfNotEmpty(markupBuilder, moduleInfo);

    String content = markupBuilder.toString();
    return new MarkupContent(MarkupKind.MARKDOWN, content);
  }

  @Override
  public SymbolKind getSymbolKind() {
    return SymbolKind.Module;
  }

  private String buildSignature(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var moduleType = documentContext.getModuleType();
    var moduleName = symbol.getName();

    var moduleTypeKey = switch (moduleType) {
      case CommonModule -> "commonModule";
      case ManagerModule -> "managerModule";
      case ObjectModule -> "objectModule";
      case FormModule -> "formModule";
      case CommandModule -> "commandModule";
      case RecordSetModule -> "recordSetModule";
      case ValueManagerModule -> "valueManagerModule";
      case SessionModule -> "sessionModule";
      case ExternalConnectionModule -> "externalConnectionModule";
      case ManagedApplicationModule -> "managedApplicationModule";
      case OrdinaryApplicationModule -> "ordinaryApplicationModule";
      case HTTPServiceModule -> "httpServiceModule";
      case WEBServiceModule -> "webServiceModule";
      case IntegrationServiceModule -> "integrationServiceModule";
      default -> "module";
    };

    var moduleTypeText = getResourceString(moduleTypeKey);
    return String.format("```bsl\n%s: %s\n```", moduleTypeText, moduleName);
  }

  private String buildLocation(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var uri = documentContext.getUri();
    var mdoRef = documentContext.getMdoRef();

    return String.format("[%s](%s)", mdoRef, uri);
  }

  private String buildModuleInfo(ModuleSymbol symbol) {
    var documentContext = symbol.getOwner();
    var mdObject = documentContext.getMdObject();

    if (mdObject.isEmpty()) {
      return "";
    }

    var mdo = mdObject.get();
    if (!(mdo instanceof CommonModule commonModule)) {
      return "";
    }

    var infoBuilder = new StringJoiner("\n");

    // Комментарий
    var comment = commonModule.getComment();
    if (!comment.isBlank()) {
      infoBuilder.add(comment);
      infoBuilder.add("");
    }

    // Флаги доступности
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

    if (!flags.isEmpty()) {
      var flagsHeader = "**" + getResourceString("availability") + ":** ";
      infoBuilder.add(flagsHeader + String.join(", ", flags));
    }

    // Режим повторного использования
    var returnValueReuse = commonModule.getReturnValuesReuse();
    if (returnValueReuse != ReturnValueReuse.DONT_USE) {
      var reuseKey = switch (returnValueReuse) {
        case DURING_REQUEST -> "duringRequest";
        case DURING_SESSION -> "duringSession";
        default -> "";
      };
      if (!reuseKey.isEmpty()) {
        var reuseHeader = "**" + getResourceString("returnValuesReuse") + ":** ";
        infoBuilder.add(reuseHeader + getResourceString(reuseKey));
      }
    }

    return infoBuilder.toString();
  }

  private void addSectionIfNotEmpty(StringJoiner markupBuilder, String newContent) {
    if (!newContent.isEmpty()) {
      markupBuilder.add(newContent);
      markupBuilder.add("");
      markupBuilder.add("---");
    }
  }

  private String getResourceString(String key) {
    return resources.getResourceString(getClass(), key);
  }
}
