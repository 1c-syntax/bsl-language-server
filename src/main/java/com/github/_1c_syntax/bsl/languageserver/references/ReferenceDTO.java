/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.Value;
import org.eclipse.lsp4j.SymbolKind;

import java.util.Locale;

@Value
public class ReferenceDTO {
  String mdoRef;

  ModuleType moduleType;

  String scopeName;

  SymbolKind symbolKind;

  String symbolName;

  boolean isWrite;

  public static ReferenceDTO of(String mdoRef, ModuleType moduleType, String symbolName) {
    return new ReferenceDTO(mdoRef, moduleType, "", SymbolKind.Method, symbolName.toLowerCase(Locale.ENGLISH), false);
  }

  public static ReferenceDTO of(String mdoRef, ModuleType moduleType, String scopeName, String symbolName, boolean isWrite) {
    return new ReferenceDTO(
      mdoRef,
      moduleType,
      scopeName.toLowerCase(Locale.ENGLISH),
      SymbolKind.Variable,
      symbolName.toLowerCase(Locale.ENGLISH),
      isWrite
    );
  }

  public static ReferenceDTO of(String mdoRef, ModuleType moduleType, String scopeName, SymbolKind symbolKind, String symbolName, boolean isWrite) {
    return new ReferenceDTO(
      mdoRef,
      moduleType,
      scopeName.toLowerCase(Locale.ENGLISH),
      symbolKind,
      symbolName.toLowerCase(Locale.ENGLISH),
      isWrite
    );
  }

  public static ReferenceDTO of(ReferenceDTO referenceKey) {
    return new ReferenceDTO(
      referenceKey.getMdoRef(),
      referenceKey.getModuleType(),
      referenceKey.getScopeName(),
      referenceKey.getSymbolKind(),
      referenceKey.getSymbolName(),
      false
    );
  }
}
