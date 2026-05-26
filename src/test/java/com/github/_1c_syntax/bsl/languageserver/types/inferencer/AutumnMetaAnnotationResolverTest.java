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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Юнит-тесты резолвера мета-аннотаций для базовых ролей (без обращения к
 * индексу библиотек). Разворачивание пользовательских аннотаций через цепочку
 * мета-аннотаций проверяется интеграционным тестом.
 */
@ExtendWith(MockitoExtension.class)
class AutumnMetaAnnotationResolverTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  private AutumnMetaAnnotationResolver resolver;

  @BeforeEach
  void setup() {
    resolver = new AutumnMetaAnnotationResolver(libraryIndex, serverContextProvider);
  }

  @Test
  void baseAnnotationMatchesItsRole() {
    // when / then
    assertThat(resolver.isRole("Пластилин", AutumnAnnotations.INJECTION)).isTrue();
    assertThat(resolver.isRole("Желудь", AutumnAnnotations.COMPONENT)).isTrue();
  }

  @Test
  void unknownAnnotationDoesNotMatchRole() {
    // when / then
    assertThat(resolver.isRole("Желудь", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void findsAnnotationByRole() {
    // given
    var annotations = List.of(annotation("Прочее"), injection("ИмяЖелудя"));

    // when
    var found = resolver.findByRole(annotations, AutumnAnnotations.INJECTION);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Пластилин");
  }

  @Test
  void hasRoleReportsAbsence() {
    // given
    var annotations = List.of(annotation("Прочее"));

    // when / then
    assertThat(resolver.hasRole(annotations, AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void collectsValuesByRole() {
    // given
    var annotations = List.of(qualifier("Васян"), qualifier("Панк"), annotation("Прочее"));

    // when
    var values = resolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER);

    // then
    assertThat(values).containsExactly("Васян", "Панк");
  }

  private static Annotation annotation(String name) {
    return Annotation.builder().name(name).kind(AnnotationKind.CUSTOM).build();
  }

  private static Annotation injection(String value) {
    return Annotation.builder()
      .name(AutumnAnnotations.INJECTION)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)))
      .build();
  }

  private static Annotation qualifier(String value) {
    return Annotation.builder()
      .name(AutumnAnnotations.QUALIFIER)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)))
      .build();
  }
}
