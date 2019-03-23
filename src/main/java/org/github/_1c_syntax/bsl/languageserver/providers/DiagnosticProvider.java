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
package org.github._1c_syntax.bsl.languageserver.providers;

import com.google.common.annotations.VisibleForTesting;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration;
import org.github._1c_syntax.bsl.languageserver.configuration.diagnostics.DiagnosticConfiguration;
import org.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import org.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.CanonicalSpellingKeywordsDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.EmptyCodeBlockDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.EmptyStatementDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.FunctionShouldHaveReturnDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.IfElseDuplicatedCodeBlockDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.IfElseDuplicatedConditionDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.IfElseIfEndsWithElseDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.LineLengthDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.MethodSizeDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.NestedTernaryOperatorDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.NumberOfOptionalParamsDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.NumberOfParamsDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.OneStatementPerLineDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.OrderOfParamsDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.SelfAssignDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.SemicolonPresenceDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.UnknownPreprocessorSymbolDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.UsingCancelParameterDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.YoLetterUsageDiagnostic;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import org.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import org.github._1c_syntax.bsl.languageserver.utils.UTF8Control;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public final class DiagnosticProvider {

  public static final String SOURCE = "bsl-language-server";
  private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticProvider.class.getSimpleName());

  private static List<Class<? extends BSLDiagnostic>> diagnosticClasses
    = createDiagnosticClasses();
  private static Map<Class<? extends BSLDiagnostic>, DiagnosticMetadata> diagnosticsMetadata
    = createDiagnosticMetadata(diagnosticClasses);
  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> severityToLSPSeverityMap
    = createSeverityToLSPSeverityMap();


  private final LanguageServerConfiguration configuration;

  public DiagnosticProvider() {
    this(LanguageServerConfiguration.create());
  }

  public DiagnosticProvider(LanguageServerConfiguration configuration) {
    this.configuration = configuration;
  }

  public void computeAndPublishDiagnostics(LanguageClient client, DocumentContext documentContext) {
    List<Diagnostic> diagnostics = computeDiagnostics(documentContext);

    client.publishDiagnostics(new PublishDiagnosticsParams(documentContext.getUri(), diagnostics));
  }

  public List<Diagnostic> computeDiagnostics(DocumentContext documentContext) {
    return getDiagnosticInstances().parallelStream()
      .flatMap(diagnostic -> diagnostic.getDiagnostics(documentContext).stream())
      .collect(Collectors.toList());
  }

  public static List<Class<? extends BSLDiagnostic>> getDiagnosticClasses() {
    return new ArrayList<>(diagnosticClasses);
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

  public static org.eclipse.lsp4j.DiagnosticSeverity getLSPDiagnosticSeverity(BSLDiagnostic diagnostic) {
    DiagnosticMetadata diagnosticMetadata = diagnosticsMetadata.get(diagnostic.getClass());
    if (diagnosticMetadata.type() == DiagnosticType.CODE_SMELL) {
      return severityToLSPSeverityMap.get(diagnosticMetadata.severity());
    } else {
      return org.eclipse.lsp4j.DiagnosticSeverity.Error;
    }
  }

  private static List<Class<? extends BSLDiagnostic>> createDiagnosticClasses() {

    return Arrays.asList(
      CanonicalSpellingKeywordsDiagnostic.class,
      EmptyCodeBlockDiagnostic.class,
      EmptyStatementDiagnostic.class,
      FunctionShouldHaveReturnDiagnostic.class,
      IfElseDuplicatedCodeBlockDiagnostic.class,
      IfElseDuplicatedConditionDiagnostic.class,
      IfElseIfEndsWithElseDiagnostic.class,
      LineLengthDiagnostic.class,
      MethodSizeDiagnostic.class,
      NestedTernaryOperatorDiagnostic.class,
      NumberOfOptionalParamsDiagnostic.class,
      NumberOfParamsDiagnostic.class,
      OneStatementPerLineDiagnostic.class,
      OrderOfParamsDiagnostic.class,
      SelfAssignDiagnostic.class,
      SemicolonPresenceDiagnostic.class,
      UnknownPreprocessorSymbolDiagnostic.class,
      UsingCancelParameterDiagnostic.class,
      YoLetterUsageDiagnostic.class
    );

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

  private static Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> createSeverityToLSPSeverityMap() {
    Map<DiagnosticSeverity, org.eclipse.lsp4j.DiagnosticSeverity> map = new EnumMap<>(DiagnosticSeverity.class);
    map.put(DiagnosticSeverity.INFO, org.eclipse.lsp4j.DiagnosticSeverity.Hint);
    map.put(DiagnosticSeverity.MINOR, org.eclipse.lsp4j.DiagnosticSeverity.Information);
    map.put(DiagnosticSeverity.MAJOR, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.CRITICAL, org.eclipse.lsp4j.DiagnosticSeverity.Warning);
    map.put(DiagnosticSeverity.BLOCKER, org.eclipse.lsp4j.DiagnosticSeverity.Warning);

    return map;
  }

  @VisibleForTesting
  public List<BSLDiagnostic> getDiagnosticInstances() {
    return diagnosticClasses.stream()
      .filter(this::isEnabled)
      .map(DiagnosticProvider::createDiagnosticInstance)
      .peek((BSLDiagnostic diagnostic) -> {
          Either<Boolean, DiagnosticConfiguration> diagnosticConfiguration =
            configuration.getDiagnostics().get(getDiagnosticCode(diagnostic));
          if (diagnosticConfiguration != null && diagnosticConfiguration.isRight()) {
            diagnostic.configure(diagnosticConfiguration.getRight());
          }
        }
      ).collect(Collectors.toList());
  }

  private static BSLDiagnostic createDiagnosticInstance(Class<? extends BSLDiagnostic> diagnosticClass) {
    BSLDiagnostic diagnostic = null;
    try {
      diagnostic = diagnosticClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      LOGGER.error("Can't instantiate diagnostic", e);
    }
    return diagnostic;
  }

  private boolean isEnabled(Class<? extends BSLDiagnostic> diagnosticClass) {
    if (diagnosticClass == null) {
      return false;
    }
    Either<Boolean, DiagnosticConfiguration> diagnosticConfiguration =
      configuration.getDiagnostics().get(getDiagnosticCode(diagnosticClass));
    return diagnosticConfiguration == null
      || diagnosticConfiguration.isRight()
      || (diagnosticConfiguration.isLeft() && diagnosticConfiguration.getLeft());
  }
}
