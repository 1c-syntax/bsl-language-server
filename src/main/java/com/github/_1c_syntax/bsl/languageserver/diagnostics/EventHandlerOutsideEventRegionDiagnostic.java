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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.EventMethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormHandlerRoleIndex;
import com.github._1c_syntax.bsl.languageserver.utils.Keywords;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Метод-обработчик платформенного события должен находиться в стандартной
 * области «Обработчики событий» (для модулей формы — «Обработчики событий
 * формы» и т.п.). См. ИТС std455 «Структура модуля».
 */
@DiagnosticMetadata(
  type = DiagnosticType.CODE_SMELL,
  severity = DiagnosticSeverity.INFO,
  scope = DiagnosticScope.BSL,
  minutesToFix = 1,
  tags = {
    DiagnosticTag.STANDARD
  }
)
public class EventHandlerOutsideEventRegionDiagnostic extends AbstractDiagnostic implements QuickFixProvider {

  /**
   * Имена областей-«обработчиков событий» по стандарту std455 — ru/en.
   * Сравнение регистронезависимое.
   */
  private static final String OBJECT_TARGET_REGION = Keywords.EVENT_HANDLERS_REGION.getRu();

  private static final Set<String> OBJECT_EVENT_REGIONS = Set.of(
    Keywords.EVENT_HANDLERS_REGION.getRu(),
    Keywords.EVENT_HANDLERS_REGION.getEn()
  );

  /**
   * Области модуля формы, куда допустимо класть обработчик, когда о нём известно
   * только то, что он обработчик: форма могла не прочитаться, а метод мог совпасть
   * с событием по имени, не будучи объявленным в {@code Form.xml}.
   */
  private static final Set<String> FORM_EVENT_REGION_PREFIXES = Set.of(
    Keywords.FORM_EVENT_HANDLERS_REGION.getRu(),
    Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getRu(),
    Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getRu(),
    Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getRu(),
    Keywords.FORM_EVENT_HANDLERS_REGION.getEn(),
    Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getEn(),
    Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getEn(),
    Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getEn()
  );

  private final FormHandlerRoleIndex formHandlerRoleIndex;

  public EventHandlerOutsideEventRegionDiagnostic(FormHandlerRoleIndex formHandlerRoleIndex) {
    this.formHandlerRoleIndex = formHandlerRoleIndex;
  }

  @Override
  public void check() {
    documentContext.getSymbolTree().getMethods().stream()
      .filter(EventMethodSymbol.class::isInstance)
      .forEach(this::checkMethod);
  }

  private void checkMethod(MethodSymbol method) {
    var expectedRegion = expectedRegion(method);
    if (isInEventRegion(method, expectedRegion)) {
      return;
    }
    diagnosticStorage.addDiagnostic(method.getSubNameRange(),
      info.getMessage(method.getName(), expectedRegion.orElse(OBJECT_TARGET_REGION)));
  }

  /**
   * Область, в которой обработчик обязан лежать по стандарту. У модуля формы она
   * зависит от того, кем обработчик объявлен: событие формы, событие элемента шапки,
   * событие элемента таблицы и действие команды живут в разных областях.
   *
   * @return имя области; пусто, если модуль формы, а объявитель обработчика неизвестен —
   *   тогда годится любая из форменных областей.
   */
  private Optional<String> expectedRegion(MethodSymbol method) {
    if (documentContext.getModuleType() != ModuleType.FormModule) {
      return Optional.of(OBJECT_TARGET_REGION);
    }
    return formHandlerRoleIndex.roleOf(documentContext, method.getName())
      .map(EventHandlerOutsideEventRegionDiagnostic::regionOf);
  }

  private static String regionOf(FormHandlerRoleIndex.Handler handler) {
    return switch (handler.role()) {
      case FORM_EVENT -> Keywords.FORM_EVENT_HANDLERS_REGION.getRu();
      case HEADER_ITEM_EVENT -> Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getRu();
      case TABLE_ITEM_EVENT -> Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getRu() + handler.owner();
      case COMMAND -> Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getRu();
    };
  }

  /**
   * Лежит ли метод там, где положено. Если конкретная область известна — сравниваем
   * с ней (в обеих локалях), иначе принимаем любую область обработчиков.
   */
  private boolean isInEventRegion(MethodSymbol method, Optional<String> expectedRegion) {
    var regionOpt = method.getRegion();
    if (regionOpt.isEmpty()) {
      return false;
    }
    var regionName = regionOpt.get().getName().toLowerCase(Locale.ROOT);
    if (expectedRegion.isEmpty()) {
      return FORM_EVENT_REGION_PREFIXES.stream()
        .map(p -> p.toLowerCase(Locale.ROOT))
        .anyMatch(regionName::startsWith);
    }
    if (documentContext.getModuleType() != ModuleType.FormModule) {
      return OBJECT_EVENT_REGIONS.stream()
        .map(r -> r.toLowerCase(Locale.ROOT))
        .anyMatch(regionName::equals);
    }
    return regionName.equals(expectedRegion.get().toLowerCase(Locale.ROOT))
      || regionName.equals(englishOf(expectedRegion.get()).toLowerCase(Locale.ROOT));
  }

  /** Английский вариант имени области — то же имя из {@link Keywords}, другая локаль. */
  private static String englishOf(String regionRu) {
    if (regionRu.startsWith(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getRu())) {
      return Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getEn()
        + regionRu.substring(Keywords.FORM_TABLE_ITEMS_EVENT_HANDLERS_REGION_START.getRu().length());
    }
    if (regionRu.equals(Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getRu())) {
      return Keywords.FORM_HEADER_ITEMS_EVENT_HANDLERS_REGION.getEn();
    }
    if (regionRu.equals(Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getRu())) {
      return Keywords.FORM_COMMANDS_EVENT_HANDLERS_REGION.getEn();
    }
    return regionRu.equals(Keywords.FORM_EVENT_HANDLERS_REGION.getRu())
      ? Keywords.FORM_EVENT_HANDLERS_REGION.getEn()
      : Keywords.EVENT_HANDLERS_REGION.getEn();
  }

  @Override
  public List<CodeAction> getQuickFixes(List<Diagnostic> diagnostics,
                                        CodeActionParams params,
                                        DocumentContext documentContext) {
    var methods = new ArrayList<MethodSymbol>();
    var fixedDiagnostics = new ArrayList<Diagnostic>();
    for (var diagnostic : diagnostics) {
      var method = findMethodAt(documentContext, diagnostic.getRange().getStart());
      if (method.isEmpty()) {
        continue;
      }
      methods.add(method.get());
      fixedDiagnostics.add(diagnostic);
    }
    if (methods.isEmpty()) {
      return List.of();
    }

    var contentList = documentContext.getContentList();
    var textEdits = new ArrayList<TextEdit>();
    for (var method : methods) {
      textEdits.add(new TextEdit(methodDeleteRange(method, contentList), ""));
    }

    var methodTexts = methods.stream()
      .map(m -> extractMethodText(documentContext, m))
      .filter(text -> !text.isEmpty())
      .toList();
    if (methodTexts.isEmpty()) {
      return List.of();
    }
    // Область берётся по первому методу: fix-all группирует методы одной области,
    // а разные роли дают разные области — их правки не смешиваются.
    var targetRegion = expectedRegion(methods.get(0)).orElse(
      documentContext.getModuleType() == ModuleType.FormModule
        ? Keywords.FORM_EVENT_HANDLERS_REGION.getRu() : OBJECT_TARGET_REGION);
    var existingRegion = findRegionByName(documentContext, targetRegion);
    if (existingRegion.isPresent()) {
      var insertPos = positionBeforeEndRegion(existingRegion.get());
      // Leading newline нужен только если перед #КонецОбласти не было пустой
      // строки (иначе последовательные quickfix'ы будут накапливать пустые
      // строки между методами). Каждый метод заканчивается «\n\n» — следующий
      // встаёт сразу под пустой строкой, без дополнительного отступа.
      var leading = needsLeadingBlank(insertPos.getLine(), contentList) ? "\n" : "";
      var body = methodTexts.stream()
        .map(text -> text + "\n\n")
        .collect(Collectors.joining());
      textEdits.add(new TextEdit(new Range(insertPos, insertPos), leading + body));
    } else {
      var anchor = newRegionInsertPosition(documentContext);
      var leading = needsLeadingBlank(anchor.getLine(), contentList) ? "\n" : "";
      var body = String.join("\n\n", methodTexts);
      var insertText = leading + "#Область " + targetRegion + "\n\n"
        + body + "\n\n#КонецОбласти\n";
      textEdits.add(new TextEdit(new Range(anchor, anchor), insertText));
    }

    return QuickFixProvider.createCodeActions(
      textEdits, info.getResourceString("quickFixMessage"),
      documentContext.getUri(), fixedDiagnostics);
  }

  private static Optional<MethodSymbol> findMethodAt(DocumentContext documentContext, Position position) {
    return documentContext.getSymbolTree().getMethods().stream()
      .filter(m -> Ranges.containsPosition(m.getSubNameRange(), position))
      .findFirst();
  }

  /**
   * Текст метода — от начала {@code Процедура}/{@code Функция} до конца
   * {@code КонецПроцедуры}/{@code КонецФункции}. Шапка doc-комментария над
   * методом тоже захватывается, если она прижата к методу.
   */
  private static String extractMethodText(DocumentContext documentContext, MethodSymbol method) {
    var content = documentContext.getContentList();
    if (content.length == 0) {
      return "";
    }
    int startLine = methodHeaderStartLine(method, content);
    int endLine = method.getRange().getEnd().getLine();
    var sb = new StringBuilder();
    for (int i = startLine; i <= endLine && i < content.length; i++) {
      if (i > startLine) {
        sb.append('\n');
      }
      sb.append(content[i]);
    }
    return sb.toString();
  }

  /**
   * Диапазон удаления метода — целые строки, включая шапку doc-комментария
   * (та же логика что в {@link #extractMethodText}, иначе после переноса
   * шапка осталась бы «висячей»), и одну хвостовую пустую строку.
   */
  private static Range methodDeleteRange(MethodSymbol method, String[] contentList) {
    int startLine = methodHeaderStartLine(method, contentList);
    int endLine = method.getRange().getEnd().getLine();
    return new Range(new Position(startLine, 0), new Position(endLine + 1, 0));
  }

  /**
   * Идём вверх от {@code Процедура}/{@code Функция} пока встречаем строки
   * с {@code //}-комментарием — это прижатая шапка метода. Возвращаем
   * первую строку этой шапки (или строку метода, если шапки нет).
   */
  private static int methodHeaderStartLine(MethodSymbol method, String[] contentList) {
    int startLine = method.getRange().getStart().getLine();
    while (startLine > 0) {
      var prev = contentList[startLine - 1].stripTrailing();
      if (!prev.startsWith("//")) {
        break;
      }
      startLine--;
    }
    return startLine;
  }

  /**
   * Нужен ли явный пустой ряд перед точкой вставки. Смотрим строку прямо над
   * {@code insertLine}: если она уже пустая или это самое начало файла —
   * новая пустая не нужна, иначе — нужна.
   */
  private static boolean needsLeadingBlank(int insertLine, String[] contentList) {
    if (insertLine == 0) {
      return false;
    }
    var prev = insertLine <= contentList.length ? contentList[insertLine - 1] : "";
    return !prev.isBlank();
  }

  private static Optional<RegionSymbol> findRegionByName(DocumentContext documentContext, String name) {
    return documentContext.getSymbolTree().getRegionsFlat().stream()
      .filter(r -> r.getName().equalsIgnoreCase(name))
      .findFirst();
  }

  /**
   * Позиция перед строкой {@code #КонецОбласти} целевой области — туда
   * вставляется перенесённый метод.
   */
  private static Position positionBeforeEndRegion(RegionSymbol region) {
    var endRange = region.getEndRange();
    return new Position(endRange.getStart().getLine(), 0);
  }

  /**
   * Якорь для новой области — после последней верхнеуровневой области
   * (если есть), иначе в самом верху модуля. Не пытаемся следовать
   * порядку std455 досконально: переместить может пользователь, главное
   * чтобы вставка была валидной.
   */
  private static Position newRegionInsertPosition(DocumentContext documentContext) {
    var topRegions = documentContext.getSymbolTree().getRegionsFlat().stream()
      .filter(r -> r.getParent().isEmpty() || r.getParent().get() == documentContext.getSymbolTree().getModule())
      .toList();
    if (topRegions.isEmpty()) {
      return new Position(0, 0);
    }
    var last = topRegions.get(topRegions.size() - 1);
    return new Position(last.getEndRange().getEnd().getLine() + 1, 0);
  }
}
