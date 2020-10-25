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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import org.apache.commons.collections4.MultiMapUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CalleeStorage {

  private final ServerContext serverContext;

  /**
   * Хранит информацию о том, какие methodName (ключ MultiValuedMap) каких mdoRef каких moduleType (ключ MultiKey)
   * были вызваны из списка Location (URI + Range).
   */
  private final Map<MultiKey<String>, MultiValuedMap<String, Location>> calleesOf = new HashMap<>();

  /**
   * Хранит информацию о том, какие methodName с mdoRef с moduleType (ключ MultiKey) в каких URI были вызваны
   * и в каких Range, расположены вызовы
   */
  private final Map<URI, MultiValuedMap<MultiKey<String>, Range>> calleesFrom = new HashMap<>();

  /**
   * Хранит информацию о том, в каких Range каких URI были вызваны
   * какие methodName каких mdoRef с каким moduleType (ключ MultiKey)
   */
  private final Map<URI, Map<Range, MultiKey<String>>> calleesRanges = new HashMap<>();

  public List<Location> getCalleesOf(String mdoRef, ModuleType moduleType, Symbol symbol) {
    var key = getKey(mdoRef, moduleType);
    var methodName = symbol.getName().toLowerCase(Locale.ENGLISH);

    var locations = calleesOf.getOrDefault(key, MultiMapUtils.emptyMultiValuedMap()).get(methodName);

    return new ArrayList<>(locations);
  }

  public Optional<Pair<MethodSymbol, Range>> getCalledMethodSymbol(URI uri, Position position) {
    return calleesRanges.getOrDefault(uri, Collections.emptyMap()).entrySet().stream()
      .filter(entry -> Ranges.containsPosition(entry.getKey(), position))
      .findAny()
      .flatMap(entry -> getMethodSymbol(entry.getValue())
        .map(methodSymbol -> Pair.of(methodSymbol, entry.getKey()))
      );
  }

  public Map<MethodSymbol, Collection<Range>> getCalledMethodSymbolsFrom(URI uri) {
    Map<MethodSymbol, Collection<Range>> methodSymbols = new HashMap<>();

    calleesFrom.getOrDefault(uri, MultiMapUtils.emptyMultiValuedMap()).asMap().forEach((multikey, value) ->
      getMethodSymbol(multikey).ifPresent(methodSymbol ->
        methodSymbols.put(methodSymbol, value)
      )
    );

    return methodSymbols;
  }

  @Synchronized
  public void clearCallees(URI uri) {
    String stringUri = uri.toString();

    calleesRanges.getOrDefault(uri, Collections.emptyMap()).values().forEach((MultiKey<String> multikey) -> {
      var key = new MultiKey<>(multikey.getKey(0), multikey.getKey(1));
      Collection<Location> locations = calleesOf.get(key).values();
      locations.removeIf(location -> location.getUri().equals(stringUri));
    });

    calleesFrom.remove(uri);
    calleesRanges.remove(uri);
  }

  @Synchronized
  public void addMethodCall(URI uri, ModuleType moduleType, MethodSymbol methodSymbol, Range range) {
    String mdoRef = methodSymbol.getMdoRef();
    String methodName = methodSymbol.getName().toLowerCase(Locale.ENGLISH);

    Location location = new Location(uri.toString(), range);

    MultiKey<String> key = getKey(mdoRef, moduleType);
    MultiKey<String> rangesKey = getRangesKey(mdoRef, moduleType, methodName);

    calleesOf.computeIfAbsent(key, k -> new ArrayListValuedHashMap<>()).put(methodName, location);
    calleesFrom.computeIfAbsent(uri, k -> new ArrayListValuedHashMap<>()).put(rangesKey, range);
    calleesRanges.computeIfAbsent(uri, k -> new HashMap<>()).put(range, rangesKey);
  }

  private Optional<MethodSymbol> getMethodSymbol(MultiKey<String> multikey) {
    String mdoRef = multikey.getKey(0);
    ModuleType moduleType = getModuleType(multikey.getKey(1));
    String methodName = multikey.getKey(2);

    return serverContext.getDocument(mdoRef, moduleType)
      .map(DocumentContext::getSymbolTree)
      .flatMap(symbolTree -> symbolTree.getMethodSymbol(methodName));
  }

  private static MultiKey<String> getKey(String mdoRef, ModuleType moduleType) {
    return new MultiKey<>(mdoRef, moduleType.getFileName());
  }

  private static MultiKey<String> getRangesKey(String mdoRef, ModuleType moduleType, String methodName) {
    return new MultiKey<>(mdoRef, moduleType.getFileName(), methodName);
  }

  private static ModuleType getModuleType(String filename) {
    return Arrays.stream(ModuleType.values())
      .filter(type -> type.getFileName().equals(filename))
      .findFirst()
      .get();
  }
}
