/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.reporters;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.github._1c_syntax.bsl.languageserver.reporters.data.AnalysisInfo;
import com.github._1c_syntax.bsl.languageserver.reporters.data.FileInfo;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@JacksonXmlRootElement(localName = "coverage")
@Value
public class GenericCoverageReport {

  @JacksonXmlProperty(isAttribute = true)
  final String version;

  @JacksonXmlElementWrapper(useWrapping = false)
  final List<GenericCoverageReportEntry> file;

  public GenericCoverageReport(AnalysisInfo analysisInfo) {

    version = "1";
    file = new ArrayList<>();

    for (FileInfo fileInfo : analysisInfo.getFileinfos()) {
      file.add(new GenericCoverageReportEntry(fileInfo));
    }
  }

  public GenericCoverageReport(
    @JsonProperty("version") String version,
    @JsonProperty("file") List<GenericCoverageReportEntry> file
  ) {

    this.version = version;
    this.file = new ArrayList<>(file);
  }

  @Value
  static class GenericCoverageReportEntry {

    @JacksonXmlProperty(isAttribute = true)
    String path;

    @JacksonXmlElementWrapper(useWrapping = false)
    List<LineToCoverEntry> lineToCover;

    public GenericCoverageReportEntry(FileInfo fileInfo) {
      this.path = fileInfo.getPath().toString();
      this.lineToCover = new ArrayList<>();

      for (int lineNumToCover : fileInfo.getMetrics().getCovlocData()) {
        this.lineToCover.add(new LineToCoverEntry(lineNumToCover, false));
      }
    }

    public GenericCoverageReportEntry(
      @JsonProperty("path") String path,
      @JsonProperty("lineToCover") List<LineToCoverEntry> lineToCover
    ) {
      this.path = path;
      this.lineToCover = new ArrayList<>(lineToCover);
    }
  }

  @Value
  static class LineToCoverEntry {

    @JacksonXmlProperty(isAttribute = true)
    int lineNumber;

    @JacksonXmlProperty(isAttribute = true)
    boolean covered;

    public LineToCoverEntry(
      @JsonProperty("lineNumber") int lineNumber,
      @JsonProperty("covered") boolean covered
    ) {
      this.covered = covered;
      this.lineNumber = lineNumber;
    }
  }
}
