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
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.bsl.types.MdoReference;
import com.github._1c_syntax.utils.StringInterner;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

/**
 * Утилитный класс для построения ссылок на объекты метаданных (MDO).
 * <p>
 * Используется для создания строковых идентификаторов объектов конфигурации 1С,
 * которые применяются при разрешении ссылок между модулями.
 */
@UtilityClass
public class MdoRefBuilder {

  /**
   * Для оптимизации хранения одинаковых строк
   */
  private final StringInterner stringInterner = new StringInterner();

  /**
   * Получить ссылку на объект метаданных для вызова метода.
   *
   * @param documentContext Контекст документа
   * @param callStatement   Контекст вызова метода
   * @return Строковая ссылка на MDO
   */
  public String getMdoRef(DocumentContext documentContext, BSLParser.CallStatementContext callStatement) {
    if (callStatement.globalMethodCall() != null) {
      // todo возвращается ссылка на документ, что не позволяет нормально с глобальными методами работать
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
    // осторожно! не менять на вызов documentContext.getMdoRef, а то зациклится
    var mdoRef = documentContext.getMdObject()
      .map(MD::getMdoRef)
      .orElseGet(() -> documentContext.getUri().toString());
    return stringInterner.intern(mdoRef);
  }

  /**
   * Формирует ссылку на объект-владелец свойства
   *
   * @param documentContext Документ (файл)
   * @param ctx             Узел
   * @return Ссылка на объект-владелец
   */
  public String getMdoRef(DocumentContext documentContext, BSLParser.ComplexIdentifierContext ctx) {
    return getMdoRef(documentContext, ctx.IDENTIFIER(), ctx.modifier());
  }

  /**
   * Формирует ссылку на объект-владелец метода или свойства по идентификатору и модификаторам узла
   *
   * @param documentContext Документ (файл)
   * @param identifier      Имя общего модуля или типа объекта метаданных
   * @param modifiers       "Модификаторы", т.е. части имени между "точками" (используется только второй)
   * @return Ссылка
   */
  public String getMdoRef(
    DocumentContext documentContext,
    @Nullable
    TerminalNode identifier,
    List<? extends BSLParser.ModifierContext> modifiers
  ) {
    if (identifier == null) {
      return "";
    }

    // предполагаем, что это вызов метода общего модуля
    var commonModule = documentContext.getServerContext().getConfiguration().findCommonModule(identifier.getText());
    if (commonModule.isPresent()) {
      return commonModule.get().getMdoRef();
    }

    // раз не общий модуль, то нужно определить тип метаданного и, если у него есть модуль менеджера, вызвать метод
    // todo такой подход не дает использовать ссылки на методы платформенных объектов
    var mdoType = MDOType.fromValue(identifier.getText());
    if (mdoType.isPresent()) {
      var mdoName = getMdoName(modifiers);
      if (!mdoName.isEmpty()) {
        return MdoReference.create(mdoType.get(), mdoName).getMdoRef();
      }
    }
    return "";
  }

  private String getMdoName(List<? extends BSLParser.ModifierContext> modifiers) {
    return modifiers.stream()
      .limit(1)
      .findFirst()
      .map(BSLParser.ModifierContext::accessProperty)
      .map(BSLParser.AccessPropertyContext::IDENTIFIER)
      .map(ParseTree::getText)
      .orElse("");
  }
}
