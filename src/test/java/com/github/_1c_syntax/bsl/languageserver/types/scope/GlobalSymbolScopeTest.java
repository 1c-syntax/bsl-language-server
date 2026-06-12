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
package com.github._1c_syntax.bsl.languageserver.types.scope;

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticKind;
import com.github._1c_syntax.bsl.languageserver.types.symbol.SyntheticSymbol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSymbolScopeTest {

  @Test
  void findEntrySelectsLanguageVariantByFileType() {
    // given — одно имя зарегистрировано двумя языковыми вариантами (issue #4054)
    var scope = new GlobalSymbolScope();
    var bslSym = new SyntheticSymbol("КодировкаТекста", SyntheticKind.PLATFORM_GLOBAL_ENUM,
      "BSL-описание", TypeRef.UNKNOWN);
    var osSym = new SyntheticSymbol("КодировкаТекста", SyntheticKind.PLATFORM_GLOBAL_ENUM,
      "OS-описание", TypeRef.UNKNOWN);

    // when
    scope.register("КодировкаТекста", bslSym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("КодировкаТекста", osSym, GlobalSymbolScope.Role.VALUE, FileType.OS);

    // then
    assertThat(scope.findEntry("КодировкаТекста", FileType.BSL))
      .map(GlobalSymbolScope.Entry::symbol).contains(bslSym);
    assertThat(scope.findEntry("КодировкаТекста", FileType.OS))
      .map(GlobalSymbolScope.Entry::symbol).contains(osSym);
  }

  @Test
  void findEntryReturnsEmptyWhenNoVariantMatchesFileType() {
    // given
    var scope = new GlobalSymbolScope();
    var osSym = new SyntheticSymbol("ФС", SyntheticKind.LIBRARY_MODULE, "", TypeRef.UNKNOWN);

    // when — OS-only запись
    scope.register("ФС", osSym, GlobalSymbolScope.Role.VALUE, FileType.OS);

    // then
    assertThat(scope.findEntry("ФС", FileType.OS)).isPresent();
    assertThat(scope.findEntry("ФС", FileType.BSL)).isEmpty();
  }

  @Test
  void registerSameScopeReplacesEntry() {
    // given
    var scope = new GlobalSymbolScope();
    var first = new SyntheticSymbol("Имя", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "первый", TypeRef.UNKNOWN);
    var second = new SyntheticSymbol("Имя", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "второй", TypeRef.UNKNOWN);

    // when — повторная регистрация с тем же языком заменяет запись
    scope.register("Имя", first, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Имя", second, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    // then
    assertThat(scope.findSymbol("Имя", FileType.BSL)).contains(second);
  }

  @Test
  void unregisterRemovesOnlyOwnLanguageVariant() {
    // given — два языковых варианта одного имени
    var scope = new GlobalSymbolScope();
    var bslSym = new SyntheticSymbol("Имя", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "BSL", TypeRef.UNKNOWN);
    var osSym = new SyntheticSymbol("Имя", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "OS", TypeRef.UNKNOWN);
    scope.register("Имя", bslSym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Имя", osSym, GlobalSymbolScope.Role.VALUE, FileType.OS);

    // when
    scope.unregister(osSym);

    // then — BSL-вариант остаётся
    assertThat(scope.findEntry("Имя", FileType.BSL))
      .map(GlobalSymbolScope.Entry::symbol).contains(bslSym);
    assertThat(scope.findEntry("Имя", FileType.OS)).isEmpty();
  }

  @Test
  void findsSymbolCaseInsensitive() {
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", TypeRef.UNKNOWN);
    scope.register("Справочники", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    assertThat(scope.findSymbol("справочники", FileType.BSL)).contains(sym);
    assertThat(scope.findSymbol("СПРАВОЧНИКИ", FileType.BSL)).contains(sym);
    assertThat(scope.findEntry("Справочники", FileType.BSL))
      .map(GlobalSymbolScope.Entry::role).contains(GlobalSymbolScope.Role.VALUE);
  }

  @Test
  void supportsRuEnAliases() {
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", TypeRef.UNKNOWN);
    scope.register("Справочники", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Catalogs", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    assertThat(scope.findSymbol("catalogs", FileType.BSL)).contains(sym);
    assertThat(scope.findSymbol("справочники", FileType.BSL)).contains(sym);
  }

  @Test
  void unregisterNullSymbolIsNoop() {
    var scope = new GlobalSymbolScope();
    // не должно бросать
    scope.unregister(null);
  }

  @Test
  void unregisterUnknownSymbolIsNoop() {
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    // символ не был зарегистрирован — unregister — no-op, без NPE.
    scope.unregister(sym);
  }

  @Test
  void unregisterRemovesAllAliases() {
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "", TypeRef.UNKNOWN);
    scope.register("Справочники", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Catalogs", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    scope.unregister(sym);

    assertThat(scope.findSymbol("справочники", FileType.BSL)).isEmpty();
    assertThat(scope.findSymbol("catalogs", FileType.BSL)).isEmpty();
  }

  @Test
  void clearByRoleRemovesOnlyMatching() {
    var scope = new GlobalSymbolScope();
    var valueSym = new SyntheticSymbol("ФС", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    var typeSym = new SyntheticSymbol("СессияПользователя", SyntheticKind.TYPE_NAME, "");
    scope.register("ФС", valueSym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("СессияПользователя", typeSym, GlobalSymbolScope.Role.TYPE_NAME, FileType.BSL);

    scope.clear(GlobalSymbolScope.Role.VALUE);

    assertThat(scope.findSymbol("ФС", FileType.BSL)).isEmpty();
    assertThat(scope.findSymbol("СессияПользователя", FileType.BSL)).isPresent();
  }

  @Test
  void registerIgnoresBlankOrNullNameAndNullSymbol() {
    // given
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("X", SyntheticKind.TYPE_NAME, "");

    // when
    scope.register("", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("   ", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register(null, sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("X", null, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    // then
    assertThat(scope.findSymbol("X", FileType.BSL)).isEmpty();
    assertThat(scope.getNames(FileType.BSL)).isEmpty();
  }

  @Test
  void findEntryReturnsEmptyForBlankInput() {
    // given
    var scope = new GlobalSymbolScope();

    // when / then
    assertThat(scope.findEntry(null, FileType.BSL)).isEmpty();
    assertThat(scope.findEntry("", FileType.BSL)).isEmpty();
    assertThat(scope.findEntry("   ", FileType.BSL)).isEmpty();
  }

  @Test
  void getNamesPreservesOriginalSpelling() {
    // given
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("ФайловыеПотоки", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    scope.register("ФайловыеПотоки", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    // when
    var names = scope.getNames(FileType.BSL);

    // then
    assertThat(names).contains("ФайловыеПотоки");
  }

  @Test
  void getEntriesReturnsOneEntryPerSymbolEvenWithAliases() {
    // given
    var scope = new GlobalSymbolScope();
    var sym = new SyntheticSymbol("Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    scope.register("Справочники", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Catalogs", sym, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    // when
    var entries = scope.getEntries(FileType.BSL);

    // then
    assertThat(entries).hasSize(1);
    assertThat(entries.iterator().next().symbol()).isEqualTo(sym);
  }

  @Test
  void streamSymbolsReturnsUniqueSymbols() {
    // given
    var scope = new GlobalSymbolScope();
    var sym1 = new SyntheticSymbol("Справочники", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    var sym2 = new SyntheticSymbol("ФС", SyntheticKind.LIBRARY_MODULE, "");
    scope.register("Справочники", sym1, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Catalogs", sym1, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("ФС", sym2, GlobalSymbolScope.Role.VALUE, FileType.BSL);

    // when
    var symbols = scope.streamSymbols(FileType.BSL).toList();

    // then
    assertThat(symbols).containsExactlyInAnyOrder(sym1, sym2);
  }

  @Test
  void clearWithoutRoleRemovesAllEntries() {
    // given
    var scope = new GlobalSymbolScope();
    var sym1 = new SyntheticSymbol("X", SyntheticKind.PLATFORM_GLOBAL_PROPERTY, "");
    var sym2 = new SyntheticSymbol("Y", SyntheticKind.TYPE_NAME, "");
    scope.register("X", sym1, GlobalSymbolScope.Role.VALUE, FileType.BSL);
    scope.register("Y", sym2, GlobalSymbolScope.Role.TYPE_NAME, FileType.BSL);

    // when
    scope.clear();

    // then
    assertThat(scope.findSymbol("X", FileType.BSL)).isEmpty();
    assertThat(scope.findSymbol("Y", FileType.BSL)).isEmpty();
    assertThat(scope.getEntries(FileType.BSL)).isEmpty();
    assertThat(scope.getNames(FileType.BSL)).isEmpty();
  }
}
