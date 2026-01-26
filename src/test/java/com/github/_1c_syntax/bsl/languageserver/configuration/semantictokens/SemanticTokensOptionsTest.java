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
package com.github._1c_syntax.bsl.languageserver.configuration.semantictokens;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticTokensOptionsTest {

  @Test
  void testDefaultValues() {
    // when
    var options = new SemanticTokensOptions();

    // then
    assertThat(options.getStrTemplateMethods())
      .hasSize(4)
      .contains(
        "ПодставитьПараметрыВСтроку",
        "SubstituteParametersToString",
        "СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку",
        "StringFunctionsClientServer.SubstituteParametersToString"
      );
  }

  @Test
  void testParseLocalMethods() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      "ПодставитьПараметрыВСтроку",
      "SubstituteParametersToString",
      "CustomMethod"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    assertThat(parsed.localMethods())
      .hasSize(3)
      .contains("подставитьпараметрывстроку", "substituteparameterstostring", "custommethod");
    assertThat(parsed.moduleMethodPairs()).isEmpty();
  }

  @Test
  void testParseModuleMethodPairs() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      "СтроковыеФункцииКлиентСервер.ПодставитьПараметрыВСтроку",
      "StringFunctionsClientServer.SubstituteParametersToString",
      "MyModule.MyMethod"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    assertThat(parsed.localMethods()).isEmpty();
    assertThat(parsed.moduleMethodPairs())
      .hasSize(3)
      .containsKeys("строковыефункцииклиентсервер", "stringfunctionsclientserver", "mymodule");
    assertThat(parsed.moduleMethodPairs().get("строковыефункцииклиентсервер"))
      .contains("подставитьпараметрывстроку");
    assertThat(parsed.moduleMethodPairs().get("stringfunctionsclientserver"))
      .contains("substituteparameterstostring");
    assertThat(parsed.moduleMethodPairs().get("mymodule"))
      .contains("mymethod");
  }

  @Test
  void testCaseInsensitiveMatching() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      "ПОДСТАВИТЬПАРАМЕТРЫВСТРОКУ",
      "СтроковыеФункцииКлиентСервер.ПОДСТАВИТЬПАРАМЕТРЫВСТРОКУ"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    assertThat(parsed.localMethods())
      .hasSize(1)
      .contains("подставитьпараметрывстроку");
    assertThat(parsed.moduleMethodPairs().get("строковыефункцииклиентсервер"))
      .contains("подставитьпараметрывстроку");
  }

  @Test
  void testEmptyStrings() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      "",
      "   ",
      "ValidMethod"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    assertThat(parsed.localMethods())
      .hasSize(1)
      .contains("validmethod");
    assertThat(parsed.moduleMethodPairs()).isEmpty();
  }

  @Test
  void testInvalidPatterns() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      ".InvalidPattern",
      "InvalidPattern.",
      "..DoubleDot",
      "Valid.Pattern"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    // Invalid patterns should not create entries
    assertThat(parsed.localMethods()).isEmpty();
    assertThat(parsed.moduleMethodPairs())
      .hasSize(1)
      .containsKey("valid");
    assertThat(parsed.moduleMethodPairs().get("valid"))
      .contains("pattern");
  }

  @Test
  void testCachingBehavior() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of("Method1"));

    // when
    var parsed1 = options.getParsedStrTemplateMethods();
    var parsed2 = options.getParsedStrTemplateMethods();

    // then
    assertThat(parsed1).isSameAs(parsed2);

    // Update methods
    options.setStrTemplateMethods(List.of("Method2"));
    var parsed3 = options.getParsedStrTemplateMethods();

    // Cache should be updated
    assertThat(parsed3).isNotSameAs(parsed1);
    assertThat(parsed3.localMethods())
      .hasSize(1)
      .contains("method2");
  }

  @Test
  void testPatternsWithMultipleDots() {
    // given
    var options = new SemanticTokensOptions();
    options.setStrTemplateMethods(List.of(
      "Module.SubModule.Method"
    ));

    // when
    var parsed = options.getParsedStrTemplateMethods();

    // then
    // Should split only on first dot
    assertThat(parsed.localMethods()).isEmpty();
    assertThat(parsed.moduleMethodPairs())
      .hasSize(1)
      .containsKey("module");
    assertThat(parsed.moduleMethodPairs().get("module"))
      .contains("submodule.method");
  }
}
