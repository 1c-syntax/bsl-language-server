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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TemplateByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Объявленные типы возвращаемых значений собираются в момент разбора документа — когда
 * часть рабочей области ещё не зарегистрирована, и написанное в описании метода имя
 * разрешается не полностью. Проверяется, что такие типы пересобираются заново, как только
 * рабочая область наполнена.
 */
class SymbolTypeIndexDeclaredTypesTest extends AbstractServerContextAwareTest {

  private static final String MODULE = """
    // Возвращаемое значение:
    //  Массив
    //
    Функция Значения() Экспорт
     Возврат Новый Массив;
    КонецФункции
    """;

  private static final TypeRef PLATFORM_ARRAY = new TypeRef(TypeKind.PLATFORM, "Массив");

  private TypeRegistry typeRegistry;
  private SymbolTypeIndex index;
  private DocumentContext documentContext;

  @BeforeEach
  void prepare(@TempDir Path workspace) {
    initServerContext(workspace);
    typeRegistry = mock(TypeRegistry.class);
    // Незнакомое реестру имя остаётся пользовательским типом — так же, как в бою.
    when(typeRegistry.intern(TypeKind.USER, "Массив"))
      .thenReturn(new TypeRef(TypeKind.USER, "Массив"));
    when(typeRegistry.getDefaultElementTypes(any())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.getOwnElementTypes(any())).thenReturn(TypeSet.EMPTY);
    index = new SymbolTypeIndex(typeRegistry, mock(FormByNameResolver.class), mock(TemplateByNameResolver.class),
      mock(GlobalScopeProvider.class));
    documentContext = TestUtils.getDocumentContext(URI.create("fake://declarations/first.bsl"), MODULE, context);
  }

  @Test
  void inferredResetPreservesDeclarationsAndTracksLaterWrites() {
    knownToRegistry(true);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));
    var method = documentContext.getSymbolTree().getMethods().get(0);
    var inferred = TypeSet.of(new TypeRef(TypeKind.PRIMITIVE, "Строка"));
    index.putReturnTypes(method, inferred);

    index.clearInferredReturnTypes();

    assertThat(index.getReturnTypes(method)).isEqualTo(index.getDeclaredReturnTypes(method));
    index.putReturnTypes(method, inferred);
    assertThat(index.getReturnTypes(method).refs()).containsAll(inferred.refs());
    index.clear(documentContext.getUri());
    assertThat(index.getReturnTypes(method)).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void declaredTypesAreRebuiltWhenWorkspaceIsPopulated() {
    // given: документ разобран, когда имя типа реестру ещё неизвестно.
    knownToRegistry(false);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));
    assertThat(declaredTypeOfFirstMethod().refs()).containsExactly(new TypeRef(TypeKind.USER, "Массив"));

    // when: рабочая область наполнена, и то же самое имя теперь разрешается.
    knownToRegistry(true);
    index.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContextWithDocument()));

    // then: объявленный тип пересобран по нынешнему состоянию реестра.
    assertThat(declaredTypeOfFirstMethod().refs()).containsExactly(PLATFORM_ARRAY);
  }

  @Test
  void stoppedWorkspaceKeepsExistingDeclarations() {
    // given
    knownToRegistry(false);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));
    var serverContext = serverContextWithDocument();
    when(serverContext.isBackgroundWorkStopped()).thenReturn(true);
    knownToRegistry(true);

    // when
    index.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then
    assertThat(declaredTypeOfFirstMethod().refs()).containsExactly(new TypeRef(TypeKind.USER, "Массив"));
  }

  @Test
  void stoppingDuringReindexLeavesRemainingDocumentsUntouched() {
    // given
    var second = TestUtils.getDocumentContext(URI.create("fake://stopping/second.bsl"), MODULE, context);
    knownToRegistry(false);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));
    index.handleEvent(new DocumentContextContentChangedEvent(second));
    var documents = new LinkedHashMap<URI, DocumentContext>();
    documents.put(documentContext.getUri(), documentContext);
    documents.put(second.getUri(), second);
    var serverContext = mock(ServerContext.class);
    when(serverContext.getDocuments()).thenReturn(documents);

    var stopped = new AtomicBoolean();
    when(serverContext.isBackgroundWorkStopped()).thenAnswer(invocation -> stopped.get());
    when(typeRegistry.resolve("Массив")).thenAnswer(invocation -> {
      stopped.set(true);
      return Optional.of(PLATFORM_ARRAY);
    });

    // when
    index.handleServerContextPopulated(new ServerContextPopulatedEvent(serverContext));

    // then
    assertThat(declaredTypeOfFirstMethod().refs()).containsExactly(PLATFORM_ARRAY);
    var secondMethod = second.getSymbolTree().getMethods().get(0);
    assertThat(index.getDeclaredReturnTypes(secondMethod).refs())
      .containsExactly(new TypeRef(TypeKind.USER, "Массив"));
  }

  @Test
  void rereadDocumentKeepsDeclaredTypes() {
    // given: документ разобран при наполненной рабочей области.
    knownToRegistry(true);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));

    // when: тот же самый текст перечитан заново, а реестр вдруг «забыл» имя.
    knownToRegistry(false);
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext, false));

    // then: перечитывание ничего не пересчитывает, поэтому прежний тип остался на месте.
    assertThat(declaredTypeOfFirstMethod().refs()).containsExactly(PLATFORM_ARRAY);
  }

  private void knownToRegistry(boolean known) {
    when(typeRegistry.resolveSet(anyString())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.resolve("Массив"))
      .thenReturn(known ? Optional.of(PLATFORM_ARRAY) : Optional.empty());
  }

  private TypeSet declaredTypeOfFirstMethod() {
    return index.getDeclaredReturnTypes(documentContext.getSymbolTree().getMethods().get(0));
  }

  private ServerContext serverContextWithDocument() {
    var serverContext = mock(ServerContext.class);
    when(serverContext.getDocuments()).thenReturn(Map.of(documentContext.getUri(), documentContext));
    return serverContext;
  }
}
