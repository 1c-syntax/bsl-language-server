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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMessage;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class DeprecatedMethodCallDiagnosticTest extends AbstractDiagnosticTest<DeprecatedMethodCallDiagnostic> {

  @Autowired
  private TypeRegistry typeRegistry;

  DeprecatedMethodCallDiagnosticTest() {
    super(DeprecatedMethodCallDiagnostic.class);
  }

  @Test
  void test() {

    // given
    initServerContext(TestUtils.PATH_TO_METADATA);

    // when
    List<Diagnostic> diagnostics = getDiagnostics();

    // then
    assertThat(diagnostics).hasSize(8);
    assertThat(diagnostics, true)
      .hasRange(1, 18, 1, 37)
      .hasRange(4, 18, 4, 35)
      .hasRange(7, 22, 7, 39)
      .hasRange(10, 23, 10, 40)
      .hasRange(16, 34, 16, 53)
      .hasRange(19, 38, 19, 55)
      .hasRange(22, 39, 22, 56)
      .hasRange(28, 0, 28, 19)
    ;

  }

  @Test
  void platformDeprecatedGlobalMethods() {

    // given — целевая платформа выше версий устаревания всех методов фикстуры,
    // данные об устаревании берутся из builtin-globals.json (без HBK).
    configuration.getV8PlatformOptions().setTargetVersion("8.3.24");
    try {
      // when
      List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallPlatform");

      // then — устаревшие глобальные методы платформы (6 с 8.3.10, 3 с 8.3.17).
      assertThat(diagnostics).hasSize(9);
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void platformDeprecatedNotReportedForOlderTarget() {

    // given — целевая платформа ниже версии устаревания методов 8.3.17,
    // но не ниже 8.3.10: срабатывают только 6 методов клиентского приложения.
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallPlatform");
      assertThat(diagnostics).hasSize(6);
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void deletedPrefixOnConfigurationProperty() {

    // given — у Справочник1 в фикстуре designer есть атрибут «УдалитьЛегаси».
    initServerContext(TestUtils.PATH_TO_METADATA);

    // when
    List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallDeletedPrefix");

    // then — оба обращения к УдалитьЛегаси (присваивание и чтение) подсвечены.
    assertThat(diagnostics).hasSize(2);
  }

  @Test
  void deprecatedConstructedType() {

    // given — «страничные» метаданные типа приходят из HBK, в JSON-fallback'е
    // их нет, поэтому версию устаревания типа регистрируем вручную.
    registerDeprecatedType();
    configuration.getV8PlatformOptions().setTargetVersion("8.3.10");
    try {
      // when
      List<Diagnostic> diagnostics = getDiagnostics("DeprecatedMethodCallType");

      // then — подсвечено имя типа в «Новый Массив()», в сообщении есть замена.
      assertThat(diagnostics).hasSize(1);
      assertThat(DiagnosticMessage.getStringValue(diagnostics.get(0).getMessage()))
        .contains("Массив")
        .contains("ФиксированныйМассив");
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  @Test
  void deprecatedConstructedTypeNotReportedForOlderTarget() {

    // given
    registerDeprecatedType();
    configuration.getV8PlatformOptions().setTargetVersion("8.3.4");
    try {
      // when / then — целевая версия ниже версии устаревания типа.
      assertThat(getDiagnostics("DeprecatedMethodCallType")).isEmpty();
    } finally {
      configuration.getV8PlatformOptions().setTargetVersion(null);
    }
  }

  /** Тип {@code Массив} устарел с 8.3.5 в пользу {@code ФиксированныйМассив} (синтетика для теста). */
  private void registerDeprecatedType() {
    var ref = typeRegistry.resolve("Массив").orElseThrow();
    typeRegistry.registerTypeMetadata(ref, new PlatformMetadata(
      "", "8.3.5", List.of("ФиксированныйМассив"), Set.of(), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()), FileType.BSL);
  }
}
