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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.AnnotationSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
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
    resolver.invalidateOnContextPopulated();

    // then: старое имя больше не разворачивается, новое — разворачивается
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isFalse();
    assertThat(resolver.isRole("Впрыск", AutumnAnnotations.INJECTION)).isTrue();
  }

  @Test
  void retainsCacheOnBslDocumentChange() {
    // given: замыкание "Внедряемое" -> Пластилин закэшировано
    register("Внедряемое", plainAnnotation("Пластилин"));
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();

    // when: репозиторий изменился, но прилетела правка .bsl-документа
    repository.clear();
    resolver.invalidateOnDocumentChange(documentChange(FileType.BSL, false));

    // then: кэш не сброшен — правка .bsl не влияет на определения аннотаций
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();
  }

  @Test
  void retainsCacheOnNonDefinitionOsChange() {
    // given
    register("Внедряемое", plainAnnotation("Пластилин"));
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();

    // when: изменён .os-документ, который НЕ является классом-определением аннотации
    repository.clear();
    resolver.invalidateOnDocumentChange(documentChange(FileType.OS, false));

    // then: кэш не сброшен — замыкания зависят только от классов-определений
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();
  }

  @Test
  void clearsCacheOnAnnotationDefinitionChange() {
    // given
    register("Внедряемое", plainAnnotation("Пластилин"));
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isTrue();

    // when: изменён .os-класс-определения аннотации (&Аннотация на конструкторе)
    repository.clear();
    resolver.invalidateOnDocumentChange(documentChange(FileType.OS, true));

    // then: кэш сброшен, пересчёт по пустому репозиторию
    assertThat(resolver.isRole("Внедряемое", AutumnAnnotations.INJECTION)).isFalse();
  }

  @Test
  void roleValuesUsesValueFixedInMetaAnnotation() {
    // given: &Лог = &Аннотация("Лог") &Пластилин("Лог"), без обработчика
    register("Лог", withValue(AutumnAnnotations.INJECTION, "Лог"));

    // when: имя желудя для использования &Лог("Префикс")
    var values = resolver.roleValues(withValue("Лог", "Префикс"), AutumnAnnotations.INJECTION);

    // then: берётся зашитое в мете имя "Лог", префикс использования как имя желудя не идёт
    assertThat(values).containsExactly("Лог");
  }

  @Test
  void roleValuesForwardsUsageValueWhenHandlerPresent() {
    // given: killjoy &Внедряемое = &Аннотация + &Пластилин + ПриРазворачиванииАннотации
    registerForwarding("Внедряемое", plainAnnotation(AutumnAnnotations.INJECTION));

    // when
    var values = resolver.roleValues(withValue("Внедряемое", "ИмяЖелудя"), AutumnAnnotations.INJECTION);

    // then: значение использования проброшено в &Пластилин
    assertThat(values).containsExactly("ИмяЖелудя");
  }

  @Test
  void roleValuesIgnoresAliasOwnValueWithoutHandler() {
    // given: &Контроллер = &Аннотация + &Желудь + &Прозвище("Контроллер"), без обработчика
    register("Контроллер",
      plainAnnotation(AutumnAnnotations.COMPONENT),
      withValue(AutumnAnnotations.QUALIFIER, "Контроллер"));

    // when / then: "/users" не идёт в имя компонента (нет форвардинга)...
    assertThat(resolver.roleValues(withValue("Контроллер", "/users"), AutumnAnnotations.COMPONENT)).isEmpty();
    // ...а прозвище берётся из зашитого &Прозвище("Контроллер")
    assertThat(resolver.roleValues(withValue("Контроллер", "/users"), AutumnAnnotations.QUALIFIER))
      .containsExactly("Контроллер");
  }

  @Test
  void roleValuesReadsDirectRoleValue() {
    // when / then: прямая &Желудь("X") — значение с самой аннотации
    assertThat(resolver.roleValues(withValue(AutumnAnnotations.COMPONENT, "X"), AutumnAnnotations.COMPONENT))
      .containsExactly("X");
  }

  // --- helpers ---------------------------------------------------------------

  private static DocumentContextContentChangedEvent documentChange(FileType fileType, boolean annotationDefinition) {
    var document = mock(DocumentContext.class);
    when(document.getFileType()).thenReturn(fileType);
    if (fileType == FileType.OS) {
      var symbolTree = mock(SymbolTree.class);
      var constructor = mock(ConstructorSymbol.class);
      when(constructor.getAnnotations()).thenReturn(annotationDefinition ? List.of(marker("Любая")) : List.of());
      when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
      when(document.getSymbolTree()).thenReturn(symbolTree);
    }
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    return event;
  }

  /** Зарегистрировать пользовательскую аннотацию: конструктор с указанными мета-аннотациями. */
  private void register(String customName, Annotation... metaAnnotations) {
    register(customName, false, metaAnnotations);
  }

  /** То же, но с обработчиком ПриРазворачиванииАннотации (алиас пробрасывает Значение в мету). */
  private void registerForwarding(String customName, Annotation... metaAnnotations) {
    register(customName, true, metaAnnotations);
  }

  private void register(String customName, boolean forwarding, Annotation... metaAnnotations) {
    var constructor = mock(MethodSymbol.class);
    var annotations = new ArrayList<Annotation>();
    annotations.add(marker(customName));
    annotations.addAll(List.of(metaAnnotations));
    when(constructor.getAnnotations()).thenReturn(annotations);

    var methods = new ArrayList<MethodSymbol>();
    methods.add(constructor);
    if (forwarding) {
      var handler = mock(MethodSymbol.class);
      when(handler.getName()).thenReturn("ПриРазворачиванииАннотации");
      methods.add(handler);
    }
    var symbolTree = mock(SymbolTree.class);
    when(symbolTree.getMethods()).thenReturn(methods);
    var owner = mock(DocumentContext.class);
    when(owner.getSymbolTree()).thenReturn(symbolTree);

    repository.register(AnnotationSymbol.builder()
      .name(customName)
      .owner(owner)
      .parent(Optional.of(constructor))
      .build());
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
