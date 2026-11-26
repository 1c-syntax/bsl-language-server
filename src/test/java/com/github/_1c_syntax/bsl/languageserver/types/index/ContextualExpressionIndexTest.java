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

import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import org.antlr.v4.runtime.ParserRuleContext;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextualExpressionIndexTest {
  private static final TypeRef STRING = new TypeRef(TypeKind.PRIMITIVE, "String");
  private static final TypeRef NUMBER = new TypeRef(TypeKind.PRIMITIVE, "Number");
  private final InferredExpressionTypeIndex index = new InferredExpressionTypeIndex();
  private final URI uri = URI.create("file:///root.bsl");
  private final ParserRuleContext node = new ParserRuleContext(null, -1);

  @Test
  void separatesModesNodesAndNeutralResults() {
    var server = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    index.put(server, TypeSet.of(STRING), Set.of());
    var client = index.contextualQuery(uri, node, ExecutionContext.THIN_CLIENT);
    index.put(client, TypeSet.of(NUMBER), Set.of());
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached())
      .isEqualTo(TypeSet.of(STRING));
    assertThat(index.contextualQuery(uri, node, ExecutionContext.THIN_CLIENT).cached())
      .isEqualTo(TypeSet.of(NUMBER));
    assertThat(index.contextualQuery(uri, new ParserRuleContext(null, -1), ExecutionContext.SERVER).cached()).isNull();
    assertThat(index.get(uri, node)).isNull();
  }

  @Test
  void cachesCompleteEmptyResults() {
    index.put(index.contextualQuery(uri, node, ExecutionContext.SERVER), TypeSet.EMPTY, Set.of());
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached()).isSameAs(TypeSet.EMPTY);
  }

  @Test
  void clearsTransitiveDependents() {
    var dependency = URI.create("file:///dependency.bsl");
    var consumer = URI.create("file:///consumer.bsl");
    index.put(index.contextualQuery(uri, node, ExecutionContext.SERVER), TypeSet.EMPTY, Set.of(dependency));
    index.put(index.contextualQuery(consumer, node, ExecutionContext.SERVER), TypeSet.EMPTY, Set.of(uri));
    index.clear(dependency);
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached()).isNull();
    assertThat(index.contextualQuery(consumer, node, ExecutionContext.SERVER).cached()).isNull();
  }

  @Test
  void rejectsLatePublicationAfterOwnDocumentChanges() {
    var old = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    index.clear(uri);
    var current = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    index.put(current, TypeSet.of(NUMBER), Set.of());
    index.put(old, TypeSet.of(STRING), Set.of());
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached())
      .isEqualTo(TypeSet.of(NUMBER));
  }

  @Test
  void rejectsLatePublicationWhenAnInputChangedBeforeDependencyWasRecorded() {
    var dependency = URI.create("file:///dependency.bsl");
    var old = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    index.clear(dependency);
    index.put(old, TypeSet.of(STRING), Set.of(dependency));
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached()).isNull();
  }

  @Test
  void neutralUnionKeepsModesSeparateAndRetainsEveryDependency() {
    var root = index.contextualQuery(uri, node, null);
    var server = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var client = index.contextualQuery(uri, node, ExecutionContext.THIN_CLIENT);
    var text = TypeSet.of(STRING);
    var number = TypeSet.of(NUMBER);
    var dependency = URI.create("file:///client-input.bsl");
    index.put(server, text, Set.of(URI.create("file:///server-input.bsl")));
    index.put(client, number, Set.of(dependency));
    var union = text.union(number);
    assertThat(index.putNeutralFromContexts(root, List.of(server, client), List.of(text, number), union)).isTrue();
    assertThat(root.cached()).isSameAs(union);
    assertThat(index.get(uri, node)).isSameAs(union);
    assertThat(server.cached()).isSameAs(text);
    assertThat(client.cached()).isSameAs(number);
    index.clear(dependency);
    assertThat(index.get(uri, node)).isNull();
    assertThat(index.contextualQuery(uri, node, null).cached()).isNull();
    assertThat(index.contextualQuery(uri, node, ExecutionContext.SERVER).cached()).isNull();
  }

  @Test
  void neutralUnionRejectsPublicationAfterOwnDocumentChanges() {
    var root = index.contextualQuery(uri, node, null);
    var part = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var text = TypeSet.of(STRING);
    index.put(part, text, Set.of());
    index.clear(uri);
    var current = TypeSet.of(NUMBER);
    index.put(uri, node, current, Set.of());
    assertThat(index.putNeutralFromContexts(root, List.of(part), List.of(text), text)).isFalse();
    assertThat(index.get(uri, node)).isSameAs(current);
  }

  @Test
  void neutralUnionRejectsAnInputChangeBeforeContextSelection() {
    var root = index.contextualQuery(uri, node, null);
    var dependency = URI.create("file:///new-input.bsl");
    index.clear(dependency);
    var part = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var text = TypeSet.of(STRING);
    index.put(part, text, Set.of(dependency));
    assertThat(index.putNeutralFromContexts(root, List.of(part), List.of(text), text)).isFalse();
    assertThat(index.get(uri, node)).isNull();
  }

  @Test
  void neutralUnionRejectsAnUnpublishedMode() {
    var root = index.contextualQuery(uri, node, null);
    var part = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var text = TypeSet.of(STRING);
    assertThat(index.putNeutralFromContexts(root, List.of(part), List.of(text), text)).isFalse();
    assertThat(root.cached()).isNull();
  }

  @Test
  void neutralUnionRejectsPartsFromAnotherNodeOrDocument() {
    var root = index.contextualQuery(uri, node, null);
    var text = TypeSet.of(STRING);
    for (var part : List.of(
      index.contextualQuery(uri, new ParserRuleContext(null, -1), ExecutionContext.SERVER),
      index.contextualQuery(URI.create("file:///other.bsl"), node, ExecutionContext.SERVER))) {
      index.put(part, text, Set.of());
      assertThat(index.putNeutralFromContexts(root, List.of(part), List.of(text), text)).isFalse();
    }
  }

  @Test
  void neutralUnionRejectsAReplacedPartValue() {
    var root = index.contextualQuery(uri, node, null);
    var part = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var old = TypeSet.of(STRING);
    index.put(part, old, Set.of());
    index.put(part, TypeSet.of(NUMBER), Set.of());
    assertThat(index.putNeutralFromContexts(root, List.of(part), List.of(old), old)).isFalse();
  }

  @Test
  void neutralUnionCannotOverwriteASelectedMode() {
    var part = index.contextualQuery(uri, node, ExecutionContext.SERVER);
    var text = TypeSet.of(STRING);
    index.put(part, text, Set.of());
    assertThat(index.putNeutralFromContexts(part, List.of(part), List.of(text), text.union(TypeSet.of(NUMBER)))).isFalse();
    assertThat(part.cached()).isSameAs(text);
  }
}
