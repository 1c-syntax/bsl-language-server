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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMessage;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Тесты диагностики «присваивание read-only-свойству».
 * <p>
 * Read-only-разметка {@code accessMode: "READ"} для свойства {@code Ссылка} на
 * {@code СправочникСсылка.<Имя справочника>}, {@code СправочникОбъект.<Имя справочника>},
 * {@code ДокументСсылка.<Имя документа>}, {@code ДокументОбъект.<Имя документа>}
 * добавлена в {@code builtin-platform-types.json}, чтобы тест работал без
 * установленной платформы 1С.
 * <p>
 * Тонкости type-aware подбора через {@link TypeRegistry} (read-only-индексы,
 * специализация generic'ов) покрыты {@code TypeRegistrySpecializationTest}.
 */
@CleanupContextBeforeClassAndAfterClass
class AssignToReadOnlyPropertyDiagnosticTest extends AbstractDiagnosticTest<AssignToReadOnlyPropertyDiagnostic> {

  /**
   * URI документа подменяется через {@link org.mockito.Mockito#spy} на путь
   * внутри тестовой конфигурации — чтобы reference-finder'ы и TypeService
   * находили правильный workspace через
   * {@link com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider#getDocumentNoLock}.
   * Без этого global properties ({@code Документы}, {@code Справочники}) не
   * резолвятся, и инференсер не вытягивает тип из цепочки вида
   * {@code Документы.Документ1.НайтиПоНомеру(...).Ссылка}.
   */
  private static final String PATH_TO_MODULE_FILE =
    PATH_TO_METADATA + "/CommonModules/ОбщегоНазначения/Ext/Module.bsl";

  @Autowired
  private TypeRegistry typeRegistry;

  AssignToReadOnlyPropertyDiagnosticTest() {
    super(AssignToReadOnlyPropertyDiagnostic.class);
  }

  @Test
  void detectsAssignmentToReadOnlyPropertyOnDocumentRef() {
    var diagnostics = getDiagnosticsAsCommonModule();

    // У ДокументСсылка все свойства read-only — мутабельны только у Объекта.
    // Фикстура содержит присваивания и русскими (Ссылка, Дата), и английскими
    // (Ref, Date) именами — read-only должно находиться независимо от языка.
    assertThat(diagnostics)
      .as("все присваивания свойствам ДокументСсылка должны подсвечиваться "
        + "независимо от языка имени свойства")
      .hasSize(4);
    var messages = diagnostics.stream()
      .map(d -> DiagnosticMessage.getStringValue(d.getMessage()))
      .toList();
    assertThat(messages).anyMatch(m -> m.contains("Ссылка"));
    assertThat(messages).anyMatch(m -> m.contains("Дата"));
    assertThat(messages).anyMatch(m -> m.contains("Ref"));
    assertThat(messages).anyMatch(m -> m.contains("Date"));
  }

  @Test
  void noFalsePositivesWithoutWorkspace() {
    var diagnostics = getDiagnostics();
    assertThat(diagnostics)
      .as("без MD-контекста типы локальной переменной не резолвятся, "
        + "false positives недопустимы")
      .isEmpty();
  }

  @Test
  void readOnlyIndexBuiltFromJsonFallback() {
    initServerContext(PATH_TO_METADATA);
    context.getConfiguration();
    assertThat(typeRegistry.hasAnyReadOnlyMember())
      .as("accessMode=READ маркеры в JSON-fallback должны населить индекс")
      .isTrue();
    assertThat(typeRegistry.isReadOnlyMemberName("Ссылка")).isTrue();
  }

  private List<Diagnostic> getDiagnosticsAsCommonModule() {
    Path moduleFile = Path.of(PATH_TO_MODULE_FILE).toAbsolutePath();
    initServerContext(PATH_TO_METADATA);
    var documentContext = spy(getDocumentContext(diagnosticInstance.getClass().getSimpleName()));
    when(documentContext.getUri()).thenReturn(moduleFile.toUri());
    return getDiagnostics(documentContext);
  }
}
