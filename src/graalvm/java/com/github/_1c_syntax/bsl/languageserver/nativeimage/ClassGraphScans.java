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

import nonapi.io.github.classgraph.scanspec.AcceptReject;
import nonapi.io.github.classgraph.scanspec.ScanSpec;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Результаты сканирований ClassGraph, выполненных при сборке native-образа.
 * <p>
 * Класс инициализируется при сборке образа: результаты (JSON {@code ScanResult}) попадают в образ
 * и доступны во время выполнения, где сканировать classpath нечем.
 */
public final class ClassGraphScans {

  private static final Map<String, String> RESULTS = new ConcurrentHashMap<>();

  private ClassGraphScans() {
    // utility class
  }

  /**
   * Ключ сканирования по его конфигурации.
   *
   * @param acceptedPackages принимаемые пакеты
   * @param rejectedPackages исключаемые пакеты
   * @param annotationInfo   собирается ли информация об аннотациях
   * @return ключ, не зависящий от порядка пакетов
   */
  public static String key(
    Collection<String> acceptedPackages,
    Collection<String> rejectedPackages,
    boolean annotationInfo
  ) {
    return "accept=" + sorted(acceptedPackages)
      + ";reject=" + sorted(rejectedPackages)
      + ";annotations=" + annotationInfo;
  }

  /**
   * Ключ сканирования по конфигурации ClassGraph.
   *
   * @param scanSpec конфигурация сканирования
   * @return ключ, см. {@link #key(Collection, Collection, boolean)}
   */
  public static String key(ScanSpec scanSpec) {
    var packages = scanSpec.packageAcceptReject;
    return key(stringSet(packages, "accept"), stringSet(packages, "reject"), scanSpec.enableAnnotationInfo);
  }

  @SuppressWarnings("unchecked")
  private static Set<String> stringSet(AcceptReject acceptReject, String fieldName) {
    try {
      Field field = AcceptReject.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      var value = (Set<String>) field.get(acceptReject);
      return value == null ? Set.of() : value;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot read ClassGraph scan spec", e);
    }
  }

  static void put(String key, String scanResultJson) {
    RESULTS.put(key, scanResultJson);
  }

  /**
   * Сохранённый результат сканирования.
   *
   * @param key ключ сканирования, см. {@link #key(Collection, Collection, boolean)}
   * @return JSON результата сканирования, если оно выполнялось при сборке образа
   */
  public static Optional<String> get(String key) {
    return Optional.ofNullable(RESULTS.get(key));
  }

  private static String sorted(Collection<String> values) {
    return values.stream().sorted().collect(Collectors.joining(",", "[", "]"));
  }
}
