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

import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.configuration.Language;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MethodCallUnavailablePlatformTest extends AbstractDiagnosticTest<MethodCallUnavailableDiagnostic> {
  private static final Path ROOT = Path.of("src/test/resources/metadata/designer");
  @Autowired
  private TypeService types;
  @Autowired
  private TypeRegistry registry;

  private static final PlatformMetadata SERVER_METADATA = new PlatformMetadata("8.0", "", List.of(), Set.of(
    Availability.SERVER, Availability.THICK_CLIENT, Availability.EXTERNAL_CONNECTION,
    Availability.MOBILE_APPLICATION_SERVER, Availability.MOBILE_STANDALONE_SERVER), null,
    BilingualString.EMPTY, BilingualString.EMPTY, List.of(), List.of());

  MethodCallUnavailablePlatformTest() {
    super(MethodCallUnavailableDiagnostic.class);
  }

  @BeforeEach
  void loadPlatformAvailability() {
    initServerContext(ROOT);
    // Тестовый пакет содержит сигнатуры, но не полные данные СП. Задаём контексты из СП явно.
    var manager = registry.resolve("СправочникМенеджер.Справочник1").orElseThrow();
    var generic = registry.genericOf(manager).orElseThrow();
    registry.registerTypeMetadata(generic, SERVER_METADATA, FileType.BSL);
    var managerMembers = registry.getMembers(generic, FileType.BSL).stream().map(member -> member.matches("Выбрать")
      ? member.withBilingualName(new BilingualString("Выбрать", "Select")).withMetadata(SERVER_METADATA) : member).toList();
    registry.registerMemberOverride(generic, () -> managerMembers, FileType.BSL);

    var global = registry.intern(TypeKind.PLATFORM, "ГлобальныйКонтекст");
    var globals = registry.getMembers(global, FileType.BSL).stream().map(member -> member.matches("Справочники")
      ? member.withBilingualName(new BilingualString("Справочники", "Catalogs")).withMetadata(SERVER_METADATA) : member).toList();
    registry.registerMemberOverride(global, () -> globals, FileType.BSL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Справочники.Справочник1.Выбрать()", "Catalogs.Справочник1.Select()"})
  void catalogSelectionIsUnavailableInThinClient(String call) {
    var document = form("""
        &AtClient
        Procedure Caller()
          %s;
        EndProcedure
        """.formatted(call));
    enableOnly("checkThinClient");

    var diagnostics = getDiagnostics(document);
    var targets = types.membersAt(document, new Position(2, 2 + call.lastIndexOf('.') + 1));
    assertThat(diagnostics).as("targets=%s", targets.stream()
        .map(target -> target.owner() + ": " + target.descriptor().metadata().availabilities()).toList())
      .singleElement().satisfies(diagnostic -> {
        var property = call.substring(0, call.indexOf('.'));
        assertThat(diagnostic.getMessage().getLeft()).isEqualTo(
          "Свойство \"" + property + "\" недоступно в контексте обращения (Тонкий клиент)");
        assertThat(diagnostic.getRange().getStart()).isEqualTo(new Position(2, 2));
        assertThat(diagnostic.getRelatedInformation()).isNull();
      });
  }

  @ParameterizedTest
  @CsvSource({"AtServerNoContext, checkServer", "AtClient, checkThickClientManagedApplication"})
  void catalogSelectionIsAvailableInServerAndThickClient(String directive, String option) {
    var document = form("""
      &%s
      Procedure Caller()
        Справочники.Справочник1.Выбрать();
      EndProcedure
      """.formatted(directive));
    enableOnly(option);
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"RU, Свойство \"Catalogs\" недоступно в контексте обращения (Тонкий клиент)",
    "EN, Property \"Catalogs\" is not available in the access context (Thin client)"})
  void aBareGlobalPropertyIsCheckedAndLocalized(Language language, String expected) {
    var document = form("&AtClient\nProcedure Caller()\n  Value = Catalogs;\nEndProcedure\n");
    enableOnly("checkThinClient");
    configuration.setLanguage(language);
    diagnosticInstance.getInfo().refresh();
    assertThat(getDiagnostics(document)).singleElement().satisfies(diagnostic ->
      assertThat(diagnostic.getMessage().getLeft()).isEqualTo(expected));
  }

  @Test
  void localParameterShadowsTheGlobalProperty() {
    var document = form("""
      &AtClient
      Procedure Caller(Catalogs)
        Value = Catalogs;
      EndProcedure
      """);
    enableOnly("checkThinClient");
    assertThat(getDiagnostics(document)).isEmpty();
  }

  @Test
  void aCallArgumentHasItsOwnAvailabilityChain() {
    var document = form("""
      &AtClient
      Procedure Caller()
        Справочники.Справочник1.Выбрать(Справочники.Справочник1.Выбрать());
      EndProcedure
      """);
    enableOnly("checkThinClient");
    assertThat(getDiagnostics(document)).hasSize(2).allSatisfy(diagnostic ->
      assertThat(diagnostic.getMessage().getLeft()).startsWith("Свойство \"Справочники\""));
  }

  @Test
  void aTypedManagerParameterDoesNotNeedTheGlobalPropertyToBeChecked() {
    var document = form("""
      // Параметры:
      //  Менеджер - СправочникМенеджер.Справочник1
      &AtClient
      Procedure Caller(Менеджер)
        Менеджер.Выбрать();
      EndProcedure
      """);
    enableOnly("checkThinClient");
    assertThat(getDiagnostics(document)).singleElement().satisfies(diagnostic ->
      assertThat(diagnostic.getMessage().getLeft()).startsWith("Метод \"Выбрать\""));
  }

  @Test
  void aRestrictedPlatformMethodIsCheckedOnAnAvailableReceiverType() {
    registry.registerMemberOverride(registry.resolve("Массив").orElseThrow(), () -> List.of(
      MemberDescriptor.method("ServerOnly").withMetadata(SERVER_METADATA)), FileType.BSL);
    var document = form("""
      &AtClient
      Procedure Caller()
        Value = New Array;
        Value.ServerOnly();
      EndProcedure
      """);
    enableOnly("checkThinClient");
    assertThat(getDiagnostics(document)).singleElement().satisfies(diagnostic ->
      assertThat(diagnostic.getMessage().getLeft()).startsWith("Метод \"ServerOnly\""));
  }

  @Test
  void disabledContextDoesNotReportPlatformMembers() {
    var document = form("&AtClient\nProcedure Caller()\n  Справочники.Справочник1.Выбрать();\nEndProcedure\n");
    enableOnly("checkServer");
    assertThat(getDiagnostics(document)).isEmpty();
  }

  private DocumentContext form(String source) {
    return TestUtils.getDocumentContext(
      ROOT.resolve("Catalogs/Справочник1/Forms/ФормаСписка/Ext/Form/Module.bsl").toUri(), source, context);
  }

  private void enableOnly(String option) {
    var options = new HashMap<>(diagnosticInstance.getInfo().getDefaultConfiguration());
    options.replaceAll((name, value) -> "off");
    options.put(option, "on");
    diagnosticInstance.configure(options);
  }
}
