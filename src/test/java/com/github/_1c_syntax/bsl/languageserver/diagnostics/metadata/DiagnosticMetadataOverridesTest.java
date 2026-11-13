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
package com.github._1c_syntax.bsl.languageserver.diagnostics.metadata;

import com.github._1c_syntax.bsl.types.ModuleType;
import io.leangen.geantyref.TypeFactory;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticMetadataOverridesTest {

  private static DiagnosticMetadata base() throws Exception {
    return TypeFactory.annotation(DiagnosticMetadata.class, Map.of(
      "type", DiagnosticType.CODE_SMELL,
      "severity", DiagnosticSeverity.MAJOR,
      "minutesToFix", 3,
      "tags", new DiagnosticTag[]{DiagnosticTag.STANDARD},
      "modules", new ModuleType[]{ModuleType.CommonModule}
    ));
  }

  @Test
  void testApplyOverridesOnlySpecifiedAttributes() throws Exception {
    // given
    var override = DiagnosticMetadataOverrides.create(Map.of(
      "severity", DiagnosticSeverity.CRITICAL,
      "lspSeverity", "Error"
    ));

    // when
    var metadata = DiagnosticMetadataOverrides.applyTo(base(), override);

    // then
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.CRITICAL);
    assertThat(metadata.lspSeverity()).isEqualTo("Error");
    assertThat(metadata.type()).isEqualTo(DiagnosticType.CODE_SMELL);
    assertThat(metadata.minutesToFix()).isEqualTo(3);
    assertThat(metadata.tags()).containsExactly(DiagnosticTag.STANDARD);
    assertThat(metadata.modules()).containsExactly(ModuleType.CommonModule);
  }

  @Test
  void testApplyOverridesAttributesExplicitlySetToDefaultValues() throws Exception {
    // given - values equal to the annotation defaults are still explicit overrides
    var override = DiagnosticMetadataOverrides.create(Map.of(
      "type", DiagnosticType.ERROR,
      "severity", DiagnosticSeverity.MINOR,
      "modules", new ModuleType[0]
    ));

    // when
    var metadata = DiagnosticMetadataOverrides.applyTo(base(), override);

    // then
    assertThat(metadata.type()).isEqualTo(DiagnosticType.ERROR);
    assertThat(metadata.severity()).isEqualTo(DiagnosticSeverity.MINOR);
    assertThat(metadata.modules()).isEmpty();
    assertThat(metadata.tags()).containsExactly(DiagnosticTag.STANDARD);
  }

  @Test
  void testApplyEmptyOverrideReturnsBase() throws Exception {
    // given
    var base = base();
    var override = DiagnosticMetadataOverrides.create(Map.of());

    // when
    var metadata = DiagnosticMetadataOverrides.applyTo(base, override);

    // then
    assertThat(metadata).isSameAs(base);
  }

  @Test
  void testApplyPlainAnnotationReplacesMetadataCompletely() throws Exception {
    // given - annotation instance created not via DiagnosticMetadataOverrides
    var override = TypeFactory.annotation(DiagnosticMetadata.class, Map.of(
      "severity", DiagnosticSeverity.CRITICAL
    ));

    // when
    var metadata = DiagnosticMetadataOverrides.applyTo(base(), override);

    // then
    assertThat(metadata).isSameAs(override);
  }

  @Test
  void testCreatedOverrideBehavesAsAnnotation() throws Exception {
    // given
    var override = DiagnosticMetadataOverrides.create(Map.of(
      "severity", DiagnosticSeverity.CRITICAL,
      "unknownAttribute", "ignored"
    ));
    var sameOverride = DiagnosticMetadataOverrides.create(Map.of(
      "severity", DiagnosticSeverity.CRITICAL
    ));
    var plainAnnotation = TypeFactory.annotation(DiagnosticMetadata.class, Map.of(
      "severity", DiagnosticSeverity.CRITICAL
    ));

    // then
    assertThat(override.annotationType()).isEqualTo(DiagnosticMetadata.class);
    assertThat(override.severity()).isEqualTo(DiagnosticSeverity.CRITICAL);
    assertThat(override.modules()).isEmpty();
    assertThat(override)
      .isEqualTo(sameOverride)
      .hasSameHashCodeAs(sameOverride)
      .isEqualTo(plainAnnotation);
  }

  @Test
  void testOverridesWithDifferentSpecifiedAttributesAreNotEqual() {
    // given - same attribute values, but severity is specified only in the first one
    var withSeverity = DiagnosticMetadataOverrides.create(Map.of(
      "severity", DiagnosticSeverity.MINOR
    ));
    var empty = DiagnosticMetadataOverrides.create(Map.of());

    // then
    assertThat(withSeverity).isNotEqualTo(empty);
  }

  @Test
  void testCreateWithInvalidValueThrows() {
    assertThatThrownBy(() -> DiagnosticMetadataOverrides.create(Map.of("severity", "CRITICAL")))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
