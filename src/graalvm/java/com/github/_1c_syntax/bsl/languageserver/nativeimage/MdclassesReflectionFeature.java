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
package com.github._1c_syntax.bsl.languageserver.nativeimage;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

/**
 * Регистрирует рефлексию, через которую mdclasses строит модель метаданных.
 * <ul>
 *   <li>объекты модели создаются билдерами Lombok: статический {@code builder()},
 *   {@code toBuilder()}, сеттеры, {@code add*}/{@code clear*} и {@code build()} билдера вызываются
 *   рефлексией ({@code TransformationUtils});</li>
 *   <li>значения перечислений модели разбираются статическим {@code valueByName}
 *   ({@code EnumConverter}).</li>
 * </ul>
 * Классы находятся на classpath образа во время сборки.
 */
public final class MdclassesReflectionFeature implements Feature {

  private static final String LIBRARIES_PACKAGE = "com.github._1c_syntax.bsl";
  private static final String PROJECT_PACKAGE = "com.github._1c_syntax.bsl.languageserver";
  private static final String BUILDER_METHOD = "builder";
  private static final String ENUM_VALUE_METHOD = "valueByName";

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    var classpath = access.getApplicationClassPath().stream().map(Path::toString).toList();
    try (var scanResult = new ClassGraph()
      .overrideClasspath(classpath)
      .enableClassInfo()
      .acceptPackages(LIBRARIES_PACKAGE)
      .rejectPackages(PROJECT_PACKAGE)
      .scan()) {
      scanResult.getAllClasses().stream()
        .map(ClassInfo::getName)
        .map(access::findClassByName)
        .filter(Objects::nonNull)
        .forEach(MdclassesReflectionFeature::register);
    }
  }

  private static void register(Class<?> type) {
    if (hasStaticMethod(type, BUILDER_METHOD)) {
      registerMethods(type);
      Arrays.stream(type.getDeclaredClasses())
        .filter(nested -> nested.getSimpleName().endsWith("Builder"))
        .forEach(builder -> {
          registerMethods(builder);
          RuntimeReflection.register(builder.getDeclaredConstructors());
        });
    }
    if (type.isEnum()) {
      Arrays.stream(type.getDeclaredMethods())
        .filter(method -> ENUM_VALUE_METHOD.equals(method.getName()) && Modifier.isStatic(method.getModifiers()))
        .forEach(method -> {
          RuntimeReflection.register(type);
          RuntimeReflection.register(method);
        });
    }
  }

  private static boolean hasStaticMethod(Class<?> type, String name) {
    return Arrays.stream(type.getDeclaredMethods())
      .anyMatch(method -> name.equals(method.getName()) && Modifier.isStatic(method.getModifiers()));
  }

  private static void registerMethods(Class<?> type) {
    RuntimeReflection.register(type);
    RuntimeReflection.register(Arrays.stream(type.getDeclaredMethods()).toArray(Method[]::new));
  }
}
