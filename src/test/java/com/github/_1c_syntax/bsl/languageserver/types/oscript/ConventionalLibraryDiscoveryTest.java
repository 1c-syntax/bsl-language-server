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
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Convention-based + flat .os discovery (modes 2 и 3) для OneScript-библиотек
 * без {@code lib.config}.
 */
@CleanupContextBeforeClassAndAfterClass
class ConventionalLibraryDiscoveryTest extends AbstractServerContextAwareTest {

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private GlobalScopeProvider globalScopeProvider;

  @Test
  void registersClassesAndModulesFromConventionDirs(@TempDir Path tempDir) throws IOException {
    var lib = tempDir.resolve("convlib");
    var classes = lib.resolve("Классы");
    var modules = lib.resolve("Модули");
    Files.createDirectories(classes);
    Files.createDirectories(modules);
    Files.writeString(classes.resolve("MyClass.os"), """
      Перем СтатусМодуля Экспорт;

      Процедура ПриСозданииОбъекта(Имя) Экспорт
      КонецПроцедуры

      Функция ПолучитьСтроку() Экспорт
        Возврат "";
      КонецФункции
      """);
    Files.writeString(modules.resolve("MyModule.os"), """
      Процедура ВывестиСообщение(Текст) Экспорт
      КонецПроцедуры
      """);

    initServerContext(tempDir, false);
    index.reindex(context);

    assertThat(globalScopeProvider.getLibraryClasses()).contains("MyClass");
    assertThat(globalScopeProvider.getLibraryModules()).contains("MyModule");

    var classRef = typeRegistry.resolve("MyClass");
    assertThat(classRef).isPresent();
    assertThat(typeRegistry.getMembers(classRef.get()))
      .extracting(m -> m.name()).contains("ПолучитьСтроку", "СтатусМодуля");

    var ctor = globalScopeProvider.findLibraryClassConstructor("MyClass");
    assertThat(ctor).hasSize(1);
    assertThat(ctor.get(0).parameters()).extracting(p -> p.name()).containsExactly("Имя");
  }

  @Test
  void supportsClassesAndModulesUnderSrcPrefix(@TempDir Path tempDir) throws IOException {
    var lib = tempDir.resolve("srclib");
    var classes = lib.resolve("src").resolve("Classes");
    var modules = lib.resolve("src").resolve("Modules");
    Files.createDirectories(classes);
    Files.createDirectories(modules);
    Files.writeString(classes.resolve("Foo.os"), """
      Процедура ПриСозданииОбъекта() Экспорт
      КонецПроцедуры

      Функция Bar() Экспорт
        Возврат "";
      КонецФункции
      """);
    Files.writeString(modules.resolve("Util.os"), """
      Процедура Run() Экспорт
      КонецПроцедуры
      """);

    initServerContext(tempDir, false);
    index.reindex(context);

    assertThat(globalScopeProvider.getLibraryClasses()).contains("Foo");
    assertThat(globalScopeProvider.getLibraryModules()).contains("Util");
  }

  @Test
  void thirdModeAllOsFilesBecomeModulesWhenNoConfigAndNoConventionDirs(@TempDir Path tempDir) throws IOException {
    var lib = tempDir.resolve("flatlib");
    Files.createDirectories(lib);
    Files.writeString(lib.resolve("Helper.os"), """
      Процедура Run() Экспорт
      КонецПроцедуры
      """);
    Files.writeString(lib.resolve("Tool.os"), """
      Функция Compute() Экспорт
        Возврат 1;
      КонецФункции
      """);

    initServerContext(tempDir, false);
    index.reindex(context);

    assertThat(globalScopeProvider.getLibraryModules()).contains("Helper", "Tool");
    assertThat(globalScopeProvider.getLibraryClasses()).doesNotContain("Helper", "Tool");
  }

  @Test
  void doesNotRegisterDirectoriesWithoutOsFiles(@TempDir Path tempDir) throws IOException {
    var lib = tempDir.resolve("not-a-lib");
    Files.createDirectories(lib);
    Files.writeString(lib.resolve("readme.txt"), "no .os here");

    initServerContext(tempDir, false);
    index.reindex(context);

    assertThat(globalScopeProvider.getLibraryModules()).isEmpty();
    assertThat(globalScopeProvider.getLibraryClasses()).isEmpty();
  }

  @Test
  void picksUpOscriptModulesEvenWhenWorkspaceItselfIsALibrary(@TempDir Path tempDir) throws IOException {
    // workspace сам по себе convention-based библиотека
    var wsModules = tempDir.resolve("src").resolve("Модули");
    Files.createDirectories(wsModules);
    Files.writeString(wsModules.resolve("СессияПользователя.os"), """
      Процедура Старт() Экспорт
      КонецПроцедуры
      """);

    // локальная зависимость в oscript_modules — не должна быть «съедена» при
    // обходе из корня workspace
    var fsModules = tempDir.resolve("oscript_modules").resolve("fs").resolve("Модули");
    Files.createDirectories(fsModules);
    Files.writeString(fsModules.resolve("ФС.os"), """
      // Проверяет существование файла или каталога.
      //
      // Возвращаемое значение:
      //  Булево
      //
      Функция КаталогПустой(Знач Путь) Экспорт
        Возврат Истина;
      КонецФункции
      """);

    initServerContext(tempDir, false);
    index.reindex(context);

    assertThat(globalScopeProvider.getLibraryModules())
      .contains("СессияПользователя", "ФС");

    var fsRef = typeRegistry.resolve("ФС");
    assertThat(fsRef).isPresent();
    assertThat(typeRegistry.getMembers(fsRef.get()))
      .extracting(m -> m.name()).contains("КаталогПустой");
  }
}
