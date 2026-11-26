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

import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.FlowSession;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FlowSessionAllocationTest {
  private static volatile FlowSession escapedSession;

  @Test
  void emptySessionsHaveBoundedAllocation(TestReporter reporter) {
    var management = ManagementFactory.getThreadMXBean();
    assumeTrue(management instanceof ThreadMXBean);
    var allocations = (ThreadMXBean) management;
    assumeTrue(allocations.isThreadAllocatedMemorySupported() && allocations.isThreadAllocatedMemoryEnabled());
    try {
      for (var warmup = 0; warmup < 2000; warmup++) {
        escapedSession = new FlowSession();
      }
      var thread = Thread.currentThread().threadId();
      var before = allocations.getThreadAllocatedBytes(thread);
      for (var query = 0; query < 1000; query++) {
        escapedSession = new FlowSession();
      }
      var allocated = allocations.getThreadAllocatedBytes(thread) - before;
      reporter.publishEntry("bytesFor1000EmptySessions", Long.toString(allocated));
      assertThat(allocated).isBetween(0L, 512_000L);
    } finally {
      escapedSession = null;
    }
  }
}
