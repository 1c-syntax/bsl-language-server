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
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.ExpressionTypeInferencer;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.github._1c_syntax.utils.Absolute;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;

@CleanupContextBeforeClassAndAfterEachTestMethod
class ServerContextTest extends AbstractServerContextAwareTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata/designer";
  private static final String PATH_TO_MODULE_FILE = "CommonModules/ПервыйОбщийМодуль/Ext/Module.bsl";
  private static final String PATH_TO_CATALOG_FILE = "Catalogs/Справочник1/Ext/ManagerModule.bsl";
  private static final String PATH_TO_CATALOG_MODULE_FILE = "Catalogs/Справочник1/Ext/ObjectModule.bsl";

  @MockitoSpyBean(name = "computeConfigurationExecutor")
  private ExecutorService computeConfigurationExecutor;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Autowired
  private TypeService typeService;

  @Autowired
  private TypeRegistry typeRegistry;

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
    var finishReading = addWorkspacePreparedInBackground();

    // when
    var seenMeanwhile = context.getConfiguration().getConfigurationSource();
    var documentContext = addDocumentContext(context, PATH_TO_MODULE_FILE);
    var moduleTypeMeanwhile = documentContext.getModuleType();
    var managerTypeMeanwhile = typeRegistry.resolve("СправочникМенеджер.Справочник1");
    finishReading.countDown();
    context.awaitPreparation();

    // then
    assertThat(seenMeanwhile).isEqualTo(ConfigurationSource.EMPTY);
    assertThat(moduleTypeMeanwhile).isEqualTo(ModuleType.UNKNOWN);
    assertThat(managerTypeMeanwhile).isEmpty();
    assertThat(context.getConfiguration().getConfigurationSource()).isEqualTo(ConfigurationSource.DESIGNER);
    assertThat(typeRegistry.resolve("СправочникМенеджер.Справочник1"))
      .as("регистрация читает настоящие метаданные, а не пустую конфигурацию на время подготовки")
      .isPresent();
    assertThat(documentContext.getModuleType()).isEqualTo(ModuleType.CommonModule);
    assertThat(context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule))
      .contains(documentContext);
  }

  @Test
  void populateWaitsForConfigurationTypes() throws InterruptedException {
    // given
    var finishReading = addWorkspacePreparedInBackground();
    var workspaceUri = context.getWorkspaceUri();
    var populating = new Thread(() -> WorkspaceContextHolder.run(workspaceUri, context::populateContext));
    populating.start();
    await().atMost(Duration.ofSeconds(10)).until(() -> populating.getState() == Thread.State.WAITING);

    // when
    var documentsMeanwhile = context.getDocuments().size();
    finishReading.countDown();
    populating.join(Duration.ofSeconds(60));

    // then
    assertThat(documentsMeanwhile).as("наполнение ждёт регистрации типов").isZero();
    assertThat(populating.isAlive()).isFalse();
    assertThat(context.getDocuments()).isNotEmpty();
    assertThat(context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule)).isPresent();
  }

  @Test
  void documentOpenedWhilePreparingIsReboundWithoutReparsing() throws IOException {
    // given
    var finishReading = addWorkspacePreparedInBackground();
    var file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    var documentContext = context.addDocument(Absolute.uri(file));
    context.openDocument(documentContext, Files.readString(file.toPath()), 1);
    var symbolTreeMeanwhile = documentContext.getSymbolTree();
    var astMeanwhile = documentContext.getAst();
    var moduleTypeRefMeanwhile = globalScopeProvider.moduleTypeRefByUri(documentContext.getUri());

    // when
    finishReading.countDown();
    context.awaitPreparation();

    // then
    assertThat(documentContext.getAst()).as("текст заново не разбирается").isSameAs(astMeanwhile);
    assertThat(documentContext.getSymbolTree()).isNotSameAs(symbolTreeMeanwhile);
    assertThat(documentContext.getSymbolTree().getModule().getName()).isEqualTo("CommonModule.ПервыйОбщийМодуль");
    assertThat(documentContext.getModuleType()).isEqualTo(ModuleType.CommonModule);
    assertThat(context.getDocumentState(documentContext)).isEqualTo(DocumentState.WITH_CONTENT);
    // Модуль зарегистрирован слушателем изменения привязки: правки текста не было.
    assertThat(moduleTypeRefMeanwhile).isEmpty();
    assertThat(globalScopeProvider.moduleTypeRefByUri(documentContext.getUri())).isPresent();
  }

  @Test
  void documentOpenedWhilePreparingIsRecomputedOnItsNewSymbols() {
    // given: документ открыт, пока читаются метаданные, — значения его функций посчитаны по
    // дереву символов без конфигурации, и раскладка расчёта по потоку осела по узлам дерева
    // разбора, которое перепривязка оставит прежним.
    var finishReading = addWorkspacePreparedInBackground();
    var file = new File(PATH_TO_METADATA, PATH_TO_MODULE_FILE);
    var documentContext = context.addDocument(Absolute.uri(file));
    var inferencerLog = (Logger) LoggerFactory.getLogger(ExpressionTypeInferencer.class);
    var logged = new ListAppender<ILoggingEvent>();
    logged.start();
    inferencerLog.addAppender(logged);
    try {
      context.openDocument(documentContext, """
        Функция Собрать(Значение) Экспорт
        	Если Значение = 1 Тогда
        		Результат = Новый Массив;
        		Для Индекс = 0 По Результат.ВГраница() Цикл
        			Результат[Индекс] = 1;
        		КонецЦикла;
        		Возврат Результат;
        	КонецЕсли;
        	Результат = Новый Структура;
        	Возврат Результат;
        КонецФункции
        """, 1);
      // Тип в точке спрашивают и запросы редактора — подсказка, подсветка.
      typeService.expressionTypesAt(documentContext, new Position(6, 12));

      // when: метаданные прочитаны, документ перепривязан, и его функции пересчитаны.
      finishReading.countDown();
      context.awaitPreparation();
    } finally {
      inferencerLog.detachAppender(logged);
    }

    // then: пересчёт видел уже сброшенную раскладку. Возьми он прежнюю — её ячейки ссылаются
    // на символы прежнего дерева, и переменная «не размещена в расчёте по потоку».
    assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.ERROR).isEmpty();
  }

  @Test
  void documentClearedWhilePreparingDoesNotStayUnderModuleTypeComputedWithoutConfiguration() {
    // given
    var finishReading = addWorkspacePreparedInBackground();
    var documentContext = addDocumentContext(context, PATH_TO_MODULE_FILE);
    var mdoRefMeanwhile = documentContext.getMdoRef();
    // Очистка сбрасывает и тип модуля, посчитанный без конфигурации.
    context.tryClearDocument(documentContext);

    // when
    finishReading.countDown();
    context.awaitPreparation();

    // then
    assertThat(context.getDocuments(mdoRefMeanwhile)).doesNotContainKey(ModuleType.UNKNOWN);
    assertThat(context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule))
      .contains(documentContext);
  }

  /** Сорвавшееся чтение файла не помечает документ прочитанным: следующая попытка читает файл снова. */
  @Test
  void rebuildDocumentFromUnreadableFileLeavesDocumentWithoutContent(@TempDir Path tempDir) throws IOException {
    // given: на месте файла каталог — существует, но прочитать нельзя
    initServerContext();
    var file = tempDir.resolve("Unreadable.bsl");
    var documentContext = context.addDocument(Absolute.uri(file.toUri()));
    Files.createDirectory(file);

    // when
    context.rebuildDocument(documentContext);

    // then
    assertThat(context.getDocumentState(documentContext)).isEqualTo(DocumentState.WITHOUT_CONTENT);

    // when: файл стал читаемым
    Files.delete(file);
    Files.writeString(file, "Процедура Тест()\nКонецПроцедуры\n");
    context.rebuildDocument(documentContext);

    // then
    assertThat(context.getDocumentState(documentContext)).isEqualTo(DocumentState.WITH_CONTENT);
    assertThat(documentContext.getContent()).contains("Процедура Тест()");
  }

  /**
   * Добавить рабочую область с конфигурацией, не дожидаясь её подготовки: чтение метаданных
   * идёт в фоне и не закончится, пока не отпущена защёлка.
   *
   * @return защёлка, по которой заканчивается чтение метаданных.
   */
  private CountDownLatch addWorkspacePreparedInBackground() {
    serverContextProvider.clear();
    var finishReading = new CountDownLatch(1);
    doAnswer(invocation -> {
      awaitUninterruptibly(finishReading);
      return invocation.callRealMethod();
    }).when(computeConfigurationExecutor).submit(ArgumentMatchers.<Callable<Object>>any());
    context = serverContextProvider.addWorkspace(Absolute.uri(Absolute.path(PATH_TO_METADATA).toUri()));
    WorkspaceContextHolder.set(context.getWorkspaceUri());
    return finishReading;
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
