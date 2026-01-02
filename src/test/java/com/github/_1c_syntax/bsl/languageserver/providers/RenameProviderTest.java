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

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import jakarta.annotation.PostConstruct;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class RenameProviderTest {

  @Autowired
  private RenameProvider renameProvider;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/providers/rename.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testEmptyReferences() {
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    var params = new RenameParams();
    params.setPosition(new Position(1, 0));

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

}
