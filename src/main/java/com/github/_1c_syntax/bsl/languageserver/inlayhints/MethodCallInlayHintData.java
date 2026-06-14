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
package com.github._1c_syntax.bsl.languageserver.inlayhints;

import com.github._1c_syntax.bsl.languageserver.databind.URITypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.net.URI;

/**
 * Данные хинта имени параметра вызова source-defined метода для отложенного
 * построения ссылки ({@link org.eclipse.lsp4j.InlayHintLabelPart#setLocation})
 * через {@code inlayHint/resolve}.
 * <p>
 * Кладётся в {@code InlayHint.data} при жадном расчёте хинтов, если клиент
 * объявил {@code inlayHint.resolveSupport} для свойства {@code label.location};
 * восстанавливается JSON round-trip'ом на резолве. Хранит координаты объявления
 * параметра в исходнике вызываемого метода: {@code targetUri} — URI документа,
 * где объявлен метод, а четвёрка {@code startLine}/{@code startCharacter}/
 * {@code endLine}/{@code endCharacter} — диапазон объявления параметра. На
 * резолве из них собирается {@link org.eclipse.lsp4j.Location} и проставляется
 * единственной части метки хинта.
 */
@Value
@NonFinal
public class MethodCallInlayHintData implements InlayHintData {
  @JsonAdapter(URITypeAdapter.class)
  URI uri;
  String id;
  String targetUri;
  int startLine;
  int startCharacter;
  int endLine;
  int endCharacter;
}
