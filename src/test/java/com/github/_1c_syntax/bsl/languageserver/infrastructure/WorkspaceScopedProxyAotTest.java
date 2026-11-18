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

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.test.tools.TestCompiler;

import java.net.URI;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Прокси бинов рабочей области должны переживать AOT-обработку контекста: бин находится
 * по типу и обращается к экземпляру текущей рабочей области.
 */
public class WorkspaceScopedProxyAotTest {

  private static final URI FIRST = URI.create("file:///aot-first");
  private static final URI SECOND = URI.create("file:///aot-second");

  @Test
  void workspaceScopedBeanIsResolvedByTypeInAotContext() {
    // given
    var buildTimeContext = new AnnotationConfigApplicationContext();
    buildTimeContext.registerBean(TestConfig.class);
    var generationContext = new TestGenerationContext();

    // when
    new ApplicationContextAotGenerator().processAheadOfTime(buildTimeContext, generationContext);
    generationContext.writeGeneratedContent();

    // then
    TestCompiler.forSystem().with(generationContext).compile(compiled -> {
      try (var context = new GenericApplicationContext()) {
        @SuppressWarnings("unchecked")
        ApplicationContextInitializer<GenericApplicationContext> initializer =
          compiled.getInstance(ApplicationContextInitializer.class);
        initializer.initialize(context);
        context.refresh();

        var counter = context.getBean(Consumer.class).counter();
        int inFirst;
        try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
          counter.increment();
          inFirst = counter.increment();
        }
        int inSecond;
        try (var ignored = WorkspaceContextHolder.forUri(SECOND, "second")) {
          inSecond = counter.increment();
        }

        assertThat(inFirst).isEqualTo(2);
        assertThat(inSecond).isEqualTo(1);

        var greeter = context.getBean(Consumer.class).greeter();
        assertThat(AopUtils.isJdkDynamicProxy(greeter)).isTrue();
        try (var ignored = WorkspaceContextHolder.forUri(FIRST, "first")) {
          assertThat(greeter.greet()).isEqualTo("hello");
        }
      }
    });
  }

  @Test
  void proxyClassIsRegisteredForReflectiveAccessToItsStaticState() {
    // given
    var buildTimeContext = new AnnotationConfigApplicationContext();
    buildTimeContext.registerBean(TestConfig.class);
    var generationContext = new TestGenerationContext();

    // when
    new ApplicationContextAotGenerator().processAheadOfTime(buildTimeContext, generationContext);

    // then: CGLIB при повторном использовании класса прокси читает его статические поля рефлексией
    var proxyClassPrefix = Counter.class.getName() + "$$SpringCGLIB$$";
    var proxyTypeHints = generationContext.getRuntimeHints().reflection().typeHints()
      .filter(typeHint -> typeHint.getType().getName().startsWith(proxyClassPrefix))
      .toList();
    assertThat(proxyTypeHints)
      .isNotEmpty()
      .allSatisfy(typeHint -> assertThat(typeHint.getMemberCategories())
        .contains(MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS));
  }

  @Test
  void proxyCodeIsGeneratedNextToTheBeanDeclaration() {
    // given
    var buildTimeContext = new AnnotationConfigApplicationContext();
    buildTimeContext.registerBean(HiddenTypeConfig.class);
    var generationContext = new TestGenerationContext();

    // when
    new ApplicationContextAotGenerator().processAheadOfTime(buildTimeContext, generationContext);
    generationContext.writeGeneratedContent();

    // then: код компилируется, только если лежит в пакете объявления бина, где виден Hidden.
    // Загружать его не нужно: TestCompiler грузит классы другим загрузчиком, и доступ к
    // package-private типам из них невозможен в принципе.
    TestCompiler.forSystem().with(generationContext).compile(compiled -> {
      // компиляция прошла
    });
  }

  // Область регистрируется без CGLIB-конфигурации: сгенерированный прокси-класс конфигурации
  // переиспользовался бы между тестами в одной JVM и не попадал бы в результат генерации.
  @Configuration(proxyBeanMethods = false)
  @Import(WorkspaceScopedProxyBeanFactoryPostProcessor.class)
  public static class ScopeConfig {
    @Bean
    public static CustomScopeConfigurer workspaceScopeConfigurer() {
      var configurer = new CustomScopeConfigurer();
      configurer.addScope(WorkspaceBeanScope.SCOPE_NAME, new WorkspaceBeanScope());
      return configurer;
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ScopeConfig.class)
  public static class TestConfig {
    // Объявлен раньше прокси: бин рабочей области ищется по типу, пока фабрика прокси
    // ещё не инициализирована — как при старте приложения.
    @Bean
    public Consumer consumer(Counter counter, Greeter greeter) {
      return new Consumer(counter, greeter);
    }

    @Bean
    @WorkspaceScope
    public Counter counter() {
      return new Counter();
    }

    // Тип бина — класс, а прокси по режиму INTERFACES должен быть интерфейсным.
    @Bean
    @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
    public SimpleGreeter greeter() {
      return new SimpleGreeter();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import(ScopeConfig.class)
  public static class HiddenTypeConfig {
    // Тип из чужого пакета с закрытым типом-параметром: код прокси должен генерироваться
    // рядом с объявлением бина, а не в пакете типа.
    @Bean
    @WorkspaceScope(proxyMode = ScopedProxyMode.INTERFACES)
    public Supplier<Hidden> hiddenSupplier() {
      return Hidden::new;
    }
  }

  static class Hidden {
  }

  public record Consumer(Counter counter, Greeter greeter) {
  }

  public interface Greeter {
    String greet();
  }

  public static class SimpleGreeter implements Greeter {
    @Override
    public String greet() {
      return "hello";
    }
  }

  public static class Counter {
    private int value;

    public int increment() {
      return ++value;
    }
  }
}
