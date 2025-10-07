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
package com.github._1c_syntax.bsl.languageserver.configuration.databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticCompatibilityMode;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticScope;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticSeverity;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticTag;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticType;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiagnosticMetadataDeserializerTest {

  @Test
  void testDeserializeWithAllFields() throws Exception {
    // given
    String json = """
      {
        "type": "ERROR",
        "severity": "BLOCKER",
        "scope": "BSL",
        "modules": ["CommonModule", "SessionModule"],
        "minutesToFix": 15,
        "activatedByDefault": false,
        "compatibilityMode": "COMPATIBILITY_MODE_8_3_3",
        "tags": ["DEPRECATED", "BADPRACTICE"],
        "canLocateOnProject": true,
        "extraMinForComplexity": 2.5
      }
      """;

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
      .addDeserializer(DiagnosticMetadata.class, new DiagnosticMetadataDeserializer()));

    // when
    DiagnosticMetadata metadata = mapper.readValue(json, DiagnosticMetadata.class);

    // then
    assertThat(metadata.type()).isEqualTo(DiagnosticType.ERROR);
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.BLOCKER);
    assertThat(metadata.scope()).isEqualTo(DiagnosticScope.BSL);
    assertThat(metadata.modules()).containsExactly(ModuleType.CommonModule, ModuleType.SessionModule);
    assertThat(metadata.minutesToFix()).isEqualTo(15);
    assertThat(metadata.activatedByDefault()).isFalse();
    assertThat(metadata.compatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.COMPATIBILITY_MODE_8_3_3);
    assertThat(metadata.tags()).containsExactly(DiagnosticTag.DEPRECATED, DiagnosticTag.BADPRACTICE);
    assertThat(metadata.canLocateOnProject()).isTrue();
    assertThat(metadata.extraMinForComplexity()).isEqualTo(2.5);
  }

  @Test
  void testDeserializeWithPartialFields() throws Exception {
    // given - Only severity is specified, all other fields should use annotation defaults
    String json = """
      {
        "severity": "CRITICAL"
      }
      """;

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
      .addDeserializer(DiagnosticMetadata.class, new DiagnosticMetadataDeserializer()));

    // when
    DiagnosticMetadata metadata = mapper.readValue(json, DiagnosticMetadata.class);

    // then - severity is overridden
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.CRITICAL);
    
    // then - all other fields should use annotation defaults
    assertThat(metadata.type()).isEqualTo(DiagnosticType.ERROR); // default from annotation
    assertThat(metadata.scope()).isEqualTo(DiagnosticScope.ALL); // default from annotation
    assertThat(metadata.modules()).isEmpty(); // default from annotation
    assertThat(metadata.minutesToFix()).isEqualTo(0); // default from annotation
    assertThat(metadata.activatedByDefault()).isTrue(); // default from annotation
    assertThat(metadata.compatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED); // default from annotation
    assertThat(metadata.tags()).isEmpty(); // default from annotation
    assertThat(metadata.canLocateOnProject()).isFalse(); // default from annotation
    assertThat(metadata.extraMinForComplexity()).isEqualTo(0.0); // default from annotation
  }

  @Test
  void testDeserializeEmptyObject() throws Exception {
    // given - Empty object, all fields should use annotation defaults
    String json = "{}";

    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
      .addDeserializer(DiagnosticMetadata.class, new DiagnosticMetadataDeserializer()));

    // when
    DiagnosticMetadata metadata = mapper.readValue(json, DiagnosticMetadata.class);

    // then - all fields should use annotation defaults
    assertThat(metadata.type()).isEqualTo(DiagnosticType.ERROR);
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.MINOR);
    assertThat(metadata.scope()).isEqualTo(DiagnosticScope.ALL);
    assertThat(metadata.modules()).isEmpty();
    assertThat(metadata.minutesToFix()).isEqualTo(0);
    assertThat(metadata.activatedByDefault()).isTrue();
    assertThat(metadata.compatibilityMode()).isEqualTo(DiagnosticCompatibilityMode.UNDEFINED);
    assertThat(metadata.tags()).isEmpty();
    assertThat(metadata.canLocateOnProject()).isFalse();
    assertThat(metadata.extraMinForComplexity()).isEqualTo(0.0);
  }
}
