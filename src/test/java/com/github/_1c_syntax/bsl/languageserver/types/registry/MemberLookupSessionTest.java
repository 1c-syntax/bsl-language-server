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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberLookupSessionTest {
  @Test
  void callersOutsideTheBatchKeepTheOriginalIterationPath() {
    var members = members(false);
    assertThat(MemberLookupSession.candidates(members, MemberKind.PROPERTY, "Имя")).isSameAs(members);
    assertThat(members.iterations).hasValue(0);
    var small = List.of(MemberDescriptor.property("Имя"));
    try (var scope = new MemberLookupSession().open()) {
      assertThat(MemberLookupSession.candidates(small, MemberKind.PROPERTY, "Имя")).isSameAs(small);
    }
  }

  @Test
  void repeatedLookupsReuseTheImmutableSnapshot() {
    var members = members(false);
    try (var scope = new MemberLookupSession().open()) {
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя")).hasSize(1);
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя")).hasSize(1);
      assertThat(members.iterations).hasValue(1);
    }
  }

  @Test
  void theCacheDoesNotSurviveTheBatch() {
    var members = members(false);
    MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
    MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
    assertThat(members.iterations).hasValue(2);
    try (var scope = new MemberLookupSession().open()) {
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
    }
    MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
    assertThat(members.iterations).hasValue(4);
  }

  @Test
  void equalSnapshotsWithDifferentIdentityRemainIndependent() {
    var first = members(false);
    var second = new CountingMembers(first.values, false);
    assertThat(first.equals(second)).isTrue();
    try (var scope = new MemberLookupSession().open()) {
      MemberLookupSession.lookup(first, MemberKind.PROPERTY, "Имя");
      MemberLookupSession.lookup(second, MemberKind.PROPERTY, "Имя");
      assertThat(first.iterations).hasValue(1);
      assertThat(second.iterations).hasValue(1);
    }
  }

  @Test
  void kindsLocalesAndCaseKeepTheirOriginalMatchingRules() {
    var property = MemberDescriptor.property("Имя").withBilingualName(BilingualString.of("Имя", "Name"));
    var method = MemberDescriptor.method("Имя");
    var values = new ArrayList<MemberDescriptor>(members(false).values);
    values.set(0, property);
    values.add(method);
    var members = new CountingMembers(values, false);
    try (var scope = new MemberLookupSession().open()) {
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "иМя")).containsExactly(property);
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "nAME")).containsExactly(property);
      assertThat(MemberLookupSession.lookup(members, MemberKind.METHOD, "ИМЯ")).containsExactly(method);
      assertThat(MemberLookupSession.first(members, MemberKind.PROPERTY, "Name")).containsSame(property);
    }
  }

  @Test
  void nestedScopesRestoreTheOuterSession() {
    var members = members(false);
    try (var outer = new MemberLookupSession().open()) {
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
      try (var inner = new MemberLookupSession().open()) {
        MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
      }
      MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя");
      assertThat(members.iterations).hasValue(2);
    }
  }

  @Test
  void diagnosticWorkersShareOnlyTheLookupResults() throws Exception {
    var members = members(false);
    var session = new MemberLookupSession();
    var start = new CyclicBarrier(2);
    var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().daemon().factory());
    try {
      var first = executor.submit(() -> { repeat(session, members, start); return null; });
      var second = executor.submit(() -> { repeat(session, members, start); return null; });
      first.get(5, SECONDS); second.get(5, SECONDS);
      assertThat(members.iterations.get()).isBetween(1, 2);
    } finally { executor.shutdownNow(); }
  }

  @Test
  void aFailedLookupIsRetried() {
    var members = members(true);
    try (var scope = new MemberLookupSession().open()) {
      assertThatThrownBy(() -> MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя"))
        .isInstanceOf(IllegalStateException.class);
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя")).hasSize(1);
      assertThat(members.iterations).hasValue(2);
    }
  }

  @Test
  void aCompleteEmptyResultCanBeReused() {
    var members = members(false);
    try (var scope = new MemberLookupSession().open()) {
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Missing")).isEmpty();
      assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Missing")).isEmpty();
      assertThat(members.iterations).hasValue(1);
    }
  }

  private static void repeat(MemberLookupSession session, CountingMembers members, CyclicBarrier start) throws Exception {
    try (var scope = session.open()) {
      start.await(3, SECONDS);
      for (var i = 0; i < 50; i++) {
        assertThat(MemberLookupSession.lookup(members, MemberKind.PROPERTY, "Имя")).hasSize(1);
      }
    }
  }

  private static CountingMembers members(boolean failFirst) {
    var values = new ArrayList<MemberDescriptor>();
    values.add(MemberDescriptor.property("Имя"));
    for (var i = 0; i < 12; i++) values.add(MemberDescriptor.property("Other" + i));
    return new CountingMembers(values, failFirst);
  }

  private static final class CountingMembers extends AbstractCollection<MemberDescriptor> {
    private final List<MemberDescriptor> values;
    private final boolean failFirst;
    private final AtomicInteger iterations = new AtomicInteger();
    private CountingMembers(List<MemberDescriptor> values, boolean failFirst) {
      this.values = List.copyOf(values); this.failFirst = failFirst;
    }
    @Override public Iterator<MemberDescriptor> iterator() {
      if (iterations.incrementAndGet() == 1 && failFirst) throw new IllegalStateException("transient lookup failure");
      return values.iterator();
    }
    @Override public int size() { return values.size(); }
    @Override public boolean equals(Object other) { return other instanceof CountingMembers that && values.equals(that.values); }
    @Override public int hashCode() { return values.hashCode(); }
  }
}
