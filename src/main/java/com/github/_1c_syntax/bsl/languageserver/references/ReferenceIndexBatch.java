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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.OccurrenceType;
import com.github._1c_syntax.bsl.languageserver.references.model.Symbol;
import com.github._1c_syntax.bsl.languageserver.references.model.SymbolOccurrence;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.GenericInterner;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/** Собирает вхождения одного документа и канонизирует локальные ключи до публикации пакета. */
@RequiredArgsConstructor
public final class ReferenceIndexBatch {
  private final ReferenceIndex index;
  private final List<SymbolOccurrence> occurrences;
  private @Nullable GenericInterner<Symbol> localVariables;

  void addMethodCall(URI uri, String mdoRef, ModuleType moduleType, String symbolName, Range range) {
    occurrences.add(index.methodCallOccurrence(uri, mdoRef, moduleType, symbolName, range));
  }

  void addModuleReference(URI uri, String mdoRef, ModuleType moduleType, Range range) {
    occurrences.add(index.moduleReferenceOccurrence(uri, mdoRef, moduleType, range));
  }

  void addVariableUsage(URI uri, String mdoRef, ModuleType moduleType, String methodName,
                        String variableName, Range range, OccurrenceType occurrenceType) {
    if (methodName.isEmpty()) {
      occurrences.add(index.variableOccurrence(uri, mdoRef, moduleType, methodName, variableName, range, occurrenceType));
      return;
    }
    var symbols = localVariables;
    if (symbols == null) {
      symbols = index.localVariableInterner(uri);
      localVariables = symbols;
    }
    var symbol = index.variableSymbol(mdoRef, moduleType, methodName, variableName);
    occurrences.add(index.variableOccurrence(uri, symbol, range, occurrenceType, symbols));
  }

  void addSelfMemberUsage(URI uri, String mdoRef, ModuleType moduleType, SymbolKind symbolKind,
                          String name, Range range) {
    occurrences.add(index.selfMemberOccurrence(uri, mdoRef, moduleType, symbolKind, name, range,
      OccurrenceType.REFERENCE));
  }
}