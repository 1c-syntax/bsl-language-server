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

import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты резолвера мета-аннотаций фреймворка «ОСень».
 * <p>
 * Резолвер опирается на {@link AnnotationRepository} (индекс зарегистрированных
 * пользовательских аннотаций) — здесь используется его реальная реализация, что
 * заодно проверяет регистронезависимость лукапа.
 */
class AutumnMetaAnnotationResolverTest {

  private final AnnotationRepository repository = new AnnotationRepository();
  private final AutumnMetaAnnotationResolver resolver = new AutumnMetaAnnotationResolver(repository);

  @Test
  void baseAnnotationMatchesItsRole() {
    // when / then: базовая аннотация фреймворка разворачивается сама в себя
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
    var annotations = List.of(plainAnnotation("Прочее"), injection("ИмяЖелудя"));

    // when
    var found = resolver.findByRole(annotations, AutumnAnnotations.INJECTION);

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Пластилин");
  }

  @Test
  void hasRoleReportsAbsence() {
    // given
    var annotations = List.of(plainAnnotation("Прочее"));

    // when / then
    assertThat(resolver.hasRole(annotations, AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void collectsValuesByRole() {
    // given
    var annotations = List.of(qualifier("Васян"), qualifier("Панк"), plainAnnotation("Прочее"));

    // when
    var values = resolver.valuesByRole(annotations, AutumnAnnotations.QUALIFIER);

    // then
    assertThat(values).containsExactly("Васян", "Панк");
  }

  @Test
  void resolvesCustomAnnotationThroughMetaAnnotation() {
    // given: АннотацияВнедряемое: &Аннотация("Внедряемое") &Пластилин
    register("Внедряемое", plainAnnotation("Пластилин"));

    // when / then
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.COMPONENT)).isFalse();
  }

  @Test
  void resolvesTransitiveMetaAnnotationChain() {
    // given: СуперВнедряемое -> Внедряемое -> &Пластилин
    register("СуперВнедряемое", plainAnnotation("Внедряемое"));
    register("Внедряемое", plainAnnotation("Пластилин"));

    // when / then
    assertThat(resolver.isRole("СуперВнедряемое", AutumnAnnotations.INJECTION)).isTrue();
  }

  @Test
  void excludesAnnotationMarkerFromRoles() {
    // given
    register("Внедряемое", plainAnnotation("Пластилин"));

    // when / then: сам маркер &Аннотация не считается ролью
    assertThat(resolver.isRole("Внедряемое", "Аннотация")).isFalse();
  }

  @Test
  void baseRoleMatchIsCaseInsensitive() {
    // when / then: имена аннотаций в BSL регистронезависимы
    assertThat(resolver.isRole("пластилин", AutumnAnnotations.INJECTION)).isTrue();
    assertThat(resolver.isRole("ЖЕЛУДЬ", AutumnAnnotations.COMPONENT)).isTrue();
  }

  @Test
  void resolvesCustomAnnotationCaseInsensitively() {
    // given: имя зарегистрировано в одном регистре, мета — в другом
    register("Внедряемое", plainAnnotation("пластилин"));

    // when / then: запрос в произвольном регистре всё равно разворачивается
    assertThat(resolver.isRole("ВНЕДРЯЕМОЕ", AutumnAnnotations.INJECTION)).isTrue();
  }

  @Test
  void breaksMetaAnnotationCycle() {
    // given: А ссылается на Б, Б ссылается на А — цикл не должен зациклить резолвер
    register("А", plainAnnotation("Б"));
    register("Б", plainAnnotation("А"));

    // when / then
    assertThat(resolver.isRole("А", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void invalidateRefreshesCacheAfterRepositoryChange() {
    // given: "Внедряемое" = &Пластилин, результат закэширован
    register("Внедряемое", plainAnnotation("Пластилин"));
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();

    // when: состав аннотаций изменился — "Внедряемое" больше нет, появилось "Впрыск"
    repository.clear();
    register("Впрыск", plainAnnotation("Пластилин"));
    resolver.invalidate();

    // then: старое имя больше не разворачивается, новое — разворачивается
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isFalse();
    assertThat(resolver.isRole("Впрыск", AutumnAnnotations.INJECTION)).isTrue();
  }

  // --- helpers ---------------------------------------------------------------

  /** Зарегистрировать пользовательскую аннотацию: конструктор с указанными мета-аннотациями. */
  private void register(String customName, Annotation... metaAnnotations) {
    var constructor = mock(MethodSymbol.class);
    var annotations = new ArrayList<Annotation>();
    annotations.add(marker(customName));
    annotations.addAll(List.of(metaAnnotations));
    when(constructor.getAnnotations()).thenReturn(annotations);

    var symbol = AnnotationSymbol.builder()
      .name(customName)
      .parent(Optional.of(constructor))
      .build();
    repository.register(symbol);
  }

  private static Annotation marker(String customName) {
    return Annotation.builder()
      .name("Аннотация")
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(customName), true)))
      .build();
  }

  private static Annotation plainAnnotation(String name) {
    return Annotation.builder().name(name).kind(AnnotationKind.CUSTOM).build();
  }

  private static Annotation injection(String value) {
    return withValue(AutumnAnnotations.INJECTION, value);
  }

  private static Annotation qualifier(String value) {
    return withValue(AutumnAnnotations.QUALIFIER, value);
  }

  private static Annotation withValue(String name, String value) {
    return Annotation.builder()
      .name(name)
      .kind(AnnotationKind.CUSTOM)
      .parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)))
      .build();
  }
}
