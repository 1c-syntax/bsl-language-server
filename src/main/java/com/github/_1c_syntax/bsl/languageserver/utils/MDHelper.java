/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.utils;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.mdclasses.ConfigurationTree;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Module;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class MDHelper {

  public Optional<CommonModule> getCommonModule(DocumentContext documentContext, String name) {
    var configuration = documentContext.getServerContext().getConfiguration();
    if (configuration.getConfigurationSource() != ConfigurationSource.EMPTY
      && configuration instanceof ConfigurationTree) {
      return ((ConfigurationTree) configuration)
        .findCommonModule(commonModule -> commonModule.getName().equalsIgnoreCase(name));
    }
    return Optional.empty();
  }

  public List<Module> getModules(DocumentContext documentContext, String mdoRef) {
    var configuration = documentContext.getServerContext().getConfiguration();
    if (configuration.getConfigurationSource() != ConfigurationSource.EMPTY) {
      var mdo = configuration.findChild(mdoRef);
      if (mdo.isPresent()) {
        var mdoValue = mdo.get();
        if (mdoValue instanceof ModuleOwner) {
          return ((ModuleOwner) mdoValue).getModules();
        }
      }
    }
    return Collections.emptyList();
  }
}
