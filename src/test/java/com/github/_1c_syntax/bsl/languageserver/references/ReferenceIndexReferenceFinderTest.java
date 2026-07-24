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
package com.github._1c_syntax.bsl.languageserver.references;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.symbol.PlatformMemberSymbol;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.file.Path;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class ReferenceIndexReferenceFinderTest extends AbstractServerContextAwareTest {

  @Autowired
  private ReferenceIndexReferenceFinder referenceFinder;

  @MockitoSpyBean
  private ReferenceIndex referenceIndex;

  private static final String PATH_TO_FILE = "./src/test/resources/references/ReferenceIndexReferenceFinder.bsl";

  @BeforeEach
  void prepareServerContext() {
    initServerContextOnce(Path.of(PATH_TO_METADATA));
  }

  @Test
  void testLocalMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var method = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(1, 10);
    var location = new Location(uri.toString(), Ranges.create(1, 4, 16));

    var expectedReference = Reference.of(method, method, location);
    when(referenceIndex.getReference(uri, position)).thenReturn(Optional.of(expectedReference));

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference).isEqualTo(expectedReference);

    // when
    var optionalReference = referenceFinder.findReference(uri, new Position());

    // then
    assertThat(optionalReference).isEmpty();
  }

  @Test
  void testCommonModuleMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var commonModuleContext = context.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var calledMethodSymbol = commonModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(2, 30);

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference.uri()).isEqualTo(uri);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(2, 22, 41));
  }

  @Test
  void testManagerModuleMethodCall() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяПроцедуры").orElseThrow();
    var managerModuleContext = context.getDocument("InformationRegister.РегистрСведений1", ModuleType.ManagerModule).orElseThrow();
    var calledMethodSymbol = managerModuleContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    var uri = documentContext.getUri();
    var position = new Position(3, 40);

    // when
    var reference = referenceFinder.findReference(uri, position).orElseThrow();

    // then
    assertThat(reference.uri()).isEqualTo(uri);
    assertThat(reference.from()).isEqualTo(methodSymbol);
    assertThat(reference.symbol()).isEqualTo(calledMethodSymbol);
    assertThat(reference.selectionRange()).isEqualTo(Ranges.create(3, 38, 57));
  }

  @Test
  void testCantFindNonExportMethodFromOtherModule() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);

    var uri = documentContext.getUri();
    var position = new Position(4, 25);

    // when
    var reference = referenceFinder.findReference(uri, position);

    // then
    assertThat(reference).isEmpty();
  }

  @Test
  void testUnknownLocationReturnsEmptyReference() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE, context);
    var method = mock(MethodSymbol.class);

    var uri = documentContext.getUri();
    var position = new Position(1, 1);
    var location = new Location(uri.toString(), Ranges.create(1, 1, 2));

    var expectedReference = Reference.of(method, method, location);
    when(referenceIndex.getReference(uri, position)).thenReturn(Optional.of(expectedReference));

    // when
    var optionalReference = referenceFinder.findReference(uri, new Position());

    // then
    assertThat(optionalReference).isEmpty();
  }

  @Test
  void bareAssignmentToOwnAttributeIsNotResolvedAsDynamicVariable() {
    // Присваивание без Перем одноимённому реквизиту объекта — это запись в реквизит
    // (self-член), а не отдельная динамическая переменная. Голый self-член индексируется
    // (ReferenceIndexFiller) как обращение к PlatformMemberSymbol, поэтому finder резолвит
    // его напрямую через индекс — единообразно с definition/hover, а не как переменную.
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        Реквизит1 = "А";
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var reference = referenceFinder.findReference(documentContext.getUri(), new Position(1, 3));

      assertThat(reference)
        .as("голый self-реквизит резолвится в self-член (PlatformMemberSymbol), не в переменную")
        .isPresent()
        .hasValueSatisfying(ref -> {
          assertThat(ref.isSourceDefinedSymbolReference()).isFalse();
          assertThat(ref.symbol()).isInstanceOf(PlatformMemberSymbol.class);
        });
    } finally {
      // Тест подменяет контент реального модуля из общей фикстуры (нужен
      // настоящий self-тип) — контекст между тестами класса переиспользуется
      // (initServerContextOnce), поэтому явно возвращаем документ к состоянию
      // на диске, иначе следующий тест увидит эту подмену.
      context.removeDocument(documentContext.getUri());
    }
  }

  @Test
  void explicitlyDeclaredVariableStillShadowsSameNamedAttribute() {
    // А вот с явным Перем — это уже настоящая локальная переменная модуля,
    // она перекрывает одноимённый реквизит: finder должен резолвить её как обычно.
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Перем Реквизит1;

      Процедура Тест()
        Реквизит1 = "А";
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var reference = referenceFinder.findReference(documentContext.getUri(), new Position(3, 3));

      assertThat(reference)
        .as("явно объявленная (Перем) переменная должна резолвиться как обычно, self-свойство её не перекрывает")
        .isPresent()
        .hasValueSatisfying(ref -> assertThat(ref.symbol()).isInstanceOfSatisfying(VariableSymbol.class,
          variable -> assertThat(variable.getKind()).isEqualTo(VariableKind.MODULE)));
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

  @Test
  void bareAssignmentToNonAttributeNameResolvesAsOrdinaryDynamicVariable() {
    // DYNAMIC-переменная (без Перем), но её имя не совпадает ни с одним
    // self-членом — isDynamicVariableShadowedBySelfMember возвращает false,
    // finder должен вернуть ссылку на саму DYNAMIC-переменную без изменений.
    // Позиция — на ВТОРОМ (читающем) вхождении: первое (объявляющее)
    // присваивание в ReferenceIndex не попадает как usage вовсе
    // (см. ReferenceIndexFiller#notVariableInitialization), поэтому для
    // непустого результата нужно повторное обращение.
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        МояЛокальная = "А";
        Х = МояЛокальная;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var reference = referenceFinder.findReference(documentContext.getUri(), new Position(2, 6));

      assertThat(reference)
        .as("DYNAMIC-переменная, не перекрытая self-членом, резолвится как обычно")
        .isPresent()
        .hasValueSatisfying(ref -> assertThat(ref.symbol()).isInstanceOfSatisfying(VariableSymbol.class,
          variable -> assertThat(variable.getKind()).isEqualTo(VariableKind.DYNAMIC)));
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

  @Test
  void forEachLoopVariableMatchingSelfAttributeResolvesAsVariableNotSelfMember() {
    // Регресс [code-review]: голое присваивание одноимённому реквизиту подавляет
    // фантомную переменную, но переменная цикла Для Каждого — реальное объявление и
    // подавляться НЕ должна, даже если её имя совпадает со стандартным реквизитом
    // объекта (Ссылка). Иначе обращение к ней внутри цикла резолвилось бы в self-член.
    var uri = Path.of(
      "./src/test/resources/metadata/designer/Catalogs/Справочник1/Ext/ObjectModule.bsl").toUri();
    var content = """
      Процедура Тест()
        Для Каждого Ссылка Из Новый Массив Цикл
          Х = Ссылка;
        КонецЦикла;
      КонецПроцедуры
      """;
    var documentContext = TestUtils.getDocumentContext(uri, content, context);
    try {
      var reference = referenceFinder.findReference(documentContext.getUri(), new Position(2, 8));

      assertThat(reference)
        .as("переменная цикла Для Каждого должна резолвиться как переменная, не как self-член")
        .isPresent()
        .hasValueSatisfying(ref -> assertThat(ref.symbol()).isInstanceOfSatisfying(VariableSymbol.class,
          variable -> assertThat(variable.getKind()).isEqualTo(VariableKind.DYNAMIC)));
    } finally {
      context.removeDocument(documentContext.getUri());
    }
  }

}
