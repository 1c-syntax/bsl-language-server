/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
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
package com.github._1c_syntax.bsl.languageserver.recognizer;

import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PatternDetector extends AbstractDetector {

  private final Pattern patterns;

  public PatternDetector(double probability, String... stringPatterns) {
    super(probability);
    StringJoiner stringJoiner = new StringJoiner("|");
    for (String elem : stringPatterns) {
      stringJoiner.add("(?:" + elem + ")");
    }
    this.patterns = Pattern.compile(
      stringJoiner.toString(),
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE
    );
  }


  @Override
  public int scan(String line) {
    int count = 0;
    Matcher matcher = patterns.matcher(line);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

}
