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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.parser.description.TypeDescription;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Индекс декларативных типов символов.
 * <p>
 * Для {@link MethodSymbol} eagerly кэширует return-types из
 * {@code MethodDescription.returnedValue}. Для {@link ParameterDefinition} типы
 * читаются on-demand из {@code ParameterDescription.types()} —
 * это дёшево и не требует отдельного хранения.
 * <p>
 * Типы переменных в этом индексе не хранятся: они вычисляются
 * {@code ExpressionTypeInferencer}'ом по выражению инициализации.
 */
@Component
@RequiredArgsConstructor
public class SymbolTypeIndex {

  private final TypeRegistry typeRegistry;

  private final Map<MethodSymbol, TypeSet> declaredReturnTypes = new ConcurrentHashMap<>();
  private final Map<URI, List<MethodSymbol>> indexedByUri = new ConcurrentHashMap<>();

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    var uri = documentContext.getUri();

    clear(uri);

    var collected = new ArrayList<MethodSymbol>();
    indexMethodsRecursive(documentContext.getSymbolTree().getModule(), collected);
    indexedByUri.put(uri, collected);
  }

  /**
   * @return объявленные типы возвращаемого значения метода либо пустой {@link TypeSet}.
   */
  public TypeSet getDeclaredReturnTypes(MethodSymbol method) {
    return declaredReturnTypes.getOrDefault(method, TypeSet.EMPTY);
  }

  /**
   * @return типы параметра, объявленные в описании метода. Вычисляется on-demand —
   *         декларации параметров уже распарсены парсером.
   */
  public TypeSet getDeclaredParameterTypes(ParameterDefinition parameter) {
    return parameter.getDescription()
      .map(descr -> resolveTypes(descr.types()))
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Очистить записи, относящиеся к данному URI.
   */
  public void clear(URI uri) {
    var methods = indexedByUri.remove(uri);
    if (methods == null) {
      return;
    }
    for (var m : methods) {
      declaredReturnTypes.remove(m);
    }
  }

  private void indexMethodsRecursive(SourceDefinedSymbol parent, List<MethodSymbol> collected) {
    if (parent instanceof MethodSymbol method) {
      method.getDescription().ifPresent(descr -> {
        var returnTypes = resolveTypes(descr.getReturnedValue());
        if (!returnTypes.isEmpty()) {
          declaredReturnTypes.put(method, returnTypes);
          collected.add(method);
        }
      });
    }
    for (var child : parent.getChildren()) {
      indexMethodsRecursive(child, collected);
    }
  }

  private TypeSet resolveTypes(List<? extends TypeDescription> descriptions) {
    if (descriptions == null || descriptions.isEmpty()) {
      return TypeSet.EMPTY;
    }
    Set<TypeRef> refs = new LinkedHashSet<>();
    for (var td : descriptions) {
      resolveOne(td.name()).ifPresent(refs::add);
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  private Optional<TypeRef> resolveOne(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return typeRegistry.resolve(name)
      .or(() -> Optional.of(typeRegistry.intern(TypeKind.USER, name)));
  }
}
