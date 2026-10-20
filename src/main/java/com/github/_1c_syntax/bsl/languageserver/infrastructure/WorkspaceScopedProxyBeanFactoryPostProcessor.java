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

import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Заменяет фабрики прокси бинов области {@link WorkspaceBeanScope#SCOPE_NAME}
 * на {@link WorkspaceScopedProxyFactoryBean}, сохраняя остальные свойства описаний бинов.
 */
public final class WorkspaceScopedProxyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    for (var name : beanFactory.getBeanDefinitionNames()) {
      var definition = beanFactory.getBeanDefinition(name);
      if (!ScopedProxyFactoryBean.class.getName().equals(definition.getBeanClassName())) {
        continue;
      }

      var targetBeanName = definition.getPropertyValues().get("targetBeanName");
      if (targetBeanName instanceof String target
        && beanFactory.containsBeanDefinition(target)
        && WorkspaceBeanScope.SCOPE_NAME.equals(beanFactory.getBeanDefinition(target).getScope())) {
        definition.setBeanClassName(WorkspaceScopedProxyFactoryBean.class.getName());
      }
    }
  }
}
