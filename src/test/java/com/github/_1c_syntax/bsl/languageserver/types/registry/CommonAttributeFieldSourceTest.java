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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.mdclasses.CF;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.types.MdoReference;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommonAttributeFieldSourceTest {

  private final CommonAttributeFieldSource source = new CommonAttributeFieldSource(mock(MdoMemberFactory.class));

  @Test
  void commonAttributesAreSelectedOncePerConfiguration() {
    // given
    var configuration = configuration();
    var catalog = mdo("Catalog.Товары");
    var document = mdo("Document.Заказ");

    // when: поля собственных таблиц двух объектов одной конфигурации
    source.fields(ownTable(catalog, configuration));
    source.fields(ownTable(document, configuration));

    // then: объекты конфигурации перебраны один раз
    verify(configuration, times(1)).getChildrenByMdoRef();
  }

  @Test
  void anotherConfigurationIsSearchedAgain() {
    // given
    var first = configuration();
    var reread = configuration();
    var catalog = mdo("Catalog.Товары");
    source.fields(ownTable(catalog, first));

    // when: конфигурация перечитана
    source.fields(ownTable(catalog, reread));

    // then
    verify(reread, times(1)).getChildrenByMdoRef();
  }

  private static CF configuration() {
    var configuration = mock(CF.class);
    when(configuration.getChildrenByMdoRef()).thenReturn(Map.of());
    return configuration;
  }

  private static MD mdo(String mdoRef) {
    var reference = mock(MdoReference.class);
    when(reference.getMdoRef()).thenReturn(mdoRef);
    var md = mock(MD.class);
    when(md.getMdoReference()).thenReturn(reference);
    return md;
  }

  private static QueryTableRequest ownTable(MD md, CF configuration) {
    return new QueryTableRequest(md.getMdoReference().getMdoRef(), md, configuration, null, Map.of(), null);
  }
}
