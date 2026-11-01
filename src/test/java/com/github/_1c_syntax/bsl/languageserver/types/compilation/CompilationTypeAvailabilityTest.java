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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CompilationTypeAvailabilityTest extends AbstractServerContextAwareTest {
  @Autowired
  private TypeRegistry registry;

  @ParameterizedTest
  @ValueSource(strings = {"СправочникОбъект.Справочник1", "ДокументМенеджер.Документ1"})
  void applicationObjectInheritsItsPlatformAvailability(String name) {
    initServerContext("src/test/resources/metadata/designer");
    var object = registry.resolve(name).orElseThrow();
    var generic = registry.genericOf(object).orElseThrow();
    // Минимальный тестовый пакет не содержит доступность СП; задаём опубликованные контексты типа.
    registry.registerTypeMetadata(generic, new PlatformMetadata("8.0", "", List.of(), Set.of(
      Availability.SERVER, Availability.THICK_CLIENT, Availability.EXTERNAL_CONNECTION,
      Availability.MOBILE_APPLICATION_SERVER, Availability.MOBILE_STANDALONE_SERVER), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of()), FileType.BSL);
    var platform = registry.getTypeMetadata(generic, FileType.BSL);

    assertThat(platform.availabilities()).contains(Availability.SERVER).doesNotContain(Availability.THIN_CLIENT);
    assertThat(registry.getTypeMetadata(object, FileType.BSL).availabilities()).isEqualTo(platform.availabilities());
  }

  @Test
  void configurationGlobalValueRetainsItsPlatformPropertyAvailability() {
    initServerContext("src/test/resources/metadata/designer");
    var global = registry.intern(TypeKind.PLATFORM, "ГлобальныйКонтекст");
    var before = registry.findMember(global, MemberKind.PROPERTY, "Справочники", FileType.BSL).orElseThrow();
    var metadata = new PlatformMetadata("8.0", "", List.of(), Set.of(Availability.SERVER), null,
      BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());
    registry.registerMemberSource(global, () -> List.of(
      MemberDescriptor.property("Справочники", TypeRef.UNKNOWN, "").withMetadata(metadata)), FileType.BSL);

    var after = registry.findMember(global, MemberKind.PROPERTY, "Справочники", FileType.BSL).orElseThrow();
    assertThat(after.returnTypes()).isEqualTo(before.returnTypes());
    assertThat(after.metadata()).isEqualTo(metadata);
  }
}
