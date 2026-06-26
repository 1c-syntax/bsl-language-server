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
package com.github._1c_syntax.bsl.languageserver.architecture;

import com.github._1c_syntax.bsl.languageserver.diagnostics.BSLDiagnostic;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMetadata;
import com.github._1c_syntax.bsl.languageserver.reporters.DiagnosticReporter;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;

import java.util.concurrent.Callable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;

/**
 * Архитектурные тесты на базе <a href="https://www.archunit.org/">ArchUnit</a>.
 * <p>
 * Фиксируют согласованные по коду конвенции (именование, обязательные аннотации, направление
 * зависимостей между слоями), чтобы новые классы не нарушали сложившуюся архитектуру незаметно.
 * Правила специально сформулированы так, чтобы проходить на текущем состоянии репозитория; если
 * для нового кода правило не подходит — обсуждайте смягчение правила, а не «обход» его.
 * <p>
 * Тесты не поднимают Spring-контекст и работают только со скомпилированными классами, поэтому
 * выполняются быстро. Классы тестов и сгенерированные {@code package-info} из анализа исключены.
 */
@AnalyzeClasses(
  packages = ArchitectureTest.ROOT_PACKAGE,
  importOptions = {ImportOption.DoNotIncludeTests.class, ArchitectureTest.DoNotIncludePackageInfo.class}
)
class ArchitectureTest {

  static final String ROOT_PACKAGE = "com.github._1c_syntax.bsl.languageserver";

  // --- Диагностики --------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule concrete_diagnostics_should_be_named_with_diagnostic_suffix = classes()
    .that().areAssignableTo(BSLDiagnostic.class)
    .and().areNotInterfaces()
    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
    .should().haveSimpleNameEndingWith("Diagnostic")
    .because("конкретные диагностики именуются с суффиксом Diagnostic (XxxDiagnostic)");

  @ArchTest
  static final ArchRule concrete_diagnostics_should_be_annotated_with_metadata = classes()
    .that().areAssignableTo(BSLDiagnostic.class)
    .and().areNotInterfaces()
    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
    .should().beAnnotatedWith(DiagnosticMetadata.class)
    .because("каждая конкретная диагностика обязана нести @DiagnosticMetadata "
      + "(её читает инфраструктура регистрации и i18n)");

  @ArchTest
  static final ArchRule abstract_diagnostics_should_be_named_with_abstract_prefix = classes()
    .that().areAssignableTo(BSLDiagnostic.class)
    .and().areNotInterfaces()
    .and().haveModifier(JavaModifier.ABSTRACT)
    .should().haveSimpleNameStartingWith("Abstract")
    .because("базовые (абстрактные) диагностики именуются с префиксом Abstract");

  @ArchTest
  static final ArchRule diagnostic_metadata_should_annotate_only_diagnostics = classes()
    .that().areAnnotatedWith(DiagnosticMetadata.class)
    .should().beAssignableTo(BSLDiagnostic.class)
    .because("@DiagnosticMetadata имеет смысл только на реализациях BSLDiagnostic");

  @ArchTest
  static final ArchRule diagnostics_should_reside_in_diagnostics_package = classes()
    .that().areAssignableTo(BSLDiagnostic.class)
    .should().resideInAPackage("..diagnostics..")
    .because("все реализации BSLDiagnostic живут в пакете diagnostics");

  // --- Стандартные потоки -------------------------------------------------------------------------
  // Никто не пишет в стандартные потоки (stdout/stderr). Исключения — лишь места, где стандартный
  // поток нужен по протоколу или природе процесса:
  //  - LanguageServerLauncherConfiguration — stdout как транспорт LSP;
  //  - McpStdioConfiguration — stdout как транспорт MCP (stdio);
  //  - ParentProcessWatcher — аварийный fallback на завершении, когда логгер уже недоступен.
  // Новый класс, которому реально нужен стандартный поток, добавляется сюда осознанно (через ревью).

  @ArchTest
  static final ArchRule no_classes_should_access_standard_streams = noClasses()
    .that().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".cli.lsp.LanguageServerLauncherConfiguration")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".mcp.McpStdioConfiguration")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".ParentProcessWatcher")
    .should(ACCESS_STANDARD_STREAMS)
    .because("вывод в стандартные потоки допустим только в транспортных точках и аварийном "
      + "fallback из списка выше; остальной код пишет через slf4j");

  // --- Провайдеры ---------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule providers_should_be_named_with_provider_suffix = classes()
    .that().resideInAPackage("..providers..")
    .and().areTopLevelClasses()
    .should().haveSimpleNameEndingWith("Provider")
    .because("классы-возможности LSP в пакете providers именуются с суффиксом Provider");

  // --- Репортеры ----------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule reporters_should_be_named_with_reporter_suffix = classes()
    .that().areAssignableTo(DiagnosticReporter.class)
    .and().areNotInterfaces()
    .should().haveSimpleNameEndingWith("Reporter")
    .because("реализации DiagnosticReporter именуются с суффиксом Reporter "
      + "(классы данных отчётов — Report/Entry — не реализуют этот интерфейс)");

  // --- CLI-команды --------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule cli_commands_should_be_named_with_command_suffix = classes()
    .that().resideInAPackage("..cli..")
    .and().areAssignableTo(Callable.class)
    .should().haveSimpleNameEndingWith("Command")
    .because("подкоманды picocli в пакете cli именуются с суффиксом Command");

  // --- События ------------------------------------------------------------------------------------
  // Правило двунаправленное: «в пакете events лежат только события» и «события лежат только в events».

  @ArchTest
  static final ArchRule classes_in_events_packages_should_be_application_events = classes()
    .that().resideInAPackage("..events..")
    .and().areTopLevelClasses()
    .should().beAssignableTo(ApplicationEvent.class)
    .andShould().haveSimpleNameEndingWith("Event")
    .because("в пакетах events лежат только Spring ApplicationEvent с суффиксом Event");

  @ArchTest
  static final ArchRule application_events_should_reside_in_events_packages = classes()
    .that().areAssignableTo(ApplicationEvent.class)
    .and().areTopLevelClasses()
    .should().resideInAPackage("..events..")
    .because("каждый Spring ApplicationEvent должен лежать в пакете events");

  @ArchTest
  static final ArchRule classes_named_event_should_reside_in_events_packages = classes()
    .that().haveSimpleNameEndingWith("Event")
    .and().areTopLevelClasses()
    .should().resideInAPackage("..events..")
    .because("класс с суффиксом Event должен лежать в пакете events");

  // --- Логирование --------------------------------------------------------------------------------

  @ArchTest
  static final ArchRule no_classes_should_use_java_util_logging = noClasses()
    .should().dependOnClassesThat().resideInAPackage("java.util.logging..")
    .because("логирование ведётся через slf4j (Lombok @Slf4j), а не через java.util.logging");

  // --- Слои и зависимости -------------------------------------------------------------------------
  // Ограничиваем два направления:
  //   - cli (точки входа) не должен использоваться ни одним слоем;
  //   - providers (возможности LSP) не должны вызываться из diagnostics.
  // Связи вроде context→diagnostics (DiagnosticComputer) и configuration→diagnostics.metadata —
  // легитимны и НЕ ограничиваются. Прежние инверсии (reporters→cli, diagnostics→providers через
  // createCodeActions и FormatProvider) устранены рефакторингом, поэтому правило строгое — без
  // FreezingArchRule и базовой линии: любое новое межслойное нарушение валит сборку сразу.
  // Важно: пакеты заданы абсолютными путями, иначе шаблон "..diagnostics.." матчил бы и
  // configuration.diagnostics, превращая внутрислойные связи в мнимые межслойные.

  @ArchTest
  static final ArchRule layer_dependencies_are_respected = layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
    .layer("CLI").definedBy(ROOT_PACKAGE + ".cli..")
    .layer("Reporters").definedBy(ROOT_PACKAGE + ".reporters..")
    .layer("Providers").definedBy(ROOT_PACKAGE + ".providers..")
    .layer("Diagnostics").definedBy(ROOT_PACKAGE + ".diagnostics..")
    .layer("Context").definedBy(ROOT_PACKAGE + ".context..")
    .layer("References").definedBy(ROOT_PACKAGE + ".references..")
    .layer("Configuration").definedBy(ROOT_PACKAGE + ".configuration..")

    .whereLayer("CLI").mayNotBeAccessedByAnyLayer()
    .whereLayer("Providers")
    .mayOnlyBeAccessedByLayers("CLI", "Reporters", "Context", "References", "Configuration")

    .as("Слоистая архитектура: cli и providers не используются «снизу» (ядром/diagnostics)");

  // --- Внедрение зависимостей ---------------------------------------------------------------------
  // Предпочтительно конструкторное внедрение (Lombok @RequiredArgsConstructor). @Autowired на полях
  // и сеттерах — нежелателен. Текущие точки нельзя убрать одномоментно: self-инъекция в code lens
  // (через конструктор невозможна), именованные бины в ReportersAggregator, setter-инъекция в
  // AspectJ-аспектах и logback-appender (объекты вне Spring-контейнера). Поэтому правило заморожено:
  // существующие места зафиксированы базовой линией, а любое НОВОЕ @Autowired валит сборку.

  @ArchTest
  static final ArchRule no_members_should_be_injected_via_autowired = FreezingArchRule.freeze(
    noMembers()
      .should().beAnnotatedWith(Autowired.class)
      .as("Поля и методы не должны помечаться @Autowired (предпочтительно конструкторное внедрение)"));

  /**
   * Исключает сгенерированные {@code package-info} из анализа: это не классы предметной области,
   * и без фильтра они ломали бы правила по простому имени класса.
   */
  static final class DoNotIncludePackageInfo implements ImportOption {
    @Override
    public boolean includes(Location location) {
      return !location.contains("package-info");
    }
  }
}
