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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.utils.Absolute;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Тесты для диагностики UnsafeFindByCodeDiagnostic.
 * <p>
 * Проверяет корректность обнаружения небезопасного использования метода {@code FindByCode()}
 * (или {@code НайтиПоКоду()}) для справочников, планов видов характеристик и планов счетов, у которых:
 * <ul>
 *   <li>отключен контроль уникальности кода ({@code CheckUnique = False})</li>
 *   <li>или включены серии кодов не по всему объекту ({@code CodeSeries} не равно {@code WHOLE_CATALOG})</li>
 * </ul>
 * <p>
 * Примечание: значения {@code WholeCharacteristicKind} и {@code WholeChartOfAccounts} из XML метаданных
 * преобразуются в {@code WHOLE_CATALOG} в enum {@code CodeSeries}, поэтому для всех типов объектов
 * проверка выполняется на {@code WHOLE_CATALOG}.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class UnsafeFindByCodeDiagnosticTest extends AbstractDiagnosticTest<UnsafeFindByCodeDiagnostic> {
  /**
   * Конструктор тестового класса.
   */
  UnsafeFindByCodeDiagnosticTest() {
    super(UnsafeFindByCodeDiagnostic.class);
  }

  /**
   * Основной тест проверки диагностики небезопасного использования метода FindByCode.
   * <p>
   * Проверяет, что диагностика срабатывает для следующих сценариев:
   * <ul>
   *   <li>Справочник без контроля уникальности кода</li>
   *   <li>Справочник с сериями кодов "В пределах подчинения"</li>
   *   <li>Справочник с сериями кодов "В пределах подчинения владельцу"</li>
   *   <li>Справочник без контроля уникальности и с сериями</li>
   *   <li>Английский вариант метода FindByCode</li>
   *   <li>Вызов в составе выражения</li>
   *   <li>План видов характеристик без контроля уникальности</li>
   *   <li>План видов характеристик с сериями кодов "В пределах подчинения"</li>
   *   <li>План видов характеристик без контроля уникальности и с сериями</li>
   *   <li>План счетов без контроля уникальности</li>
   *   <li>План счетов с сериями кодов "В пределах подчинения"</li>
   *   <li>План счетов без контроля уникальности и с сериями</li>
   * </ul>
   * <p>
   * Ожидается 12 срабатываний диагностики на строках: 2, 5, 8, 11, 23, 26, 31, 34, 37, 43, 46, 49.
   * Диагностика срабатывает на строках с комментариями, предшествующими вызовам метода FindByCode/НайтиПоКоду для небезопасных объектов.
   */
  @Test
  void test() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/diagnostics/UnsafeFindByCodeDiagnostic.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    
    var diagnosticLines = diagnostics.stream()
      .map(d -> d.getRange().getStart().getLine())
      .sorted()
      .toList();
    
    assertThat(diagnostics).hasSize(12);
    assertThat(diagnosticLines).containsExactly(2, 5, 8, 11, 23, 26, 31, 34, 37, 43, 46, 49);
  }

  /**
   * Тест проверки граничных случаев.
   * <p>
   * Проверяет, что диагностика не срабатывает для:
   * <ul>
   *   <li>Справочников с безопасным использованием (с контролем уникальности и WHOLE_CATALOG) (строка 14 - комментарий)</li>
   *   <li>Объектов, не являющихся справочниками, планами видов характеристик или планами счетов (строка 17 - комментарий перед Документом)</li>
   *   <li>Методов, отличных от FindByCode/НайтиПоКоду (строка 20 - комментарий)</li>
   *   <li>Планов видов характеристик с безопасным использованием (с контролем уникальности и WHOLE_CATALOG) (строка 40 - комментарий)</li>
   *   <li>Планов счетов с безопасным использованием (с контролем уникальности и WHOLE_CATALOG) (строка 52 - комментарий)</li>
   * </ul>
   * <p>
   * Примечание: значения {@code WholeCharacteristicKind} и {@code WholeChartOfAccounts} из XML метаданных
   * преобразуются в {@code WHOLE_CATALOG} в enum {@code CodeSeries}, поэтому для всех типов объектов
   * проверка выполняется на {@code WHOLE_CATALOG}.
   */
  @Test
  void testEdgeCases() {
    var documentContext = createDocumentContextFromFile(
      "src/test/resources/diagnostics/UnsafeFindByCodeDiagnostic.bsl"
    );
    List<Diagnostic> diagnostics = diagnosticInstance.getDiagnostics(documentContext);
    
    var diagnosticLines = diagnostics.stream()
      .map(d -> d.getRange().getStart().getLine())
      .sorted()
      .toList();
    
    assertThat(diagnosticLines)
      .as("Диагностика не должна срабатывать для безопасных объектов, других методов и объектов не из списка проверяемых")
      .isNotEmpty()
      .doesNotContain(14, 17, 20, 40, 52);
  }


  /**
   * Создает контекст документа из файла для тестирования.
   * <p>
   * Загружает тестовый файл и инициализирует контекст сервера с метаданными
   * из директории {@code src/test/resources/metadata/UnsafeFindByCodeDiagnostic}.
   *
   * @param pathToFile путь к тестовому файлу относительно корня проекта
   * @return контекст документа для тестирования
   * @throws Exception если произошла ошибка при чтении файла или инициализации контекста
   */
  @SneakyThrows
  DocumentContext createDocumentContextFromFile(String pathToFile) {
    Path path = Absolute.path("src/test/resources/metadata/UnsafeFindByCodeDiagnostic");
    Path testFile = Paths.get(pathToFile).toAbsolutePath();

    initServerContext(path);
    return TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );
  }
}
