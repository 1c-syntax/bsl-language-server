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

import com.github._1c_syntax.bsl.languageserver.lsp.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.DefinitionCapabilities;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class DefinitionProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private DefinitionProvider definitionProvider;

  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/definition.bsl";
  private static final String PATH_TO_COMMON_MODULE_FILE = "./src/test/resources/providers/definitionCommonModule.bsl";

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
    // По умолчанию заявляем клиентскую поддержку linkSupport, чтобы навигационные тесты
    // получали LocationLink[]; отдельный тест проверяет понижение до Location[].
    setClientLinkSupport(true);
  }

  /**
   * Настраивает заявленную клиентом возможность {@code textDocument.definition.linkSupport}
   * и пересчитывает её кэш в провайдере через {@code handleInitializeEvent}.
   *
   * @param linkSupport {@code true}, если клиент поддерживает {@link LocationLink}
   */
  private void setClientLinkSupport(boolean linkSupport) {
    var capabilities = new ClientCapabilities();
    var textDocumentCapabilities = new TextDocumentClientCapabilities();
    textDocumentCapabilities.setDefinition(new DefinitionCapabilities(false, linkSupport));
    capabilities.setTextDocument(textDocumentCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    definitionProvider.handleInitializeEvent();
  }

  @Test
  void testEmptyDefinition() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new DefinitionParams();
    params.setPosition(new Position(1, 0));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).isEmpty();
  }

  @Test
  void testDefinitionOfLocalMethod() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяФункции").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(4, 9));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).hasSize(1);

    var definition = definitions.getRight().get(0);

    assertThat(definition.getTargetUri()).isEqualTo(documentContext.getUri().toString());
    assertThat(definition.getTargetSelectionRange()).isEqualTo(methodSymbol.getSelectionRange());
    assertThat(definition.getTargetRange()).isEqualTo(methodSymbol.getRange());
    assertThat(definition.getOriginSelectionRange()).isEqualTo(Ranges.create(4, 0, 10));
  }

  @Test
  void testDefinitionOfManagerModuleMethod() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var managerModule = context.getDocument("Catalog.Справочник1", ModuleType.ManagerModule).orElseThrow();
    var methodSymbol = managerModule.getSymbolTree().getMethodSymbol("ТестЭкспортная").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(6, 30));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).hasSize(1);

    var definition = definitions.getRight().get(0);

    assertThat(definition.getTargetUri()).isEqualTo(managerModule.getUri().toString());
    assertThat(definition.getTargetSelectionRange()).isEqualTo(methodSymbol.getSelectionRange());
    assertThat(definition.getTargetRange()).isEqualTo(methodSymbol.getRange());
    assertThat(definition.getOriginSelectionRange()).isEqualTo(Ranges.create(6, 24, 38));
  }

  @Test
  void testDefinitionOfCommonModuleName() {
    // Тест: клик на "ПервыйОбщийМодуль" в "ПервыйОбщийМодуль.НеУстаревшаяПроцедура()"
    // должен вести к модулю ПервыйОбщийМодуль
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_COMMON_MODULE_FILE);
    var commonModule = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var moduleSymbol = commonModule.getSymbolTree().getModule();

    var params = new DefinitionParams();
    // Position on "ПервыйОбщийМодуль" (line 2, columns 0-17)
    params.setPosition(new Position(1, 5));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).hasSize(1);

    var definition = definitions.getRight().get(0);

    assertThat(definition.getTargetUri()).isEqualTo(commonModule.getUri().toString());
    assertThat(definition.getTargetSelectionRange()).isEqualTo(moduleSymbol.getSelectionRange());
    assertThat(definition.getTargetRange()).isEqualTo(moduleSymbol.getRange());
    // "ПервыйОбщийМодуль" spans 17 characters
    assertThat(definition.getOriginSelectionRange()).isEqualTo(Ranges.create(1, 0, 17));
  }

  @Test
  void definitionReturnsLocationLinksWhenClientSupportsLinkSupport() {
    // given - клиент, заявивший textDocument.definition.linkSupport
    setClientLinkSupport(true);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяФункции").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(4, 9));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then - клиент с поддержкой связей получает LocationLink[]
    assertThat(definitions.isRight()).isTrue();
    assertThat(definitions.getRight()).hasSize(1);

    var locationLink = definitions.getRight().get(0);
    assertThat(locationLink).isInstanceOf(LocationLink.class);
    assertThat(locationLink.getTargetUri()).isEqualTo(documentContext.getUri().toString());
    assertThat(locationLink.getTargetSelectionRange()).isEqualTo(methodSymbol.getSelectionRange());
    assertThat(locationLink.getTargetRange()).isEqualTo(methodSymbol.getRange());
    assertThat(locationLink.getOriginSelectionRange()).isEqualTo(Ranges.create(4, 0, 10));
  }

  @Test
  void definitionDowngradesToLocationsWhenClientLacksLinkSupport() {
    // given - клиент без textDocument.definition.linkSupport
    setClientLinkSupport(false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяФункции").orElseThrow();

    var params = new DefinitionParams();
    params.setPosition(new Position(4, 9));

    // when
    var definitions = definitionProvider.getDefinition(documentContext, params);

    // then - результат понижается до Location[] с targetUri и targetSelectionRange
    assertThat(definitions.isLeft()).isTrue();
    assertThat(definitions.getLeft()).hasSize(1);
    assertThat(definitions.getLeft().get(0))
      .isEqualTo(new Location(documentContext.getUri().toString(), methodSymbol.getSelectionRange()));
  }
}
