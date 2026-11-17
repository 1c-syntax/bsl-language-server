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

import org.jspecify.annotations.Nullable;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragments;
import org.springframework.beans.factory.aot.BeanRegistrationCodeFragmentsDecorator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.ClassUtils;

import javax.lang.model.element.Modifier;
import java.util.function.Predicate;

/**
 * AOT-обработка прокси бинов рабочей области — аналог обработки {@link ScopedProxyFactoryBean}
 * в Spring для {@link WorkspaceScopedProxyFactoryBean}.
 * <p>
 * Сгенерированное описание прокси несёт тип бина-цели, поэтому прокси находится по типу ещё до
 * создания фабрики. Фабрика создаётся сгенерированным кодом: имя цели и режим проксирования
 * ({@code proxyTargetClass}) задаются до {@link WorkspaceScopedProxyFactoryBean#setBeanFactory},
 * где строится прокси.
 */
class WorkspaceScopedProxyBeanRegistrationAotProcessor implements BeanRegistrationAotProcessor {

  private static final String TARGET_BEAN_NAME_PROPERTY = "targetBeanName";
  private static final String PROXY_TARGET_CLASS_PROPERTY = "proxyTargetClass";

  @Override
  public @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
    if (!WorkspaceScopedProxyFactoryBean.class.equals(registeredBean.getBeanClass())) {
      return null;
    }

    var propertyValues = registeredBean.getMergedBeanDefinition().getPropertyValues();
    if (!(propertyValues.get(TARGET_BEAN_NAME_PROPERTY) instanceof String targetBeanName)
      || !registeredBean.getBeanFactory().containsBean(targetBeanName)) {
      return null;
    }

    var targetBeanDefinition = registeredBean.getBeanFactory().getMergedBeanDefinition(targetBeanName);
    var proxyTargetClass = propertyValues.get(PROXY_TARGET_CLASS_PROPERTY);

    return BeanRegistrationAotContribution.withCustomCodeFragments(codeFragments ->
      new CodeFragments(codeFragments, registeredBean, targetBeanName, targetBeanDefinition, proxyTargetClass));
  }

  private static final class CodeFragments extends BeanRegistrationCodeFragmentsDecorator {

    private static final String REGISTERED_BEAN_PARAMETER = "registeredBean";

    private final RegisteredBean registeredBean;
    private final String targetBeanName;
    private final BeanDefinition targetBeanDefinition;
    private final @Nullable Object proxyTargetClass;

    private CodeFragments(
      BeanRegistrationCodeFragments delegate,
      RegisteredBean registeredBean,
      String targetBeanName,
      BeanDefinition targetBeanDefinition,
      @Nullable Object proxyTargetClass
    ) {
      super(delegate);
      this.registeredBean = registeredBean;
      this.targetBeanName = targetBeanName;
      this.targetBeanDefinition = targetBeanDefinition;
      this.proxyTargetClass = proxyTargetClass;
    }

    @Override
    public ClassName getTarget(RegisteredBean registeredBean) {
      // Код кладётся туда же, куда и код бина-цели (например, к классу его @Bean-метода):
      // там доступны и сам тип цели, и типы в его параметрах.
      var targetBean = RegisteredBean.of(registeredBean.getBeanFactory(), targetBeanName);
      var declaringClass = targetBean.resolveInstantiationDescriptor().targetClass();
      return ClassName.get(ClassUtils.getUserClass(declaringClass));
    }

    @Override
    public CodeBlock generateNewBeanDefinitionCode(
      GenerationContext generationContext,
      ResolvableType beanType,
      BeanRegistrationCode beanRegistrationCode
    ) {
      return super.generateNewBeanDefinitionCode(
        generationContext,
        targetBeanDefinition.getResolvableType(),
        beanRegistrationCode
      );
    }

    @Override
    public CodeBlock generateSetBeanDefinitionPropertiesCode(
      GenerationContext generationContext,
      BeanRegistrationCode beanRegistrationCode,
      RootBeanDefinition beanDefinition,
      Predicate<String> attributeFilter
    ) {
      var processedBeanDefinition = new RootBeanDefinition(beanDefinition);
      processedBeanDefinition.setTargetType(targetBeanDefinition.getResolvableType());
      processedBeanDefinition.getPropertyValues().removePropertyValue(TARGET_BEAN_NAME_PROPERTY);
      processedBeanDefinition.getPropertyValues().removePropertyValue(PROXY_TARGET_CLASS_PROPERTY);
      return super.generateSetBeanDefinitionPropertiesCode(
        generationContext,
        beanRegistrationCode,
        processedBeanDefinition,
        attributeFilter
      );
    }

    @Override
    public CodeBlock generateInstanceSupplierCode(
      GenerationContext generationContext,
      BeanRegistrationCode beanRegistrationCode,
      boolean allowDirectSupplierShortcut
    ) {
      var generatedMethod = beanRegistrationCode.getMethods().add("getWorkspaceScopedProxyInstance", method -> {
        method.addJavadoc("Create the workspace scoped proxy bean instance for '$L'.", registeredBean.getBeanName());
        method.addModifiers(Modifier.PRIVATE, Modifier.STATIC);
        method.returns(WorkspaceScopedProxyFactoryBean.class);
        method.addParameter(RegisteredBean.class, REGISTERED_BEAN_PARAMETER);
        method.addStatement("$T factory = new $T()",
          WorkspaceScopedProxyFactoryBean.class, WorkspaceScopedProxyFactoryBean.class);
        method.addStatement("factory.setTargetBeanName($S)", targetBeanName);
        if (proxyTargetClass != null) {
          method.addStatement("factory.setProxyTargetClass($L)", Boolean.parseBoolean(proxyTargetClass.toString()));
        }
        method.addStatement("factory.setBeanFactory($L.getBeanFactory())", REGISTERED_BEAN_PARAMETER);
        method.addStatement("return factory");
      });
      return CodeBlock.of("$T.of($L)", InstanceSupplier.class, generatedMethod.toMethodReference().toCodeBlock());
    }
  }
}
