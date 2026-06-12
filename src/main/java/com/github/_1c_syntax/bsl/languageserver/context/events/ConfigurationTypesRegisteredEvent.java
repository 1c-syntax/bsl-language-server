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
package com.github._1c_syntax.bsl.languageserver.context.events;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import org.springframework.context.ApplicationEvent;

/**
 * Публикуется однократно когда {@code ConfigurationTypesProvider} успешно
 * зарегистрировал конфигурационные типы (Document/Catalog/InformationRegister/…)
 * и их специализации в {@code TypeRegistry}. До этого момента lookup'ы вида
 * {@code typeRegistry.resolve("ДокументОбъект.<имя>")} могут возвращать пусто.
 * <p>
 * Потребители используют это событие, чтобы:
 * <ul>
 *   <li>сбросить ленивые кэши, опирающиеся на резолв типов;</li>
 *   <li>попросить клиента перезапросить диагностики или
 *       (если клиент не поддерживает pull-refresh) перепушить их.</li>
 * </ul>
 * Отличается от {@code ServerContextPopulatedEvent}, который публикуется по
 * завершении полного обхода файлов workspace (cross-document готовность).
 */
public class ConfigurationTypesRegisteredEvent extends ApplicationEvent {

  public ConfigurationTypesRegisteredEvent(ServerContext source) {
    super(source);
  }

  @Override
  public ServerContext getSource() {
    return (ServerContext) super.getSource();
  }
}
