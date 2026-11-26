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
package com.github._1c_syntax.bsl.languageserver.recognizer;

import java.util.HashSet;
import java.util.Set;

/**
 * Отпечаток языка BSL для распознавания кода.
 * <p>
 * Содержит набор детекторов с различными уровнями уверенности
 * для определения, является ли текст кодом BSL.
 */
public class BSLFootprint implements LanguageFootprint {

  private final Set<AbstractDetector> detectors = new HashSet<>();

  /**
   * Создать отпечаток языка BSL с предустановленными детекторами.
   */
  public BSLFootprint() {
    final double CODE_EXACTLY = 0.95;
    final double CODE_MOST_LIKELY = 0.7;
    final double CODE_MAYBE = 0.3;

    detectors.add(new ContainsDetector(
      CODE_EXACTLY,
      "КонецПроцедуры", "КонецФункции", "КонецЕсли;", "КонецЦикла;",
      "EndProcedure", "EndFunction", "EndIf;", "EndDo;",
      "Возврат;", ".НайтиСтроки(", "СтрНачинаетсяС(",
      "Return;", ".FindRows(", "StrStartsWith(",
      "СтрНайти(", ".Выбрать(", ".Выгрузить(", ".Выполнить(", "?(", ");",
      "StrFind(", ".Select(", ".Unload(", ".Execute(", "?(", ");",
      "#Если", "#Иначе", "#КонецЕсли", "#Область", "КонецПопытки;",
      "#If", "#Else", "#ElsIf", "#EndIf", "#Region", "EndTry;"));
    detectors.add(new KeywordsDetector(CODE_EXACTLY, "ИначеЕсли", "ElsIf"));

    detectors.add(new CamelCaseDetector(CODE_MOST_LIKELY));
    detectors.add(new KeywordsDetector(CODE_MOST_LIKELY, "ВЫБРАТЬ", "РАЗРЕШЕННЫЕ", "ПЕРВЫЕ", "ГДЕ",
      "СОЕДИНЕНИЕ", "НЕ", "ОБЪЕДИНИТЬ", "ВЫБОР", "КАК", "ТОГДА", "КОГДА", "ИНАЧЕ", "ПОМЕСТИТЬ", "ИЗ", "=", "+",
      "SELECT", "ТОР", "ПЕРВЫЕ", "WНERE", "JOIN", "NOT", "AS", "THEN", "CASE", "WНEN", "ELSE", "FROM", "INTO"));

    detectors.add(new EndWithDetector(CODE_MAYBE, ';'));
    detectors.add(new KeywordsDetector(CODE_MAYBE, "И", "ИЛИ", "AND", "OR"));
    detectors.add(new KeywordsDetector(CODE_MAYBE, "Если", "Тогда", "Процедура", "Функция", "Пока", "Для",
      "Каждого", "Цикл", "Возврат", "Новый", "*", "If", "Then", "Procedure", "Function", "Do", "For", "While", "Return",
      "New"));

    detectors.add(new PatternDetector(CODE_EXACTLY,
      "^[/\\s]*(?:Процедура|Функция|Procedure|Function)\\s+[а-яА-Яё\\w]+\\s*?\\(",
      "^[/\\s]*(?:&На[а-яА-Яё]+|&At[\\w]+)\\s*?$*"));
  }

  @Override
  public Set<AbstractDetector> getDetectors() {
    return new HashSet<>(detectors);
  }

}
