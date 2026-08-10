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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.ModuleReference;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * Общий модуль, полученный по имени: {@code ОбщегоНазначения.ОбщийМодуль("ИмяМодуля")}
 * либо, внутри самой БСП, локальный {@code ОбщийМодуль("ИмяМодуля")}.
 * <p>
 * Объявленный тип возврата такого метода обобщённый — {@code ОбщийМодуль}, а у части
 * вариантов ещё и {@code МодульМенеджераОбъекта}, — то есть «какой-то модуль», без единого
 * прикладного метода. Пока имя модуля стоит в вызове строковым литералом, оно известно
 * статически, и тип выражения — тип именно этого модуля.
 * <p>
 * Какие вызовы считать таким получателем, задаёт настройка
 * {@code references.commonModuleAccessors} — та же, по которой индекс ссылок разбирает
 * {@code ОбщийМодуль("Имя").Метод(...)}, чтобы переход по F12 вёл в конкретный модуль.
 * Здесь она читается на каждом обращении: настройка меняется на лету, а разбор списка
 * стоит дешевле, чем инвалидация кэша по событию её изменения.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class CommonModuleByNameInference {

  private final LanguageServerConfiguration configuration;
  private final TypeRegistry typeRegistry;

  /**
   * Тип общего модуля, названного строковым литералом в вызове у получателя:
   * {@code ОбщегоНазначения.ОбщийМодуль("ИмяМодуля")}.
   *
   * @param receiverTypes типы получателя вызова.
   * @param memberName    имя вызванного метода.
   * @param call          узел вызова.
   * @param fileType      вид файла, в котором идёт разрешение имени модуля.
   * @return тип названного модуля; {@code null}, если вызов не является получателем
   *     общего модуля, имя задано не литералом либо такого модуля в рабочей области нет.
   */
  @Nullable
  public TypeSet refinedCallTypes(TypeSet receiverTypes, String memberName,
                                  MethodCallNode call, FileType fileType) {
    var accessors = ModuleReference.parseAccessors(
      configuration.getReferencesOptions().getCommonModuleAccessors());
    var member = memberName.toLowerCase(Locale.ENGLISH);
    var matches = receiverTypes.refs().stream()
      .map(ref -> accessors.moduleMethodPairs().get(ref.qualifiedName().toLowerCase(Locale.ENGLISH)))
      .filter(Objects::nonNull)
      .anyMatch(methods -> methods.contains(member));
    return matches ? namedModuleType(call, fileType) : null;
  }

  /**
   * Тип общего модуля, названного строковым литералом в вызове без получателя:
   * {@code ОбщийМодуль("ИмяМодуля")} внутри самой БСП.
   *
   * @param call     узел вызова.
   * @param fileType вид файла, в котором идёт разрешение имени модуля.
   * @return тип названного модуля либо {@code null}.
   */
  @Nullable
  public TypeSet localCallTypes(MethodCallNode call, FileType fileType) {
    var accessors = ModuleReference.parseAccessors(
      configuration.getReferencesOptions().getCommonModuleAccessors());
    var callName = call.getName().getText().toLowerCase(Locale.ENGLISH);
    return accessors.localMethods().contains(callName) ? namedModuleType(call, fileType) : null;
  }

  /**
   * Тип модуля, названного первым аргументом вызова.
   *
   * @param call     узел вызова.
   * @param fileType вид файла, в котором идёт разрешение имени модуля.
   * @return тип модуля; {@code null}, если имя задано не литералом либо такого модуля
   *     в рабочей области нет.
   */
  @Nullable
  private TypeSet namedModuleType(MethodCallNode call, FileType fileType) {
    var arguments = call.arguments();
    if (arguments.isEmpty()) {
      return null;
    }
    var moduleName = OpenDataObjectInference.stringLiteralOf(arguments.get(0));
    if (moduleName == null || moduleName.isBlank()) {
      return null;
    }
    return typeRegistry.resolve(moduleName.trim(), fileType).map(TypeSet::of).orElse(null);
  }
}
