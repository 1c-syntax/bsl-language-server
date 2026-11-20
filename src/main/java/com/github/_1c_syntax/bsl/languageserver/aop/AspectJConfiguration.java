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

import com.github._1c_syntax.bsl.languageserver.aop.measures.ConditionalOnMeasuresEnabled;
import org.aspectj.lang.Aspects;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Конфигурация фреймворка AspectJ.
 * <p>
 * Каждый аспект с compile-time weaving должен быть объявлен как {@link Bean}.
 */
@Configuration
@ImportRuntimeHints(AspectJConfiguration.AspectsRuntimeHints.class)
public class AspectJConfiguration {

  /**
   * Подсказки для native-image к коду, который добавляет ajc при compile-time weaving.
   * <ul>
   *   <li>экземпляры аспектов получаются рефлексивным вызовом статического {@code aspectOf()};</li>
   *   <li>статические части join point'ов ({@code ajc$tjp_*}) при инициализации класса разрешают
   *   по имени типы своих сигнатур ({@code Factory.makeClass}): объявляющий класс, параметры,
   *   результат, исключения. Вплетённые классы находятся на classpath во время AOT-обработки.</li>
   * </ul>
   */
  static class AspectsRuntimeHints implements RuntimeHintsRegistrar {

    private static final String BASE_PACKAGE = "com.github._1c_syntax.bsl.languageserver";
    private static final String JOIN_POINT_FIELD_PREFIX = "ajc$tjp_";

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
      Stream.of(EventPublisherAspect.class, SentryAspect.class, MeasuresAspect.class)
        .forEach(aspectClass -> hints.reflection().registerType(aspectClass, MemberCategory.INVOKE_PUBLIC_METHODS));

      wovenClasses(classLoader).forEach(wovenClass -> registerSignatureTypes(hints, wovenClass));
    }

    private static Stream<Class<?>> wovenClasses(@Nullable ClassLoader classLoader) {
      var scanner = new ClassPathScanningCandidateComponentProvider(false) {
        @Override
        protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
          return true;
        }
      };
      scanner.setResourceLoader(new DefaultResourceLoader(classLoader));
      scanner.addIncludeFilter((metadataReader, metadataReaderFactory) -> true);
      return scanner.findCandidateComponents(BASE_PACKAGE).stream()
        .map(BeanDefinition::getBeanClassName)
        .filter(Objects::nonNull)
        .<Class<?>>map(className -> ClassUtils.resolveClassName(className, classLoader))
        .filter(AspectsRuntimeHints::hasJoinPoints);
    }

    private static boolean hasJoinPoints(Class<?> type) {
      return Arrays.stream(type.getDeclaredFields())
        .anyMatch(field -> field.getName().startsWith(JOIN_POINT_FIELD_PREFIX));
    }

    private static void registerSignatureTypes(RuntimeHints hints, Class<?> wovenClass) {
      var types = new HashSet<Class<?>>();
      types.add(wovenClass);
      for (var method : wovenClass.getDeclaredMethods()) {
        types.add(method.getReturnType());
        addExecutableTypes(types, method);
      }
      for (var constructor : wovenClass.getDeclaredConstructors()) {
        addExecutableTypes(types, constructor);
      }
      types.stream()
        .map(AspectsRuntimeHints::componentType)
        .filter(type -> !type.isPrimitive())
        .forEach(type -> hints.reflection().registerType(type));
    }

    private static void addExecutableTypes(Set<Class<?>> types, Executable executable) {
      types.addAll(Arrays.asList(executable.getParameterTypes()));
      types.addAll(Arrays.asList(executable.getExceptionTypes()));
    }

    private static Class<?> componentType(Class<?> type) {
      var result = type;
      while (result.isArray()) {
        result = result.getComponentType();
      }
      return result;
    }
  }

  /**
   * @return Основной аспект подсистемы событий.
   */
  @Bean
  @Lazy(false)
  public EventPublisherAspect eventPublisherAspect() {
    return Aspects.aspectOf(EventPublisherAspect.class);
  }

  /**
   * @return Аспект перехвата ошибок и взаимодействия с Sentry.
   */
  @Bean
  @Lazy(false)
  public SentryAspect sentryAspect() {
    return Aspects.aspectOf(SentryAspect.class);
  }

  /**
   * @return Аспект выполнения замеров производительности.
   */
  @Bean
  @ConditionalOnMeasuresEnabled
  public MeasuresAspect measuresAspect() {
    return Aspects.aspectOf(MeasuresAspect.class);
  }
}
