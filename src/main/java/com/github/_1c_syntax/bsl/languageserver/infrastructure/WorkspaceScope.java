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

import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Помечает бин как принадлежащий per-workspace scope {@code "workspace"}
 * (см. {@link WorkspaceBeanScope}).
 * <p>
 * Композитная замена для повторяющегося
 * {@code @Scope(value = WorkspaceBeanScope.SCOPE_NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)}.
 * По умолчанию создаётся CGLIB-прокси ({@link ScopedProxyMode#TARGET_CLASS});
 * для бинов, объявленных через интерфейс (например {@code @Bean}-методы,
 * возвращающие интерфейс), укажите {@link ScopedProxyMode#INTERFACES}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scope(WorkspaceBeanScope.SCOPE_NAME)
public @interface WorkspaceScope {

  /**
   * Режим создания scoped-прокси для бина.
   *
   * @return режим прокси; по умолчанию {@link ScopedProxyMode#TARGET_CLASS}
   */
  @AliasFor(annotation = Scope.class, attribute = "proxyMode")
  ScopedProxyMode proxyMode() default ScopedProxyMode.TARGET_CLASS;
}
