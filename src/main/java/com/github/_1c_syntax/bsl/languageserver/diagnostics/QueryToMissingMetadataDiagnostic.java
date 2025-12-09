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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCube;
import com.github._1c_syntax.bsl.mdo.children.ExternalDataSourceCubeDimensionTable;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.types.ConfigurationSource;
import com.github._1c_syntax.bsl.types.MDOType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Objects;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  scope = DiagnosticScope.BSL,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.SQL
  }

)
public class QueryToMissingMetadataDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitQueryPackage(SDBLParser.QueryPackageContext ctx) {
    if (documentContext.getServerContext().getConfiguration().getConfigurationSource() == ConfigurationSource.EMPTY) {
      return ctx;
    }
    return super.visitQueryPackage(ctx);
  }

  @Override
  public ParseTree visitMdo(SDBLParser.MdoContext mdo) {
    if (nonMdoExists(mdo.type.getText(), mdo.tableName.getText())) {
      diagnosticStorage.addDiagnostic(mdo,
        info.getMessage(mdo.getText()));
    }
    return super.visitMdo(mdo);
  }

  @Override
  public ParseTree visitDataSources(SDBLParser.DataSourcesContext ctx) {
    ctx.dataSource().stream()
      .map(SDBLParser.DataSourceContext::externalDataSourceTable)
      .filter(Objects::nonNull)
      .filter(eds -> eds.cubeName != null)
      .forEach(eds -> {
        if (nonCubeExists(eds)) {
          diagnosticStorage.addDiagnostic(eds.cubeName, info.getMessage(eds.cubeName.getText()));
        }

        var cubeDimTable = eds.tableName;

        if (cubeDimTable != null && nonCubeDimTableExists(eds)) {
          diagnosticStorage.addDiagnostic(cubeDimTable, info.getMessage(eds.tableName.getText()));
        }
      });

    return super.visitDataSources(ctx);
  }

  private boolean nonMdoExists(String mdoType, String mdoName) {
    return getMdo(mdoType, mdoName).isEmpty();
  }

  private boolean nonCubeExists(SDBLParser.ExternalDataSourceTableContext eds) {
    return getCube(eds).isEmpty();
  }

  private boolean nonCubeDimTableExists(SDBLParser.ExternalDataSourceTableContext eds_ctx) {
    return getCubeDimTable(eds_ctx).isEmpty();
  }

  private Optional<MD> getMdo(String mdoTypeName, String mdoName) {
    return MDOType.fromValue(mdoTypeName).flatMap(mdoType ->
      documentContext.getServerContext().getConfiguration().findChild(mdo -> mdo.getMdoType() == mdoType
        && mdoName.equalsIgnoreCase(mdo.getName())));
  }

  private Optional<ExternalDataSourceCube> getCube(SDBLParser.ExternalDataSourceTableContext eds) {

    var mdoName = eds.mdo().tableName.getText();
    var cubeName = eds.cubeName.getText();

    return documentContext.getServerContext().getConfiguration().getExternalDataSources()
      .stream()
      .filter(mdo -> mdoName.equalsIgnoreCase(mdo.getName()))
      .flatMap(mdo -> mdo.getCubes().stream())
      .filter(cube -> cubeName.equalsIgnoreCase(cube.getName()))
      .findFirst();
  }

  private Optional<ExternalDataSourceCubeDimensionTable> getCubeDimTable(SDBLParser.ExternalDataSourceTableContext eds_ctx) {

    var mdo_name = eds_ctx.mdo().tableName.getText();
    var cube_dim_table_name = eds_ctx.tableName.getText();

    return documentContext.getServerContext().getConfiguration().getExternalDataSources()
      .stream()
      .filter(mdo -> mdo_name.equalsIgnoreCase(mdo.getName()))
      .flatMap(mdo -> mdo.getCubes().stream()
        .flatMap(c -> c.getDimensionTables().stream()))
      .filter(table -> cube_dim_table_name.equalsIgnoreCase(table.getName()))
      .findFirst();
  }
}
