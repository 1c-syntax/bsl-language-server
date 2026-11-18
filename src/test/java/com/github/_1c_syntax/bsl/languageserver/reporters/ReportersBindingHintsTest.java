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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.contrastsecurity.sarif.Result;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.DiagnosticCodeSerializer;
import com.github._1c_syntax.bsl.languageserver.reporters.databind.DiagnosticMessageSerializer;
import com.github._1c_syntax.bsl.languageserver.util.AotHints;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Типы, которые репортёры сериализуют Jackson'ом, регистрируются для native-image.
 */
class ReportersBindingHintsTest {

  static Stream<Arguments> reportedTypes() {
    return Stream.of(
      Arguments.of(JsonReporter.class, FileInfo.class, "getDiagnostics"),
      Arguments.of(JUnitReporter.class, JUnitTestSuites.JUnitTestCase.class, "getClassname"),
      Arguments.of(TSLintReporter.class, TSLintReportEntry.EntryPosition.class, "getLine"),
      Arguments.of(GenericIssueReporter.class, GenericIssueReport.GenericIssueEntry.class, "getRuleId"),
      Arguments.of(CodeQualityReporter.class, CodeQualityReportEntry.class, "getCheckName"),
      Arguments.of(SarifReporter.class, Result.class, "getRuleId")
    );
  }

  @ParameterizedTest
  @MethodSource("reportedTypes")
  void testReportedTypesAreRegisteredForBinding(Class<?> reporter, Class<?> reportedType, String accessor) {
    // when
    var hints = AotHints.hintsOf(reporter);

    // then
    assertThat(RuntimeHintsPredicates.reflection().onMethodInvocation(reportedType, accessor)).accepts(hints);
  }

  @ParameterizedTest
  @ValueSource(classes = {DiagnosticCodeSerializer.class, DiagnosticMessageSerializer.class})
  void testMixInSerializersOfJsonReportAreRegistered(Class<?> serializer) {
    // when
    var hints = AotHints.hintsOf(JsonReporter.class);

    // then
    assertThat(RuntimeHintsPredicates.reflection().onType(serializer)
      .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)).accepts(hints);
  }
}
