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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Члены таблицы формы, тип которых зависит не от самой таблицы, а от её данных:
 * строка данных ({@code ТекущиеДанные}, {@code ДанныеСтроки}) и идентификатор строки
 * ({@code ТекущаяСтрока}, {@code ТекущийРодитель}, {@code ВыделенныеСтроки}).
 * <p>
 * Собраны вместе, потому что нужны с двух сторон и в одинаковом виде: типу вида данных
 * ({@code ТаблицаФормы.ДинамическийСписок} — там типы известны из описания расширения)
 * и типу конкретной таблицы (там они известны из её данных: колонки строки, ключевое
 * поле основной таблицы списка). Платформа объявляет все пять членов как
 * {@code Произвольный}, поэтому без подстановки цепочка от них обрывается.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
class TableDataMembers {

  /** Свойства таблицы, тип которых задаёт идентификатор строки. */
  private static final BilingualString CURRENT_ROW = BilingualString.of("ТекущаяСтрока", "CurrentRow");
  private static final BilingualString CURRENT_PARENT =
    BilingualString.of("ТекущийРодитель", "CurrentParent");
  private static final BilingualString SELECTED_ROWS =
    BilingualString.of("ВыделенныеСтроки", "SelectedRows");

  /** Метод, отдающий строку данных по её идентификатору. */
  private static final BilingualString ROW_DATA_METHOD = BilingualString.of("ДанныеСтроки", "RowData");

  private static final String ARRAY_RU = "Массив";
  private static final String ARRAY_EN = "Array";

  private final TypeRegistry typeRegistry;
  private final FormDataTypesRegistrar formDataTypes;

  /**
   * {@code ТекущиеДанные} и {@code ДанныеСтроки} — оба отдают строку данных таблицы.
   * Описание расширения называет это «структурой, заполненной копией данных», но
   * проверка на платформе даёт {@code ТипЗнч(…ТекущиеДанные) =
   * ДанныеФормыЭлементКоллекции}: «структура» там — про устройство значения, а не про тип.
   *
   * @param rowRef тип строки данных.
   * @return свойство и метод.
   */
  List<MemberDescriptor> currentData(TypeRef rowRef) {
    return List.of(
      FormPlatformTypes.platformProperty(
        BilingualString.of(FormPlatformTypes.CURRENT_DATA_RU, FormPlatformTypes.CURRENT_DATA_EN),
        rowRef, BilingualString.EMPTY),
      MemberDescriptor.method(ROW_DATA_METHOD.ru(), "",
          List.of(new SignatureDescriptor(
            List.of(new ParameterDescriptor(CURRENT_ROW, TypeSet.EMPTY, false,
              BilingualString.of("Идентификатор строки.", "Row identifier."), "")),
            TypeSet.of(rowRef), BilingualString.EMPTY)))
        .withBilingualName(ROW_DATA_METHOD));
  }

  /**
   * {@code ТекущаяСтрока}, {@code ТекущийРодитель} и {@code ВыделенныеСтроки} — все три
   * адресуют строку одним и тем же идентификатором, третий коллекцией таких же.
   *
   * @param rowIdRef тип идентификатора строки.
   * @return свойства; без {@code ВыделенныеСтроки}, если самого {@code Массив} в реестре нет.
   */
  List<MemberDescriptor> rowIdentifier(TypeRef rowIdRef) {
    var selectedRowsRef = registerIdentifierArray(rowIdRef);
    var members = List.of(
      FormPlatformTypes.platformProperty(CURRENT_ROW, rowIdRef, BilingualString.EMPTY),
      FormPlatformTypes.platformProperty(CURRENT_PARENT, rowIdRef, BilingualString.EMPTY));
    return selectedRowsRef == null
      ? members
      : FormPlatformTypes.concat(members,
      List.of(FormPlatformTypes.platformProperty(SELECTED_ROWS, selectedRowsRef, BilingualString.EMPTY)));
  }

  /**
   * Массив идентификаторов строк — то, что лежит в {@code ВыделенныеСтроки}. Платформа
   * объявляет там обычный {@code Массив}, а описание расширения говорит, чем он заполнен;
   * без специализации обход {@code Для Каждого Идентификатор Из ВыделенныеСтроки} терял
   * тип элемента.
   *
   * @param elementRef тип элемента массива.
   * @return специализация массива; {@code null}, если самого {@code Массив} в реестре нет.
   */
  private @Nullable TypeRef registerIdentifierArray(TypeRef elementRef) {
    var arrayBase = typeRegistry.resolve(ARRAY_RU).orElse(null);
    if (arrayBase == null) {
      return null;
    }
    var arrayRef = formDataTypes.registerFormDataMirror(ARRAY_RU, ARRAY_EN, elementRef.qualifiedName(), "", arrayBase);
    typeRegistry.registerDefaultElementTypes(arrayRef, List.of(elementRef));
    typeRegistry.inheritCollectionTraits(arrayRef, arrayBase, FileType.BSL);
    return arrayRef;
  }
}
