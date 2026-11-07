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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.RandomAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;

class SymbolPositionLookupTest extends AbstractServerContextAwareTest {
  @Test
  void lookupMatchesHierarchicalTraversalAtEverySourcePosition() throws Exception {
    initServerContext();
    var sources = List.of(
      Files.readString(Path.of("src/test/resources/context/symbol/SymbolTreeComputer.bsl")),
      "#Область Внешняя\nПерем А, Б;\nПроцедура М(П)\n#Область Внутренняя\nЛ = П;\n#КонецОбласти\nКонецПроцедуры\n#КонецОбласти\n",
      "Процедура А() КонецПроцедуры Процедура Б() КонецПроцедуры\n",
      "#Область Незакрытая\nПроцедура М(\nЕсли Тогда\nА = ;\nКонецПроцедуры\n"
    );
    for (var source : sources) {
      var tree = TestUtils.getDocumentContext(source, context).getSymbolTree();
      assertPositions(tree, source.lines().toList());
    }
  }

  @Test
  void overlappingEmptyAndReversedRangesKeepTheirPriority() {
    var children = new ArrayList<SourceDefinedSymbol>();
    for (var i = 0; i < 12; i++) {
      children.add(symbol("Символ" + i, Ranges.create(i, 0, i + 2, 3),
        i == 3 ? SymbolKind.Namespace : SymbolKind.Variable));
    }
    var orderedModule = ModuleSymbol.builder().name("Тест").range(Ranges.create(0, 0, 25, 0))
      .selectionRange(Ranges.create()).children(List.copyOf(children)).build();
    assertPositions(new SymbolTree(orderedModule), java.util.Collections.nCopies(26, "        "));
    children.add(0, symbol("Область", Ranges.create(0, 0, 20, 0), SymbolKind.Namespace));
    children.add(2, symbol("Пустой", Ranges.create(1, 2, 1, 2), SymbolKind.Variable));
    children.add(3, symbol("Обратный", Ranges.create(3, 4, 2, 0), SymbolKind.Variable));
    var module = ModuleSymbol.builder().name("Тест").range(Ranges.create(0, 0, 25, 0))
      .selectionRange(Ranges.create()).children(children).build();
    var tree = new SymbolTree(module);
    assertPositions(tree, java.util.Collections.nCopies(26, "        "));
    var points = new ArrayList<SourceDefinedSymbol>();
    for (var i = 0; i < 12; i++) {
      points.add(symbol("Точка" + i, Ranges.create(i, 0, i, 0), SymbolKind.Variable));
    }
    var pointModule = ModuleSymbol.builder().name("Точки").range(Ranges.create(0, 0, 25, 0))
      .selectionRange(Ranges.create()).children(points).build();
    assertPositions(new SymbolTree(pointModule), java.util.Collections.nCopies(26, "        "));
  }

  @Test
  void largeOrderedScopeDoesNotScanEverySibling(TestReporter reporter) {
    initServerContext();
    var source = new StringBuilder();
    for (var i = 0; i < 4096; i++) {
      source.append("Процедура М%d()\n  Сообщить(1);\nКонецПроцедуры\n".formatted(i));
    }
    var original = TestUtils.getDocumentContext(source.toString(), context).getSymbolTree();
    var children = new CountingList(original.getModule().getChildren());
    ReflectionTestUtils.setField(original.getModule(), "children", children);
    var tree = new SymbolTree(original.getModule());
    var position = new Position(4095 * 3 + 1, 4);
    assertThat(tree.getSymbolAtPosition(position).getName()).isEqualTo("М4095");
    children.reads = 0;

    assertThat(tree.getSymbolAtPosition(position).getName()).isEqualTo("М4095");

    reporter.publishEntry("siblingReads", Integer.toString(children.reads));
    assertThat(children.reads).as("reads in a 4096-method scope after index initialization").isLessThan(64);
  }

  private static void assertPositions(SymbolTree tree, List<String> lines) {
    for (var line = 0; line < lines.size(); line++) {
      for (var character = 0; character <= lines.get(line).length() + 1; character++) {
        var position = new Position(line, character);
        assertThat(tree.getSymbolAtPosition(position)).as("position %s", position)
          .isSameAs(linear(tree.getModule(), position));
      }
    }
  }

  private static SourceDefinedSymbol linear(SourceDefinedSymbol parent, Position position) {
    for (var child : parent.getChildren()) {
      if (Ranges.containsPosition(child.getRange(), position)) {
        var found = linear(child, position);
        if (found.getSymbolKind() != SymbolKind.Namespace) {
          return found;
        }
      }
    }
    return parent;
  }

  private static SourceDefinedSymbol symbol(String name, Range range, SymbolKind kind) {
    var symbol = mock(SourceDefinedSymbol.class, CALLS_REAL_METHODS);
    when(symbol.getName()).thenReturn(name);
    when(symbol.getRange()).thenReturn(range);
    when(symbol.getSymbolKind()).thenReturn(kind);
    when(symbol.getChildren()).thenReturn(List.of());
    return symbol;
  }

  private static final class CountingList extends AbstractList<SourceDefinedSymbol> implements RandomAccess {
    private final List<SourceDefinedSymbol> delegate;
    private int reads;
    private CountingList(List<SourceDefinedSymbol> delegate) {
      this.delegate = delegate;
    }
    @Override
    public SourceDefinedSymbol get(int index) {
      reads++;
      return delegate.get(index);
    }
    @Override
    public int size() {
      return delegate.size();
    }
  }
}
