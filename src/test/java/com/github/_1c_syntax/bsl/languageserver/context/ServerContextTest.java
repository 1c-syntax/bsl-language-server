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
package com.github._1c_syntax.bsl.languageserver.context;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterEachTestMethod
class ServerContextTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @Test
  void scriptVariantLanguageFollowsTheConfiguration() {
    initServerContext(PATH_TO_METADATA);

    assertThat(context.getConfiguration().getScriptVariant()).isEqualTo(ScriptVariant.RUSSIAN);
    assertThat(context.getScriptVariantLanguage())
      .as("язык исходников проекта — из ScriptVariant конфигурации, а не из настроек LS")
      .isEqualTo(Language.RU);
  }

  @Test
  void withoutConfigurationScriptVariantLanguageFallsBackToTheServerLanguage() {
    // Проект без mdclasses-конфы: ScriptVariant взять неоткуда, остаётся язык сервера.
    initServerContext();
    var serverLanguage = context.getLanguageServerConfiguration().getLanguage();

    assertThat(context.getConfiguration().getConfigurationSource()).isEqualTo(ConfigurationSource.EMPTY);
    assertThat(context.getScriptVariantLanguage()).isEqualTo(serverLanguage);
  }

  @Test
  void testConfigurationMetadata() {
    // given
    initServerContext(PATH_TO_METADATA);

    // when
    var configurationMetadata = context.getConfiguration();

    // then
    assertThat(configurationMetadata).isNotNull();

    assertThat(configurationMetadata.getScriptVariant()).isEqualTo(ScriptVariant.RUSSIAN);

    assertThat(configurationMetadata.getConfigurationSource()).isEqualTo(ConfigurationSource.DESIGNER);

    assertThat(configurationMetadata.getCompatibilityMode().getMinor()).isEqualTo(3);
    assertThat(configurationMetadata.getCompatibilityMode().getVersion()).isEqualTo(10);

    File file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    ModuleType type = configurationMetadata.getModuleTypeByURI(Absolute.uri(file.toURI()));
    assertThat(type).isEqualTo(ModuleType.CommonModule);
  }

  @Test
  void testMdoRefs() {
    // given
    initServerContext(PATH_TO_METADATA);
    var mdoRefCommonModule = "CommonModule.ПервыйОбщийМодуль";

    var documentContext = addDocumentContext(context, PATH_TO_MODULE_FILE);

    // then
    assertThat(context.getDocument(mdoRefCommonModule, documentContext.getModuleType()))
      .isPresent()
      .get()
      .isEqualTo(documentContext);
    assertThat(context.getDocuments(mdoRefCommonModule))
      .hasSize(1)
      .containsKey(documentContext.getModuleType())
      .containsValue(documentContext);

    // given — документы справочника; повтор добавления того же файла для проверки на дубль
    addDocumentContext(context, PATH_TO_CATALOG_MODULE_FILE);
    addDocumentContext(context, PATH_TO_CATALOG_FILE);

    // для проверки на дубль
    addDocumentContext(context, PATH_TO_CATALOG_FILE);

    // then
    assertThat(context.getDocuments("Catalog.Справочник1"))
      .hasSize(2)
      .containsKeys(ModuleType.ManagerModule, ModuleType.ObjectModule);

    // when
    context.removeDocument(Absolute.uri(new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE)));

    // then
    assertThat(context.getDocument(mdoRefCommonModule, ModuleType.CommonModule))
      .isNotPresent();
  }

  @Test
  void testErrorConfigurationMetadata() {
    // given
    Path path = Absolute.path(PATH_TO_METADATA + "test");

    initServerContext(path);

    // when
    var configurationMetadata = context.getConfiguration();

    // then
    assertThat(configurationMetadata).isNotNull();
    assertThat(configurationMetadata.getModulesByType()).isEmpty();
  }

  @Test
  void testPopulateContext() {
    // given
    initServerContext(PATH_TO_METADATA, false);
    assertThat(context.getDocuments()).isEmpty();

    // when
    context.populateContext();

    // then
    assertThat(context.getDocuments()).hasSizeGreaterThan(0);
  }

  /** Каталоги, перечисленные в {@code excludePaths} конфигурации, не попадают в начальный контекст. */
  @Test
  void testPopulateContextExcludesPathsFromConfig() {
    // given
    Path path = Absolute.path(PATH_TO_METADATA);
    initServerContext(path, false);
    context.getLanguageServerConfiguration().setExcludePaths(List.of("CommonModules"));

    // when
    context.populateContext();

    // then
    var documents = context.getDocuments();
    assertThat(documents).isNotEmpty();
    var commonModuleUris = documents.keySet().stream()
      .map(URI::getPath)
      .filter(p -> p.contains("CommonModules"))
      .toList();
    assertThat(commonModuleUris).isEmpty();
  }

  @Test
  void configurationReadInBackgroundIsNotAwaited() {
    // given
    var finishPreparation = startPreparationInBackground();
    var seenByPreparation = new AtomicReference<ConfigurationSource>();
    context.prepareInBackground(() -> {
      awaitUninterruptibly(finishPreparation);
      seenByPreparation.set(context.getConfiguration().getConfigurationSource());
    });

    // when
    var seenMeanwhile = context.getConfiguration().getConfigurationSource();
    var documentContext = addDocumentContext(context, PATH_TO_MODULE_FILE);
    var moduleTypeMeanwhile = documentContext.getModuleType();
    finishPreparation.countDown();
    context.awaitPreparation();

    // then
    assertThat(seenMeanwhile).isEqualTo(ConfigurationSource.EMPTY);
    assertThat(moduleTypeMeanwhile).isEqualTo(ModuleType.UNKNOWN);
    assertThat(seenByPreparation.get()).isEqualTo(ConfigurationSource.DESIGNER);
    assertThat(documentContext.getModuleType()).isEqualTo(ModuleType.CommonModule);
    assertThat(context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule))
      .contains(documentContext);
  }

  @Test
  void documentOpenedWhilePreparingIsReparsedAfterPreparation() throws IOException {
    // given
    var finishPreparation = startPreparationInBackground();
    context.prepareInBackground(() -> {
      awaitUninterruptibly(finishPreparation);
      context.getConfiguration();
    });
    var file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    var documentContext = context.addDocument(Absolute.uri(file));
    context.openDocument(documentContext, Files.readString(file.toPath()), 1);
    var symbolTreeMeanwhile = documentContext.getSymbolTree();

    // when
    finishPreparation.countDown();
    context.awaitPreparation();

    // then
    assertThat(documentContext.getSymbolTree()).isNotSameAs(symbolTreeMeanwhile);
    assertThat(documentContext.getModuleType()).isEqualTo(ModuleType.CommonModule);
    assertThat(context.getDocumentState(documentContext)).isEqualTo(DocumentState.WITH_CONTENT);
  }

  /**
   * Рабочая область с конфигурацией, ещё не прочитанной: чтение достанется подготовке в фоне.
   *
   * @return защёлка, по которой подготовка в тесте заканчивается.
   */
  private CountDownLatch startPreparationInBackground() {
    initServerContext(PATH_TO_METADATA, false);
    // Добавление рабочей области уже прочитало конфигурацию — забываем её.
    context.clear();
    return new CountDownLatch(1);
  }

  private static void awaitUninterruptibly(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private DocumentContext addDocumentContext(ServerContext serverContext, String path) {
    var file = new File(PATH_TO_METADATA, path);
    var uri = Absolute.uri(file);
    var documentContext = serverContext.addDocument(uri);
    serverContext.rebuildDocument(documentContext);
    return documentContext;
  }
}
