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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.util.CleanupContextBeforeClassAndAfterClass;
import com.github._1c_syntax.utils.Absolute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Модули заимствованного документа из основной конфигурации и из расширения строятся, не
 * дожидаясь друг друга.
 * <p>
 * Модуль объекта документа и модуль объекта расширения, заимствующего документ, — источники
 * членов одного типа {@code ДокументОбъект.Документ1}; так же устроены модули менеджера. Строя
 * или проверяя дерево символов под блокировкой документа, модуль спрашивает члены своего типа —
 * какие его методы обработчики событий, какие голые присваивания реквизиты, — то есть читает
 * и дерево второго модуля, а тип его экспортной переменной со ссылкой «см.» ищет по функции из
 * того же дерева. Если второй модуль в это же время перестраивается, а его построение так же
 * читает дерево первого, ожидание замкнётся: так зависал {@code analyze}.
 */
@CleanupContextBeforeClassAndAfterClass
class BorrowedDocumentModulesLockTest extends AbstractServerContextAwareTest {

  private static final Path FIXTURE =
    Path.of("src/test/resources/metadata/borrowedDocumentModules").toAbsolutePath();
  private static final String MAIN_OBJECT_MODULE = "cf/Documents/Документ1/Ext/ObjectModule.bsl";
  private static final String EXTENSION_OBJECT_MODULE = "cfe/Documents/Документ1/Ext/ObjectModule.bsl";
  private static final String MAIN_MANAGER_MODULE = "cf/Documents/Документ1/Ext/ManagerModule.bsl";
  private static final String EXTENSION_MANAGER_MODULE = "cfe/Documents/Документ1/Ext/ManagerModule.bsl";

  @Autowired
  private TypeRegistry typeRegistry;

  @Autowired
  @Qualifier("diagnosticComputerExecutor")
  private ExecutorService executor;

  @Test
  void objectModuleRereadDoesNotWaitForExtensionModule() throws Exception {
    // given: блокировку модуля расширения держит тестовый поток, как держал бы её поток,
    // перестраивающий этот модуль. Памятка членов типа сброшена: иначе члены модулей не
    // перечитываются.
    initServerContext(FIXTURE);
    var main = document(MAIN_OBJECT_MODULE);
    var extension = document(EXTENSION_OBJECT_MODULE);
    var objectType = typeRegistry.resolve("ДокументОбъект.Документ1", FileType.BSL).orElseThrow();
    typeRegistry.invalidateMembers(objectType);
    var extensionLock = computeLock(extension);
    extensionLock.lock();
    try {
      // when: модуль основной конфигурации перечитан с тем же текстом, как во втором проходе
      // analyze; вставшее перечитывание обрывается таймаутом ожидания, а не вешает весь прогон.
      var content = Files.readString(FIXTURE.resolve(MAIN_OBJECT_MODULE));
      executor.submit(() -> context.rebuildDocument(main, content, 1)).get(30, TimeUnit.SECONDS);

      // then: в членах типа, собранных на другом потоке, пока модуль расширения занят, есть
      // экспортный метод этого модуля и его экспортная переменная с типом по ссылке «см.», оба —
      // из прежнего дерева модуля.
      assertThat(main.getContent()).isEqualTo(content);
      var members = executor.submit(() -> typeRegistry.getMembers(objectType, FileType.BSL))
        .get(30, TimeUnit.SECONDS);
      assertThat(members).extracting(MemberDescriptor::name).contains("Расш1_Заполнить");
      assertThat(members)
        .filteredOn(member -> member.name().equals("Расш1_Кэш"))
        .extracting(member -> member.returnType().qualifiedName())
        .containsExactly("Структура");

      // when: перестроение модуля расширения закончилось, экспортный метод в нём переименован.
      var changed = Files.readString(FIXTURE.resolve(EXTENSION_OBJECT_MODULE))
        .replace("Расш1_Заполнить", "Расш1_Очистить");
      context.rebuildDocument(extension, changed, 1);
    } finally {
      extensionLock.unlock();
    }

    // then: члены типа собраны по новому дереву модуля расширения, прежнее в памятке не осталось.
    assertThat(typeRegistry.getMembers(objectType, FileType.BSL))
      .extracting(MemberDescriptor::name)
      .contains("Расш1_Очистить")
      .doesNotContain("Расш1_Заполнить");
  }

  @Test
  void extensionModuleEditDoesNotWaitForMainModule() throws Exception {
    // given: блокировку модуля основной конфигурации держит тестовый поток.
    initServerContext(FIXTURE);
    var main = document(MAIN_OBJECT_MODULE);
    var extension = document(EXTENSION_OBJECT_MODULE);
    var objectType = typeRegistry.resolve("ДокументОбъект.Документ1", FileType.BSL).orElseThrow();
    typeRegistry.invalidateMembers(objectType);
    var mainLock = computeLock(main);
    mainLock.lock();
    try {
      // when: в модуль расширения дописали процедуру, которая присваивает экспортной переменной
      // модуля основной конфигурации, — дерево строится заново.
      var content = Files.readString(FIXTURE.resolve(EXTENSION_OBJECT_MODULE))
        + "\nПроцедура Расш1_Сбросить()\n\tКэш = Неопределено;\nКонецПроцедуры\n";
      executor.submit(() -> context.rebuildDocument(extension, content, 1)).get(30, TimeUnit.SECONDS);

      // then: дерево построено по новому тексту, а присваивание признано реквизитом типа — по
      // прежнему дереву модуля основной конфигурации.
      var tree = extension.getSymbolTree();
      assertThat(tree.getMethodSymbol("Расш1_Сбросить")).isPresent();
      assertThat(tree.getBasis().bareSelfProperties()).contains("Кэш");
    } finally {
      mainLock.unlock();
    }
  }

  @Test
  void managerModuleEditDoesNotWaitForExtensionModule() throws Exception {
    // given: модули менеджера — источники членов типа ДокументМенеджер.Документ1, блокировку
    // модуля расширения держит тестовый поток.
    initServerContext(FIXTURE);
    var main = document(MAIN_MANAGER_MODULE);
    var extension = document(EXTENSION_MANAGER_MODULE);
    var managerType = typeRegistry.resolve("ДокументМенеджер.Документ1", FileType.BSL).orElseThrow();
    typeRegistry.invalidateMembers(managerType);
    var extensionLock = computeLock(extension);
    extensionLock.lock();
    try {
      // when: модуль основной конфигурации правят — дерево строится заново.
      var content = Files.readString(FIXTURE.resolve(MAIN_MANAGER_MODULE)) + "\n";
      executor.submit(() -> context.rebuildDocument(main, content, 1)).get(30, TimeUnit.SECONDS);

      // then: дерево построено по новому тексту, а в членах типа, собранных на другом потоке,
      // пока модуль расширения занят, есть его экспортная функция.
      assertThat(main.getContent()).isEqualTo(content);
      var members = executor.submit(() -> typeRegistry.getMembers(managerType, FileType.BSL))
        .get(30, TimeUnit.SECONDS);
      assertThat(members).extracting(MemberDescriptor::name).contains("Расш1_Найти");
    } finally {
      extensionLock.unlock();
    }
  }

  private DocumentContext document(String path) {
    var documentContext = context.getDocument(Absolute.uri(FIXTURE.resolve(path).toUri()));
    assertThat(documentContext).as("документ %s в рабочей папке", path).isNotNull();
    return documentContext;
  }

  private static ReentrantLock computeLock(DocumentContext documentContext) {
    var lock = (ReentrantLock) ReflectionTestUtils.getField(documentContext, "computeLock");
    assertThat(lock).isNotNull();
    return lock;
  }
}
