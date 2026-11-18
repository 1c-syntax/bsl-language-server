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

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import io.github.classgraph.ScanResult;
import nonapi.io.github.classgraph.scanspec.ScanSpec;

import java.util.Set;

/**
 * Сканирование ClassGraph в native-образе: classpath для сканирования во время выполнения нет,
 * поэтому результат берётся из выполненного при сборке образа (см. {@link ClassGraphScansFeature}).
 */
@TargetClass(className = "io.github.classgraph.ClassGraph")
final class Target_io_github_classgraph_ClassGraph {

  @Alias
  ScanSpec scanSpec;

  @Substitute
  public ScanResult scan() {
    var packages = SubstrateUtil.cast(scanSpec.packageAcceptReject, Target_nonapi_io_github_classgraph_scanspec_AcceptReject.class);
    var key = ClassGraphScans.key(
      packages.accept == null ? Set.of() : packages.accept,
      packages.reject == null ? Set.of() : packages.reject,
      scanSpec.enableAnnotationInfo
    );
    var json = ClassGraphScans.get(key).orElseThrow(() -> new IllegalStateException(
      "ClassGraph scan was not performed at native image build time: " + key
        + ". Add it to " + ClassGraphScansFeature.class.getName()));
    return ScanResult.fromJSON(json);
  }
}

@TargetClass(className = "nonapi.io.github.classgraph.scanspec.AcceptReject")
final class Target_nonapi_io_github_classgraph_scanspec_AcceptReject {

  @Alias
  Set<String> accept;

  @Alias
  Set<String> reject;
}
