/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2022
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
package com.github._1c_syntax.bsl.languageserver.context;

public enum ModuleType {
  BotModule(),
  IntegrationServiceModule(),
  CommandModule(),
  CommonModule(),
  ObjectModule(),
  ManagerModule(),
  FormModule(),
  RecordSetModule(),
  ValueManagerModule(),
  ApplicationModule(),
  ManagedApplicationModule(),
  SessionModule(),
  ExternalConnectionModule(),
  OrdinaryApplicationModule(),
  HTTPServiceModule(),
  WEBServiceModule(),
  Class(),
  Module(),
  UNKNOWN(); // для неизвестных типов модулей

  public static ModuleType valueOf(com.github._1c_syntax.mdclasses.mdo.support.ModuleType mdoModuleType){

    return valueOf(mdoModuleType.name());
  }
}
