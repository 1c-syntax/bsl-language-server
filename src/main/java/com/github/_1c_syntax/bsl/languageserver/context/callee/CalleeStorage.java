/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
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
package com.github._1c_syntax.bsl.languageserver.context.callee;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.Synchronized;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CalleeStorage {

  private final Map<MultiKey<String>, MultiValuedMap<String, Location>> callees = new HashMap<>();

  public List<Location> getCallees(String mdoRef, ModuleType moduleType, Symbol symbol) {
    var key = getKey(mdoRef, moduleType);
    var methodName = symbol.getName().toLowerCase(Locale.ENGLISH);

    var locations = callees.getOrDefault(key, MultiMapUtils.emptyMultiValuedMap()).get(methodName);

    return new ArrayList<>(locations);
  }

  @Synchronized("callees")
  public void clearCallees(String mdoRef, ModuleType moduleType) {
    var key = getKey(mdoRef, moduleType);
    callees.remove(key);
  }

  @Synchronized("callees")
  public void addMethodCall(URI uri, ModuleType moduleType, MethodSymbol methodSymbol, Range range) {
    String mdoRef = methodSymbol.getMdoRef();
    String methodName = methodSymbol.getName().toLowerCase(Locale.ENGLISH);

    Location location = new Location(uri.toString(), range);

    MultiKey<String> key = getKey(mdoRef, moduleType);

    callees.computeIfAbsent(key, k -> new ArrayListValuedHashMap<>()).put(methodName, location);
  }

  private static MultiKey<String> getKey(String mdoRef, ModuleType moduleType) {
    return new MultiKey<>(mdoRef, moduleType.getFileName());
  }
}
