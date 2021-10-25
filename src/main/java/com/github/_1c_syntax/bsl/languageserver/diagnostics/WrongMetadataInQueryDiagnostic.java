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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.mdclasses.mdo.AbstractMDObjectBase;
import com.github._1c_syntax.mdclasses.mdo.support.MDOType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Map;
import java.util.Optional;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.BLOCKER,
  minutesToFix = 5,
  tags = {
    DiagnosticTag.SUSPICIOUS,
    DiagnosticTag.SQL
  }

)
public class WrongMetadataInQueryDiagnostic extends AbstractSDBLVisitorDiagnostic {

  @Override
  public ParseTree visitMdo(SDBLParser.MdoContext mdo) {
    if (nonMdoExists(mdo.type.getText(), mdo.tableName.getText())) {
      diagnosticStorage.addDiagnostic(mdo,
        info.getMessage(mdo.getText()));
    }
    return super.visitMdo(mdo);
  }

  private boolean nonMdoExists(String mdoType, String mdoName) {
    return getMdo(mdoType, mdoName).isEmpty();
  }

  private Optional<AbstractMDObjectBase> getMdo(String mdoTypeName, String mdoName) {
    return MDOType.fromValue(mdoTypeName).flatMap(mdoType ->
      documentContext.getServerContext().getConfiguration().getChildrenByMdoRef().entrySet().stream()
        .filter(entry -> entry.getKey().getType().equals(mdoType)
          && mdoName.equals(entry.getValue().getName()))
        .map(Map.Entry::getValue)
        .findFirst()
    );
  }
}
