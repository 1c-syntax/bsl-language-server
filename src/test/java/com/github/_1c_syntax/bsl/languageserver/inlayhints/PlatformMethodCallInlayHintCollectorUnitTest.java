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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.configuration.inlayhints.InlayHintOptions;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.configuration.Resources;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mockito-юнит для {@link PlatformMethodCallInlayHintCollector} — покрывает
 * пути без полной интеграции (null AST, defaults для конфигурации).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformMethodCallInlayHintCollectorUnitTest {

  @Mock
  private TypeService typeService;
  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private Resources resources;
  @Mock
  private DocumentContext documentContext;

  private PlatformMethodCallInlayHintCollector supplier;

  @BeforeEach
  void setUp() {
    supplier = new PlatformMethodCallInlayHintCollector(configuration, typeService, resources);
  }

  @Test
  void getInlayHintsWithNullAstReturnsEmpty() {
    // given — documentContext.getAst() возвращает null.
    when(documentContext.getAst()).thenReturn(null);
    var params = new InlayHintParams();
    params.setRange(new Range(new Position(0, 0), new Position(0, 0)));

    // when
    var hints = supplier.getInlayHints(documentContext, params);

    // then — при отсутствии AST подсказок нет.
    assertThat(hints).isEmpty();
  }

  @Test
  void skippedArgumentShowsDefaultValueHint() {
    // given — реальный AST вызова СтрНайти с пропущенным средним аргументом;
    // сигнатура содержит средний параметр со значением по умолчанию.
    when(documentContext.getAst()).thenReturn(new BSLTokenizer("СтрНайти(\"a\",,\"b\");\n").getAst());

    var signature = SignatureDescriptor.of(List.of(
      ParameterDescriptor.of("СтрокаПоиска"),
      new ParameterDescriptor("НачальнаяПозиция", TypeSet.EMPTY, true, "", "1"),
      ParameterDescriptor.of("Подстрока")
    ));
    var member = MemberDescriptor.method("СтрНайти", List.of(signature));
    var typedMember = new TypeService.TypedMember(null, member, Ranges.create(0, 0, 8), 3);

    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(configuration.getInlayHintOptions()).thenReturn(new InlayHintOptions());
    when(typeService.memberAt(any(), any())).thenReturn(Optional.of(typedMember));
    when(typeService.expressionTypesAt(any(), any())).thenReturn(TypeSet.EMPTY);

    var params = new InlayHintParams();
    params.setRange(new Range(new Position(0, 0), new Position(1, 0)));

    // when
    var hints = supplier.getInlayHints(documentContext, params);

    // then — для пропущенного среднего аргумента показан хинт со значением по умолчанию.
    assertThat(hints)
      .extracting(InlayHint::getLabel)
      .extracting(Either::getLeft)
      .contains("НачальнаяПозиция (1)");
  }

  @Test
  void emptySingleArgumentDoesNotProduceHint() {
    // given — `Сообщить()` парсится как один пустой callParam; это ноль фактических
    // аргументов, а не пропущенный — хинт показывать нельзя.
    when(documentContext.getAst()).thenReturn(new BSLTokenizer("Сообщить();\n").getAst());

    var signature = SignatureDescriptor.of(List.of(
      new ParameterDescriptor("ТекстСообщения", TypeSet.EMPTY, true, "", "Пустая строка")
    ));
    var member = MemberDescriptor.method("Сообщить", List.of(signature));
    var typedMember = new TypeService.TypedMember(null, member, Ranges.create(0, 0, 8), 0);

    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(configuration.getInlayHintOptions()).thenReturn(new InlayHintOptions());
    when(typeService.memberAt(any(), any())).thenReturn(Optional.of(typedMember));
    when(typeService.expressionTypesAt(any(), any())).thenReturn(TypeSet.EMPTY);

    var params = new InlayHintParams();
    params.setRange(new Range(new Position(0, 0), new Position(1, 0)));

    // when
    var hints = supplier.getInlayHints(documentContext, params);

    // then — ноль аргументов: хинтов нет.
    assertThat(hints).isEmpty();
  }
}
