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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@CleanupContextBeforeClassAndAfterClass
class LibConfigParserTest extends AbstractServerContextAwareTest {

  @Autowired
  private LibConfigParser parser;

  @Test
  void parsesFixture() {
    var libConfig = Paths.get("src/test/resources/oscript-libraries/mylib/lib.config")
      .toAbsolutePath().normalize();

    var result = parser.parse(libConfig);

    assertThat(result.modules())
      .extracting(LibConfigParser.LibEntry::name, LibConfigParser.LibEntry::file)
      .containsExactly(org.assertj.core.groups.Tuple.tuple("MyModule", "src/MyModule.os"));
    assertThat(result.classes())
      .extracting(LibConfigParser.LibEntry::name, LibConfigParser.LibEntry::file)
      .containsExactlyInAnyOrder(
        org.assertj.core.groups.Tuple.tuple("MyClass", "src/MyClass.os"),
        org.assertj.core.groups.Tuple.tuple("RenamedClass", "src/mainclass.os"));
  }

  @Test
  void parsesMultipleEntries(@TempDir Path tmp) throws IOException {
    var libConfig = tmp.resolve("lib.config");
    Files.writeString(libConfig,
      "<package-def>"
        + "<module name=\"M1\" file=\"M1.os\"/>"
        + "<module name=\"M2\" file=\"sub/M2.os\"/>"
        + "<class name=\"C1\" file=\"C1.os\"/>"
        + "<class name=\"C2\" file=\"sub/C2.os\"/>"
        + "</package-def>");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("M1", "M2");
    assertThat(result.classes()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("C1", "C2");
  }

  @Test
  void parsesInterleavedModulesAndClasses(@TempDir Path tmp) throws IOException {
    // module и class идут вперемешку (как в реальном logos/lib.config)
    var libConfig = tmp.resolve("lib.config");
    Files.writeString(libConfig,
      "<package-def>"
        + "<module name=\"M1\" file=\"m1.os\"/>"
        + "<class name=\"C1\" file=\"c1.os\"/>"
        + "<module name=\"M2\" file=\"m2.os\"/>"
        + "<class name=\"C2\" file=\"c2.os\"/>"
        + "</package-def>");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("M1", "M2");
    assertThat(result.classes()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("C1", "C2");
  }

  @Test
  void parsesConfigWithUtf8Bom(@TempDir Path tmp) throws IOException {
    // lib.config с UTF-8 BOM перед <package-def> (как у logos/lib.config)
    var libConfig = tmp.resolve("lib.config");
    var bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    var xml = "<package-def><class name=\"Лог\" file=\"src/log.os\"/></package-def>"
      .getBytes(StandardCharsets.UTF_8);
    var bytes = new byte[bom.length + xml.length];
    System.arraycopy(bom, 0, bytes, 0, bom.length);
    System.arraycopy(xml, 0, bytes, bom.length, xml.length);
    Files.write(libConfig, bytes);

    var result = parser.parse(libConfig);

    assertThat(result.classes()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("Лог");
  }

  @Test
  void rejectsDtdToPreventXxe(@TempDir Path tmp) throws IOException {
    // Внешняя сущность не должна раскрываться: DTD отключён, документ с DOCTYPE отвергается.
    var secret = tmp.resolve("secret.txt");
    Files.writeString(secret, "<class name=\"Утечка\" file=\"leak.os\"/>");
    var libConfig = tmp.resolve("lib.config");
    Files.writeString(libConfig,
      "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE package-def [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
        + "<package-def><class name=\"OK\" file=\"OK.os\">&xxe;</class></package-def>");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).isEmpty();
    assertThat(result.classes()).isEmpty();
  }

  @Test
  void returnsEmptyOnMalformedXml(@TempDir Path tmp) throws IOException {
    var libConfig = tmp.resolve("lib.config");
    Files.writeString(libConfig, "<package-def><module name='broken");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).isEmpty();
    assertThat(result.classes()).isEmpty();
  }

  @Test
  void returnsEmptyOnMissingFile(@TempDir Path tmp) {
    var libConfig = tmp.resolve("nonexistent.config");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).isEmpty();
    assertThat(result.classes()).isEmpty();
  }

  @Test
  void skipsEntriesWithoutRequiredAttributes(@TempDir Path tmp) throws IOException {
    var libConfig = tmp.resolve("lib.config");
    Files.writeString(libConfig,
      "<package-def>"
        + "<module name=\"OnlyName\"/>"
        + "<module file=\"OnlyFile.os\"/>"
        + "<module name=\"OK\" file=\"OK.os\"/>"
        + "<class file=\"NoName.os\"/>"
        + "<class name=\"GoodClass\" file=\"GoodClass.os\"/>"
        + "</package-def>");

    var result = parser.parse(libConfig);

    assertThat(result.modules()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("OK");
    assertThat(result.classes()).extracting(LibConfigParser.LibEntry::name)
      .containsExactly("GoodClass");
  }
}
