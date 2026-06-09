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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnBeanIndex.FactoryBean;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnInjectionPointIndex;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn.AutumnInjectionPointIndex.InjectionPoint;
import com.github._1c_syntax.bsl.languageserver.utils.Resources;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeanUsagesCodeLensSupplierTest {

  private static final URI PRODUCER_URI = Absolute.uri("file:///КлассЛоггер.os");
  private static final URI CONSUMER_URI = Absolute.uri("file:///Потребитель.os");
  private static final Range CONSTRUCTOR_RANGE = range(1);
  private static final Range INJECTION_RANGE = range(5);
  private static final Range FACTORY_METHOD_RANGE = range(8);

  @Mock
  private LanguageServerConfiguration configuration;
  @Mock
  private AutumnBeanIndex beanIndex;
  @Mock
  private AutumnInjectionPointIndex injectionPointIndex;
  @Mock
  private NavigationCommandBuilder navigationCommandBuilder;

  @Mock
  private DocumentContext documentContext;
  @Mock
  private SymbolTree symbolTree;

  private BeanUsagesCodeLensSupplier supplier() {
    when(documentContext.getUri()).thenReturn(PRODUCER_URI);
    when(documentContext.getSymbolTree()).thenReturn(symbolTree);
    return new BeanUsagesCodeLensSupplier(
      new Resources(configuration), beanIndex, injectionPointIndex, navigationCommandBuilder);
  }

  @Test
  void buildsLensOverConstructorWhenBeanHasInjectionPoints() {
    // given
    var supplier = supplier();
    when(beanIndex.namesForUri(PRODUCER_URI)).thenReturn(Set.of("мойлог"));
    when(injectionPointIndex.resolve("мойлог")).thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE)));
    var constructor = mock(ConstructorSymbol.class);
    when(constructor.getSelectionRange()).thenReturn(CONSTRUCTOR_RANGE);
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).singleElement().satisfies(codeLens -> {
      assertThat(codeLens.getRange()).isEqualTo(CONSTRUCTOR_RANGE);
      assertThat(codeLens.getCommand()).isNull();
    });
  }

  @Test
  void doesNotBuildLensWhenNoInjectionPoints() {
    // given
    var supplier = supplier();
    when(beanIndex.namesForUri(PRODUCER_URI)).thenReturn(Set.of("мойлог"));
    when(injectionPointIndex.resolve("мойлог")).thenReturn(List.of());

    // when / then
    assertThat(supplier.getCodeLenses(documentContext)).isEmpty();
  }

  @Test
  void doesNotBuildLensWhenDocumentDeclaresNoBeans() {
    // given
    var supplier = supplier();
    when(beanIndex.namesForUri(PRODUCER_URI)).thenReturn(Set.of());

    // when / then
    assertThat(supplier.getCodeLenses(documentContext)).isEmpty();
  }

  @Test
  void resolveBuildsReferencesCommandToInjectionPoints() {
    // given
    var supplier = supplier();
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(beanIndex.namesForUri(PRODUCER_URI)).thenReturn(Set.of("мойлог"));
    when(injectionPointIndex.resolve("мойлог")).thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE)));
    var expectedLocations = List.of(new Location(CONSUMER_URI.toString(), INJECTION_RANGE));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.referencesCommand(
      anyString(), eq(PRODUCER_URI), eq(CONSTRUCTOR_RANGE.getStart()), eq(expectedLocations)))
      .thenReturn(command);
    var unresolved = new CodeLens(CONSTRUCTOR_RANGE);
    var data = new BeanUsagesCodeLensSupplier.BeanUsagesCodeLensData(PRODUCER_URI, supplier.getId(), null);

    // when
    var resolved = supplier.resolve(documentContext, unresolved, data);

    // then
    assertThat(resolved.getCommand()).isSameAs(command);
  }

  @Test
  void buildsLensOnFactoryMethodForItsBean() {
    // given: фабричный метод &Завязь объявляет желудь, у которого есть точки внедрения;
    // агрегатных желудей на конструкторе нет (namesForUri пуст) — проверяем именно линзу на методе
    var supplier = supplier();
    when(beanIndex.namesForUri(PRODUCER_URI)).thenReturn(Set.of());
    when(beanIndex.factoryBeansForUri(PRODUCER_URI))
      .thenReturn(List.of(new FactoryBean("СоздатьЛог", Set.of("лог"))));
    when(injectionPointIndex.resolve("лог")).thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE)));
    var method = mock(MethodSymbol.class);
    when(method.getSelectionRange()).thenReturn(FACTORY_METHOD_RANGE);
    when(symbolTree.getMethodSymbol("СоздатьЛог")).thenReturn(Optional.of(method));

    // when
    var codeLenses = supplier.getCodeLenses(documentContext);

    // then
    assertThat(codeLenses).singleElement().satisfies(codeLens -> {
      assertThat(codeLens.getRange()).isEqualTo(FACTORY_METHOD_RANGE);
      var data = (BeanUsagesCodeLensSupplier.BeanUsagesCodeLensData) codeLens.getData();
      assertThat(data.getFactoryMethodName()).isEqualTo("СоздатьЛог");
    });
  }

  @Test
  void resolveFactoryLensUsesItsBeanInjectionPoints() {
    // given
    var supplier = supplier();
    when(configuration.getLanguage()).thenReturn(Language.RU);
    when(beanIndex.factoryBeansForUri(PRODUCER_URI))
      .thenReturn(List.of(new FactoryBean("СоздатьЛог", Set.of("лог"))));
    when(injectionPointIndex.resolve("лог")).thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE)));
    var expectedLocations = List.of(new Location(CONSUMER_URI.toString(), INJECTION_RANGE));
    var command = new Command("title", "command", List.of());
    when(navigationCommandBuilder.referencesCommand(
      anyString(), eq(PRODUCER_URI), eq(FACTORY_METHOD_RANGE.getStart()), eq(expectedLocations)))
      .thenReturn(command);
    var unresolved = new CodeLens(FACTORY_METHOD_RANGE);
    var data = new BeanUsagesCodeLensSupplier.BeanUsagesCodeLensData(PRODUCER_URI, supplier.getId(), "СоздатьЛог");

    // when
    var resolved = supplier.resolve(documentContext, unresolved, data);

    // then
    assertThat(resolved.getCommand()).isSameAs(command);
  }

  private static Range range(int line) {
    return new Range(new Position(line, 0), new Position(line, 10));
  }
}
