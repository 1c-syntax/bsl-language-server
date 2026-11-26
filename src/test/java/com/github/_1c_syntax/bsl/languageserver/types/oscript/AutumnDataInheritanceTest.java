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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверка наследования через мета-аннотацию по образцу
 * <a href="https://github.com/autumn-library/autumn-data">autumn-data</a>:
 * класс, помеченный {@code &ХранилищеСущностей}, должен получать методы
 * супер-класса ({@code ПолучитьОдно}, {@code Получить}, {@code Сохранить}),
 * хотя сам {@code &Расширяет} зашит в определении мета-аннотации, а не на классе.
 */
@CleanupContextBeforeClassAndAfterClass
class AutumnDataInheritanceTest extends AbstractServerContextAwareTest {

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  private OScriptLibraryIndex index;

  @Test
  void entityRepositoryInheritsBaseStorageMembersViaMetaAnnotation() {
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/autumn-data-sample").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    var ref = typeRegistry.resolve("СправочникиХранилище", FileType.OS).orElseThrow();
    var members = typeRegistry.getMembers(ref, FileType.OS);

    assertThat(members)
      .extracting(MemberDescriptor::name)
      .contains("ПолучитьОдно", "Получить", "Сохранить", "НайтиПоКоду");
  }

  @Test
  void annotationDefinitionClassDoesNotInheritBaseStorageMembers() {
    // given — класс-определение мета-аннотации помечен &Аннотация и несёт
    // &Расширяет как ШАБЛОН для классов, помеченных этой аннотацией, а не как
    // собственное наследование. Поэтому сам он не должен получать члены базового
    // класса ХранилищеСущностей.
    var fixtureRoot = Path.of("src/test/resources/oscript-libraries/autumn-data-sample").toAbsolutePath();
    initServerContext(fixtureRoot, false);
    index.reindex(context);

    // when
    var ref = typeRegistry.resolve("АннотацияХранилищеСущностей", FileType.OS).orElseThrow();
    var members = typeRegistry.getMembers(ref, FileType.OS);

    // then — только собственные члены, без членов базового ХранилищеСущностей.
    assertThat(members)
      .extracting(MemberDescriptor::name)
      .contains("ПолучитьИмяТипаСущности", "ПолучитьИсточникДанных")
      .doesNotContain("ПолучитьОдно", "Получить", "Сохранить");
  }
}
