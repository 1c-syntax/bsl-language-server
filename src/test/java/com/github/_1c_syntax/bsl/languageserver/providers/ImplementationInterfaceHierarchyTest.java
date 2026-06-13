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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Иерархия интерфейсов библиотеки extends: интерфейс может расширять другой
 * интерфейс через {@code &Расширяет}. Переход к реализациям базового интерфейса
 * должен находить и прямые реализации, и реализации производных интерфейсов
 * (транзитивно по {@code &Расширяет} интерфейса).
 */
@CleanupContextBeforeClassAndAfterClass
class ImplementationInterfaceHierarchyTest extends AbstractServerContextAwareTest {

  @Autowired
  private ImplementationProvider provider;

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/interface-hierarchy-lib").toAbsolutePath();

  @BeforeEach
  void init() {
    initServerContext(FIXTURE_ROOT, false);
  }

  @Test
  void baseInterfaceImplementationsIncludeImplementorsOfDerivedInterfaces() {
    var interfaceDoc = document("БазовыйИнтерфейс.os");
    var params = params(interfaceDoc, new Position(0, 0));

    var implementations = provider.getImplementations(interfaceDoc, params);

    // Прямая реализация базового интерфейса и реализация производного интерфейса
    // (производный &Расширяет базовый — значит его реализатор реализует и базовый).
    assertThat(implementations)
      .extracting(ImplementationInterfaceHierarchyTest::uriBaseName)
      .containsExactlyInAnyOrder("РеализацияБазового", "РеализацияПроизводного");
  }

  @Test
  void derivedInterfaceImplementationsDoNotIncludeBaseOnlyImplementors() {
    var interfaceDoc = document("ПроизводныйИнтерфейс.os");
    var params = params(interfaceDoc, new Position(0, 0));

    var implementations = provider.getImplementations(interfaceDoc, params);

    // Реализатор только базового интерфейса не является реализатором производного.
    assertThat(implementations)
      .extracting(ImplementationInterfaceHierarchyTest::uriBaseName)
      .containsExactly("РеализацияПроизводного");
  }

  @Test
  void baseInterfaceMethodImplementationsResolveAcrossInterfaceHierarchy() {
    var interfaceDoc = document("БазовыйИнтерфейс.os");
    var params = params(interfaceDoc, methodPosition(interfaceDoc, "БазовыйМетод"));

    var implementations = provider.getImplementations(interfaceDoc, params);

    // Метод БазовыйМетод реализован в обоих классах.
    assertThat(implementations)
      .extracting(ImplementationInterfaceHierarchyTest::uriBaseName)
      .containsExactlyInAnyOrder("РеализацияБазового", "РеализацияПроизводного");
  }

  private DocumentContext document(String fileName) {
    var uri = Absolute.uri(FIXTURE_ROOT.resolve("src").resolve(fileName).toUri());
    var documentContext = context.getDocument(uri);
    assertThat(documentContext).as("document %s must be populated", fileName).isNotNull();
    return documentContext;
  }

  private static Position methodPosition(DocumentContext documentContext, String methodName) {
    var method = documentContext.getSymbolTree().getMethodSymbol(methodName).orElseThrow();
    return method.getSelectionRange().getStart();
  }

  private static ImplementationParams params(DocumentContext documentContext, Position position) {
    var params = new ImplementationParams();
    params.setTextDocument(new TextDocumentIdentifier(documentContext.getUri().toString()));
    params.setPosition(position);
    return params;
  }

  private static String uriBaseName(Location location) {
    var path = URI.create(location.getUri()).getPath();
    var name = path.substring(path.lastIndexOf('/') + 1);
    return name.endsWith(".os") ? name.substring(0, name.length() - ".os".length()) : name;
  }
}
