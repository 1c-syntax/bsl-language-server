/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Collection;
import java.util.Map;

@EnableAutoConfiguration
@ComponentScan("com.github._1c_syntax.bsl.languageserver")
@EnableCaching(proxyTargetClass = true)
public class BSLLSBinding {

  @Getter(lazy = true, value = AccessLevel.PRIVATE)
  private static final SpringApplication application = createApplication();
  @Getter(lazy = true, value = AccessLevel.PRIVATE)
  private static final ConfigurableApplicationContext context = createContext();

  public BSLLSBinding() {
    // public constructor is needed for spring initialization
  }

  public static ConfigurableApplicationContext getApplicationContext() {
    var context = getContext();
    if (!context.isActive()) {
      context = createContext();
    }

    return context;
  }

  @SuppressWarnings("unchecked")
  public static Collection<DiagnosticInfo> getDiagnosticInfos() {
    return getApplicationContext().getBean("diagnosticInfos", Collection.class);
  }

  public static LanguageServerConfiguration getLanguageServerConfiguration() {
    return getApplicationContext().getBean(LanguageServerConfiguration.class);
  }

  public static ServerContext getServerContext() {
    return getApplicationContext().getBean(ServerContext.class);
  }

  public static com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider getServerContextProvider() {
    return getApplicationContext().getBean(com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider.class);
  }

  private static SpringApplication createApplication() {
    var app = new SpringApplicationBuilder(BSLLSBinding.class)
      .bannerMode(Banner.Mode.OFF)
      .web(WebApplicationType.NONE)
      .logStartupInfo(false)
      .resourceLoader(new DefaultResourceLoader(BSLLSBinding.class.getClassLoader()))
      .lazyInitialization(true)
      .properties(Map.of(
        "app.command.line.runner.enabled", "false",
        "app.scheduling.enabled", "false",
        "spring.cache.caffeine.spec", "maximumSize=500,expireAfterAccess=600s",
        "spring.cache.cache-names", "testIds,testSources"
      ))
      .build();

    app.setRegisterShutdownHook(false);
    return app;
  }

  private static ConfigurableApplicationContext createContext() {
    return getApplication().run();
  }
}
