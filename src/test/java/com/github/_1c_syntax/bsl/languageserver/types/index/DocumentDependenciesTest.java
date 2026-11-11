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
package com.github._1c_syntax.bsl.languageserver.types.index;

import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Порядок пересчёта документов: зависимые считаются после тех, от кого зависят, а взаимные
 * зависимости собираются в одну компоненту.
 */
class DocumentDependenciesTest {

  private static final URI FIRST = Absolute.uri("file:///first.bsl");
  private static final URI SECOND = Absolute.uri("file:///second.bsl");
  private static final URI THIRD = Absolute.uri("file:///third.bsl");

  @Test
  void independentDocumentsGiveComponentPerDocument() {
    // given: документы друг о друге не знают.
    var order = DocumentDependencies.of(List.of(FIRST, SECOND), uri -> List.of());

    // when
    var components = order.components();

    // then: каждый сам по себе.
    assertThat(components).hasSize(2);
    assertThat(components).allSatisfy(component -> assertThat(component).hasSize(1));
  }

  @Test
  void dependencyIsOrderedBeforeDependent() {
    // given: первый построен на втором, второй — на третьем.
    Map<URI, Collection<URI>> edges = Map.of(
      FIRST, List.of(SECOND),
      SECOND, List.of(THIRD),
      THIRD, List.of()
    );
    var order = DocumentDependencies.of(edges.keySet(), uri -> edges.getOrDefault(uri, List.of()));

    // when
    var components = order.components().stream().map(component -> component.get(0)).toList();

    // then: сначала то, от чего зависят.
    assertThat(components).containsExactly(THIRD, SECOND, FIRST);
  }

  @Test
  void mutualDependenciesCollapseIntoOneComponent() {
    // given: первый и второй ссылаются друг на друга, третий зависит от первого.
    Map<URI, Collection<URI>> edges = Map.of(
      FIRST, List.of(SECOND),
      SECOND, List.of(FIRST),
      THIRD, List.of(FIRST)
    );
    var order = DocumentDependencies.of(edges.keySet(), uri -> edges.getOrDefault(uri, List.of()));

    // when
    var components = order.components();

    // then: цикл — одна компонента из двух документов, и она раньше зависящего от неё.
    assertThat(components).hasSize(2);
    assertThat(components.get(0)).containsExactlyInAnyOrder(FIRST, SECOND);
    assertThat(components.get(1)).containsExactly(THIRD);
  }

  @Test
  void selfDependencyDoesNotMakeCycleComponent() {
    // given: документ ссылается сам на себя.
    var order = DocumentDependencies.of(List.of(FIRST), uri -> List.of(FIRST));

    // when
    var components = order.components();

    // then: компонента одна и из одного документа — крутить его до неподвижной точки незачем.
    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactly(FIRST);
  }

  @Test
  void dependencyOutsidePendingSetIsIgnored() {
    // given: пересчитывается только первый, а зависит он от документа вне пересчёта.
    var order = DocumentDependencies.of(Set.of(FIRST), uri -> List.of(SECOND));

    // when
    var components = order.components();

    // then: чужой документ в порядок не попадает — его значение уже окончательно.
    assertThat(components).hasSize(1);
    assertThat(components.get(0)).containsExactly(FIRST);
  }

  @Test
  void repeatedDependencyIsCountedOnce() {
    // given: зависимость названа дважды.
    Map<URI, Collection<URI>> edges = Map.of(
      FIRST, List.of(SECOND, SECOND),
      SECOND, List.of()
    );
    var order = DocumentDependencies.of(edges.keySet(), uri -> edges.getOrDefault(uri, List.of()));

    // when
    var components = order.components().stream().map(component -> component.get(0)).toList();

    // then: порядок обычный, повтор ничего не ломает.
    assertThat(components).containsExactly(SECOND, FIRST);
  }
}
