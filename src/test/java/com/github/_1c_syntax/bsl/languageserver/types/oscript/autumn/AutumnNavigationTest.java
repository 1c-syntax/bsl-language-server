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
package com.github._1c_syntax.bsl.languageserver.types.oscript.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnBeanIndex.BeanDefinition;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnBeanIndex.FactoryMethodBean;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnInjectionPointIndex.InjectionPoint;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnNavigationTest {

  private static final URI PRODUCER_URI = Absolute.uri("file:///beans/Лог.os");
  private static final URI CONSUMER_URI = Absolute.uri("file:///beans/Потребитель.os");
  private static final Range PRODUCER_RANGE = range(7);
  private static final Range INJECTION_RANGE = range(3);

  @Mock
  private AutumnBeanIndex beanIndex;
  @Mock
  private AutumnInjectionPointIndex injectionPointIndex;
  @Mock
  private ServerContextProvider serverContextProvider;

  @InjectMocks
  private AutumnNavigation autumnNavigation;

  @Test
  void producerDefinitionsRouteSingletonAndCollection() {
    // given
    var definition = componentDefinition();
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(definition));
    when(beanIndex.resolveAllDefinitions("Лог")).thenReturn(List.of(definition, definition));

    // when / then: одиночное внедрение — с приоритетом, коллекция — все кандидаты
    assertThat(autumnNavigation.producerDefinitions("Лог", false)).hasSize(1);
    assertThat(autumnNavigation.producerDefinitions("Лог", true)).hasSize(2);
  }

  @Test
  void producerLocationsResolveProducerMethodRange() {
    // given: производитель локализуется по имени метода-производителя в его файле
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDefinition()));
    stubProducerDocument("ПриСозданииОбъекта");

    // when
    var locations = autumnNavigation.producerLocations("Лог", false);

    // then
    assertThat(locations)
      .containsExactly(new Location(PRODUCER_URI.toString(), PRODUCER_RANGE));
  }

  @Test
  void producerLocationsEmptyWhenServerContextMissing() {
    // given: файл производителя вне известных контекстов — локация не строится
    when(beanIndex.resolveDefinitions("Лог")).thenReturn(List.of(componentDefinition()));
    when(serverContextProvider.getServerContext(PRODUCER_URI)).thenReturn(Optional.empty());

    // when / then
    assertThat(autumnNavigation.producerLocations("Лог", false)).isEmpty();
  }

  @Test
  void componentUsageLocationsMapInjectionPoints() {
    // given
    when(beanIndex.componentBeanNamesForUri(PRODUCER_URI)).thenReturn(Set.of("лог"));
    when(injectionPointIndex.usagesOfComponent(PRODUCER_URI, Set.of("лог")))
      .thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE, false)));

    // when / then
    assertThat(autumnNavigation.componentUsageLocations(PRODUCER_URI))
      .containsExactly(new Location(CONSUMER_URI.toString(), INJECTION_RANGE));
  }

  @Test
  void factoryMethodUsageLocationsFilterByMethod() {
    // given
    when(beanIndex.factoryMethodBeansForUri(PRODUCER_URI))
      .thenReturn(List.of(new FactoryMethodBean("СоздатьЛог", Set.of("лог"))));
    when(injectionPointIndex.usagesOfFactoryMethod(PRODUCER_URI, "СоздатьЛог", Set.of("лог")))
      .thenReturn(List.of(new InjectionPoint(CONSUMER_URI, INJECTION_RANGE, false)));

    // when / then: известный метод — точки; неизвестный — пусто
    assertThat(autumnNavigation.factoryMethodUsageLocations(PRODUCER_URI, "СоздатьЛог"))
      .containsExactly(new Location(CONSUMER_URI.toString(), INJECTION_RANGE));
    assertThat(autumnNavigation.factoryMethodUsageLocations(PRODUCER_URI, "НетТакого")).isEmpty();
  }

  // --- helpers ---------------------------------------------------------------

  private static BeanDefinition componentDefinition() {
    return new BeanDefinition(
      new TypeRef(TypeKind.USER, "Лог"), false, PRODUCER_URI, "ПриСозданииОбъекта", true);
  }

  private void stubProducerDocument(String producerMethodName) {
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    var producerMethod = mock(MethodSymbol.class);
    when(serverContextProvider.getServerContext(PRODUCER_URI)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(PRODUCER_URI)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getMethodSymbol(producerMethodName)).thenReturn(Optional.of(producerMethod));
    when(producerMethod.getSelectionRange()).thenReturn(PRODUCER_RANGE);
  }

  private static Range range(int line) {
    return new Range(new Position(line, 0), new Position(line, 10));
  }
}
