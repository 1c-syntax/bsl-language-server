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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.languageserver.utils.UTF8Control;
import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reflections.ReflectionUtils.getAllFields;
import static org.reflections.ReflectionUtils.withAnnotation;

public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";
  private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticProvider.class.getSimpleName());

  private static List<Class<? extends BSLDiagnostic>> diagnosticClasses
    = createDiagnosticClasses();
  private static Map<Class<? extends BSLDiagnostic>, DiagnosticMetadata> diagnosticsMetadata
    = createDiagnosticMetadata(diagnosticClasses);
  private static Map<Class<? extends BSLDiagnostic>, Map<String, DiagnosticParameter>> diagnosticParameters
    = createDiagnosticParameters(diagnosticClasses);
  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> severityToLSPSeverityMap
    = createSeverityToLSPSeverityMap();
  private static Map<String, Class<? extends BSLDiagnostic>> diagnosticsCodes
    = createDiagnosticsCodes(diagnosticClasses);

  private final LanguageServerConfiguration configuration;
  private final Map<String, Set<Diagnostic>> computedDiagnostics;

  public DiagnosticProvider() {
    this(LanguageServerConfiguration.create());
  }

  public DiagnosticProvider(LanguageServerConfiguration configuration) {
    this.configuration = configuration;
    computedDiagnostics = new HashMap<>();
  }

  public void computeAndPublishDiagnostics(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = computeDiagnostics(documentContext);

    client.publishDiagnostics(new PublishDiagnosticsParams(documentContext.getUri(), diagnostics));
  }

  public void publishEmptyDiagnosticList(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = new ArrayList<>();
    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>());
    client.publishDiagnostics(
      new PublishDiagnosticsParams(documentContext.getUri(), diagnostics)
    );
  }

  public List<Diagnostic> computeDiagnostics(DocumentContext documentContext) {

    List<Diagnostic> diagnostics = getDiagnosticInstances(documentContext.getFileType()).parallelStream()
      .flatMap(diagnostic -> diagnostic.getDiagnostics(documentContext).stream())
      .collect(Collectors.toList());

    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>(diagnostics));

    return diagnostics;
  }

  public Set<Diagnostic> getComputedDiagnostics(DocumentContext documentContext) {
    return computedDiagnostics.getOrDefault(documentContext.getUri(), new LinkedHashSet<>());
  }

  public void clearComputedDiagnostics(DocumentContext documentContext) {
    computedDiagnostics.put(documentContext.getUri(), new LinkedHashSet<>());
  }

  public static List<Class<? extends BSLDiagnostic>> getDiagnosticClasses() {
    return new ArrayList<>(diagnosticClasses);
  }

  public static Optional<Class<? extends BSLDiagnostic>> getDiagnosticClass(String diagnosticCode) {
    return DiagnosticProvider.getDiagnosticClasses()
      .stream()
      .filter(bslDiagnosticClass -> DiagnosticProvider.getDiagnosticCode(bslDiagnosticClass).equals(diagnosticCode))
      .findAny();
  }


  public static String getDiagnosticCode(Class<? extends BSLDiagnostic> diagnosticClass) {
    String simpleName = diagnosticClass.getSimpleName();
    if (simpleName.endsWith("Diagnostic")) {
      simpleName = simpleName.substring(0, simpleName.length() - "Diagnostic".length());
    }

    return simpleName;
  }

  public static String getDiagnosticCode(BSLDiagnostic diagnostic) {
    return getDiagnosticCode(diagnostic.getClass());
  }

  public static String getDiagnosticName(Class<? extends BSLDiagnostic> diagnosticClass) {
    return ResourceBundle.getBundle(diagnosticClass.getName(), new UTF8Control()).getString("diagnosticName");
  }

  public static String getDiagnosticName(BSLDiagnostic diagnostic) {
    return getDiagnosticName(diagnostic.getClass());
  }

  public static String getDiagnosticDescription(Class<? extends BSLDiagnostic> diagnosticClass) {
    String diagnosticCode = getDiagnosticCode(diagnosticClass);
    InputStream descriptionStream = diagnosticClass.getResourceAsStream(diagnosticCode + ".md");

    if (descriptionStream == null) {
      return "";
    }

    try {
      return IOUtils.toString(descriptionStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  public static String getDiagnosticDescription(BSLDiagnostic diagnostic) {
    return getDiagnosticDescription(diagnostic.getClass());
  }

  public static DiagnosticType getDiagnosticType(Class<? extends BSLDiagnostic> diagnosticClass) {
    return diagnosticsMetadata.get(diagnosticClass).type();
  }

  public static DiagnosticType getDiagnosticType(BSLDiagnostic diagnostic) {
    return getDiagnosticType(diagnostic.getClass());
  }

  public static DiagnosticSeverity getDiagnosticSeverity(Class<? extends BSLDiagnostic> diagnosticClass) {
    return diagnosticsMetadata.get(diagnosticClass).severity();
  }

  public static DiagnosticSeverity getDiagnosticSeverity(BSLDiagnostic diagnostic) {
    return getDiagnosticSeverity(diagnostic.getClass());
  }

  public static int getMinutesToFix(Class<? extends BSLDiagnostic> diagnosticClass) {
    DiagnosticMetadata diagnosticMetadata = diagnosticsMetadata.get(diagnosticClass);
    return diagnosticMetadata == null ? 0 : diagnosticMetadata.minutesToFix();
  }

  public static int getMinutesToFix(BSLDiagnostic diagnostic) {
    return getMinutesToFix(diagnostic.getClass());
  }

  public static int getMinutesToFix(Diagnostic diagnostic) {
    Class<? extends BSLDiagnostic> diagnosticClass = getBSLDiagnosticClass(diagnostic);
    return getMinutesToFix(diagnosticClass);
  }

  public static boolean isActivatedByDefault(Class<? extends BSLDiagnostic> diagnosticClass) {
    return diagnosticsMetadata.get(diagnosticClass).activatedByDefault();
  }

  public static boolean isActivatedByDefault(BSLDiagnostic diagnostic) {
    return isActivatedByDefault(diagnostic.getClass());
  }

  public static Map<String, DiagnosticParameter> getDiagnosticParameters(
    Class<? extends BSLDiagnostic> diagnosticClass
  ) {
    return diagnosticParameters.get(diagnosticClass);
  }

  public static Map<String, DiagnosticParameter> getDiagnosticParameters(BSLDiagnostic diagnostic) {
    return getDiagnosticParameters(diagnostic.getClass());
  }

  public static Object getDefaultValue(DiagnosticParameter diagnosticParameter) {
    return castDiagnosticParameterValue(diagnosticParameter.defaultValue(), diagnosticParameter.type());
  }

  public static Object castDiagnosticParameterValue(String valueToCast, Class type) {
    Object value;
    if (type == Integer.class) {
      value = Integer.parseInt(valueToCast);
    } else if (type == Boolean.class) {
      value = Boolean.parseBoolean(valueToCast);
    } else if (type == Float.class) {
      value = Float.parseFloat(valueToCast);
    } else if (type == String.class) {
      value = valueToCast;
    } else {
      throw new IllegalArgumentException("Unsupported diagnostic parameter type " + type);
    }

    return value;
  }

  public static Map<String, Object> getDefaultDiagnosticConfiguration(Class<? extends BSLDiagnostic> diagnosticClass) {
    Map<String, DiagnosticParameter> diagnosticParameters = getDiagnosticParameters(diagnosticClass);
    return diagnosticParameters.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        (Map.Entry<String, DiagnosticParameter> entry) -> getDefaultValue(entry.getValue())
        )
      );
  }

  public static Map<String, Object> getDefaultDiagnosticConfiguration(BSLDiagnostic diagnostic) {
    return getDefaultDiagnosticConfiguration(diagnostic.getClass());
  }

  public static Class<? extends BSLDiagnostic> getBSLDiagnosticClass(Diagnostic diagnostic) {
    return diagnosticsCodes.get(diagnostic.getCode());
  }

  public static org.eclipse.lsp4j.DiagnosticSeverity getLSPDiagnosticSeverity(BSLDiagnostic diagnostic) {
    DiagnosticMetadata diagnosticMetadata = diagnosticsMetadata.get(diagnostic.getClass());
    if (diagnosticMetadata.type() == DiagnosticType.CODE_SMELL) {
      return severityToLSPSeverityMap.get(diagnosticMetadata.severity());
    } else {
      return org.eclipse.lsp4j.DiagnosticSeverity.Error;
    }
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

  private static Map<Class<? extends BSLDiagnostic>, DiagnosticMetadata> createDiagnosticMetadata(
    List<Class<? extends BSLDiagnostic>> diagnosticClasses
  ) {

    return diagnosticClasses.stream()
      .collect(Collectors.toMap(
        (Class<? extends BSLDiagnostic> diagnosticClass) -> diagnosticClass,
        (Class<? extends BSLDiagnostic> diagnosticClass) -> diagnosticClass.getAnnotation(DiagnosticMetadata.class))
      );
  }

  @SuppressWarnings("unchecked")
  private static Map<Class<? extends BSLDiagnostic>, Map<String, DiagnosticParameter>> createDiagnosticParameters(
    List<Class<? extends BSLDiagnostic>> diagnosticClasses
  ) {
    return diagnosticClasses.stream()
      .collect(Collectors.toMap(
        (Class<? extends BSLDiagnostic> diagnosticClass) -> diagnosticClass,
        (Class<? extends BSLDiagnostic> diagnosticClass) -> getAllFields(
          diagnosticClass,
          withAnnotation(DiagnosticParameter.class)
        ).stream()
          .collect(Collectors.toMap(
            Field::getName,
            (Field field) -> field.getAnnotation(DiagnosticParameter.class)
          ))
      ));
  }

  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> createSeverityToLSPSeverityMap() {
    Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> map = new EnumMap<>(DiagnosticSeverity.class);
    map.put(DiagnosticSeverity.INFO, org.eclipse.lsp4j.DiagnosticSeverity.Hint);
    map.put(DiagnosticSeverity.MINOR, org.eclipse.lsp4j.DiagnosticSeverity.Information);
    map.put(DiagnosticSeverity.MAJOR, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.CRITICAL, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.BLOCKER, org.eclipse.lsp4j.DiagnosticSeverity.Warning);

    return map;
  }

  private static Map<String, Class<? extends BSLDiagnostic>> createDiagnosticsCodes(
    List<Class<? extends BSLDiagnostic>> diagnosticClasses
  ) {
    return diagnosticClasses.stream().collect(
      Collectors.toMap(
        DiagnosticProvider::getDiagnosticCode,
        diagnosticClass -> diagnosticClass
      )
    );
  }

  public BSLDiagnostic getDiagnosticInstance(Class<? extends BSLDiagnostic> diagnosticClass) {
    BSLDiagnostic diagnosticInstance = createDiagnosticInstance(diagnosticClass);
    configureDiagnostic(diagnosticInstance);

    return diagnosticInstance;
  }

  @VisibleForTesting
  public List<BSLDiagnostic> getDiagnosticInstances() {
    return diagnosticClasses.stream()
      .filter(this::isEnabled)
      .map(DiagnosticProvider::createDiagnosticInstance)
      .peek(this::configureDiagnostic
      ).collect(Collectors.toList());
  }

  private List<BSLDiagnostic> getDiagnosticInstances(FileType fileType) {
    return diagnosticClasses.stream()
      .filter(this::isEnabled)
      .filter(element -> inScope(element, fileType))
      .map(DiagnosticProvider::createDiagnosticInstance)
      .peek(this::configureDiagnostic
      ).collect(Collectors.toList());
  }

  private static BSLDiagnostic createDiagnosticInstance(Class<? extends BSLDiagnostic> diagnosticClass) {
    BSLDiagnostic diagnostic = null;
    try {
      diagnostic = diagnosticClass.getDeclaredConstructor().newInstance();
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      LOGGER.error("Can't instantiate diagnostic", e);
    }
    return diagnostic;
  }

  private static boolean inScope(Class<? extends BSLDiagnostic> diagnosticClass, FileType fileType) {
    DiagnosticScope scope = diagnosticsMetadata.get(diagnosticClass).scope();
    DiagnosticScope fileScope;
    if (fileType == FileType.OS) {
      fileScope = DiagnosticScope.OS;
    } else {
      fileScope = DiagnosticScope.BSL;
    }
    return scope == DiagnosticScope.ALL || scope == fileScope;
  }

  private void configureDiagnostic(BSLDiagnostic diagnostic) {
    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnostics().get(getDiagnosticCode(diagnostic));
    if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
      diagnostic.configure(diagnosticConfiguration.getRight());
    }
  }

  private boolean isEnabled(Class<? extends BSLDiagnostic> diagnosticClass) {
    if (diagnosticClass == null) {
      return false;
    }

    Either<Boolean, Map<String, Object>> diagnosticConfiguration =
      configuration.getDiagnostics().get(getDiagnosticCode(diagnosticClass));

    boolean activatedByDefault = diagnosticConfiguration == null && isActivatedByDefault(diagnosticClass);
    boolean hasCustomConfiguration = diagnosticConfiguration != null && diagnosticConfiguration.isRight();
    boolean enabledDirectly = diagnosticConfiguration != null
      && diagnosticConfiguration.isLeft()
      && diagnosticConfiguration.getLeft();

    return activatedByDefault
      || hasCustomConfiguration
      || enabledDirectly;
  }
}
