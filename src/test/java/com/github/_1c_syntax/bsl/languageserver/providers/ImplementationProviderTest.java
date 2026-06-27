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

import com.github._1c_syntax.bsl.languageserver.client.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.ImplementationCapabilities;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@CleanupContextBeforeClassAndAfterClass
class ImplementationProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ImplementationProvider provider;

  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  private static final Path FIXTURE_ROOT =
    Path.of("src/test/resources/oscript-libraries/interface-lib").toAbsolutePath();

  @BeforeEach
  void init() {
    initServerContext(FIXTURE_ROOT, false);
    // По умолчанию клиент не заявляет linkSupport: унаследованные тесты навигации
    // получают Location[]; отдельные тесты проверяют ответ LocationLink[].
    setClientLinkSupport(false);
  }

  /**
   * Настраивает заявленную клиентом возможность {@code textDocument.implementation.linkSupport}
   * и пересчитывает её кэш в провайдере через {@code handleInitializeEvent}.
   *
   * @param linkSupport {@code true}, если клиент поддерживает {@link LocationLink}
   */
  private void setClientLinkSupport(boolean linkSupport) {
    var capabilities = new ClientCapabilities();
    var textDocumentCapabilities = new TextDocumentClientCapabilities();
    var implementationCapabilities = new ImplementationCapabilities();
    implementationCapabilities.setLinkSupport(linkSupport);
    textDocumentCapabilities.setImplementation(implementationCapabilities);
    capabilities.setTextDocument(textDocumentCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    provider.handleInitializeEvent();
  }

  @Test
  void implementationOnInterfaceMethodReturnsImplementingMethods() {
    // given
    var interfaceDoc = document("МойИнтерфейс.os");
    // Позиция на объявлении метода МойМетод в интерфейсе.
    var params = params(interfaceDoc, methodPosition(interfaceDoc, "МойМетод"));

    // when
    var implementations = provider.getImplementations(interfaceDoc, params).getLeft();

    // then
    assertThat(implementations)
      .hasSize(2)
      .allMatch(location -> location.getUri().endsWith(".os"));
    assertThat(implementations)
      .extracting(location -> uriBaseName(location))
      .containsExactlyInAnyOrder("Реализация1", "Реализация2");
  }

  @Test
  void implementationOnInterfaceBodyReturnsImplementingClasses() {
    // given
    var interfaceDoc = document("МойИнтерфейс.os");
    // Позиция вне метода (на конструкторе) — переход к самим классам.
    var params = params(interfaceDoc, new Position(0, 0));

    // when
    var implementations = provider.getImplementations(interfaceDoc, params).getLeft();

    // then
    assertThat(implementations)
      .extracting(location -> uriBaseName(location))
      .containsExactlyInAnyOrder("Реализация1", "Реализация2");
  }

  @Test
  void implementationOnNonInterfaceIsEmpty() {
    // given
    var implementorDoc = document("Реализация1.os");
    var params = params(implementorDoc, new Position(0, 0));

    // when
    var implementations = provider.getImplementations(implementorDoc, params).getLeft();

    // then
    assertThat(implementations).isEmpty();
  }

  @Test
  void implementationReturnsLocationLinksWhenClientSupportsLinkSupport() {
    // given - клиент, заявивший textDocument.implementation.linkSupport, и курсор
    // на экспортном методе интерфейса.
    setClientLinkSupport(true);
    var interfaceDoc = document("МойИнтерфейс.os");
    var interfaceMethod = interfaceDoc.getSymbolTree().getMethodSymbol("МойМетод").orElseThrow();
    var params = params(interfaceDoc, interfaceMethod.getSelectionRange().getStart());

    // when
    var implementations = provider.getImplementations(interfaceDoc, params).getRight();

    // then - клиент с поддержкой связей получает LocationLink[] с диапазоном-источником
    // под курсором и корректными диапазонами цели.
    assertThat(implementations)
      .hasSize(2)
      .allSatisfy(link -> {
        assertThat(link).isInstanceOf(LocationLink.class);
        assertThat(link.getTargetUri()).endsWith(".os");
        assertThat(link.getOriginSelectionRange()).isEqualTo(interfaceMethod.getSelectionRange());
        assertThat(link.getTargetRange()).isNotNull();
        assertThat(link.getTargetSelectionRange()).isNotNull();
      });
    assertThat(implementations)
      .extracting(ImplementationProviderTest::linkBaseName)
      .containsExactlyInAnyOrder("Реализация1", "Реализация2");

    // Диапазон цели метода-реализатора совпадает с его символом.
    var implementor1 = document("Реализация1.os");
    var implementorMethod = implementor1.getSymbolTree().getMethodSymbol("МойМетод").orElseThrow();
    assertThat(implementations)
      .filteredOn(link -> "Реализация1".equals(linkBaseName(link)))
      .singleElement()
      .satisfies(link -> {
        assertThat(link.getTargetUri()).isEqualTo(implementor1.getUri().toString());
        assertThat(link.getTargetRange()).isEqualTo(implementorMethod.getRange());
        assertThat(link.getTargetSelectionRange()).isEqualTo(implementorMethod.getSelectionRange());
      });
  }

  @Test
  void implementationDowngradesToLocationsWhenClientLacksLinkSupport() {
    // given - клиент без textDocument.implementation.linkSupport
    setClientLinkSupport(false);
    var interfaceDoc = document("МойИнтерфейс.os");
    var params = params(interfaceDoc, methodPosition(interfaceDoc, "МойМетод"));

    // when
    var implementations = provider.getImplementations(interfaceDoc, params);

    // then - результат понижается до Location[] с targetUri и targetSelectionRange
    assertThat(implementations.isLeft()).isTrue();
    assertThat(implementations.getLeft())
      .hasSize(2)
      .extracting(location -> uriBaseName(location))
      .containsExactlyInAnyOrder("Реализация1", "Реализация2");

    var implementor1 = document("Реализация1.os");
    var implementorMethod = implementor1.getSymbolTree().getMethodSymbol("МойМетод").orElseThrow();
    assertThat(implementations.getLeft())
      .filteredOn(location -> "Реализация1".equals(uriBaseName(location)))
      .singleElement()
      .isEqualTo(new Location(implementor1.getUri().toString(), implementorMethod.getSelectionRange()));
  }

  @Test
  void implementationLocationLinkOnInterfaceBodyHasNoOriginSelectionRange() {
    // given - клиент с linkSupport и курсор в теле интерфейса (не на методе):
    // под курсором нет идентификатора, переход — к самим классам.
    setClientLinkSupport(true);
    var interfaceDoc = document("МойИнтерфейс.os");
    var params = params(interfaceDoc, new Position(0, 0));

    // when
    var implementations = provider.getImplementations(interfaceDoc, params).getRight();

    // then - связи на классы без originSelectionRange
    assertThat(implementations)
      .hasSize(2)
      .allSatisfy(link -> assertThat(link.getOriginSelectionRange()).isNull());
    assertThat(implementations)
      .extracting(ImplementationProviderTest::linkBaseName)
      .containsExactlyInAnyOrder("Реализация1", "Реализация2");
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
    return baseName(location.getUri());
  }

  private static String linkBaseName(LocationLink link) {
    return baseName(link.getTargetUri());
  }

  private static String baseName(String uri) {
    var path = URI.create(uri).getPath();
    var name = path.substring(path.lastIndexOf('/') + 1);
    return name.endsWith(".os") ? name.substring(0, name.length() - ".os".length()) : name;
  }
}
