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
import org.springframework.context.ApplicationEvent;

import java.util.concurrent.Callable;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
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
  // stdout/stderr запрещены везде, кроме мест, где они нужны по протоколу/природе процесса:
  //  - ParentProcessWatcher — fallback-логирование до инициализации логгера;
  //  - LanguageServerLauncherConfiguration — поток stdout как транспорт LSP;
  //  - VersionCommand — печать версии в CLI;
  //  - McpStdioConfiguration — поток stdout как транспорт MCP (stdio).
  // Новый класс, которому реально нужен stdout, добавляется в этот список осознанно (через ревью).

  @ArchTest
  static final ArchRule no_classes_should_access_standard_streams_except_allowed = noClasses()
    .that().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".ParentProcessWatcher")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".cli.lsp.LanguageServerLauncherConfiguration")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".cli.VersionCommand")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".mcp.McpStdioConfiguration")
    .should(ACCESS_STANDARD_STREAMS)
    .because("вывод в stdout/stderr допустим только в транспортных и CLI-точках из списка выше; "
      + "остальной код использует slf4j и доменные каналы вывода");

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
  // Слоистую архитектуру вводим «потихоньку»: пока зафиксировано единственное ограничение, которое
  // уже выполняется, — слой точек входа cli не должен использоваться доменными слоями (исключение —
  // reporters.infrastructure, где сборка отчёта анализа стартует из AnalyzeCommand). По мере чистки
  // зависимостей сюда добавляются новые whereLayer(...)-ограничения, а cli со временем ужесточается
  // до mayNotBeAccessedByAnyLayer().

  @ArchTest
  static final ArchRule layer_dependencies_are_respected = layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
    .layer("CLI").definedBy("..cli..")
    .layer("Reporters").definedBy("..reporters..")
    .layer("Providers").definedBy("..providers..")
    .layer("Diagnostics").definedBy("..diagnostics..")
    .layer("References").definedBy("..references..")
    .layer("Configuration").definedBy("..configuration..")
    .layer("Context").definedBy("..context..")

    .whereLayer("CLI").mayOnlyBeAccessedByLayers("Reporters")

    .because("cli — слой точек входа (подкоманды picocli); доменные слои не должны от него зависеть");

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
