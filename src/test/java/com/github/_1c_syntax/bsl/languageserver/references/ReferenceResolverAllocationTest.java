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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.sun.management.ThreadMXBean;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReferenceResolverAllocationTest {
  private static volatile Optional<Reference> escapedReference;

  @Test
  void repeatedMissesHaveBoundedAllocationForBothLookupForms(TestReporter reporter) {
    var management = ManagementFactory.getThreadMXBean();
    assumeTrue(management instanceof ThreadMXBean);
    var allocations = (ThreadMXBean) management;
    assumeTrue(allocations.isThreadAllocatedMemorySupported() && allocations.isThreadAllocatedMemoryEnabled());
    var empty = new EmptyFinder();
    var resolver = new ReferenceResolver(List.of(empty, empty, empty, empty));
    var uri = URI.create("file:///resolver-allocation.bsl");
    var position = new Position(0, 0);
    var terminal = new TerminalNodeImpl(new CommonToken(0, "X"));
    try {
      for (var warmup = 0; warmup < 10_000; warmup++) {
        escapedReference = resolver.findReference(uri, position);
        escapedReference = resolver.findReference(uri, terminal);
      }
      var thread = Thread.currentThread().threadId();
      var before = allocations.getThreadAllocatedBytes(thread);
      for (var read = 0; read < 1000; read++) {
        escapedReference = resolver.findReference(uri, position);
        escapedReference = resolver.findReference(uri, terminal);
      }
      var allocated = allocations.getThreadAllocatedBytes(thread) - before;
      reporter.publishEntry("bytesFor2000ReferenceMisses", Long.toString(allocated));
      assertThat(escapedReference).isEmpty();
      // An inline spy in another test instruments every instance of this class.
      // Its dispatch allocations fit this budget; the old Stream pipelines require over 1 MiB.
      assertThat(allocated).isBetween(0L, 256_000L);
    } finally {
      escapedReference = null;
    }
  }

  private static final class EmptyFinder implements ReferenceFinder {
    @Override
    public Optional<Reference> findReference(URI uri, Position position) {
      return Optional.empty();
    }

    @Override
    public Optional<Reference> findReference(URI uri, TerminalNode terminal) {
      return Optional.empty();
    }
  }
}
