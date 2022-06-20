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
package com.github._1c_syntax.bsl.languageserver.context.computer;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ModuleType;
import org.apache.commons.io.FilenameUtils;

import java.nio.file.Paths;

public class ModuleTypeComputer {

  private final DocumentContext documentContext;

  public ModuleTypeComputer(DocumentContext documentContext) {
    this.documentContext = documentContext;
  }

  public ModuleType computeModuleType() {

    ModuleType moduleType;
    if (documentContext.getFileType() == FileType.BSL) {
      moduleType = computeBSL();
    } else if (documentContext.getFileType() == FileType.OS) {
      moduleType = computeOS();
    } else {
      moduleType = ModuleType.UNKNOWN;
    }

    return moduleType;
  }

  private ModuleType computeBSL() {
    com.github._1c_syntax.mdclasses.mdo.support.ModuleType type = documentContext.getServerContext()
      .getConfiguration().getModuleType(documentContext.getUri());
    return ModuleType.valueOf(type.name());
  }

  private ModuleType computeOS() {
    if (documentContext.getUri().toString().contains("Модули")) {
      return ModuleType.Module;
    } else if (documentContext.getUri().toString().contains("Классы")) {
      return ModuleType.Class;
    } else {
      return ModuleType.UNKNOWN;
    }
  }

  public String computeTypeName() {
    if (documentContext.getModuleType() == ModuleType.Module
      || documentContext.getModuleType() == ModuleType.Class) {
      // to do убрать FilenameUtils
      return FilenameUtils.getBaseName(Paths.get(documentContext.getUri()).toString());
    }
    return "";
  }
}

