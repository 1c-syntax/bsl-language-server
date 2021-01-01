/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2020
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HoverProviderTest {

  private static final String PATH_TO_METADATA = "src/test/resources/metadata";
  private static final String PATH_TO_FILE = "./src/test/resources/providers/hover.bsl";

  @Autowired
  private HoverProvider hoverProvider;

  @Autowired
  protected ServerContext context;

  @Test
  void getEmptyHover() {
    HoverParams params = new HoverParams();
    params.setPosition(new Position(0, 0));

    DocumentContext documentContext = TestUtils.getDocumentContextFromFile("./src/test/resources/providers/hover.bsl");
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isNotPresent();
  }

  @Test
  void testLocalMethods() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(34, 0));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(5);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Функция ИмяФункции(Знач П1: Дата | Число, П2: Число = -10, Знач П3: Структура = \"\", " +
      "П4: Массив | СписокЗначений, ПДата: См. ОбщийМодуль.СуперМетод() = '20100101', ПДатаВремя = '20110101121212', " +
      "П6 = Ложь, П7 = Истина, П8 = Неопределено, П9 = NULL) Экспорт: Строка | Структура\n```\n\n");
    assertThat(blocks.get(1)).matches("Метод из file://.*/src/test/resources/providers/hover.bsl\n\n");
    assertThat(blocks.get(2)).isEqualTo("Описание функции.\nМногострочное.\n\n");
    assertThat(blocks.get(3)).isEqualTo("**Параметры:**\n\n" +
      "* **П1**: `Дата` | `Число` - Описание даты/числа  \n" +
      "* **П2**: `Число` - Описание числа  \n" +
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
      "* **ПДата**: [См. ОбщийМодуль.СуперМетод()](ОбщийМодуль.СуперМетод())\n" +
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
  void testHoverOnMethodDefinitionOfLocalModule() {
    // given
    DocumentContext documentContext = TestUtils.getDocumentContextFromFile(PATH_TO_FILE);

    HoverParams params = new HoverParams();
    params.setPosition(new Position(30, 13));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));
    
    assertThat(blocks).hasSize(5);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Функция ИмяФункции(Знач П1: Дата | Число, П2: Число = -10, Знач П3: Структура = \"\", " +
      "П4: Массив | СписокЗначений, ПДата: См. ОбщийМодуль.СуперМетод() = '20100101', ПДатаВремя = '20110101121212', " +
      "П6 = Ложь, П7 = Истина, П8 = Неопределено, П9 = NULL) Экспорт: Строка | Структура\n```\n\n");
    assertThat(blocks.get(1)).matches("Метод из file://.*/src/test/resources/providers/hover.bsl\n\n");
    assertThat(blocks.get(2)).isEqualTo("Описание функции.\nМногострочное.\n\n");
    assertThat(blocks.get(3)).isEqualTo("**Параметры:**\n\n" +
      "* **П1**: `Дата` | `Число` - Описание даты/числа  \n" +
      "* **П2**: `Число` - Описание числа  \n" +
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
      "* **ПДата**: [См. ОбщийМодуль.СуперМетод()](ОбщийМодуль.СуперМетод())\n" +
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
  void testMethodsFromManagerModule() {

    DocumentContext documentContext = getTestDocumentContext();

    HoverParams params = new HoverParams();
    params.setPosition(new Position(36, 25));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура ТестЭкспортная() Экспорт\n```\n\n");
    assertThat(blocks.get(1)).isEqualTo("Метод из Catalog.Справочник1\n\n");
  }

  @Test
  void testMethodsFromCommonModule() {
    DocumentContext documentContext = getTestDocumentContext();

    HoverParams params = new HoverParams();
    params.setPosition(new Position(38, 25));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(3);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура УстаревшаяПроцедура() Экспорт\n```\n\n");
    assertThat(blocks.get(1)).isEqualTo("Метод из CommonModule.ПервыйОбщийМодуль\n\n");
    assertThat(blocks.get(2)).isEqualTo("Процедура - Устаревшая процедура\n\n");
  }

  @Test
  void testMethodsFromCommonModuleNonPublic() {
    DocumentContext documentContext = getTestDocumentContext();

    HoverParams params = new HoverParams();
    params.setPosition(new Position(40, 25));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    // TODO а должен ли ховер ее видеть???
    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура РегистрацияИзмененийПередУдалением(Источник, Отказ)\n```\n\n");
    assertThat(blocks.get(1)).isEqualTo("Метод из CommonModule.ПервыйОбщийМодуль\n\n");
  }

  @Test
  @Disabled
  void testMethodsFromGlobalContext() {
    DocumentContext documentContext = getTestDocumentContext();

    HoverParams params = new HoverParams();
    params.setPosition(new Position(42, 10));

    // when
    Optional<Hover> optionalHover = hoverProvider.getHover(documentContext, params);

    assertThat(optionalHover).isPresent();

    var hover = optionalHover.get();
    var content = hover.getContents().getRight().getValue();
    assertThat(content).isNotEmpty();

    var blocks = Arrays.asList(content.split("---\n?"));

    assertThat(blocks).hasSize(2);
    assertThat(blocks.get(0)).isEqualTo("```bsl\n" +
      "Процедура ПроцедураМодуляПриложения() Экспорт\n```\n\n");
    assertThat(blocks.get(1)).isEqualTo("Метод из ManagedApplicationModule\n\n");
    assertThat(blocks.get(2)).isEqualTo("Доступный на клиенте метод\n\n");
  }

  @SneakyThrows
  private DocumentContext getTestDocumentContext() {
    context.setConfigurationRoot(Paths.get(PATH_TO_METADATA));
    context.populateContext();

    Path testFile = Paths.get(PATH_TO_FILE);
    return TestUtils.getDocumentContext(
      testFile.toUri(),
      FileUtils.readFileToString(testFile.toFile(), StandardCharsets.UTF_8),
      context
    );
  }
}
