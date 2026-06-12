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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.index.EventContractsIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Position;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.StringJoiner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Юнит-тест рендеринга содержимого «открытых» объектов (Структура/Соответствие,
 * фиксированные варианты, строка ТаблицыЗначений) в hover переменной.
 * <p>
 * Типы задаются вручную через {@link TypeSet}, чтобы проверить именно отрисовку
 * маркдаун-списка, независимо от особенностей вывода типов в инференсере.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VariableSymbolStructureRenderTest {

  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "Строка");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Число");

  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private DescriptionFormatter descriptionFormatter;
  @Mock
  private Resources resources;
  @Mock
  private TypeService typeService;
  @Mock
  private EventContractsIndex eventContractsIndex;
  @Mock
  private VariableSymbol symbol;

  private VariableSymbolMarkupContentBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new VariableSymbolMarkupContentBuilder(
      configuration, descriptionFormatter, resources, typeService, eventContractsIndex);

    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(typeService.displayName(any(TypeRef.class), any(Language.class)))
      .thenAnswer(inv -> ((TypeRef) inv.getArgument(0)).qualifiedName());
    when(resources.getResourceString(VariableSymbolMarkupContentBuilder.class, "type")).thenReturn("Тип");
    when(resources.getResourceString(VariableSymbolMarkupContentBuilder.class, "moduleVariable"))
      .thenReturn("Переменная уровня модуля");

    when(symbol.getKind()).thenReturn(VariableKind.MODULE);
    when(symbol.getDescription()).thenReturn(Optional.empty());
    when(descriptionFormatter.getSignature(symbol)).thenReturn("");
    when(descriptionFormatter.getLocation(symbol)).thenReturn("");
    // Реальная логика addSectionIfNotEmpty: непустые секции попадают в вывод.
    doAnswer(inv -> {
      StringJoiner joiner = inv.getArgument(0);
      String content = inv.getArgument(1);
      if (!content.isEmpty()) {
        joiner.add(content);
      }
      return null;
    }).when(descriptionFormatter).addSectionIfNotEmpty(any(StringJoiner.class), any(String.class));
  }

  private String render(TypeSet types) {
    var reference = Reference.of(Mockito.mock(SourceDefinedSymbol.class), symbol,
      new Location("file:///t", new Range(new Position(0, 0), new Position(0, 0))));
    when(typeService.typesAt(reference)).thenReturn(types);
    return builder.getContent(reference).getValue();
  }

  private static TypeRef platform(String name) {
    return new TypeRef(TypeKind.PLATFORM, name);
  }

  @Test
  void structureContentsRenderedAsBulletList() {
    // given
    var ref = platform("Структура");
    var types = TypeSet.of(ref)
      .withField(ref, "Имя", TypeSet.of(STRING))
      .withField(ref, "Возраст", TypeSet.of(NUMBER));

    // when
    var content = render(types);

    // then
    assertThat(content)
      .contains("Тип: Структура")
      .contains("* **Имя**: `Строка`")
      .contains("* **Возраст**: `Число`")
      .doesNotContain("{");
  }

  @Test
  void mapContentsRenderedAsBulletList() {
    // given
    var ref = platform("Соответствие");
    var types = TypeSet.of(ref).withField(ref, "Ключ1", TypeSet.of(STRING));

    // when
    var content = render(types);

    // then
    assertThat(content)
      .contains("Тип: Соответствие")
      .contains("* **Ключ1**: `Строка`");
  }

  @Test
  void fixedStructureContentsRenderedAsBulletList() {
    // given
    var ref = platform("ФиксированнаяСтруктура");
    var types = TypeSet.of(ref).withField(ref, "Код", TypeSet.of(NUMBER));

    // when
    var content = render(types);

    // then
    assertThat(content)
      .contains("Тип: ФиксированнаяСтруктура")
      .contains("* **Код**: `Число`");
  }

  @Test
  void fixedMapContentsRenderedAsBulletList() {
    // given
    var ref = platform("ФиксированноеСоответствие");
    var types = TypeSet.of(ref).withField(ref, "Логин", TypeSet.of(STRING));

    // when
    var content = render(types);

    // then
    assertThat(content)
      .contains("Тип: ФиксированноеСоответствие")
      .contains("* **Логин**: `Строка`");
  }

  @Test
  void valueTableRowColumnsRenderedAsBulletList() {
    // given: таблица значений с одной колонкой Сумма типа Число
    var tableRef = platform("ТаблицаЗначений");
    var rowRef = platform("СтрокаТаблицыЗначений");
    var rowSet = TypeSet.of(rowRef).withField(rowRef, "Сумма", TypeSet.of(NUMBER));
    var types = TypeSet.of(tableRef).withElement(tableRef, rowSet);

    // when
    var content = render(types);

    // then
    assertThat(content)
      .contains("Тип: ТаблицаЗначений из СтрокаТаблицыЗначений")
      .contains("* **Сумма**: `Число`");
  }

  @Test
  void nestedStructureRenderedWithIndentation() {
    // given: структура с ключом Контакты, значение которого — вложенная структура с ключом Email
    var ref = platform("Структура");
    var inner = platform("Структура");
    var innerSet = TypeSet.of(inner).withField(inner, "Email", TypeSet.of(STRING));
    var types = TypeSet.of(ref).withField(ref, "Контакты", innerSet);

    // when
    var content = render(types);

    // then: вложенное поле получает отступ в два пробела.
    assertThat(content)
      .contains("* **Контакты**: `Структура`")
      .contains("  * **Email**: `Строка`");
  }

  @Test
  void sameKeyAcrossUnionRefsRenderedOnce() {
    // given: union Структура | Неопределено, ключ Имя объявлен у структурного ref.
    var structure = platform("Структура");
    var undefined = new TypeRef(TypeKind.PRIMITIVE, "Неопределено");
    var types = TypeSet.of(structure).withField(structure, "Имя", TypeSet.of(STRING))
      .union(TypeSet.of(undefined));

    // when
    var content = render(types);

    // then: ключ не дублируется из-за нескольких ref в наборе.
    assertThat(content.split("\\* \\*\\*Имя\\*\\*", -1)).hasSize(2);
  }

  @Test
  void unionFieldTypesRenderedWithPipe() {
    // given
    var ref = platform("Структура");
    var types = TypeSet.of(ref).withField(ref, "Статус", TypeSet.of(STRING).union(TypeSet.of(NUMBER)));

    // when
    var content = render(types);

    // then
    assertThat(content).contains("* **Статус**: `Строка` | `Число`");
  }
}
