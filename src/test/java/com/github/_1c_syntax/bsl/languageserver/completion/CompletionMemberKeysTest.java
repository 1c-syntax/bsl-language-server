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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class CompletionMemberKeysTest {
  @Test
  void kindsStaySeparateAndAliasesKeepTheirRootCaseMapping() {
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "ИМЯ")).isEqualTo("METHOD:имя");
    assertThat(CompletionMemberKeys.of(MemberKind.PROPERTY, "ИМЯ")).isEqualTo("PROPERTY:имя");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "ИМЯ"))
      .isEqualTo(CompletionMemberKeys.of(MemberKind.METHOD, "имя"));
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "")).isEqualTo("METHOD:");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "İ")).isEqualTo("METHOD:i̇");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "ΟΣ")).isEqualTo("METHOD:ος");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "οσ")).isEqualTo("METHOD:οσ");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "𐐀Emoji😀")).isEqualTo("METHOD:𐐨emoji😀");
  }

  @Test
  void defaultLocaleDoesNotChangePreviouslyOrNewlyComputedKeys() {
    var original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.ENGLISH);
      var first = CompletionMemberKeys.of(MemberKind.METHOD, "IDENTIFIER");
      Locale.setDefault(Locale.forLanguageTag("tr"));
      assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "IDENTIFIER")).isEqualTo(first);
      assertThat(CompletionMemberKeys.of(MemberKind.PROPERTY, "IDENTIFIER"))
        .isEqualTo("PROPERTY:identifier");
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void longNamesAreNeitherTruncatedNorConfusedWithTheirPrefix() {
    var name = "A".repeat(300) + "İ";
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, name))
      .isEqualTo("METHOD:" + "a".repeat(300) + "i̇");
    assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "AI")).isEqualTo("METHOD:ai");
  }

  @Test
  void collidingHashesNeverReturnAnotherName() {
    assertThat("Aa").hasSameHashCodeAs("BB");
    for (var repeat = 0; repeat < 100; repeat++) {
      assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "Aa")).isEqualTo("METHOD:aa");
      assertThat(CompletionMemberKeys.of(MemberKind.METHOD, "BB")).isEqualTo("METHOD:bb");
    }
  }

  @Test
  void parallelLookupsKeepTheirOwnKindsAndNames() throws Exception {
    try (var executor = Executors.newFixedThreadPool(4)) {
      var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
      for (var task = 0; task < 8; task++) {
        tasks.add(() -> {
          for (var name : List.of("ИМЯ", "Identifier", "ΟΣ", "İ", "Aa", "BB")) {
            for (var repeat = 0; repeat < 100; repeat++) {
              assertThat(CompletionMemberKeys.of(MemberKind.PROPERTY, name))
                .isEqualTo("PROPERTY:" + name.toLowerCase(Locale.ROOT));
              assertThat(CompletionMemberKeys.of(MemberKind.METHOD, name))
                .isEqualTo("METHOD:" + name.toLowerCase(Locale.ROOT));
            }
          }
          return null;
        });
      }
      for (var result : executor.invokeAll(tasks)) {
        result.get();
      }
    }
  }
}
