/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2019
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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.mdclasses.metadata.additional.CompatibilityMode;
import com.github._1c_syntax.mdclasses.metadata.additional.ModuleType;
import lombok.SneakyThrows;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DiagnosticSupplier {

  private final LanguageServerConfiguration configuration;
  private static List<Class<? extends BSLDiagnostic>> diagnosticClasses = createDiagnosticClasses();

  public DiagnosticSupplier(LanguageServerConfiguration configuration) {
    this.configuration = configuration;
  }

  public Optional<Class<? extends BSLDiagnostic>> getDiagnosticClass(String diagnosticCode) {
    return diagnosticClasses.stream()
      .filter(diagnosticClass -> createDiagnosticInfo(diagnosticClass).getCode().equals(diagnosticCode))
      .findAny();
  }

  public List<BSLDiagnostic> getDiagnosticInstances(
    FileType fileType,
    ModuleType moduleType,
    CompatibilityMode compatibilityMode) {

    return diagnosticClasses.stream()
      .map(this::createDiagnosticInfo)
      .filter(this::isEnabled)
      .filter(info -> inScope(info, fileType))
      .filter(info -> correctModuleType(info, moduleType))
      .filter(info -> passedCompatibilityMode(info, compatibilityMode))
      .map(this::createDiagnosticInstance)
      .peek(this::configureDiagnostic)
      .collect(Collectors.toList());
  }

  public List<BSLDiagnostic> getDiagnosticInstances(FileType fileType) {
    return getDiagnosticInstances(fileType, ModuleType.Unknown, new CompatibilityMode("DontUse"));
  }

  public List<BSLDiagnostic> getDiagnosticInstances(FileType fileType, ModuleType moduleType) {
    return getDiagnosticInstances(fileType, moduleType, new CompatibilityMode("DontUse"));
  }

  public List<BSLDiagnostic> getDiagnosticInstances(FileType fileType, CompatibilityMode compatibilityMode) {
    return getDiagnosticInstances(fileType, ModuleType.Unknown, compatibilityMode);
  }

  public BSLDiagnostic getDiagnosticInstance(Class<? extends BSLDiagnostic> diagnosticClass) {
    DiagnosticInfo info = new DiagnosticInfo(diagnosticClass, configuration.getDiagnosticLanguage());
    BSLDiagnostic diagnosticInstance = createDiagnosticInstance(info);
    configureDiagnostic(diagnosticInstance);

    return diagnosticInstance;
  }

  @SneakyThrows
  private BSLDiagnostic createDiagnosticInstance(DiagnosticInfo diagnosticInfo) {
    Class<? extends BSLDiagnostic> diagnosticClass = diagnosticInfo.getDiagnosticClass();
    DiagnosticInfo info = createDiagnosticInfo(diagnosticClass);

    return diagnosticClass.getDeclaredConstructor(DiagnosticInfo.class).newInstance(info);
  }

  private DiagnosticInfo createDiagnosticInfo(Class<? extends BSLDiagnostic> diagnosticClass) {
    return new DiagnosticInfo(diagnosticClass, configuration.getDiagnosticLanguage());
  }

  private void configureDiagnostic(BSLDiagnostic diagnostic) {
    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnostics().get(diagnostic.getInfo().getCode());
    if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
      diagnostic.configure(diagnosticConfiguration.getRight());
    }
  }

  private boolean isEnabled(DiagnosticInfo diagnosticInfo) {
    if (diagnosticInfo == null) {
      return false;
    }

    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnostics().get(diagnosticInfo.getCode());

    boolean activatedByDefault = diagnosticConfiguration == null && diagnosticInfo.isActivatedByDefault();
    boolean hasCustomConfiguration = diagnosticConfiguration != null && diagnosticConfiguration.isRight();
    boolean enabledDirectly = diagnosticConfiguration != null
      && diagnosticConfiguration.isLeft()
      && diagnosticConfiguration.getLeft();

    return activatedByDefault
      || hasCustomConfiguration
      || enabledDirectly;
  }

  private static boolean inScope(DiagnosticInfo diagnosticInfo, FileType fileType) {
    DiagnosticScope scope = diagnosticInfo.getScope();
    DiagnosticScope fileScope;
    if (fileType == FileType.OS) {
      fileScope = DiagnosticScope.OS;
    } else {
      fileScope = DiagnosticScope.BSL;
    }
    return scope == DiagnosticScope.ALL || scope == fileScope;
  }

  private static boolean correctModuleType(DiagnosticInfo diagnosticInfo, ModuleType moduletype) {
    ModuleType[] diagnosticModules = diagnosticInfo.getModules();

    if (diagnosticModules.length == 0) {
      return true;
    }

    boolean contain = false;
    for (ModuleType module : diagnosticModules) {
      if (module == moduletype) {
        contain = true;
        break;
      }
    }
    return contain;
  }

  private static boolean passedCompatibilityMode(
    DiagnosticInfo diagnosticInfo,
    CompatibilityMode contextCompatibilityMode
  ) {
    DiagnosticCompatibilityMode compatibilityMode = diagnosticInfo.getCompatibilityMode();

    if (compatibilityMode == DiagnosticCompatibilityMode.UNDEFINED) {
      return true;
    }
    if (contextCompatibilityMode == null) {
      return false;
    }

    return CompatibilityMode.compareTo(compatibilityMode.getCompatibilityMode(), contextCompatibilityMode) >= 0;
  }

  @SuppressWarnings("unchecked")
  private static List<Class<? extends BSLDiagnostic>> createDiagnosticClasses() {

    Reflections diagnosticReflections = new Reflections(
      new ConfigurationBuilder()
        .setUrls(
          ClasspathHelper.forPackage(
            BSLDiagnostic.class.getPackage().getName(),
            ClasspathHelper.contextClassLoader(),
            ClasspathHelper.staticClassLoader()
          )
        )
    );

    return diagnosticReflections.getTypesAnnotatedWith(DiagnosticMetadata.class)
      .stream()
      .map(aClass -> (Class<? extends BSLDiagnostic>) aClass)
      .collect(Collectors.toList());
  }

  public static List<Class<? extends BSLDiagnostic>> getDiagnosticClasses() {
    return new ArrayList<>(diagnosticClasses);
  }

}
