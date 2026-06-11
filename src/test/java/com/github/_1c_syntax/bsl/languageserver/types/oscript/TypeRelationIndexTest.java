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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextDocumentRemovedEvent;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.annotations.OScriptMetaAnnotationResolver;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TypeRelationIndexTest {

  @Mock
  private OScriptExtends oScriptExtends;
  @Mock
  private OScriptMetaAnnotationResolver metaAnnotationResolver;
  @Mock
  private ServerContext serverContext;

  private final Map<URI, DocumentContext> documents = new LinkedHashMap<>();

  private TypeRelationIndex index() {
    when(serverContext.getDocuments()).thenReturn(documents);
    return new TypeRelationIndex(oScriptExtends, metaAnnotationResolver);
  }

  @Test
  void buildsDirectRelationsCaseInsensitively() {
    // given: наследник Базы и реализатор Интерфейса
    var child = osDocument("Наследник", "База", List.of());
    var implementor = osDocument("Реализатор", null, List.of("Интерфейс"));
    var index = index();

    // when / then: лукап по имени без учёта регистра
    assertThat(index.directSubtypeUris(List.of("бАзА"), serverContext))
      .containsExactly(child.getUri());
    assertThat(index.directImplementorUris(List.of("ИНТЕРФЕЙС"), serverContext))
      .containsExactly(implementor.getUri());
    assertThat(index.directSubtypeUris(List.of("НетТакого"), serverContext)).isEmpty();
  }

  @Test
  void ignoresBslDocuments() {
    // given: bsl-документ с теми же аннотациями в индекс не попадает
    var bsl = document("Модуль", FileType.BSL, "База", List.of("Интерфейс"));
    var index = index();

    // when / then
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).isEmpty();
    assertThat(index.directImplementorUris(List.of("Интерфейс"), serverContext)).isEmpty();
    assertThat(bsl).isNotNull();
  }

  @Test
  void documentChangeReplacesContribution() {
    // given: после первичной сборки наследник меняет родителя
    var child = osDocument("Наследник", "СтараяБаза", List.of());
    var index = index();
    assertThat(index.directSubtypeUris(List.of("СтараяБаза"), serverContext)).containsExactly(child.getUri());

    // when: документ перечитан с новым родителем
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("НоваяБаза"));
    index.handleDocumentChange(new DocumentContextContentChangedEvent(child));

    // then: вклад заменён точечно
    assertThat(index.directSubtypeUris(List.of("СтараяБаза"), serverContext)).isEmpty();
    assertThat(index.directSubtypeUris(List.of("НоваяБаза"), serverContext)).containsExactly(child.getUri());
  }

  @Test
  void documentRemovalDropsContribution() {
    // given
    var child = osDocument("Наследник", "База", List.of("Интерфейс"));
    var index = index();
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).containsExactly(child.getUri());

    // when
    documents.remove(child.getUri());
    index.handleDocumentRemoved(new ServerContextDocumentRemovedEvent(serverContext, child.getUri()));

    // then
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).isEmpty();
    assertThat(index.directImplementorUris(List.of("Интерфейс"), serverContext)).isEmpty();
  }

  @Test
  void annotationDefinitionChangeResetsIndex() {
    // given: правка класса-определения аннотации меняет разворачивание мета-аннотаций
    // в чужих классах — индекс сбрасывается на полную ленивую пересборку
    var child = osDocument("Наследник", "База", List.of());
    var annotationDefinition = osDocument("МояАннотация", null, List.of());
    when(metaAnnotationResolver.isAnnotationDefinition(annotationDefinition)).thenReturn(true);
    var index = index();
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).containsExactly(child.getUri());

    // when: меняем шаблон аннотации и родителя наследника (виден только после пересборки)
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("ДругаяБаза"));
    index.handleDocumentChange(new DocumentContextContentChangedEvent(annotationDefinition));

    // then: после сброса ленивая пересборка видит актуальные отношения
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).isEmpty();
    assertThat(index.directSubtypeUris(List.of("ДругаяБаза"), serverContext)).containsExactly(child.getUri());
  }

  @Test
  void eventsBeforeFirstBuildAreNoOp() {
    // given: индекс ещё не строился
    var child = osDocument("Наследник", "База", List.of());
    var index = index();

    // when: события до первой сборки игнорируются (соберётся лениво при первом запросе)
    index.handleDocumentChange(new DocumentContextContentChangedEvent(child));
    index.handleDocumentRemoved(new ServerContextDocumentRemovedEvent(serverContext, child.getUri()));

    // then: первый запрос строит индекс по текущему состоянию документов
    assertThat(index.directSubtypeUris(List.of("База"), serverContext)).containsExactly(child.getUri());
  }

  // --- helpers ---------------------------------------------------------------

  private DocumentContext osDocument(String name, String parentName, List<String> interfaceNames) {
    return document(name, FileType.OS, parentName, interfaceNames);
  }

  private DocumentContext document(String name, FileType fileType, String parentName, List<String> interfaceNames) {
    var documentContext = mock(DocumentContext.class);
    var uri = Absolute.uri("file:///classes/" + name + ".os");
    lenient().when(documentContext.getUri()).thenReturn(uri);
    lenient().when(documentContext.getFileType()).thenReturn(fileType);
    lenient().when(oScriptExtends.parentClassName(documentContext)).thenReturn(Optional.ofNullable(parentName));
    lenient().when(oScriptExtends.implementedInterfaceNames(documentContext)).thenReturn(interfaceNames);
    documents.put(uri, documentContext);
    return documentContext;
  }
}
