/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2023
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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;
import java.util.Arrays;

import static com.github._1c_syntax.bsl.languageserver.util.TestUtils.PATH_TO_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@CleanupContextBeforeClassAndAfterClass
class MethodSymbolMarkupContentBuilderTest {

  @Autowired
  private MethodSymbolMarkupContentBuilder markupContentBuilder;

  @Autowired
  private ServerContext serverContext;

  private static final String PATH_TO_FILE = "./src/test/resources/hover/methodSymbolMarkupContentBuilder.bsl";

  @PostConstruct
  void prepareServerContext() {
    serverContext.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    serverContext.populateContext();
  }

  @Test
  void testContentFromDirectFile() {
    // given
    var documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ИмяФункции").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(methodSymbol).getValue();

    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(5);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Функция ИмяФункции(Знач П1: Дата | Число, П2: Число = -10, П2_5, Знач П3: Структура = \"\", " +
      "П4: Массив | СписокЗначений, ПДата: См. ОбщийМодуль.СуперМетод() = '20100101', ПДатаВремя = '20110101121212', " +
      "П6 = Ложь, П7 = Истина, П8 = Неопределено, П9 = NULL) Экспорт: Строка | Структура\n```\n\n");
    assertThat(blocks.get(1)).matches("\\[file://.*/src/test/resources/hover/methodSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/methodSymbolMarkupContentBuilder.bsl#\\d+\\)\n\n");
    assertThat(blocks.get(2)).isEqualTo("Описание функции.\nМногострочное.\n\n");
    assertThat(blocks.get(3)).isEqualTo("**Параметры:**\n\n" +
      "* **П1**: `Дата` | `Число` - Описание даты/числа  \n" +
      "* **П2**: `Число` - Описание числа  \n" +
      "* **П2_5**:   \n" +
      "* **П3**: `Структура` - Описание строки<br>&nbsp;&nbsp;продолжается на следующей строкке:  \n" +
      "  * **Поле1**: `Число` - Описание поле1  \n" +
      "  * **Поле2**: `Строка` - Описание поле2  \n" +
      "  * **Поле3**: `Структура` :  \n" +
      "    * **Поле31**: `строка`  \n" +
      "    * **Поле32**: `Структура` :  \n" +
      "      * **Поле321**: `Число` - Описание поля 321  \n" +
      "    * **Поле33**: `строка`  \n" +
      "  * **Поле4**: `строка`  \n" +
      "* **П4**:   \n" +
      "&nbsp;&nbsp;`Массив` - Описание Массива  \n" +
      "&nbsp;&nbsp;`СписокЗначений` - Описание списка  \n" +
      "* **ПДата**: [См. ОбщийМодуль.СуперМетод()](ОбщийМодуль.СуперМетод())  \n" +
      "* **ПДатаВремя**:   \n" +
      "* **П6**:   \n" +
      "* **П7**:   \n" +
      "* **П8**:   \n" +
      "* **П9**: \n" +
      "\n");
    assertThat(blocks.get(4)).isEqualTo("**Возвращаемое значение:**\n\n" +
      "&nbsp;&nbsp;`Строка` - вернувшаяся строка  \n" +
      "&nbsp;&nbsp;`Структура` - Описание строки<br>&nbsp;&nbsp;продолжается на следующей строкке:  \n" +
      "  * **Поле1**: `Число` - Описание поле1  \n" +
      "  * **Поле2**: `Строка` - Описание поле2  \n" +
      "  * **Поле3**: `Структура` :  \n" +
      "    * **Поле31**: `строка`  \n" +
      "    * **Поле32**: `Структура`\n\n");
  }

  @Test
  void testContentFromManagerModule() {

    // given
    var documentContext = serverContext.getDocument("Catalog.Справочник1", ModuleType.ManagerModule).orElseThrow();
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("ТестЭкспортная").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(methodSymbol).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура ТестЭкспортная() Экспорт\n```\n\n");
    assertThat(blocks.get(1)).matches("\\[Catalog.Справочник1]\\(.*Catalogs/.*/Ext/ManagerModule.bsl#\\d+\\)\n\n");
  }

  @Test
  void testMethodsFromCommonModule() {
    // given
    var documentContext = serverContext.getDocument("CommonModule.ПервыйОбщийМодуль", ModuleType.CommonModule).orElseThrow();
    var methodSymbol = documentContext.getSymbolTree().getMethodSymbol("УстаревшаяПроцедура").orElseThrow();

    // when
    var content = markupContentBuilder.getContent(methodSymbol).getValue();

    // then
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура УстаревшаяПроцедура() Экспорт\n```\n\n");
    assertThat(blocks.get(1)).matches("\\[CommonModule.ПервыйОбщийМодуль]\\(.*CommonModules/.*/Ext/Module.bsl#\\d+\\)\n\n");
    assertThat(blocks.get(2)).isEqualTo("Процедура - Устаревшая процедура\n\n");
  }

}