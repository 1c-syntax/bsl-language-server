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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.DocumentState;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceContextHolder;
import com.github._1c_syntax.bsl.languageserver.types.AssignmentCommentCache;
import com.github._1c_syntax.bsl.languageserver.types.CommentTypeResolver;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.InferredExpressionTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.parser.BSLParser;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ForeignContextDocumentLifetimeTest extends AbstractServerContextAwareTest {
  private static final Path METADATA = Path.of("src/test/resources/metadata/commonModuleCalls");
  private static final String CALLER = """
    Процедура Вызов()
      Результат = КлиентСерверныйМодуль.Проверка();
    КонецПроцедуры
    """;
  private static final String CONTEXTUAL_CALLEE = """
    Функция Проверка() Экспорт
    #Если Клиент Тогда
      Возврат "клиент";
    #Иначе
      Возврат 1;
    #КонецЕсли
    КонецФункции
    """;

  @TempDir
  Path workspace;

  @Autowired
  private TypeService typeService;
  @Autowired
  private ExpressionTypeInferencer inferencer;
  @Autowired
  private VariableFlowAnalyzer variableFlowAnalyzer;
  @Autowired
  private InferredExpressionTypeIndex expressionTypeIndex;
  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @BeforeEach
  void initWorkspace() throws Exception {
    FileUtils.copyDirectory(METADATA.toFile(), workspace.toFile());
    initServerContext(workspace, false);
  }

  @Test
  void nestedInferenceKeepsCalleeContentUntilItsResultIsReady() throws Exception {
    var documents = documents("""
      Функция Проверка() Экспорт
        ИД = 1;
        Возврат ИД;
      КонецФункции
      """);
    var attempted = new AtomicBoolean();
    var clearedDuringInference = new AtomicBoolean();
    var uri = context.getWorkspaceUri();

    try (var cleaner = Executors.newSingleThreadExecutor(); var errors = new ErrorLog();
         var ignored = interceptComments(owner -> {
           if (owner != documents.callee() || !attempted.compareAndSet(false, true)) {
             return;
           }
           try {
             clearedDuringInference.set(cleaner.submit(() -> WorkspaceContextHolder.call(uri, () -> {
               var lock = context.getDocumentLock(owner.getUri()).writeLock();
               if (!lock.tryLock()) {
                 return false;
               }
               try {
                 owner.freezeComputedData();
                 return context.tryClearDocument(owner);
               } finally {
                 lock.unlock();
               }
             })).get(5, SECONDS));
           } catch (Exception exception) {
             throw new AssertionError("The cleanup attempt did not complete", exception);
           }
         })) {
      assertThat(typesAtCall(documents.caller(), ExecutionContext.THIN_CLIENT)).containsExactly("Число");
      assertThat(attempted).isTrue();
      assertThat(clearedDuringInference).isFalse();
      assertThat(errors.messages()).isEmpty();
    }

    assertCleared(documents.callee());
  }

  @ParameterizedTest
  @NullSource
  @EnumSource(value = ExecutionContext.class, names = "THIN_CLIENT")
  void busyCalleeDoesNotBlockOrKeepTheIndexedApproximation(@Nullable ExecutionContext executionContext) throws Exception {
    var documents = documents(CONTEXTUAL_CALLEE);
    var lock = context.getDocumentLock(documents.callee().getUri()).writeLock();
    var uri = context.getWorkspaceUri();

    try (var reader = Executors.newSingleThreadExecutor(); var errors = new ErrorLog()) {
      lock.lock();
      try {
        var result = reader.submit(() -> WorkspaceContextHolder.call(uri,
          () -> typesAtCall(documents.caller(), executionContext)));

        assertThat(result.get(5, SECONDS)).containsExactlyInAnyOrder("Строка", "Число");
      } finally {
        lock.unlock();
      }

      assertThat(typesAtCall(documents.caller(), executionContext)).containsExactly("Строка");
      assertThat(indexedTypes(documents.callee())).containsExactlyInAnyOrder("Строка", "Число");
      assertThat(errors.messages()).isEmpty();
    }

    assertCleared(documents.callee());
  }

  @Test
  void releasedCalleeKeepsItsCollectedReturnWithoutReloadingContent() throws Exception {
    var documents = documents(CONTEXTUAL_CALLEE);
    assertCleared(documents.callee());

    try (var errors = new ErrorLog()) {
      assertThat(typesAtCall(documents.caller(), ExecutionContext.THIN_CLIENT))
        .containsExactlyInAnyOrder("Строка", "Число");
      assertThat(context.getDocumentState(documents.callee())).isEqualTo(DocumentState.WITHOUT_CONTENT);
      assertThat(errors.messages()).isEmpty();
    }
  }

  @Test
  void sameDocumentCallsStillNarrowByCompilationContext() {
    var documents = documents(CONTEXTUAL_CALLEE + """
      Процедура Вызов()
        Результат = Проверка();
      КонецПроцедуры
      """);

    assertThat(typesAtCall(documents.callee(), ExecutionContext.THIN_CLIENT)).containsExactly("Строка");
    assertThat(typesAtCall(documents.callee(), ExecutionContext.SERVER)).containsExactly("Число");
    assertThat(indexedTypes(documents.callee())).containsExactlyInAnyOrder("Строка", "Число");
  }

  private Documents documents(String calleeSource) {
    var callee = TestUtils.getDocumentContext(
      workspace.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(), calleeSource, context);
    var caller = TestUtils.getDocumentContext(
      workspace.resolve("CommonModules/КлиентскийМодуль/Ext/Module.bsl").toUri(), CALLER, context);
    var method = callee.getSymbolTree().getMethodSymbol("Проверка").orElseThrow();
    typeService.inferReturnTypes(method);

    for (var document : List.of(caller, callee)) {
      variableFlowAnalyzer.clear(document.getUri());
      expressionTypeIndex.clear(document.getUri());
    }
    return new Documents(caller, callee);
  }

  private List<String> indexedTypes(DocumentContext document) {
    var method = document.getSymbolTree().getMethodSymbol("Проверка").orElseThrow();
    return symbolTypeIndex.getReturnTypes(method).refs().stream().map(TypeRef::qualifiedName).toList();
  }

  private List<String> typesAtCall(DocumentContext document, @Nullable ExecutionContext executionContext) {
    var source = document.getContent();
    var index = source.lastIndexOf("Проверка()") + 2;
    var position = new Position(source.substring(0, index).split("\n", -1).length - 1,
      index - source.lastIndexOf('\n', index) - 1);
    var lock = context.getDocumentLock(document.getUri()).readLock();
    lock.lock();
    try {
      return typeService.expressionTypesAt(document, position, executionContext).refs().stream()
        .map(TypeRef::qualifiedName).toList();
    } finally {
      lock.unlock();
    }
  }

  private void assertCleared(DocumentContext document) throws Exception {
    var lock = context.getDocumentLock(document.getUri()).writeLock();
    assertThat(lock.tryLock(5, SECONDS)).isTrue();
    try {
      document.freezeComputedData();
      assertThat(context.tryClearDocument(document)).isTrue();
      assertThat(context.getDocumentState(document)).isEqualTo(DocumentState.WITHOUT_CONTENT);
    } finally {
      lock.unlock();
    }
  }

  private AutoCloseable interceptComments(Consumer<DocumentContext> beforeRead) {
    ExpressionTypeInferencer target = AopTestUtils.getUltimateTargetObject(inferencer);
    var previous = (CommentTypeResolver) ReflectionTestUtils.getField(target, "commentTypeResolver");
    var intercepted = spy(previous);
    doAnswer(call -> {
      beforeRead.accept(call.getArgument(0));
      return call.callRealMethod();
    }).when(intercepted).ofAssignment(any(DocumentContext.class), any(BSLParser.AssignmentContext.class),
      nullable(Runnable.class), any(AssignmentCommentCache.class));
    ReflectionTestUtils.setField(target, "commentTypeResolver", intercepted);
    return () -> ReflectionTestUtils.setField(target, "commentTypeResolver", previous);
  }

  private record Documents(DocumentContext caller, DocumentContext callee) {
  }

  private static final class ErrorLog implements AutoCloseable {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ExpressionTypeInferencer.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private ErrorLog() {
      appender.start();
      logger.addAppender(appender);
    }

    private List<String> messages() {
      return appender.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
        .map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
