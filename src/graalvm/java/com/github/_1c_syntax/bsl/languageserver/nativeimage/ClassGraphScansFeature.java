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
import io.github.classgraph.ScanResult;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Выполняет при сборке native-образа сканирования ClassGraph, которые библиотеки делают во время
 * выполнения, и сохраняет их результаты в {@link ClassGraphScans}.
 * <p>
 * Список сканирований повторяет вызовы {@code new ClassGraph()...scan()} в mdclasses: если там
 * появится новое сканирование, подменённый {@code ClassGraph.scan()} в образе завершится ошибкой
 * с его конфигурацией. Для найденных классов регистрируется рефлексия, которая нужна вызывающему
 * коду, а для ClassGraph — то, что нужно для восстановления результата из JSON.
 */
public final class ClassGraphScansFeature implements Feature {

  private static final String MDO_PACKAGE = "com.github._1c_syntax.bsl.mdo";

  private static final List<Scan> SCANS = List.of(
    // ExtendXStream.registerConverters: создаёт конвертеры через первый объявленный конструктор
    Scan.withAnnotations("com.github._1c_syntax.bsl.reader.common.converter", Usage.INSTANTIATE),
    Scan.withAnnotations("com.github._1c_syntax.bsl.reader.designer.converter", Usage.INSTANTIATE),
    Scan.withAnnotations("com.github._1c_syntax.bsl.reader.edt.converter", Usage.INSTANTIATE),
    // ExtendXStream.registerClasses: Class.forName по именам реализаций MD
    new Scan(List.of(MDO_PACKAGE), List.of(MDO_PACKAGE + ".children"), false, Usage.LOAD),
    // Rights.computePossibleRights: статический possibleRights() у реализаций AccessRightsOwner
    new Scan(List.of("com.github._1c_syntax.bsl"), List.of(), false, Usage.ACCESS_RIGHTS_OWNERS)
  );

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    RuntimeClassInitialization.initializeAtBuildTime(ClassGraphScans.class);

    var classpath = access.getApplicationClassPath().stream().map(Path::toString).toList();
    for (var scan : SCANS) {
      var classGraph = scan.configure(new ClassGraph().overrideClasspath(classpath));
      try (var scanResult = classGraph.scan()) {
        ClassGraphScans.put(keyOf(classGraph), scanResult.toJSON());
        scan.usage().classes.apply(scanResult).forEach(classInfo -> register(access, classInfo, scan.usage()));
      }
    }

    registerClassGraphJsonModel(access, classpath);
  }

  private static void register(BeforeAnalysisAccess access, ClassInfo classInfo, Usage usage) {
    var clazz = access.findClassByName(classInfo.getName());
    if (clazz == null) {
      return;
    }
    RuntimeReflection.register(clazz);
    switch (usage) {
      case INSTANTIATE -> RuntimeReflection.register(clazz.getDeclaredConstructors());
      case ACCESS_RIGHTS_OWNERS -> RuntimeReflection.register(clazz.getDeclaredMethods());
      case LOAD -> {
        // достаточно Class.forName
      }
    }
  }

  /**
   * ScanResult.fromJSON восстанавливает объекты модели ClassGraph рефлексией по полям.
   */
  private static void registerClassGraphJsonModel(BeforeAnalysisAccess access, List<String> classpath) {
    try (var scanResult = new ClassGraph()
      .overrideClasspath(classpath)
      .enableClassInfo()
      .acceptPackages("io.github.classgraph", "nonapi.io.github.classgraph")
      .scan()) {
      scanResult.getAllClasses().forEach((ClassInfo classInfo) -> {
        var clazz = access.findClassByName(classInfo.getName());
        if (clazz != null) {
          RuntimeReflection.register(clazz);
          RuntimeReflection.register(clazz.getDeclaredConstructors());
          RuntimeReflection.register(clazz.getDeclaredFields());
        }
      });
    }
  }

  /**
   * Ключ по конфигурации ClassGraph — тот же, что вычисляет подменённый {@code ClassGraph.scan()}.
   */
  private static String keyOf(ClassGraph classGraph) {
    try {
      var scanSpecField = ClassGraph.class.getDeclaredField("scanSpec");
      scanSpecField.setAccessible(true);
      var scanSpec = (nonapi.io.github.classgraph.scanspec.ScanSpec) scanSpecField.get(classGraph);
      var packages = scanSpec.packageAcceptReject;
      return ClassGraphScans.key(
        setField(packages, "accept"),
        setField(packages, "reject"),
        scanSpec.enableAnnotationInfo
      );
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read ClassGraph scan spec", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Set<String> setField(Object acceptReject, String name) throws ReflectiveOperationException {
    Field field = nonapi.io.github.classgraph.scanspec.AcceptReject.class.getDeclaredField(name);
    field.setAccessible(true);
    var value = (Set<String>) field.get(acceptReject);
    return value == null ? Set.of() : value;
  }

  private enum Usage {
    INSTANTIATE(ScanResult::getAllClasses),
    LOAD(ScanResult::getAllClasses),
    ACCESS_RIGHTS_OWNERS(scanResult -> scanResult.getClassesImplementing(MDO_PACKAGE + ".AccessRightsOwner"));

    private final Function<ScanResult, List<ClassInfo>> classes;

    Usage(Function<ScanResult, List<ClassInfo>> classes) {
      this.classes = classes;
    }
  }

  private record Scan(List<String> acceptPackages, List<String> rejectPackages, boolean annotationInfo, Usage usage) {

    static Scan withAnnotations(String packageName, Usage usage) {
      return new Scan(List.of(packageName), List.of(), true, usage);
    }

    ClassGraph configure(ClassGraph classGraph) {
      classGraph.enableClassInfo()
        .acceptPackages(acceptPackages.toArray(String[]::new))
        .rejectPackages(rejectPackages.toArray(String[]::new));
      if (annotationInfo) {
        classGraph.enableAnnotationInfo();
      }
      return classGraph;
    }
  }
}
