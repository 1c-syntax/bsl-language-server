/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.bsl.types.ModuleType;
import com.github._1c_syntax.utils.StringInterner;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Утилитный класс для построения ссылок на объекты метаданных (MDO).
 * <p>
 * Используется для создания строковых идентификаторов объектов конфигурации 1С,
 * которые применяются при разрешении ссылок между модулями.
 */
@UtilityClass
public class MdoRefBuilder {

  private final StringInterner stringInterner = new StringInterner();

  /**
   * Получить ссылку на объект метаданных для вызова метода.
   *
   * @param documentContext Контекст документа
   * @param callStatement Контекст вызова метода
   * @return Строковая ссылка на MDO
   */
  public String getMdoRef(DocumentContext documentContext, BSLParser.CallStatementContext callStatement) {
    if (callStatement.globalMethodCall() != null) {
      return getMdoRef(documentContext);
    } else {
      return getMdoRef(documentContext, callStatement.IDENTIFIER(), callStatement.modifier());
    }
  }

  /**
   * Получить ссылку на объект метаданных для документа.
   *
   * @param documentContext Контекст документа
   * @return Строковая ссылка на MDO документа
   */
  public static String getMdoRef(DocumentContext documentContext) {
    var mdoRef = documentContext.getMdObject()
      .map(MD::getMdoReference)
      .map(MdoReference::getMdoRef)
      .orElseGet(() -> documentContext.getUri().toString());
    return stringInterner.intern(mdoRef);
  }

  /**
   * Получить ссылку на объект метаданных из контекста сложного идентификатора.
   *
   * @param documentContext Контекст документа
   * @param complexIdentifier Контекст сложного идентификатора
   * @return Строковая ссылка на MDO
   */
  public String getMdoRef(DocumentContext documentContext, BSLParser.ComplexIdentifierContext complexIdentifier) {
    return getMdoRef(documentContext, complexIdentifier.IDENTIFIER(), complexIdentifier.modifier());
  }

  /**
   * Получить ссылку на объект метаданных из идентификатора и модификаторов.
   *
   * @param documentContext Контекст документа
   * @param identifier Терминальный узел с идентификатором
   * @param modifiers Список модификаторов
   * @return Строковая ссылка на MDO
   */
  public String getMdoRef(
    DocumentContext documentContext,
    @Nullable
    TerminalNode identifier,
    List<? extends BSLParser.ModifierContext> modifiers
  ) {

    AtomicReference<String> mdoRef = new AtomicReference<>("");

    Optional.ofNullable(identifier)
      .map(ParseTree::getText)
      .flatMap(commonModuleName -> getCommonModuleMdoRef(documentContext, commonModuleName))
      .or(() ->
        Optional.ofNullable(identifier)
          .map(ParseTree::getText)
          .flatMap(MDOType::fromValue)
          .filter(mdoType -> ModuleType.byMDOType(mdoType)
            .contains(ModuleType.ManagerModule))
          .map(mdoType -> getMdoRef(mdoType, getMdoName(modifiers)))
      )
      .ifPresent(mdoRef::set);

    return stringInterner.intern(mdoRef.get());
  }

  /**
   * Получить mdoRef в языке конфигурации
   *
   * @param documentContext the document context
   * @param mdo             the mdo
   * @return the locale mdoRef
   */
  public String getLocaleMdoRef(DocumentContext documentContext, MD mdo) {
    return stringInterner.intern(
      mdo.getMdoReference().getMdoRef(documentContext.getServerContext().getConfiguration().getScriptVariant()));
  }

  /**
   * Получить имя родителя метаданного в языке конфигурации.
   *
   * @param documentContext the document context
   * @param mdo             the mdo
   * @return the locale owner mdo name
   */
  public String getLocaleOwnerMdoName(DocumentContext documentContext, MD mdo) {
    final var names = getLocaleMdoRef(documentContext, mdo).split("\\.");
    if (names.length <= 1) {
      return "";
    }
    return stringInterner.intern(names[0].concat(".").concat(names[1]));
  }

  private Optional<String> getCommonModuleMdoRef(DocumentContext documentContext, String commonModuleName) {
    return documentContext.getServerContext()
      .getConfiguration()
      .findCommonModule(commonModuleName)
      .map(CommonModule::getMdoReference)
      .map(MdoReference::getMdoRef);
  }

  private String getMdoRef(MDOType mdoType, String identifier) {
    if (identifier.isEmpty()) {
      return "";
    }

    return mdoType.nameEn() + "." + identifier;
  }

  private String getMdoName(List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .limit(1)
      .findAny()
      .map(BSLParser.ModifierContext::accessProperty)
      .map(BSLParser.AccessPropertyContext::IDENTIFIER)
      .map(ParseTree::getText)
      .orElse("");
  }
}
