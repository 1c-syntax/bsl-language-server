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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.events.ServerContextPopulatedEvent;
import com.github._1c_syntax.bsl.languageserver.types.index.SymbolTypeIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationTypesProvider;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterEachTestMethod;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Значение функции, параметр которой описан ссылкой {@code см.} на параметр метода чужого
 * модуля, не должно зависеть от того, в каком порядке разбирались документы рабочей области.
 * <p>
 * Заполнение идёт параллельно и в произвольном порядке, поэтому модуль, на который ведёт
 * ссылка, может оказаться ещё не разобранным к моменту расчёта функции. Второй возврат
 * ({@code Возврат Неопределено}) делает значение непустым и в этом случае — так что
 * неполным его выдаёт только сама ссылка.
 */
@CleanupContextBeforeClassAndAfterEachTestMethod
class SeeReferenceParseOrderTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE = Path.of("src/test/resources/metadata/seeReferenceOrder").toAbsolutePath();

  @Autowired
  private ConfigurationTypesProvider provider;

  @Autowired
  private ApplicationEventPublisher publisher;

  @Autowired
  private TypeService typeService;

  @Autowired
  private SymbolTypeIndex symbolTypeIndex;

  @Test
  void rowIsReturnedWhenTargetOfReferenceIsParsedFirst() {
    assertThat(returnTypes(List.of("Переопределяемый", "Поставщик"), "ДобавитьКоманду"))
      .containsExactlyInAnyOrder("Неопределено", "СтрокаТаблицыЗначений");
  }

  @Test
  void rowIsReturnedWhenTargetOfReferenceIsParsedLast() {
    assertThat(returnTypes(List.of("Поставщик", "Переопределяемый"), "ДобавитьКоманду"))
      .containsExactlyInAnyOrder("Неопределено", "СтрокаТаблицыЗначений");
  }

  @Test
  void rowIsReturnedWhenMethodReferencedByDescriptionIsParsedLast() {
    // Своего описания у параметра нет: тип ему даёт одноимённый параметр метода, на который
    // ссылается описание метода целиком.
    assertThat(returnTypes(List.of("Поставщик", "Переопределяемый"), "ДобавитьКомандуПоИнтерфейсу"))
      .containsExactlyInAnyOrder("Неопределено", "СтрокаТаблицыЗначений");
  }

  @Test
  void rowIsReturnedWhenReferencedMethodPointsFurtherToModuleParsedLast() {
    // Метод-интерфейс разобран, но описание его параметра само ссылается дальше — на модуль,
    // до которого очередь ещё не дошла.
    assertThat(returnTypes(List.of("Интерфейс", "Поставщик", "Переопределяемый"), "ДобавитьКомандуЧерезИнтерфейс"))
      .containsExactlyInAnyOrder("Неопределено", "СтрокаТаблицыЗначений");
  }

  @Test
  void callerSeesDeclaredValueReferringToModuleParsedLast() {
    // Значение `Результат` объявлено ссылкой на функцию модуля, до которого очередь ещё не
    // дошла, а по телу — только `Неопределено`. Вызывающая функция посчитана при разборе, и её
    // значение непустое — без пометки о неполноте проход после наполнения её не пересчитал бы.
    assertThat(returnTypes(List.of("Поставщик", "Переопределяемый"), "РезультатЧерезВызов"))
      .containsExactlyInAnyOrder("Неопределено", "Структура");
  }

  @Test
  void callerSeesDeclaredValueReferringThroughLocalFunction() {
    // Объявленное значение ссылается на функцию своего модуля, а уже её описание — на модуль,
    // до которого очередь ещё не дошла.
    assertThat(returnTypes(List.of("Поставщик", "Переопределяемый"), "РезультатЧерезЛокальнуюВызов"))
      .containsExactlyInAnyOrder("Неопределено", "Структура");
  }

  @Test
  void declaredValueRebuiltAfterPopulationDoesNotDependOnRebuildOrder() {
    // given: при наполнении интерфейс и поставщик разобраны раньше переопределяемого модуля,
    // и объявленное значение функции интерфейса посчитано без него: в поле — одно
    // `Неопределено`.
    initServerContext(FIXTURE, false);
    context.getConfiguration();
    provider.tryRegister();
    for (var module : List.of("Интерфейс", "Поставщик", "Переопределяемый")) {
      var documentContext = context.addDocument(moduleUri(module));
      context.rebuildDocument(documentContext);
      context.tryClearDocument(documentContext);
    }

    // when: пересборка после наполнения доходит до поставщика раньше, чем до интерфейса, на
    // функцию которого он ссылается.
    var rebuildOrder = new LinkedHashMap<URI, DocumentContext>();
    for (var module : List.of("Поставщик", "Интерфейс", "Переопределяемый")) {
      rebuildOrder.put(moduleUri(module), context.getDocument(moduleUri(module)));
    }
    var populated = mock(ServerContext.class);
    when(populated.getDocuments()).thenReturn(rebuildOrder);
    symbolTypeIndex.handleServerContextPopulated(new ServerContextPopulatedEvent(populated));

    // then: поле развёрнуто до конца. Возьми поставщик значение интерфейса, оставшееся с
    // наполнения, — итог решал бы порядок обхода документов.
    var method = context.getDocument(moduleUri("Поставщик")).getSymbolTree()
      .getMethodSymbol("ДанныеЧерезИнтерфейс").orElseThrow();
    var declared = symbolTypeIndex.getDeclaredReturnTypes(method);
    var structure = declared.refs().stream()
      .filter(ref -> "Структура".equals(ref.qualifiedName()))
      .findFirst()
      .orElseThrow();
    assertThat(declared.getLocalFields(structure).get("Результат").types().refs())
      .extracting(TypeRef::qualifiedName)
      .containsExactlyInAnyOrder("Неопределено", "Структура");
  }

  @Test
  void callerSeesFieldOfDeclaredValueReferringThroughParsedModule() {
    // Объявленное значение ссылается на функцию модуля, который уже разобран, а в описании
    // той функции поле ссылается дальше — на модуль, до которого очередь ещё не дошла. Сама
    // ссылка разбора не ждёт, и без спуска в описание цели вызывающая функция осталась бы с
    // одним `Неопределено` в поле — а что из двух выйдет, решал бы порядок наполнения.
    assertThat(returnTypes(List.of("Интерфейс", "Поставщик", "Переопределяемый"), "РезультатИзДанныхЧерезИнтерфейс"))
      .containsExactlyInAnyOrder("Неопределено", "Структура");
  }

  @Test
  void parsedModuleWithoutOwnMembersDoesNotAwaitParsing() {
    // given: модуль объявлен из метаданных, но его документ ещё не разобран.
    initServerContext(FIXTURE, false);
    context.getConfiguration();
    provider.tryRegister();
    var before = symbolTypeIndex.awaitsUnparsedModule("ПустойМодуль.НетМетода", FileType.BSL);

    // when: документ разобран, а экспортных методов в нём нет вовсе.
    context.rebuildDocument(context.addDocument(moduleUri("ПустойМодуль")));

    // then: ссылка в него больше не ждёт разбора — она просто никуда не ведёт. Иначе расчёт,
    // опирающийся на неё, возвращался бы в очередь на каждой волне прохода после наполнения.
    assertThat(before).isTrue();
    assertThat(symbolTypeIndex.awaitsUnparsedModule("ПустойМодуль.НетМетода", FileType.BSL)).isFalse();
  }

  /**
   * Значение функции модуля {@code Поставщик} после разбора модулей в заданном порядке
   * и прохода доразрешения — так, как рабочую область наполняет {@code populateContext}.
   */
  private List<String> returnTypes(List<String> parseOrder, String functionName) {
    initServerContext(FIXTURE, false);
    context.getConfiguration();
    provider.tryRegister();
    for (var module : parseOrder) {
      var documentContext = context.addDocument(moduleUri(module));
      context.rebuildDocument(documentContext);
      context.tryClearDocument(documentContext);
    }
    publisher.publishEvent(new ServerContextPopulatedEvent(context));

    var supplier = context.getDocument(moduleUri("Поставщик"));
    var method = supplier.getSymbolTree().getMethodSymbol(functionName).orElseThrow();
    return typeService.getReturnTypes(method).refs().stream()
      .map(TypeRef::qualifiedName)
      .toList();
  }

  private static URI moduleUri(String name) {
    return Absolute.uri(FIXTURE.resolve("CommonModules").resolve(name).resolve("Ext").resolve("Module.bsl").toUri());
  }
}
