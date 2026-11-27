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
package com.github._1c_syntax.bsl.languageserver.lsp;

import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Подсказки для native-image к JSON-RPC слою LSP4J.
 * <p>
 * LSP4J находит методы сервисов по аннотациям ({@code @JsonRequest}, {@code @JsonNotification},
 * {@code @JsonDelegate}) и вызывает их рефлексией, а сообщения протокола сериализует Gson'ом по
 * полям. Регистрируются все классы LSP4J и классы протокола сервера, найденные на classpath во
 * время AOT-обработки, и сервисы сервера.
 */
class Lsp4jRuntimeHints implements RuntimeHintsRegistrar {

  private static final List<String> PROTOCOL_PACKAGES = List.of(
    "org.eclipse.lsp4j",
    "com.github._1c_syntax.bsl.languageserver.jsonrpc"
  );

  private static final List<Class<?>> SERVICES = List.of(
    BSLLanguageServer.class,
    BSLTextDocumentService.class,
    BSLWorkspaceService.class
  );

  private static final MemberCategory[] PROTOCOL_MEMBERS = {
    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
    MemberCategory.ACCESS_DECLARED_FIELDS,
    MemberCategory.INVOKE_PUBLIC_METHODS
  };

  @Override
  public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
    PROTOCOL_PACKAGES.stream()
      .flatMap(basePackage -> classes(basePackage, classLoader))
      .forEach(type -> hints.reflection().registerType(type, PROTOCOL_MEMBERS));
    SERVICES.forEach(service -> hints.reflection().registerType(service, MemberCategory.INVOKE_PUBLIC_METHODS));
  }

  private static Stream<Class<?>> classes(String basePackage, @Nullable ClassLoader classLoader) {
    var scanner = new ClassPathScanningCandidateComponentProvider(false) {
      @Override
      protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return true;
      }
    };
    scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
    scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
    return scanner.findCandidateComponents(basePackage).stream()
      .map(BeanDefinition::getBeanClassName)
      .filter(Objects::nonNull)
      .map(className -> ClassUtils.resolveClassName(className, classLoader));
  }
}
