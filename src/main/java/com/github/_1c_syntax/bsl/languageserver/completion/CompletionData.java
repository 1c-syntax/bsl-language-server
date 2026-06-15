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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.net.URI;

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
 * поля сделаны bean-style (Lombok {@code @Data}) для round-trip через Jackson.
 * <p>
 * Поддерживаются два вида ключа восстановления:
 * <ul>
 *   <li>член типа (dot-completion): заполнены {@code typeKind}/{@code typeQualifiedName}
 *       и {@code memberName}, по ним восстанавливается член типа-владельца;</li>
 *   <li>глобальная функция (no-dot completion): заполнено {@code functionName},
 *       по нему функция ищется в глобальной области видимости. Поля типа-владельца
 *       при этом не используются.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompletionData {

  /**
   * URI документа, в контексте которого item был построен.
   * <p>
   * Нужен для {@code completionItem/resolve}: запрос приходит без позиции и без
   * текстового документа, а {@code typeService}/{@code globalScopeProvider} —
   * workspace-scoped бины. По этому URI восстанавливается документ и его
   * workspace, чтобы установить workspace-контекст перед резолвом documentation.
   */
  private URI uri;

  /**
   * Вид типа-владельца члена (часть идентичности {@link com.github._1c_syntax.bsl.languageserver.types.model.TypeRef}).
   * {@code null} для варианта глобальной функции.
   */
  @Nullable
  private TypeKind typeKind;

  /**
   * Каноническое полное имя типа-владельца члена (часть идентичности TypeRef).
   * {@code null} для варианта глобальной функции.
   */
  @Nullable
  private String typeQualifiedName;

  /**
   * Имя члена (метода/свойства), для которого нужно восстановить документацию.
   * {@code null} для варианта глобальной функции.
   */
  @Nullable
  private String memberName;

  /**
   * Тип файла-потребителя (BSL/OS) — влияет на набор членов типа и набор
   * глобальных функций.
   */
  private FileType fileType;

  /**
   * Локаль скрипта (ru/en) для отбора написаний и языка описания.
   */
  private Language scriptVariant;

  /**
   * Имя глобальной функции, для которой нужно восстановить документацию.
   * Заполнено только для варианта глобальной функции; для члена типа — {@code null}.
   */
  @Nullable
  private String functionName;

  /**
   * Создаёт ключ восстановления документации члена типа (dot-completion).
   *
   * @param uri               URI документа, в контексте которого построен item.
   * @param typeKind          вид типа-владельца члена.
   * @param typeQualifiedName каноническое полное имя типа-владельца.
   * @param memberName        имя члена (метода/свойства).
   * @param fileType          тип файла-потребителя (BSL/OS).
   * @param scriptVariant     локаль скрипта (ru/en).
   * @return ключ восстановления члена типа.
   */
  public static CompletionData forMember(URI uri, TypeKind typeKind, String typeQualifiedName, String memberName,
                                         FileType fileType, Language scriptVariant) {
    return new CompletionData(uri, typeKind, typeQualifiedName, memberName, fileType, scriptVariant, null);
  }

  /**
   * Создаёт ключ восстановления документации глобальной функции (no-dot completion).
   *
   * @param uri           URI документа, в контексте которого построен item.
   * @param functionName  имя глобальной функции.
   * @param fileType      тип файла-потребителя (BSL/OS).
   * @param scriptVariant локаль скрипта (ru/en).
   * @return ключ восстановления глобальной функции.
   */
  public static CompletionData forFunction(URI uri, String functionName, FileType fileType, Language scriptVariant) {
    return new CompletionData(uri, null, null, null, fileType, scriptVariant, functionName);
  }
}
