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
package com.github._1c_syntax.bsl.languageserver.utils;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NavigationLinksTest {

  private static final LocationLink LINK = new LocationLink(
    "file:///target.bsl", Ranges.create(1, 0, 5, 0), Ranges.create(1, 10, 1, 15), Ranges.create(3, 2, 3, 7));

  @Test
  void linksAreReturnedAsIsWhenClientSupportsThem() {
    var response = NavigationLinks.forClient(List.of(LINK), true);

    assertThat(response.isRight()).isTrue();
    assertThat(response.getRight()).isEqualTo(List.of(LINK));
  }

  @Test
  void linksAreDowngradedToTargetSelectionWhenClientDoesNotSupportThem() {
    var response = NavigationLinks.forClient(List.of(LINK), false);

    assertThat(response.isLeft()).isTrue();
    assertThat(response.getLeft())
      .isEqualTo(List.of(new Location("file:///target.bsl", Ranges.create(1, 10, 1, 15))));
  }
}
