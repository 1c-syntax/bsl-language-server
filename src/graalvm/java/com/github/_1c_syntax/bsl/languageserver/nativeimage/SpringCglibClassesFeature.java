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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Регистрирует рефлексию для CGLIB-классов прокси, сгенерированных Spring AOT.
 * <p>
 * Spring AOT регистрирует для них только конструкторы, а CGLIB, повторно используя готовый класс
 * ({@code Enhancer.wrapCachedClass}), читает его статические поля и вызывает статические методы.
 * Номер в имени класса ({@code $$SpringCGLIB$$N}) зависит от порядка генерации, поэтому
 * регистрируются все такие классы проекта, найденные на classpath образа.
 */
public final class SpringCglibClassesFeature implements Feature {

  private static final String BASE_PACKAGE = "com.github._1c_syntax.bsl.languageserver";
  private static final String CGLIB_CLASS_MARKER = "$$SpringCGLIB$$";

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    var classpath = access.getApplicationClassPath().stream().map(Path::toString).toList();
    try (var scanResult = new ClassGraph()
      .overrideClasspath(classpath)
      .enableClassInfo()
      .acceptPackages(BASE_PACKAGE)
      .scan()) {
      scanResult.getAllClasses().stream()
        .map(ClassInfo::getName)
        .filter(name -> name.contains(CGLIB_CLASS_MARKER))
        .map(access::findClassByName)
        .filter(Objects::nonNull)
        .forEach((Class<?> cglibClass) -> {
          RuntimeReflection.register(cglibClass);
          RuntimeReflection.register(cglibClass.getDeclaredConstructors());
          RuntimeReflection.register(cglibClass.getDeclaredMethods());
          RuntimeReflection.register(cglibClass.getDeclaredFields());
        });
    }
  }
}
