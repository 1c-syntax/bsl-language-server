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

import org.aopalliance.intercept.MethodInterceptor;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.scope.DefaultScopedObject;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.ClassUtils;

import java.io.Serial;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

/**
 * Прокси бина рабочей области — замена {@link ScopedProxyFactoryBean} для области
 * {@link WorkspaceBeanScope#SCOPE_NAME}.
 * <p>
 * Прокси устроен так же, но без двух вещей, которые стоят на каждом вызове. Цель берётся прямо
 * из {@link WorkspaceBeanScope}, а фабрика бинов спрашивается, только пока бина в рабочей
 * области ещё нет. {@link ScopedObject} прокси по-прежнему реализует, но его методы обслуживает
 * советник, который срабатывает только на них, а не перехватчик-введение на всех методах цели:
 * у остальных методов цепочка пуста, и вызов идёт в цель напрямую.
 */
public final class WorkspaceScopedProxyFactoryBean extends ProxyConfig
  implements FactoryBean<Object>, BeanFactoryAware, AopInfrastructureBean {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Pointcut SCOPED_OBJECT_METHODS = new StaticMethodMatcherPointcut() {
    @Override
    public boolean matches(Method method, Class<?> targetClass) {
      return method.getDeclaringClass() == ScopedObject.class;
    }
  };

  private @Nullable String targetBeanName;
  private transient @Nullable WorkspaceTargetSource targetSource;
  private transient @Nullable Object proxy;

  /**
   * Прокси по классу цели, как у {@link ScopedProxyFactoryBean}; по интерфейсам — если
   * {@code proxyTargetClass} выключен в описании бина.
   */
  public WorkspaceScopedProxyFactoryBean() {
    setProxyTargetClass(true);
  }

  /**
   * @param targetBeanName имя бина-цели в фабрике бинов.
   */
  public void setTargetBeanName(String targetBeanName) {
    this.targetBeanName = targetBeanName;
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) {
    if (!(beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory)) {
      throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
    }
    var name = Objects.requireNonNull(targetBeanName, "Property 'targetBeanName' is required");
    var beanType = beanFactory.getType(name);
    if (beanType == null) {
      throw new IllegalStateException(
        "Cannot create scoped proxy for bean '" + name + "': Target type could not be determined at the time of proxy creation.");
    }
    if (!(configurableBeanFactory.getRegisteredScope(WorkspaceBeanScope.SCOPE_NAME) instanceof WorkspaceBeanScope scope)) {
      throw new IllegalStateException("Scope '" + WorkspaceBeanScope.SCOPE_NAME + "' is not registered");
    }
    targetSource = new WorkspaceTargetSource(configurableBeanFactory, scope, name, beanType);

    var factory = new ProxyFactory();
    factory.copyFrom(this);
    factory.setTargetSource(targetSource);
    if (!isProxyTargetClass() || beanType.isInterface() || Modifier.isPrivate(beanType.getModifiers())) {
      factory.setInterfaces(ClassUtils.getAllInterfacesForClass(beanType, configurableBeanFactory.getBeanClassLoader()));
    }
    var scopedObject = new DefaultScopedObject(configurableBeanFactory, name);
    factory.addInterface(ScopedObject.class);
    factory.addAdvisor(new DefaultPointcutAdvisor(SCOPED_OBJECT_METHODS,
      (MethodInterceptor) invocation -> switch (invocation.getMethod().getName()) {
        case "getTargetObject" -> scopedObject.getTargetObject();
        case "removeFromScope" -> {
          scopedObject.removeFromScope();
          yield null;
        }
        default -> invocation.proceed();
      }));
    factory.addInterface(AopInfrastructureBean.class);

    proxy = factory.getProxy(configurableBeanFactory.getBeanClassLoader());
  }

  @Override
  public @Nullable Object getObject() {
    if (proxy == null) {
      throw new FactoryBeanNotInitializedException();
    }
    return proxy;
  }

  @Override
  public @Nullable Class<?> getObjectType() {
    if (proxy != null) {
      return proxy.getClass();
    }
    return targetSource == null ? null : targetSource.getTargetClass();
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  /**
   * Цель — бин текущей рабочей области; пока его нет, он создаётся обычным путём фабрики бинов.
   */
  private record WorkspaceTargetSource(
    ConfigurableBeanFactory beanFactory,
    WorkspaceBeanScope scope,
    String targetBeanName,
    Class<?> targetClass
  ) implements TargetSource {

    @Override
    public Class<?> getTargetClass() {
      return targetClass;
    }

    @Override
    public boolean isStatic() {
      return false;
    }

    @Override
    public Object getTarget() {
      var existing = scope.getIfPresent(targetBeanName);
      return existing != null ? existing : beanFactory.getBean(targetBeanName);
    }

    @Override
    public void releaseTarget(Object target) {
      // цель живёт в рабочей области, отпускать нечего
    }
  }
}
