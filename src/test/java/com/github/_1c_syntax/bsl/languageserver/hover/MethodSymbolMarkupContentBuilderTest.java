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
package com.github._1c_syntax.bsl.languageserver.hover;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.types.ModuleType;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
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
    serverContext.setConfigurationRoot(Path.of(PATH_TO_METADATA));
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
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Функция ИмяФункции(Знач П1: Дата | Число, П2: Число = -10, П2_5, Знач П3: Структура = "", П4: Массив | СписокЗначений, ПДата: См. ОбщийМодуль.СуперМетод() = '20100101', ПДатаВремя = '20110101121212', П6 = Ложь, П7 = Истина, П8 = Неопределено, П9 = NULL) Экспорт: Строка | Структура
      ```

      """);
    assertThat(blocks.get(1)).matches("\\[file://.*/src/test/resources/hover/methodSymbolMarkupContentBuilder.bsl]\\(.*src/test/resources/hover/methodSymbolMarkupContentBuilder.bsl#\\d+\\)\n\n");
    assertThat(blocks.get(2)).isEqualTo("Описание функции.\nМногострочное.\n\n");
    assertThat(blocks.get(3)).isEqualTo("""
      **Параметры:**

      * **П1**: `Дата` | `Число` - Описание даты/числа \s
      * **П2**: `Число` - Описание числа \s
      * **П2_5**:  \s
      * **П3**: `Структура` - Описание строки<br>&nbsp;&nbsp;продолжается на следующей строкке: \s
        * **Поле1**: `Число` - Описание поле1 \s
        * **Поле2**: `Строка` - Описание поле2 \s
        * **Поле3**: `Структура` : \s
          * **Поле31**: `строка` \s
          * **Поле32**: `Структура` : \s
            * **Поле321**: `Число` - Описание поля 321 \s
          * **Поле33**: `строка` \s
        * **Поле4**: `строка` \s
      * **П4**:  \s
      &nbsp;&nbsp;`Массив` - Описание Массива \s
      &nbsp;&nbsp;`СписокЗначений` - Описание списка \s
      * **ПДата**: [См. ОбщийМодуль.СуперМетод()](ОбщийМодуль.СуперМетод()) \s
      * **ПДатаВремя**:  \s
      * **П6**:  \s
      * **П7**:  \s
      * **П8**:  \s
      * **П9**:\s

      """);
    assertThat(blocks.get(4)).isEqualTo("""
      **Возвращаемое значение:**

      &nbsp;&nbsp;`Строка` - вернувшаяся строка \s
      &nbsp;&nbsp;`Структура` - Описание строки<br>&nbsp;&nbsp;продолжается на следующей строкке: \s
        * **Поле1**: `Число` - Описание поле1 \s
        * **Поле2**: `Строка` - Описание поле2 \s
        * **Поле3**: `Структура` : \s
          * **Поле31**: `строка` \s
          * **Поле32**: `Структура`

      """);
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
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Процедура ТестЭкспортная() Экспорт
      ```

      """);
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
    assertThat(blocks.get(0)).isEqualTo("""
      ```bsl
      Процедура УстаревшаяПроцедура() Экспорт
      ```

      """);
    assertThat(blocks.get(1)).matches("\\[CommonModule.ПервыйОбщийМодуль]\\(.*CommonModules/.*/Ext/Module.bsl#\\d+\\)\n\n");
    assertThat(blocks.get(2)).isEqualTo("Процедура - Устаревшая процедура\n\n");
  }

}