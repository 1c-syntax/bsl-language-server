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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.references.model.LocationRepository;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

class ReferenceIndexRemovedDocumentTest extends AbstractServerContextAwareTest {
  private static final String CALLER = """
    Процедура Вызов()
      КлиентСерверныйМодуль.Цель();
    КонецПроцедуры
    """;
  private static final Position CALL_POSITION = new Position(1, 24);

  @TempDir
  Path workspace;

  @Autowired
  private ReferenceIndex referenceIndex;

  @BeforeEach
  void initWorkspace() throws Exception {
    FileUtils.copyDirectory(Path.of("src/test/resources/metadata/commonModuleCalls").toFile(), workspace.toFile());
    initServerContext(workspace, false);
  }

  @Test
  void pointLookupSkipsOccurrenceWhoseSourceWasRemoved() {
    var caller = caller();
    var uri = caller.getUri();
    var original = referenceIndex.getReference(uri, CALL_POSITION).orElseThrow();

    var index = AopTestUtils.<ReferenceIndex>getUltimateTargetObject(referenceIndex);
    var repository = (LocationRepository) ReflectionTestUtils.getField(index, "locationRepository");
    var intercepted = spy(AopTestUtils.<LocationRepository>getUltimateTargetObject(repository));
    doAnswer(invocation -> {
      var occurrence = invocation.callRealMethod();
      // The lookup has captured an immutable occurrence before the removal event clears the index.
      context.removeDocument(uri);
      return occurrence;
    }).when(intercepted).findByPosition(uri, CALL_POSITION);

    ReflectionTestUtils.setField(index, "locationRepository", intercepted);
    try {
      assertThat(referenceIndex.getReference(uri, CALL_POSITION)).isEmpty();
      assertThat(context.getDocumentNoLock(uri)).isNull();
      assertThat(context.getDocumentNoLock(original.getSourceDefinedSymbol().orElseThrow().getOwner().getUri()))
        .isNotNull();
    } finally {
      ReflectionTestUtils.setField(index, "locationRepository", repository);
    }

    TestUtils.getDocumentContext(uri, CALLER, context);
    assertThat(referenceIndex.getReference(uri, CALL_POSITION).orElseThrow().symbol()).isEqualTo(original.symbol());
  }

  @Test
  void selfMemberLookupSkipsSourceRemovedDuringResolution() throws Exception {
    var root = workspace.resolve("catalog");
    FileUtils.copyDirectory(Path.of(TestUtils.PATH_TO_METADATA).toFile(), root.toFile());
    initServerContext(root, false);
    context.getConfiguration();

    var document = TestUtils.getDocumentContext(
      root.resolve("Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri(), """
        Процедура Вызов()
          Записать();
        КонецПроцедуры
        """, context);
    var position = new Position(1, 4);
    assertThat(referenceIndex.getReference(document.getUri(), position)).isPresent();

    var index = AopTestUtils.<ReferenceIndex>getUltimateTargetObject(referenceIndex);
    var resolver = (SelfMemberResolver) ReflectionTestUtils.getField(index, "selfMemberResolver");
    var intercepted = spy(AopTestUtils.<SelfMemberResolver>getUltimateTargetObject(resolver));
    doAnswer(invocation -> {
      var member = invocation.callRealMethod();
      context.removeDocument(document.getUri());
      return member;
    }).when(intercepted).resolveSelfMember(document, SymbolKind.Method, "записать");

    ReflectionTestUtils.setField(index, "selfMemberResolver", intercepted);
    try {
      assertThat(referenceIndex.getReference(document.getUri(), position)).isEmpty();
      assertThat(context.getDocumentNoLock(document.getUri())).isNull();
    } finally {
      ReflectionTestUtils.setField(index, "selfMemberResolver", resolver);
    }
  }

  @Test
  void outgoingLookupSkipsSnapshotWhoseSourceWasRemoved() {
    var caller = caller();
    var uri = caller.getUri();
    assertThat(referenceIndex.getReferencesFrom(uri)).isNotEmpty();

    var index = AopTestUtils.<ReferenceIndex>getUltimateTargetObject(referenceIndex);
    var repository = (LocationRepository) ReflectionTestUtils.getField(index, "locationRepository");
    var intercepted = spy(AopTestUtils.<LocationRepository>getUltimateTargetObject(repository));
    var removed = new AtomicBoolean();
    doAnswer(invocation -> {
      var occurrences = invocation.callRealMethod();
      // removeDocument also reads the repository while clearing the index; intercept only the original query.
      if (removed.compareAndSet(false, true)) {
        context.removeDocument(uri);
      }
      return occurrences;
    }).when(intercepted).getSymbolOccurrencesByLocationUri(uri);

    ReflectionTestUtils.setField(index, "locationRepository", intercepted);
    try {
      assertThat(referenceIndex.getReferencesFrom(uri)).isEmpty();
      assertThat(removed).isTrue();
      assertThat(context.getDocumentNoLock(uri)).isNull();
    } finally {
      ReflectionTestUtils.setField(index, "locationRepository", repository);
    }
  }

  private DocumentContext caller() {
    TestUtils.getDocumentContext(
      workspace.resolve("CommonModules/КлиентСерверныйМодуль/Ext/Module.bsl").toUri(),
      "Процедура Цель() Экспорт\nКонецПроцедуры", context);
    return TestUtils.getDocumentContext(
      workspace.resolve("CommonModules/КлиентскийМодуль/Ext/Module.bsl").toUri(), CALLER, context);
  }
}
