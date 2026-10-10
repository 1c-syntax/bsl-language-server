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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.mdo.Template;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import com.github._1c_syntax.bsl.types.MdoReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Резолв типа значения макета по полному имени макета —
 * {@code Справочник.Товары.Макет.ПечатнаяФорма}, {@code ОбщийМакет.Имя}.
 * <p>
 * Значение макета — то, что отдаёт его получение ({@code ПолучитьМакет},
 * {@code ПолучитьОбщийМакет}), и вид этого значения задан видом макета в метаданных.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class TemplateByNameResolver {

  /**
   * Виды макетов, значение которых — платформенный тип с тем же именем, что у вида
   * ({@code ТабличныйДокумент}, {@code СхемаКомпоновкиДанных}, …). Остальные виды
   * (HTML-документ, ActiveDocument, внешняя компонента) так не называются, и тип их
   * значения из вида не следует.
   */
  private static final Set<TemplateType> VALUE_NAMED_BY_KIND = EnumSet.of(
    TemplateType.SPREADSHEET_DOCUMENT,
    TemplateType.TEXT_DOCUMENT,
    TemplateType.BINARY_DATA,
    TemplateType.DATA_COMPOSITION_SCHEME,
    TemplateType.DATA_COMPOSITION_APPEARANCE_TEMPLATE,
    TemplateType.GRAPHICAL_SCHEME,
    TemplateType.GEOGRAPHICAL_SCHEMA
  );

  private final TypeRegistry typeRegistry;

  /**
   * Тип значения макета по его полному имени.
   *
   * @param documentContext документ, из которого идёт обращение — нужен для доступа
   *                        к конфигурации.
   * @param templateName    полное имя макета.
   * @return тип значения макета; empty, если такого макета в конфигурации нет либо
   *   тип значения по его виду не определяется.
   */
  public Optional<TypeRef> resolve(DocumentContext documentContext, String templateName) {
    if (templateName.isBlank()) {
      return Optional.empty();
    }
    return MdoReference.find(templateName)
      .flatMap(ref -> documentContext.getServerContext().getConfiguration().findChild(ref))
      .filter(Template.class::isInstance)
      .map(md -> ((Template) md).getTemplateType())
      .filter(VALUE_NAMED_BY_KIND::contains)
      .map(kind -> valueType(kind.fullName().getRu()));
  }

  /**
   * Платформенный тип по имени. Без синтакс-помощника части этих типов в реестре нет —
   * тогда он заводится по имени, как и прочие типы, названные платформой.
   */
  private TypeRef valueType(String name) {
    return typeRegistry.resolve(name).orElseGet(() -> typeRegistry.intern(TypeKind.PLATFORM, name));
  }
}
