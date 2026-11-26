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
package com.github._1c_syntax.bsl.languageserver.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WorkspaceScopedProxyFactoryBeanTest {

  private static final URI FIRST = URI.create("file:///proxy-first");
  private static final URI SECOND = URI.create("file:///proxy-second");

  @Autowired
  private Counter counter;

  @Autowired
  private WorkspaceBeanScope workspaceScope;

  @AfterEach
  void tearDown() {
    workspaceScope.removeWorkspace(FIRST);
    workspaceScope.removeWorkspace(SECOND);
  }

  @Test
  void callGoesToTheBeanOfTheCurrentWorkspace() {
    // given
    try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
      counter.increment();
      counter.increment();
    }

    // when
    int inSecond;
    try (var ignored = WorkspaceContextHolder.forUri(SECOND, "second")) {
      inSecond = counter.increment();
    }
    int inFirst;
    try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
      inFirst = counter.increment();
    }

    // then: у каждой рабочей области свой экземпляр, и прокси находит его и после создания
    assertThat(inSecond).isEqualTo(1);
    assertThat(inFirst).isEqualTo(3);
  }

  @Test
  void proxyIsBuiltByWorkspaceFactory() {
    // then: цель прокси берёт источник фабрики рабочей области, а не SimpleBeanTargetSource Spring
    var targetSource = ((Advised) counter).getTargetSource();
    assertThat(targetSource.getClass().getEnclosingClass()).isEqualTo(WorkspaceScopedProxyFactoryBean.class);
  }

  @Test
  void scopedObjectGivesTheBeanOfTheCurrentWorkspace() {
    // given
    try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
      counter.increment();

      // when
      var target = ((ScopedObject) counter).getTargetObject();

      // then: прокси по-прежнему ScopedObject, и цель — бин текущей рабочей области
      assertThat(AopUtils.isCglibProxy(counter)).isTrue();
      assertThat(target).isNotSameAs(counter);
      assertThat(((Counter) target).increment()).isEqualTo(2);
    }
  }

  @Test
  void removeFromScopeDropsTheBeanOfTheCurrentWorkspace() {
    // given
    try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
      counter.increment();
      counter.increment();

      // when
      ((ScopedObject) counter).removeFromScope();

      // then: следующий вызов идёт в новый бин этой рабочей области
      assertThat(counter.increment()).isEqualTo(1);
    }
  }

  @Test
  void callWithoutWorkspaceFailsAsBefore() {
    // given
    WorkspaceContextHolder.clear();

    // when, then: без выбранной рабочей области бин по-прежнему не создаётся
    assertThatThrownBy(() -> counter.increment())
      .hasStackTraceContaining("Workspace context is not set");
  }

  @TestConfiguration
  static class TestConfig {
    @Bean
    @WorkspaceScope
    Counter counter() {
      return new Counter();
    }
  }

  static class Counter {
    private int value;

    public int increment() {
      return ++value;
    }
  }
}
