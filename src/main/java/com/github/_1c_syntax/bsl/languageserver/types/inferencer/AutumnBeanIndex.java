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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndexedEvent;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Индекс желудей фреймворка «ОСень»: отображение «имя/прозвище желудя → тип».
 * <p>
 * Покрывает случаи, которые нельзя разрешить прямым резолвом имени типа:
 * <ul>
 *   <li>переименованный компонент — {@code &Желудь("ДругоеИмя")};</li>
 *   <li>фабричный метод — {@code &Завязь};</li>
 *   <li>прозвища — {@code &Прозвище("Алиас")} (повторяемая);</li>
 *   <li>приоритет при конфликте имён/прозвищ — {@code &Верховный}.</li>
 * </ul>
 * Строится лениво из {@link OScriptLibraryIndex} (классы-желуди) и сбрасывается
 * при переиндексации библиотек.
 */
@Component
@Scope(value = WorkspaceScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class AutumnBeanIndex {

  private final OScriptLibraryIndex libraryIndex;
  private final ServerContextProvider serverContextProvider;
  private final TypeRegistry typeRegistry;

  private volatile Map<String, List<BeanCandidate>> beansByName;

  /**
   * Кандидат-желудь: тип компонента и признак приоритетного ({@code &Верховный}).
   */
  private record BeanCandidate(TypeRef type, boolean primary) {
  }

  /**
   * Разрешить тип желудя по его имени или прозвищу.
   *
   * @return тип(ы) желудя; при конфликте имён предпочитаются помеченные
   *         {@code &Верховный}, иначе объединяются все кандидаты. Пусто, если
   *         желудь с таким именем не найден.
   */
  public TypeSet resolve(String name) {
    if (name == null || name.isBlank()) {
      return TypeSet.EMPTY;
    }
    var candidates = index().get(name.toLowerCase(Locale.ROOT));
    if (candidates == null || candidates.isEmpty()) {
      return TypeSet.EMPTY;
    }

    var refs = new LinkedHashSet<TypeRef>();
    for (var candidate : candidates) {
      if (candidate.primary()) {
        refs.add(candidate.type());
      }
    }
    if (refs.isEmpty()) {
      for (var candidate : candidates) {
        refs.add(candidate.type());
      }
    }
    return TypeSet.of(refs);
  }

  /**
   * Сбросить индекс — будет перестроен при следующем обращении.
   */
  @EventListener(OScriptLibraryIndexedEvent.class)
  public void invalidate() {
    beansByName = null;
  }

  private Map<String, List<BeanCandidate>> index() {
    var local = beansByName;
    if (local == null) {
      synchronized (this) {
        local = beansByName;
        if (local == null) {
          local = build();
          beansByName = local;
        }
      }
    }
    return local;
  }

  private Map<String, List<BeanCandidate>> build() {
    Map<String, List<BeanCandidate>> map = new LinkedHashMap<>();
    for (var entry : libraryIndex.findEntries(EntryKind.CLASS)) {
      var serverContext = serverContextProvider.getServerContext(entry.uri()).orElse(null);
      if (serverContext == null) {
        continue;
      }
      DocumentContext document = serverContext.getDocument(entry.uri());
      if (document == null) {
        continue;
      }
      var ownerType = typeRegistry.resolve(entry.qualifiedName()).orElse(null);
      for (var method : document.getSymbolTree().getMethods()) {
        registerComponent(map, method, entry.qualifiedName(), ownerType);
        registerFactory(map, method);
      }
    }
    return map;
  }

  private void registerComponent(
    Map<String, List<BeanCandidate>> map,
    MethodSymbol method,
    String defaultName,
    TypeRef ownerType
  ) {
    var annotations = method.getAnnotations();
    var component = AutumnAnnotations.find(annotations, AutumnAnnotations.COMPONENT);
    if (component == null || ownerType == null) {
      return;
    }
    var name = AutumnAnnotations.stringParameter(component, AutumnAnnotations.VALUE_PARAMETER, 0);
    if (name == null || name.isBlank()) {
      name = defaultName;
    }
    register(map, annotations, name, ownerType);
  }

  private void registerFactory(Map<String, List<BeanCandidate>> map, MethodSymbol method) {
    var annotations = method.getAnnotations();
    var factory = AutumnAnnotations.find(annotations, AutumnAnnotations.FACTORY);
    if (factory == null) {
      return;
    }
    var name = AutumnAnnotations.stringParameter(factory, AutumnAnnotations.VALUE_PARAMETER, 0);
    if (name == null || name.isBlank()) {
      name = method.getName();
    }
    var beanType = factoryBeanType(factory, name);
    if (beanType == null) {
      return;
    }
    register(map, annotations, name, beanType);
  }

  private TypeRef factoryBeanType(Annotation factory, String beanName) {
    var explicitType = AutumnAnnotations.stringParameter(factory, AutumnAnnotations.TYPE_PARAMETER, 1);
    if (explicitType != null && !explicitType.isBlank() && !AutumnAnnotations.BEAN_TYPE.equalsIgnoreCase(explicitType)) {
      return typeRegistry.resolve(explicitType).orElse(null);
    }
    return typeRegistry.resolve(beanName).orElse(null);
  }

  private void register(
    Map<String, List<BeanCandidate>> map,
    List<Annotation> annotations,
    String primaryName,
    TypeRef type
  ) {
    var primary = AutumnAnnotations.has(annotations, AutumnAnnotations.PRIMARY);
    var candidate = new BeanCandidate(type, primary);

    addCandidate(map, primaryName, candidate);
    for (var alias : AutumnAnnotations.values(annotations, AutumnAnnotations.QUALIFIER)) {
      addCandidate(map, alias, candidate);
    }
  }

  private static void addCandidate(
    Map<String, List<BeanCandidate>> map,
    String name,
    BeanCandidate candidate
  ) {
    map.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(candidate);
  }
}
