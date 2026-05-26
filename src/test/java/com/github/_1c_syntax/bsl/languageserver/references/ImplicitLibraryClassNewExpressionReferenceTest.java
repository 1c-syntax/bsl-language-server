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
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ModuleSymbol;
import com.github._1c_syntax.bsl.languageserver.references.model.Reference;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressy для резолва typeName внутри {@code Новый <ИмяКласса>(...)} для
 * OneScript library-классов. Цель — единая семантика независимо от того,
 * объявлен ли класс в {@code lib.config} (manifest) или подцеплен implicit-обходом
 * convention-каталога {@code Классы}:
 * <ol>
 *   <li>если у класса есть явный конструктор ({@code ПриСозданииОбъекта} /
 *   {@code OnObjectCreate}) — ссылка резолвится в {@link ConstructorSymbol}
 *   этого конструктора, чтобы go-to-def вёл прямо на тело конструктора,
 *   а hover показывал его сигнатуру;</li>
 *   <li>если конструктора в исходнике нет — ссылка резолвится в {@link ModuleSymbol}
 *   .os-файла, чтобы go-to-def вёл в файл целиком.</li>
 * </ol>
 * <p>
 * Воспроизводит топологию {@code autumn-library/winow → oscript_modules/autumn}:
 * lib.config объявляет {@code PublicEntity}/{@code ClassWithoutCtor},
 * {@code src/internal/Классы/InternalEntity.os} и {@code ВнутренняяСущность.os}
 * НЕ объявлены.
 */
@CleanupContextBeforeClassAndAfterClass
class ImplicitLibraryClassNewExpressionReferenceTest extends AbstractServerContextAwareTest {

  private static final String FIXTURE_DIR = "src/test/resources/oscript-libraries/internal-classes-test";

  @Autowired
  private OScriptLibraryIndex index;

  @Autowired
  private ReferenceResolver referenceResolver;

  // --- Sanity: implicit-сбор реально работает ---

  @Test
  void implicitLatinLibraryClassIsRegisteredInIndex() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    assertThat(index.findClassUri("PublicEntity"))
      .as("manifest-объявленный класс должен иметь class-URI в OScriptLibraryIndex")
      .isPresent();
    assertThat(index.findClassUri("InternalEntity"))
      .as("implicit-класс из src/internal/Классы должен иметь class-URI в OScriptLibraryIndex")
      .isPresent();
  }

  @Test
  void implicitCyrillicLibraryClassIsRegisteredInIndex() {
    // given
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);

    // when
    index.reindex(context);

    // then
    assertThat(index.findClassUri("ВнутренняяСущность"))
      .as("кириллический implicit-класс должен иметь class-URI в OScriptLibraryIndex")
      .isPresent();
  }

  // --- Желаемое поведение: typeName c явным конструктором → ConstructorSymbol ---

  @Test
  void newExpressionOnManifestClassWithCtorResolvesToConstructor() {
    // given: PublicEntity объявлен в lib.config и имеет ПриСозданииОбъекта() Экспорт
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);
    index.reindex(context);

    var content = """
      #Использовать internal-classes-lib
      Х = Новый PublicEntity();
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
    var position = positionInsideIdentifier(content, "PublicEntity");

    // when
    var reference = referenceResolver.findReference(dc.getUri(), position).orElseThrow();

    // then
    assertConstructorReference(reference);
  }

  @Test
  void newExpressionOnImplicitLatinClassWithCtorResolvesToConstructor() {
    // given: InternalEntity — implicit-класс, конструктор БЕЗ Экспорт
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);
    index.reindex(context);

    var content = """
      #Использовать internal-classes-lib
      Х = Новый InternalEntity();
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
    var position = positionInsideIdentifier(content, "InternalEntity");

    // when
    var reference = referenceResolver.findReference(dc.getUri(), position).orElseThrow();

    // then
    assertConstructorReference(reference);
  }

  @Test
  void newExpressionOnImplicitCyrillicClassWithCtorResolvesToConstructor() {
    // given: ВнутренняяСущность — implicit-класс с кириллическим именем
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);
    index.reindex(context);

    var content = """
      #Использовать internal-classes-lib
      Х = Новый ВнутренняяСущность();
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
    var position = positionInsideIdentifier(content, "ВнутренняяСущность");

    // when
    var reference = referenceResolver.findReference(dc.getUri(), position).orElseThrow();

    // then
    assertConstructorReference(reference);
  }

  // --- Желаемое поведение: typeName без явного конструктора → ModuleSymbol ---

  @Test
  void newExpressionOnClassWithoutCtorResolvesToModuleSymbol() {
    // given: ClassWithoutCtor — нет ПриСозданииОбъекта в исходнике
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), false);
    index.reindex(context);

    var content = """
      #Использовать internal-classes-lib
      Х = Новый ClassWithoutCtor();
      """;
    var dc = TestUtils.getDocumentContext(TestUtils.FAKE_OSCRIPT_DOCUMENT_URI, content, context);
    var position = positionInsideIdentifier(content, "ClassWithoutCtor");

    // when
    var reference = referenceResolver.findReference(dc.getUri(), position).orElseThrow();

    // then
    assertThat(reference.symbol())
      .as("класс без явного конструктора должен резолвиться в ModuleSymbol .os-файла")
      .isInstanceOf(ModuleSymbol.class);
  }

  // --- Caller прямо в workspace (как реальный СессияПользователя.os) ---

  @Test
  void newExpressionInWorkspaceCallerResolvesEachClassAsExpected() {
    // given: caller лежит ПРЯМО в workspace, populate=true — как в реальном winow.
    initServerContext(Path.of(FIXTURE_DIR).toAbsolutePath(), true);

    var callerPath = Path.of(FIXTURE_DIR, "src/Классы/Caller.os").toAbsolutePath();
    var callerUri = callerPath.toUri();
    var callerContent = readFile(callerPath);

    var positionPublic = positionInsideIdentifier(callerContent, "Новый PublicEntity", "PublicEntity");
    var positionInternal = positionInsideIdentifier(callerContent, "Новый InternalEntity", "InternalEntity");
    var positionCyrillic = positionInsideIdentifier(callerContent, "Новый ВнутренняяСущность", "ВнутренняяСущность");
    var positionNoCtor = positionInsideIdentifier(callerContent, "Новый ClassWithoutCtor", "ClassWithoutCtor");

    // when
    var refPublic = referenceResolver.findReference(callerUri, positionPublic).orElseThrow();
    var refInternal = referenceResolver.findReference(callerUri, positionInternal).orElseThrow();
    var refCyrillic = referenceResolver.findReference(callerUri, positionCyrillic).orElseThrow();
    var refNoCtor = referenceResolver.findReference(callerUri, positionNoCtor).orElseThrow();

    // then
    assertConstructorReference(refPublic);
    assertConstructorReference(refInternal);
    assertConstructorReference(refCyrillic);
    assertThat(refNoCtor.symbol())
      .as("класс без конструктора в workspace-caller'е — ModuleSymbol")
      .isInstanceOf(ModuleSymbol.class);
  }

  // --- helpers ---

  private static void assertConstructorReference(Reference reference) {
    assertThat(reference.symbol())
      .as("ожидается ConstructorSymbol, фактически " + reference.symbol().getClass().getSimpleName())
      .isInstanceOf(ConstructorSymbol.class);
    var method = (MethodSymbol) reference.symbol();
    assertThat(method.getName().toLowerCase(Locale.ROOT))
      .as("имя метода должно быть ПриСозданииОбъекта или OnObjectCreate, фактически " + method.getName())
      .isIn("присозданииобъекта", "onobjectcreate");
  }

  /**
   * Позиция в середине первого вхождения {@code identifier} в {@code content}.
   * Координаты — LSP: 0-based line, 0-based character.
   */
  private static Position positionInsideIdentifier(String content, String identifier) {
    return positionInsideIdentifier(content, identifier, identifier);
  }

  /**
   * Сначала находим {@code anchor} в {@code content} (чтобы избежать совпадения
   * с тем же идентификатором в другом месте), курсор ставим в середину
   * {@code identifier} внутри anchor'а.
   */
  private static Position positionInsideIdentifier(String content, String anchor, String identifier) {
    int anchorOffset = content.indexOf(anchor);
    if (anchorOffset < 0) {
      throw new IllegalArgumentException("Anchor not found in content: " + anchor);
    }
    int identifierOffsetInAnchor = anchor.indexOf(identifier);
    if (identifierOffsetInAnchor < 0) {
      throw new IllegalArgumentException("Identifier not part of anchor: " + identifier);
    }
    int absoluteOffset = anchorOffset + identifierOffsetInAnchor;
    int line = 0;
    int lineStart = 0;
    for (int i = 0; i < absoluteOffset; i++) {
      if (content.charAt(i) == '\n') {
        line++;
        lineStart = i + 1;
      }
    }
    int column = absoluteOffset - lineStart + identifier.length() / 2;
    return new Position(line, column);
  }

  private static String readFile(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read fixture file: " + path, e);
    }
  }
}
