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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для хранения промежуточных данных completion item между его созданием
 * ({@code textDocument/completion}) и отложенным разрешением документации
 * ({@code completionItem/resolve}).
 * <p>
 * Кладётся в {@link org.eclipse.lsp4j.CompletionItem#setData(Object)} вместо
 * жадно построенной {@code documentation}, чтобы тяжёлые описания членов
 * широкого типа (Глобальный контекст, union типов) не путешествовали в каждом
 * ответе completion. По этому ключу провайдер восстанавливает член типа и
 * собирает {@code documentation} только для выбранного клиентом item.
 * <p>
 * Сериализуется клиентом в JSON и приходит обратно как {@code JsonObject}/Map —
 * поля сделаны bean-style (Lombok {@link Data}) для round-trip через Jackson.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompletionData {

  /**
   * Вид типа-владельца члена (часть идентичности {@link com.github._1c_syntax.bsl.languageserver.types.model.TypeRef}).
   */
  private TypeKind typeKind;

  /**
   * Каноническое полное имя типа-владельца члена (часть идентичности TypeRef).
   */
  private String typeQualifiedName;

  /**
   * Имя члена (метода/свойства), для которого нужно восстановить документацию.
   */
  private String memberName;

  /**
   * Тип файла-потребителя (BSL/OS) — влияет на набор членов типа.
   */
  private FileType fileType;

  /**
   * Локаль скрипта (ru/en) для отбора написаний и языка описания.
   */
  private Language scriptVariant;
}
