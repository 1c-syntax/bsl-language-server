/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.utils;

import lombok.SneakyThrows;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;

public final class Absolute {

  private Absolute() {
    // Utility class
  }

  @SneakyThrows
  public static URI uri(String uri) {
    return uri(URI.create(uri));
  }

  @SneakyThrows
  public static URI uri(URI uri) {
    return URI.create(uri.getScheme() + ":" + uri.getSchemeSpecificPart());
  }

  @SneakyThrows
  public static Path path(String path) {
    return path(Path.of(path));
  }

  @SneakyThrows
  public static Path path(URI uri) {
    return path(Path.of(uri(uri)));
  }

  @SneakyThrows
  public static Path path(Path path) {
    return path(path.toFile());
  }

  @SneakyThrows
  public static Path path(File file) {
    return file.getCanonicalFile().toPath().toAbsolutePath();
  }
}
