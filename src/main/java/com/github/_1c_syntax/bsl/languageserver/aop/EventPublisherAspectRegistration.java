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
package com.github._1c_syntax.bsl.languageserver.aop;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Регистратор {@link EventPublisherAspect} для каждого Spring-контекста.
 * <p>
 * Поскольку {@link EventPublisherAspect} является синглтоном AspectJ CTW — один экземпляр на всю JVM —
 * каждый Spring-контекст создаёт <em>свой</em> экземпляр этого {@code @Component}.
 * <p>
 * При инициализации бина {@link ApplicationContextAware#setApplicationContext} вызывается Spring-ом
 * с тем контекстом, которому принадлежит бин (строгий контракт Spring). Бин регистрирует свой контекст
 * в аспекте. При уничтожении контекста {@link PreDestroy} гарантированно отменяет регистрацию,
 * не затрагивая остальные живые контексты.
 */
@Component
@RequiredArgsConstructor
public class EventPublisherAspectRegistration implements ApplicationContextAware {

  private final EventPublisherAspect aspect;

  private ApplicationContext applicationContext;

  @Override
  public void setApplicationContext(ApplicationContext ctx) {
    this.applicationContext = ctx;
    aspect.register(ctx);
  }

  @PreDestroy
  public void destroy() {
    aspect.unregister(applicationContext);
  }
}
