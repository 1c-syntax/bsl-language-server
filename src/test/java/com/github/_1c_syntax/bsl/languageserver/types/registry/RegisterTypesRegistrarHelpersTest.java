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

import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.ObjectAttribute;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты на pure-static helpers {@link RegisterTypesRegistrar} —
 * без поднятия Spring/HBK.
 */
class RegisterTypesRegistrarHelpersTest {

  // === registerChildrenOf ===

  @Test
  void registerChildrenOf_informationRegister_returnsTriple() {
    MD ir = InformationRegister.builder().name("РС1").build();
    var children = RegisterTypesRegistrar.registerChildrenOf(ir);
    assertThat(children).isNotNull();
    assertThat(children.dimensions()).isNotNull();
    assertThat(children.resources()).isNotNull();
    assertThat(children.attributes()).isNotNull();
  }

  @Test
  void registerChildrenOf_accumulationRegister_returnsTriple() {
    MD r = AccumulationRegister.builder().name("РН1").build();
    assertThat(RegisterTypesRegistrar.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_accountingRegister_returnsTriple() {
    MD r = AccountingRegister.builder().name("РБ1").build();
    assertThat(RegisterTypesRegistrar.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_calculationRegister_returnsTriple() {
    MD r = CalculationRegister.builder().name("РР1").build();
    assertThat(RegisterTypesRegistrar.registerChildrenOf(r)).isNotNull();
  }

  @Test
  void registerChildrenOf_nonRegister_returnsNull() {
    MD catalog = Catalog.builder().name("Контрагенты").build();
    assertThat(RegisterTypesRegistrar.registerChildrenOf(catalog)).isNull();
  }

  // === putAttributeNames ===

  @Test
  void putAttributeNames_nonEmpty_putsKey() {
    var attr = ObjectAttribute.builder().name("Контрагент").build();
    var sink = new HashMap<String, List<String>>();
    RegisterTypesRegistrar.putAttributeNames(sink, "Имя реквизита", List.of(attr));
    assertThat(sink).containsKey("Имя реквизита");
    assertThat(sink.get("Имя реквизита")).containsExactly("Контрагент");
  }

  @Test
  void putAttributeNames_emptyList_doesNotPut() {
    var sink = new HashMap<String, List<String>>();
    RegisterTypesRegistrar.putAttributeNames(sink, "X", List.of());
    assertThat(sink).isEmpty();
  }

  @Test
  void putAttributeNames_blankNames_skipped() {
    var blank = ObjectAttribute.builder().name("").build();
    var named = ObjectAttribute.builder().name("Имя1").build();
    var sink = new LinkedHashMap<String, List<String>>();
    RegisterTypesRegistrar.putAttributeNames(sink, "K", List.of(blank, named));
    assertThat(sink.get("K")).containsExactly("Имя1");
  }
}
