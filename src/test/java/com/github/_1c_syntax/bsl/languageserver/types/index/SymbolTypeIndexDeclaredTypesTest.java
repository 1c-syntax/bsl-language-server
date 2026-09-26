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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.Optional;

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
@SpringBootTest
class SymbolTypeIndexDeclaredTypesTest {

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
  void prepare() {
    typeRegistry = mock(TypeRegistry.class);
    // Незнакомое реестру имя остаётся пользовательским типом — так же, как в бою.
    when(typeRegistry.intern(TypeKind.USER, "Массив"))
      .thenReturn(new TypeRef(TypeKind.USER, "Массив"));
    when(typeRegistry.getDefaultElementTypes(any())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.getOwnElementTypes(any())).thenReturn(TypeSet.EMPTY);
    index = new SymbolTypeIndex(typeRegistry, mock(FormByNameResolver.class));
    documentContext = TestUtils.getDocumentContext(MODULE);
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
