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

import com.github._1c_syntax.bsl.languageserver.ClientCapabilitiesHolder;
import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.AnnotatedTextEdit;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceClientCapabilities;
import org.eclipse.lsp4j.WorkspaceEditCapabilities;
import org.eclipse.lsp4j.WorkspaceEditChangeAnnotationSupportCapabilities;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class RenameProviderTest extends AbstractServerContextAwareTest {

  @Autowired
  private RenameProvider renameProvider;

  @MockitoSpyBean
  private ClientCapabilitiesHolder clientCapabilitiesHolder;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/rename.bsl";
  private static final String PATH_TO_COMMON_MODULE_FILE = "./src/test/resources/providers/renameCommonModule.bsl";

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
    // По умолчанию клиент не заявляет documentChanges, поэтому существующие тесты получают
    // legacy changes-map; отдельные тесты включают documentChanges и аннотации правок.
    setClientWorkspaceEditCapabilities(false, false);
  }

  /**
   * Настраивает заявленные клиентом возможности {@code workspace.workspaceEdit} и пересчитывает
   * их кэш в провайдере через {@code handleInitializeEvent}.
   *
   * @param documentChanges         {@code true}, если клиент поддерживает documentChanges
   * @param changeAnnotationSupport {@code true}, если клиент поддерживает аннотации правок
   */
  private void setClientWorkspaceEditCapabilities(boolean documentChanges, boolean changeAnnotationSupport) {
    var capabilities = new ClientCapabilities();
    var workspaceCapabilities = new WorkspaceClientCapabilities();
    var workspaceEditCapabilities = new WorkspaceEditCapabilities();
    workspaceEditCapabilities.setDocumentChanges(documentChanges);
    if (changeAnnotationSupport) {
      workspaceEditCapabilities.setChangeAnnotationSupport(
        new WorkspaceEditChangeAnnotationSupportCapabilities(false)
      );
    }
    workspaceCapabilities.setWorkspaceEdit(workspaceEditCapabilities);
    capabilities.setWorkspace(workspaceCapabilities);
    when(clientCapabilitiesHolder.getCapabilities()).thenReturn(Optional.of(capabilities));
    renameProvider.handleInitializeEvent();
  }

  @Test
  void testEmptyReferences() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new RenameParams();
    params.setPosition(new Position(1, 0));
    params.setNewName("НовоеИмя");

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges()).isEmpty();
  }

  @Test
  void testRenameLocalMethods() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(0, 8, 18), newName))
      .contains(new TextEdit(Ranges.create(6, 0, 10), newName));
  }

  @Test
  void testRenameLocalVariableDeclaration() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(1, 10));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(1, 4, 14), newName))
      .contains(new TextEdit(Ranges.create(2, 13, 23), newName));
  }

  @Test
  void testRenameLocalVariableUse() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(3, 5));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(2, 4, 10), newName))
      .contains(new TextEdit(Ranges.create(3, 4, 10), newName));
  }

  @Test
  void testRenameParam() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(0, 25));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(0, 24, 26), newName))
      .contains(new TextEdit(Ranges.create(1, 17, 19), newName));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "имя с пробелом", "1Имя", "Если", "КонецФункции"})
  void testRenameWithInvalidNewNameThrowsInvalidParams(String invalidNewName) {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(invalidNewName);

    // when - then
    assertThatThrownBy(() -> renameProvider.getRename(documentContext, params))
      .isInstanceOfSatisfying(ResponseErrorException.class, exception ->
        assertThat(exception.getResponseError().getCode())
          .isEqualTo(ResponseErrorCode.InvalidParams.getValue())
      );
  }

  @ParameterizedTest
  @ValueSource(strings = {"НовоеИмя", "NewName", "_Имя_С_Подчеркиванием2"})
  void testRenameWithValidNewNameReturnsEdits(String validNewName) {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(validNewName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(0, 8, 18), validNewName))
      .contains(new TextEdit(Ranges.create(6, 0, 10), validNewName));
  }

  @Test
  void testRenameOnCommonModuleNameProducesNoEdits() {
    // given
    // курсор на "ПервыйОбщийМодуль" в "ПервыйОбщийМодуль.НеУстаревшаяПроцедура()"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_COMMON_MODULE_FILE);

    var params = new RenameParams();
    params.setPosition(new Position(1, 5));
    params.setNewName("НовоеИмя");

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    // имя общего модуля задаётся метаданными и не может быть переименовано текстовой правкой
    assertThat(workspaceEdit.getChanges()).isEmpty();
  }

  @Test
  void testPrepareRenameOnCommonModuleNameIsRejected() {
    // given
    // курсор на "ПервыйОбщийМодуль" в "ПервыйОбщийМодуль.НеУстаревшаяПроцедура()"
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_COMMON_MODULE_FILE);

    var params = new PrepareRenameParams();
    params.setPosition(new Position(1, 5));

    // when
    var range = renameProvider.getPrepareRename(documentContext, params);

    // then
    assertThat(range).isNull();
  }

  @Test
  void testPrepareRenameEmpty() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new PrepareRenameParams();
    params.setPosition(new Position(0, 3));

    // when
    var range = renameProvider.getPrepareRename(documentContext, params);

    // then
    assertThat(range).isNull();

  }

  @Test
  void testPrepareRenameMethods() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new PrepareRenameParams();
    params.setPosition(new Position(0, 14));

    // when
    var range = renameProvider.getPrepareRename(documentContext, params);

    // then
    assertThat(range).isEqualTo(Ranges.create(0, 8, 18));

  }

  @Test
  void testRenameWithDocumentChangesSupportProducesDocumentChanges() {
    // given
    setClientWorkspaceEditCapabilities(true, false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChanges()).isNullOrEmpty();
    assertThat(workspaceEdit.getDocumentChanges())
      .hasSize(1)
      .allSatisfy(documentChange -> assertThat(documentChange.isLeft()).isTrue());

    var textDocumentEdit = (TextDocumentEdit) workspaceEdit.getDocumentChanges().get(0).getLeft();
    assertThat(textDocumentEdit.getTextDocument().getUri()).isEqualTo(documentContext.getUri().toString());
    assertThat(textDocumentEdit.getEdits())
      .hasSize(2)
      .allSatisfy(edit -> assertThat(edit.isLeft()).isTrue())
      .extracting(edit -> edit.getLeft())
      .noneMatch(edit -> edit instanceof AnnotatedTextEdit)
      .contains(new TextEdit(Ranges.create(0, 8, 18), newName))
      .contains(new TextEdit(Ranges.create(6, 0, 10), newName));
  }

  @Test
  void testRenameWithChangeAnnotationSupportAnnotatesEdits() {
    // given
    setClientWorkspaceEditCapabilities(true, true);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getChangeAnnotations()).hasSize(1);
    var annotationId = workspaceEdit.getChangeAnnotations().keySet().iterator().next();

    var textDocumentEdit = (TextDocumentEdit) workspaceEdit.getDocumentChanges().get(0).getLeft();
    assertThat(textDocumentEdit.getEdits())
      .hasSize(2)
      .extracting(edit -> edit.getLeft())
      .allSatisfy(edit -> {
        assertThat(edit).isInstanceOf(AnnotatedTextEdit.class);
        assertThat(((AnnotatedTextEdit) edit).getAnnotationId()).isEqualTo(annotationId);
      });
  }

  @Test
  void testRenameWithoutDocumentChangesSupportProducesChangesMap() {
    // given
    setClientWorkspaceEditCapabilities(false, false);
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var newName = "НовоеИмя";

    var params = new RenameParams();
    params.setPosition(new Position(0, 14));
    params.setNewName(newName);

    // when
    var workspaceEdit = renameProvider.getRename(documentContext, params);

    // then
    assertThat(workspaceEdit.getDocumentChanges()).isNullOrEmpty();
    assertThat(workspaceEdit.getChanges().get(documentContext.getUri().toString()))
      .hasSize(2)
      .contains(new TextEdit(Ranges.create(0, 8, 18), newName))
      .contains(new TextEdit(Ranges.create(6, 0, 10), newName));
  }

}
