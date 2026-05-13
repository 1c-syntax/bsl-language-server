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
package com.github._1c_syntax.bsl.languageserver.types.model;

/**
 * Член типа (метод или свойство).
 * <p>
 * Минимальный набор метаданных, необходимый для отображения в hover/completion.
 * Расширение (параметры метода, тип возвращаемого значения, документация)
 * планируется по мере подключения внешних provider'ов синтакс-помощника.
 *
 * @param name        имя члена в каноническом написании (как пишется в коде)
 * @param kind        метод или свойство
 * @param description краткое описание (может быть пустым)
 * @param returnType  тип возвращаемого значения / тип свойства; {@link TypeRef#UNKNOWN} если неизвестен
 */
public record MemberDescriptor(String name, MemberKind kind, String description, TypeRef returnType) {

  public static MemberDescriptor method(String name) {
    return new MemberDescriptor(name, MemberKind.METHOD, "", TypeRef.UNKNOWN);
  }

  public static MemberDescriptor property(String name) {
    return new MemberDescriptor(name, MemberKind.PROPERTY, "", TypeRef.UNKNOWN);
  }
}
