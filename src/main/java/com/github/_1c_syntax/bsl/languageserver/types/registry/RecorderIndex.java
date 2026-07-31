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

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.MD;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Индекс «регистр → документы, пишущие в него движения».
 * <p>
 * Обратной связи в метаданных нет: состав регистраторов известен только со стороны
 * документа ({@code <RegisterRecords>} в его XML), поэтому индекс собирается обходом
 * документов конфигурации. Из него типизируется всё, что платформа объявляет через
 * {@code ДокументСсылка.<Имя документа>}: стандартный реквизит {@code Регистратор}
 * записи регистра и параметр {@code ПараметрОтборПоРегистратору} обычной формы списка.
 * <p>
 * Индекс собирается целиком и публикуется одной ссылкой на неизменяемую карту, а не
 * наполняется на месте. Так читателю не нужно ни знать про поток регистрации, ни ловить
 * полусобранное состояние в момент пересборки: он либо видит прежнюю карту, либо новую.
 */
@Component
@WorkspaceScope
public class RecorderIndex {

  /**
   * mdoRef регистра → имена документов-регистраторов в порядке обхода конфигурации.
   * <p>
   * Снимок целиком за атомарной ссылкой: сама карта неизменяема, меняется только то,
   * на какую из них поле указывает.
   */
  private final AtomicReference<Map<String, List<String>>> recordersByRegister =
    new AtomicReference<>(Map.of());

  /**
   * Перестраивает индекс по объектам конфигурации. Идемпотентен: повторный вызов
   * заменяет содержимое, а не дополняет его.
   *
   * @param children объекты метаданных конфигурации.
   */
  public void index(Iterable<MD> children) {
    Map<String, Set<String>> collected = new LinkedHashMap<>();
    for (var md : children) {
      if (md instanceof Document document) {
        indexDocument(document, collected);
      }
    }
    Map<String, List<String>> built = LinkedHashMap.newLinkedHashMap(collected.size());
    collected.forEach((register, documents) -> built.put(register, List.copyOf(documents)));
    recordersByRegister.set(Collections.unmodifiableMap(built));
  }

  /**
   * Документы-регистраторы регистра.
   *
   * @param registerMdoRef mdoRef регистра ({@code РегистрНакопления.ТоварыНаСкладах}).
   * @return имена документов; пусто — регистр независимый либо движений в него никто не пишет.
   */
  public List<String> recordersOf(String registerMdoRef) {
    return recordersByRegister.get().getOrDefault(registerMdoRef, List.of());
  }

  private static void indexDocument(Document document, Map<String, Set<String>> sink) {
    var documentName = shortName(document.getMdoReference().getMdoRefRu());
    if (documentName.isBlank()) {
      return;
    }
    for (var register : document.getRegisterRecords()) {
      sink.computeIfAbsent(register.getMdoRefRu(), key -> new LinkedHashSet<>())
        .add(documentName);
    }
  }

  /** {@code Документ.Документ1} → {@code Документ1}. */
  private static String shortName(String mdoRef) {
    var dot = mdoRef.lastIndexOf('.');
    return dot < 0 ? mdoRef : mdoRef.substring(dot + 1);
  }
}
