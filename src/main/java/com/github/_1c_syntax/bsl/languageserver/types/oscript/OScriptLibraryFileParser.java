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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Methods;
import com.github._1c_syntax.utils.Absolute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Загружает .os-файл библиотеки в указанный {@link ServerContext} как обычный
 * {@link DocumentContext} (через {@code addDocument} + {@code rebuildDocument}),
 * а затем извлекает из его {@code SymbolTree} экспортные методы, экспортные
 * переменные и (для классов) конструктор {@code ПриСозданииОбъекта}.
 * <p>
 * Никакого ручного разбора AST: используется уже готовый
 * {@link com.github._1c_syntax.bsl.languageserver.context.computer.MethodSymbolComputer}
 * со всем разбором описаний параметров и возвращаемых типов.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OScriptLibraryFileParser {

  private final TypeRegistry typeRegistry;

  /**
   * Распарсить .os-файл и достать из него библиотечные метаданные.
   *
   * @param osFile        путь к файлу
   * @param serverContext контекст workspace, в который будет добавлен файл
   * @return метаданные либо {@code Optional.empty()}, если файл не удалось
   *         загрузить
   */
  public Optional<OScriptLibraryFile> parse(Path osFile, ServerContext serverContext) {
    URI uri = Absolute.uri(osFile.toUri());
    DocumentContext dc;
    try {
      dc = serverContext.addDocument(uri);
      serverContext.rebuildDocument(dc);
    } catch (RuntimeException e) {
      LOGGER.warn("Failed to load oscript library file: {}", osFile, e);
      return Optional.empty();
    }
    return Optional.of(extract(dc));
  }

  /**
   * Извлечь метаданные библиотеки из уже разобранного {@link DocumentContext}.
   * <p>
   * Используется для повторной индексации при изменении содержимого файла,
   * чтобы не вызывать {@code rebuildDocument} ещё раз (он уже выполнен и сам
   * породил событие изменения).
   */
  public OScriptLibraryFile parseFromDocumentContext(DocumentContext documentContext) {
    return extract(documentContext);
  }

  private OScriptLibraryFile extract(DocumentContext dc) {
    var symbolTree = dc.getSymbolTree();
    var constructorSymbol = Methods.getOscriptClassConstructor(symbolTree);
    Optional<MethodInfo> constructor = constructorSymbol.map(this::toInfo);
    var methods = new ArrayList<MethodInfo>();
    for (var ms : symbolTree.getMethods()) {
      if (constructorSymbol.isPresent() && ms == constructorSymbol.get()) {
        continue;
      }
      if (ms.isExport()) {
        methods.add(toInfo(ms));
      }
    }
    var exportVars = new ArrayList<String>();
    for (VariableSymbol v : symbolTree.getVariables()) {
      if (v.isExport()) {
        exportVars.add(v.getName());
      }
    }
    return new OScriptLibraryFile(methods, exportVars, constructor);
  }

  private static String buildParameterDescription(com.github._1c_syntax.bsl.parser.description.ParameterDescription pd) {
    // pd.element() — это просто маркер (range/type) без текста, его toString() выглядит мусором.
    // Полезные тексты описания лежат в TypeDescription.description() каждого варианта типа.
    var typeDescriptions = pd.types();
    if (typeDescriptions == null || typeDescriptions.isEmpty()) {
      return "";
    }
    var sb = new StringBuilder();
    for (var td : typeDescriptions) {
      var text = td.description();
      if (text == null || text.isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(text.trim());
    }
    return sb.toString();
  }

  private MethodInfo toInfo(MethodSymbol ms) {
    var params = new ArrayList<ParameterDescriptor>();
    var paramDescs = ms.getDescription()
      .map(d -> d.getParameters())
      .orElse(List.of());
    var paramDefs = ms.getParameters();
    for (int i = 0; i < paramDefs.size(); i++) {
      var def = paramDefs.get(i);
      String description = "";
      TypeSet types = TypeSet.EMPTY;
      if (i < paramDescs.size()) {
        var pd = paramDescs.get(i);
        types = resolveTypes(pd.types());
        description = buildParameterDescription(pd);
      }
      params.add(new ParameterDescriptor(def.getName(), types, def.isOptional(), description));
    }
    TypeRef returnType = TypeRef.UNKNOWN;
    if (ms.isFunction()) {
      var returnTypes = ms.getDescription()
        .map(d -> d.getReturnedValue())
        .orElse(List.of());
      var resolved = resolveTypes(returnTypes);
      if (!resolved.refs().isEmpty()) {
        returnType = resolved.refs().iterator().next();
      }
    }
    var signature = new SignatureDescriptor(params, returnType, descriptionText(ms));
    return new MethodInfo(ms.getName(), ms.isExport(), ms.isFunction(), List.of(signature));
  }

  private static String descriptionText(MethodSymbol ms) {
    return ms.getDescription()
      .map(d -> d.getPurposeDescription())
      .orElse("");
  }

  private TypeSet resolveTypes(List<com.github._1c_syntax.bsl.parser.description.TypeDescription> types) {
    if (types == null || types.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new ArrayList<TypeRef>();
    for (var td : types) {
      typeRegistry.resolve(td.name()).ifPresent(refs::add);
    }
    return refs.isEmpty() ? TypeSet.EMPTY : TypeSet.of(refs);
  }

  /**
   * Результат парсинга одного .os-файла.
   */
  public record OScriptLibraryFile(
    List<MethodInfo> exportMethods,
    List<String> exportVars,
    Optional<MethodInfo> constructor
  ) {
  }

  /**
   * Метаданные одного метода .os-файла.
   */
  public record MethodInfo(
    String name,
    boolean export,
    boolean function,
    List<SignatureDescriptor> signatures
  ) {
  }
}
