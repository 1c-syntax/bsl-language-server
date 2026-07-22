/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2026
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
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticParameter;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.mdo.Attribute;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.Register;
import com.github._1c_syntax.bsl.types.Qualifier;
import com.github._1c_syntax.bsl.types.ValueType;
import com.github._1c_syntax.bsl.types.ValueTypeDescription;
import com.github._1c_syntax.bsl.types.qualifiers.NumberQualifiers;
import com.github._1c_syntax.bsl.types.qualifiers.StringQualifiers;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DiagnosticMetadata(
  type = DiagnosticType.ERROR,
  severity = DiagnosticSeverity.CRITICAL,
  minutesToFix = 30,
  tags = {DiagnosticTag.STANDARD, DiagnosticTag.DESIGN, DiagnosticTag.ERROR}
)
@Getter
@Setter
public class FileDbIndexKeyLengthExceededDiagnostic extends AbstractMetadataDiagnostic {

  private static final int MSSQL_LIMIT = 900;

  @DiagnosticParameter(type = Integer.class, defaultValue = "1920")
  private int maxIndexKeyLengthBytes = 1920;

  public enum CheckMode {
    FILE, MSSQL, ALL
  }

  @DiagnosticParameter(type = String.class, defaultValue = "ALL")
  private CheckMode checkMode = CheckMode.ALL;

  @Override
  public void configure(Map<String, Object> configuration) {
    if (configuration.containsKey("checkMode")) {
      try {
        this.setCheckMode(CheckMode.valueOf(configuration.get("checkMode").toString()));
      } catch (IllegalArgumentException e) {
        this.setCheckMode(CheckMode.ALL);
      }
    }
    if (configuration.containsKey("maxIndexKeyLengthBytes")) {
      Object value = configuration.get("maxIndexKeyLengthBytes");
      this.setMaxIndexKeyLengthBytes(value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(value.toString()));
    }
  }

  @Override
  public void checkMetadata(MD md) {
    if (!(md instanceof Register register)) {
      return;
    }

    int mainIndexSize = 0;
    List<String> byDimsFields = new ArrayList<>();

    // Шаг 1: Проверка периодичности (Только для InformationRegister и CalculationRegister)
    if (register instanceof com.github._1c_syntax.bsl.mdo.InformationRegister infoReg) {
      boolean isPeriodic = infoReg.getInformationRegisterPeriodicity() != com.github._1c_syntax.bsl.mdo.support.InformationRegisterPeriodicity.NONPERIODICAL
        && infoReg.getInformationRegisterPeriodicity() != com.github._1c_syntax.bsl.mdo.support.InformationRegisterPeriodicity.UNKNOWN;
      if (isPeriodic) {
        mainIndexSize += 8;
        byDimsFields.add("Период (8 байт)");
      }
    } else if (register instanceof com.github._1c_syntax.bsl.mdo.CalculationRegister calcReg) {
      boolean isPeriodic = calcReg.getPeriodicity() != com.github._1c_syntax.bsl.mdo.support.CalculationRegisterPeriodicity.UNKNOWN;
      if (isPeriodic) {
        mainIndexSize += 8;
        byDimsFields.add("Период (8 байт)");
      }
    }

    // Шаг 2: Обход измерений
    for (Attribute dimension : register.getDimensions()) {
      int dimensionSize = calculateDimensionSizeBytes(dimension);

      // Измерения всегда входят в основной составной индекс ByDims
      mainIndexSize += dimensionSize;
      byDimsFields.add(String.format("%s (%d байт)", dimension.getName(), dimensionSize));

      // Индивидуальный индекс измерения проверяется ТОЛЬКО если он явно задан пользователем
      String indexingName = dimension.getIndexing().name();
      if (!"None".equalsIgnoreCase(indexingName) && !"DONT_INDEX".equalsIgnoreCase(indexingName)) {
        checkAndReport(dimension.getName(), String.format("%s (%d байт)", dimension.getName(), dimensionSize), dimensionSize);
      }
    }

    // Шаг 3: Обход реквизитов (они не входят в ByDims, проверяем только их явные индексы)
    for (Attribute attribute : register.getAttributes()) {
      String indexingName = attribute.getIndexing().name();
      if (!"None".equalsIgnoreCase(indexingName) && !"DONT_INDEX".equalsIgnoreCase(indexingName)) {
        int attributeSize = calculateDimensionSizeBytes(attribute);
        checkAndReport(attribute.getName(), String.format("%s (%d байт)", attribute.getName(), attributeSize), attributeSize);
      }
    }

    // Шаг 4: Проверка основного составного индекса (ByDims)
    String byDimsDetails = String.join(", ", byDimsFields);
    checkAndReport("ByDims", byDimsDetails, mainIndexSize);
  }

  private void checkAndReport(String indexName, String fieldsDetail, int currentSize) {
    if ((checkMode == CheckMode.MSSQL || checkMode == CheckMode.ALL) && currentSize > MSSQL_LIMIT) {
      addDiagnostic(getInfo().getMessage(indexName, fieldsDetail, "MSSQL", MSSQL_LIMIT));
    }
    if ((checkMode == CheckMode.FILE || checkMode == CheckMode.ALL) && currentSize > this.maxIndexKeyLengthBytes) {
      addDiagnostic(getInfo().getMessage(indexName, fieldsDetail, "FILE", this.maxIndexKeyLengthBytes));
    }
  }

  private int calculateDimensionSizeBytes(Attribute dimension) {
    ValueTypeDescription vtd = dimension.getValueType();
    if (vtd.isEmpty()) return 16;

    long stringLength = 0;
    int numberPrecision = 10;

    for (Qualifier q : vtd.getQualifiers()) {
      if (q instanceof StringQualifiers) stringLength = ((StringQualifiers) q).getLength();
      if (q instanceof NumberQualifiers) numberPrecision = ((NumberQualifiers) q).getPrecision();
    }

    if (vtd.isComposite()) {
      int totalCompositeSize = 1;
      boolean hasRefType = false;
      int maxStringSize = 0;
      int maxNumberSize = 0;
      int maxDateSize = 0;
      int maxBooleanSize = 0;

      for (ValueType type : vtd.getTypes()) {
        String typeName = type.nameEn();
        switch (typeName) {
          case "String" -> maxStringSize = calculateSingleTypeSize("String", stringLength, numberPrecision);
          case "Number" -> maxNumberSize = calculateSingleTypeSize("Number", stringLength, numberPrecision);
          case "Date" -> maxDateSize = 8;
          case "Boolean" -> maxBooleanSize = 1;
          default -> hasRefType = true;
        }
      }

      totalCompositeSize += maxStringSize + maxNumberSize + maxDateSize + maxBooleanSize + (hasRefType ? 20 : 0);
      return totalCompositeSize;

    } else {
      int maxTypeSize = 0;
      for (ValueType type : vtd.getTypes()) {
        int size = calculateSingleTypeSize(type.nameEn(), stringLength, numberPrecision);
        if (size > maxTypeSize) maxTypeSize = size;
      }
      return maxTypeSize;
    }
  }

  private int calculateSingleTypeSize(String typeName, long stringLength, int numberPrecision) {
    return switch (typeName) {
      case "String" -> (stringLength == 0) ? 9999 : (int) stringLength * 3 + 2;
      case "Number" -> (numberPrecision / 2) + 1;
      case "Date" -> 8;
      case "Boolean" -> 1;
      default -> 16;
    };
  }
}