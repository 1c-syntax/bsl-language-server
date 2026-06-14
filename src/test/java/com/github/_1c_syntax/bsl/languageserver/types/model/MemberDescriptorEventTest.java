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
package com.github._1c_syntax.bsl.languageserver.types.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Фабрика {@link MemberDescriptor#event(String, String, List)} — событие
 * платформенного типа: имя, описание, сигнатура параметров без возвращаемого
 * типа.
 */
class MemberDescriptorEventTest {

  @Test
  void eventFactorySetsKindAndKeepsSignatures() {
    var param = new ParameterDescriptor("Отказ", TypeSet.of(TypeRef.UNKNOWN), false, "", "");
    var signature = new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");

    var event = MemberDescriptor.event(
      "ПриОткрытии",
      "Возникает при открытии формы.",
      List.of(signature)
    );

    assertThat(event.kind()).isEqualTo(MemberKind.EVENT);
    assertThat(event.name()).isEqualTo("ПриОткрытии");
    assertThat(event.description()).isEqualTo("Возникает при открытии формы.");
    // У события нет returnType — handler без возврата.
    assertThat(event.returnTypes()).isEqualTo(TypeSet.EMPTY);
    assertThat(event.signatures()).hasSize(1);
    assertThat(event.signatures().get(0).parameters()).hasSize(1);
    assertThat(event.signatures().get(0).parameters().get(0).name()).isEqualTo("Отказ");
    // Не generic, не async — событие специальный kind, отдельные флаги default'ные.
    assertThat(event.generic()).isFalse();
    assertThat(event.async()).isFalse();
    assertThat(event.metadata()).isEqualTo(PlatformMetadata.EMPTY);
  }
}
