/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.eclipse.lsp4j.Diagnostic;

import java.util.Map;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;

@Getter
@AllArgsConstructor
public class CodeQualityReportEntry {

  private static final Map<DiagnosticSeverity, Severity> SEVERITY_MAP = Map.of(
    DiagnosticSeverity.BLOCKER, Severity.BLOCKER,
    DiagnosticSeverity.CRITICAL, Severity.CRITICAL,
    DiagnosticSeverity.MAJOR, Severity.MAJOR,
    DiagnosticSeverity.MINOR, Severity.MINOR,
    DiagnosticSeverity.INFO, Severity.INFO
  );

  /**
   * A human-readable description of the code quality violation.
   */
  private final String description;

  /**
   * A unique name representing the check, or rule, associated with this violation.
   */
  @JsonProperty("check_name")
  private final String checkName;

  /**
   * A unique fingerprint to identify this specific code quality violation, such as a hash of its contents.
   */
  private final String fingerprint;

  /**
   * The severity of the violation.
   */
  private final Severity severity;

  private final Location location;

  public CodeQualityReportEntry(String path, Diagnostic diagnostic, DiagnosticInfo diagnosticInfo) {
    this.description = diagnostic.getMessage();
    this.checkName = diagnosticInfo.getCode().getStringValue();
    var fingerprintData = path + "//" + this.checkName + "//" + diagnostic.getRange();
    this.fingerprint = sha256Hex(fingerprintData);
    this.severity = SEVERITY_MAP.get(diagnosticInfo.getSeverity());
    this.location = new Location();
    this.location.path = path;
    this.location.lines = new Lines();
    this.location.lines.begin = diagnostic.getRange().getStart().getLine();
  }

  @Getter
  @AllArgsConstructor
  @NoArgsConstructor
  @ToString
  public static class Location {

    /**
     * The file containing the code quality violation, expressed as a relative path in the repository.
     */
    private String path;
    private Lines lines;
  }

  @Getter
  @AllArgsConstructor
  @NoArgsConstructor
  @ToString
  public static class Lines {
    /**
     * The line on which the code quality violation occurred.
     */
    private int begin;
  }

  public enum Severity {
    @JsonProperty("blocker")
    BLOCKER,
    @JsonProperty("critical")
    CRITICAL,
    @JsonProperty("major")
    MAJOR,
    @JsonProperty("minor")
    MINOR,
    @JsonProperty("info")
    INFO
  }
}
