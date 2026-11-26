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
package com.github._1c_syntax.bsl.languageserver.semantictokens;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper;
import com.github._1c_syntax.bsl.languageserver.util.SemanticTokensTestHelper.ExpectedToken;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.SemanticTokenTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.lsp4j.SemanticTokenModifiers.DefaultLibrary;

/**
 * Разметка цепочки обращения к дереву метаданных.
 * <p>
 * Настоящее дерево живёт в синтакс-помощнике, поэтому здесь оно собирается
 * вручную — ровно теми типами, какими его заводит
 * {@code MetadataCollectionSpecializer}: корень
 * ({@code ОбъектМетаданныхКонфигурация}), коллекция
 * ({@code КоллекцияОбъектовМетаданных.Справочники}) и конкретный объект
 * ({@code ОбъектМетаданных: Справочник.Справочник1}). На реальной конфигурации
 * то же самое проверяет {@code MetadataChainSemanticTokensHbkTest}.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
@Import(SemanticTokensTestHelper.class)
class MetadataChainSemanticTokensTest extends AbstractServerContextAwareTest {

  private static final String CONFIGURATION_TYPE = "ОбъектМетаданныхКонфигурация";
  private static final String CATALOGS_COLLECTION = "КоллекцияОбъектовМетаданных.Справочники";
  private static final String CATALOG_OBJECT = "ОбъектМетаданных: Справочник.Справочник1";

  @Autowired
  private GlobalScopeSemanticTokensSupplier supplier;

  @Autowired
  private SemanticTokensTestHelper helper;

  @Autowired
  private TypeRegistry typeRegistry;

  /** Заводит корень дерева метаданных под именем {@code Метаданные} и два звена под ним. */
  private void registerMetadataTree() {
    var configuration = typeRegistry.intern(TypeKind.PLATFORM, CONFIGURATION_TYPE);
    typeRegistry.registerDisplayName(configuration, BilingualString.of("Метаданные", "Metadata"));
    typeRegistry.registerGlobalPropertyType(configuration, FileType.BSL);

    var catalogs = typeRegistry.intern(TypeKind.PLATFORM, CATALOGS_COLLECTION);
    typeRegistry.registerMemberSource(configuration,
      () -> List.of(MemberDescriptor.property("Справочники", catalogs, "")), FileType.BSL);

    var catalog = typeRegistry.intern(TypeKind.PLATFORM, CATALOG_OBJECT);
    typeRegistry.registerMemberSource(catalogs,
      () -> List.of(MemberDescriptor.property("Справочник1", catalog, "")), FileType.BSL);
    typeRegistry.registerMemberSource(catalog,
      () -> List.of(MemberDescriptor.property("Имя", TypeRef.ANY, "")), FileType.BSL);
  }

  @Test
  void collectionsAreProperties_andMetadataObjectsAreClasses() {
    initServerContext(TestUtils.PATH_TO_METADATA);
    registerMetadataTree();
    var bsl = """
      Процедура Тест()
          А = Метаданные.Справочники.Справочник1.Имя;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(bsl, context);

    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Имя объекта метаданных читается в коде как имя объекта — Class, как у
    // `Справочники.Справочник1`; коллекция и обычное свойство — Property.
    helper.assertContainsTokens(decoded, List.of(
      new ExpectedToken(1, 8, 10, SemanticTokenTypes.Class, Set.of(DefaultLibrary), "Метаданные"),
      new ExpectedToken(1, 19, 11, SemanticTokenTypes.Property, Set.of(DefaultLibrary), "Справочники"),
      new ExpectedToken(1, 31, 11, SemanticTokenTypes.Class, "Справочник1"),
      new ExpectedToken(1, 43, 3, SemanticTokenTypes.Property, Set.of(DefaultLibrary), "Имя")
    ));
  }

  @Test
  void chainStopsAtTheFirstUnknownMember() {
    initServerContext(TestUtils.PATH_TO_METADATA);
    registerMetadataTree();
    var bsl = """
      Процедура Тест()
          А = Метаданные.Справочники.НетТакого.Имя;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(bsl, context);

    var decoded = helper.decodeFromEntries(supplier.getSemanticTokens(documentContext));

    // Несуществующее имя не красится, и разметка дальше не идёт: выдуманный тип
    // хуже отсутствия подсветки.
    assertThat(decoded)
      .filteredOn(token -> token.line() == 1 && token.start() >= 31)
      .isEmpty();
  }
}
