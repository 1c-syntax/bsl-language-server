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
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Поле структуры, объявленное в описании метода ссылкой в другой модуль.
 * <p>
 * Пока рабочая область наполняется, такая ссылка никуда не ведёт: модуля, на который она
 * показывает, ещё нет. Тип поля из неё не выводится — но само поле объявлено, и потерять
 * его имя нельзя: обращение к нему выглядело бы обращением к несуществующему члену, а
 * повезло ссылке разрешиться или нет, решает порядок разбора, разный от запуска к запуску.
 */
@SpringBootTest
class DeclaredFieldWithUnresolvedTypeTest {

  private static final String MODULE = """
    // Возвращаемое значение:
    //  Структура:
    //   * Ключ - см. ЧужойМодуль.Значение
    //
    Функция Состояние() Экспорт
     Возврат Новый Структура("Ключ");
    КонецФункции
    """;

  private static final TypeRef STRUCTURE = new TypeRef(TypeKind.PLATFORM, "Структура");

  private SymbolTypeIndex index;
  private DocumentContext documentContext;

  @BeforeEach
  void prepare() {
    var typeRegistry = mock(TypeRegistry.class);
    when(typeRegistry.resolve("Структура")).thenReturn(Optional.of(STRUCTURE));
    // Ссылка в чужой модуль никуда не ведёт — так же, как во время наполнения области.
    when(typeRegistry.resolve(anyString())).thenReturn(Optional.empty());
    when(typeRegistry.resolve("Структура")).thenReturn(Optional.of(STRUCTURE));
    when(typeRegistry.resolveSet(anyString())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.getDefaultElementTypes(any())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.getOwnElementTypes(any())).thenReturn(TypeSet.EMPTY);
    when(typeRegistry.intern(any(), anyString()))
      .thenAnswer(invocation -> new TypeRef(invocation.getArgument(0), invocation.getArgument(1)));
    index = new SymbolTypeIndex(typeRegistry, mock(FormByNameResolver.class));
    documentContext = TestUtils.getDocumentContext(MODULE);
  }

  @Test
  void fieldWithUnresolvedTypeKeepsItsName() {
    // given / when: документ разобран, когда ссылка ещё никуда не ведёт.
    index.handleEvent(new DocumentContextContentChangedEvent(documentContext));

    // then: поле объявлено, поэтому оно есть — пусть и без типа.
    var declared = index.getDeclaredReturnTypes(documentContext.getSymbolTree().getMethods().get(0));
    assertThat(declared.getAllFieldNames())
      .as("объявленное поле не должно пропадать из-за неразрешённой ссылки на его тип")
      .contains("Ключ");
  }
}
