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
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Регистрирует пользовательские типы (UserType) при появлении/изменении
 * документов.
 * <p>
 * На первой итерации поддерживает базовый случай: OneScript-модуль
 * ({@link FileType#OS}) считается пользовательским типом с qualifiedName,
 * равным имени файла без расширения. Это поведение совпадает с тем, что
 * раньше делал {@code KnownTypes}, но ограничено только {@code .os}-файлами
 * и не «загрязняет» реестр BSL-модулями (для которых тип = расширение
 * платформенного, см. {@code ConfigurationModuleMembersProvider} — будущая работа).
 */
@Component
@RequiredArgsConstructor
public class UserTypesProvider {

  private final TypeRegistry typeRegistry;

  /** Для каждого URI помним qualifiedName зарегистрированного типа, чтобы корректно очищать при изменении. */
  private final Map<URI, String> registeredByUri = new ConcurrentHashMap<>();

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    if (documentContext.getFileType() != FileType.OS) {
      return;
    }

    var uri = documentContext.getUri();
    var module = documentContext.getSymbolTree().getModule();
    var typeName = FilenameUtils.getBaseName(uri.getPath());

    var previous = registeredByUri.put(uri, typeName);
    if (previous != null && !previous.equals(typeName)) {
      typeRegistry.unregisterUserType(previous);
    }
    typeRegistry.registerUserType(typeName, module);
  }
}
