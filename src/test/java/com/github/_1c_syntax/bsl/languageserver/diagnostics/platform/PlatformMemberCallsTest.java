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
package com.github._1c_syntax.bsl.languageserver.diagnostics.platform;

import org.junit.jupiter.api.Test;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.parser.BSLTokenizer;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.util.List;
import java.util.ArrayList;
import com.github._1c_syntax.bsl.support.CompatibilityMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Чисто-функциональные ветви {@link PlatformMemberCalls}: префикс-фильтр
 * «Удалить»/«Delete». Полный путь резолва покрывается тестами диагностик
 * {@code DeprecatedMethodCall} и {@code UnavailableMemberCall}; версионная
 * применимость члена — в {@code PlatformMemberVersionsTest}.
 */
class PlatformMemberCallsTest {

  @Test
  void eachVersionCheckResolvesOnlyApplicableNamesAndDeletedProperties() {
    var document = mock(DocumentContext.class);
    when(document.getAst()).thenReturn(new BSLTokenizer("""
      Получатель.Будущий();
      Получатель.Устаревший();
      Значение = Получатель.УдалитьПоле;
      Получатель.УдалитьСтарое();
      """).getAst());
    var service = mock(TypeService.class);
    var target = new CompatibilityMode("8.2.16");
    when(service.mayBeDeprecatedMemberName("Устаревший", target)).thenReturn(true);
    when(service.mayBeUnavailableMemberName("Будущий", target)).thenReturn(true);
    var resolved = new ArrayList<String>();
    when(service.accessTargetsAt(same(document), any(TerminalNode.class), isNull())).thenAnswer(call -> {
      resolved.add(((TerminalNode) call.getArgument(1)).getText());
      return List.of();
    });
    PlatformMemberCalls.collectDeprecated(document, service, target);
    assertThat(resolved).containsExactly("Устаревший", "УдалитьПоле");
    resolved.clear();
    PlatformMemberCalls.collectUnavailable(document, service, target);
    assertThat(resolved).containsExactly("Будущий");
  }

  @Test
  void versionChecksDoNotRequestArgumentTypes() {
    var document = mock(DocumentContext.class);
    when(document.getAst()).thenReturn(new BSLTokenizer("Получатель.Версионный(Аргумент);\n").getAst());
    var service = mock(TypeService.class);
    var member = mock(TypeService.TypedMember.class);
    var target = new CompatibilityMode("8.2.16");
    when(service.mayBeDeprecatedMemberName("Версионный", target)).thenReturn(true);
    when(service.membersAt(same(document), any(TerminalNode.class)))
      .thenThrow(new AssertionError("Version checks need target metadata, not argument inference"));
    when(service.accessTargetsAt(same(document), any(TerminalNode.class), isNull())).thenReturn(List.of(member));
    assertThat(PlatformMemberCalls.collectDeprecated(document, service, target).members()).containsExactly(member);
  }

  @Test
  void hasDeletedPrefixCoversRuAndEnPrefixes() {
    assertThat(PlatformMemberCalls.hasDeletedPrefix("УдалитьСтароеПоле")).isTrue();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("DeleteOld")).isTrue();
    // регистронезависимая префиксная проверка
    assertThat(PlatformMemberCalls.hasDeletedPrefix("удалитьЛегаси")).isTrue();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("DELETElegacy")).isTrue();
  }

  @Test
  void hasDeletedPrefixRejectsBareAndShorterNames() {
    // длина равна префиксу — не считается «помеченным», т.к. нет постфикса.
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Удалить")).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Delete")).isFalse();
    // не префикс
    assertThat(PlatformMemberCalls.hasDeletedPrefix("Поле")).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix(null)).isFalse();
    assertThat(PlatformMemberCalls.hasDeletedPrefix("")).isFalse();
  }
}
