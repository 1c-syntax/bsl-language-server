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
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;

import java.util.Set;
import java.util.concurrent.Callable;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMembers;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
  // Никто не пишет в стандартные потоки stdout и stderr. Исключения — лишь места, где стандартный
  // поток нужен по протоколу или природе процесса. Это транспорт LSP в классе
  // LanguageServerLauncherConfiguration, транспорт MCP по stdio в классе McpStdioConfiguration и
  // аварийный fallback в классе ParentProcessWatcher на завершении процесса, когда логгер уже
  // недоступен. Новый класс с такой потребностью добавляется в список исключений осознанно, через ревью.

  @ArchTest
  static final ArchRule no_classes_should_access_standard_streams = noClasses()
    .that().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".cli.lsp.LanguageServerLauncherConfiguration")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".mcp.McpStdioConfiguration")
    .and().doNotHaveFullyQualifiedName(ROOT_PACKAGE + ".lsp.ParentProcessWatcher")
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
  // Правило фиксирует направление вызовов между пакетами по зависимостям времени компиляции (циклы
  // оно не проверяет). Группы слоёв:
  //   - головы: cli доступен только из Application (корня с MainApplication, подключающим подкоманды);
  //   - возможности LSP: фасад providers доступен только из голов lsp/cli/mcp; поставщики отдельных
  //     фич (codeactions, color, documenthighlight, documentlink, folding, rename, semantictokens) —
  //     только из providers; презентационный кластер codelenses/commands/databind/hover/inlayhints
  //     композируется внутри себя; а codelenses/completion/inlayhints вдобавок доступны из lsp,
  //     который резолвит их Data-DTO в ответ на resolve-запросы протокола;
  //   - узкие предметные пакеты с единственным потребителем: cfg (граф потока управления) и recognizer
  //     — только из diagnostics; jsonrpc (DTO нестандартных LSP-запросов) — только из lsp; formatting
  //     (движок форматирования) — из diagnostics и providers.
  // Application задан точным именем (корень с bootstrap-классами MainApplication и BSLLSBinding), Lsp —
  // точным именем пакета сервисов-голов; состояние LSP-клиента — отдельный листовой пакет client. Остальные
  // пакеты — абсолютными путями, иначе шаблон по имени diagnostics матчил бы и одноимённый пакет внутри
  // configuration. Ядро (configuration↔diagnostics↔context↔types↔…) переплетено циклами и здесь не
  // моделируется: у его пакетов нет фиксированного набора потребителей, который можно было бы закрепить.
  // Пакет websocket слоем не задан: его режим поднимает подкоманда cli через Spring
  // (@ConditionalOnWebApplication), без ссылок времени компиляции, поэтому слоистому правилу,
  // работающему по зависимостям компиляции, закреплять тут нечего.

  @ArchTest
  static final ArchRule layer_dependencies_are_respected = layeredArchitecture()
    .consideringOnlyDependenciesInLayers()

    // Точки входа и «головы»
    .layer("Application").definedBy(ROOT_PACKAGE)
    .layer("Lsp").definedBy(ROOT_PACKAGE + ".lsp")
    .layer("Mcp").definedBy(ROOT_PACKAGE + ".mcp..")
    .layer("CLI").definedBy(ROOT_PACKAGE + ".cli..")
    .layer("Reporters").definedBy(ROOT_PACKAGE + ".reporters..")

    // Возможности LSP: фасад providers и поставщики отдельных фич
    .layer("Providers").definedBy(ROOT_PACKAGE + ".providers..")
    .layer("CodeActions").definedBy(ROOT_PACKAGE + ".codeactions..")
    .layer("CodeLenses").definedBy(ROOT_PACKAGE + ".codelenses..")
    .layer("Commands").definedBy(ROOT_PACKAGE + ".commands..")
    .layer("Completion").definedBy(ROOT_PACKAGE + ".completion..")
    .layer("Color").definedBy(ROOT_PACKAGE + ".color..")
    .layer("Databind").definedBy(ROOT_PACKAGE + ".databind..")
    .layer("DocumentHighlight").definedBy(ROOT_PACKAGE + ".documenthighlight..")
    .layer("DocumentLink").definedBy(ROOT_PACKAGE + ".documentlink..")
    .layer("Folding").definedBy(ROOT_PACKAGE + ".folding..")
    .layer("Hover").definedBy(ROOT_PACKAGE + ".hover..")
    .layer("InlayHints").definedBy(ROOT_PACKAGE + ".inlayhints..")
    .layer("Rename").definedBy(ROOT_PACKAGE + ".rename..")
    .layer("SemanticTokens").definedBy(ROOT_PACKAGE + ".semantictokens..")

    // Домены и узкие предметные пакеты
    .layer("Diagnostics").definedBy(ROOT_PACKAGE + ".diagnostics..")
    .layer("Context").definedBy(ROOT_PACKAGE + ".context..")
    .layer("References").definedBy(ROOT_PACKAGE + ".references..")
    .layer("Configuration").definedBy(ROOT_PACKAGE + ".configuration..")
    .layer("Cfg").definedBy(ROOT_PACKAGE + ".cfg..")
    .layer("Jsonrpc").definedBy(ROOT_PACKAGE + ".jsonrpc..")
    .layer("Recognizer").definedBy(ROOT_PACKAGE + ".recognizer..")
    .layer("Formatting").definedBy(ROOT_PACKAGE + ".formatting..")

    // Голова cli доступна только из корня.
    .whereLayer("CLI").mayOnlyBeAccessedByLayers("Application")

    // Фасад providers — только из голов; поставщики фич — из providers (и из соседних фич,
    // где фичи композируются друг с другом).
    .whereLayer("Providers").mayOnlyBeAccessedByLayers("Lsp", "CLI", "Mcp")
    .whereLayer("CodeActions").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("Color").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("Completion").mayOnlyBeAccessedByLayers("Providers", "Lsp")
    .whereLayer("DocumentHighlight").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("DocumentLink").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("Folding").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("Rename").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("SemanticTokens").mayOnlyBeAccessedByLayers("Providers")
    .whereLayer("CodeLenses").mayOnlyBeAccessedByLayers("Providers", "Lsp", "CodeActions", "Commands", "Databind")
    .whereLayer("Commands").mayOnlyBeAccessedByLayers("Providers", "CodeLenses", "Databind")
    .whereLayer("Databind").mayOnlyBeAccessedByLayers("CodeLenses", "Commands", "InlayHints")
    .whereLayer("Hover").mayOnlyBeAccessedByLayers("Providers", "InlayHints")
    .whereLayer("InlayHints").mayOnlyBeAccessedByLayers("Providers", "Lsp", "Commands", "Databind")

    // Узкие предметные пакеты с единственным легитимным потребителем
    .whereLayer("Cfg").mayOnlyBeAccessedByLayers("Diagnostics")
    .whereLayer("Recognizer").mayOnlyBeAccessedByLayers("Diagnostics")
    .whereLayer("Jsonrpc").mayOnlyBeAccessedByLayers("Lsp")
    .whereLayer("Formatting").mayOnlyBeAccessedByLayers("Diagnostics", "Providers")

    .as("Слоистая архитектура: головы (lsp/cli/mcp), фасад providers с поставщиками "
      + "фич, домены и узкие предметные пакеты с фиксированными потребителями");

  // --- Листовой пакет utils -----------------------------------------------------------------------
  // utils — переиспользуемые хелперы (AST, диапазоны, строки, файлы) — обязан оставаться листом:
  // он не зависит ни от одного пакета проекта вне самого utils. Хелперу, которому нужен доменный
  // тип, место в этом домене, а не здесь.

  @ArchTest
  static final ArchRule utils_should_not_depend_on_project_packages = noClasses()
    .that().resideInAPackage(ROOT_PACKAGE + ".utils..")
    .should().dependOnClassesThat(
      resideInAPackage(ROOT_PACKAGE + "..")
        .and(resideOutsideOfPackage(ROOT_PACKAGE + ".utils..")))
    .because("utils — листовой пакет: не зависит от доменных пакетов проекта");

  // --- Ацикличность чистых доменов ----------------------------------------------------------------
  // Ядро (configuration↔diagnostics↔context↔providers↔…) сейчас переплетено циклами и здесь не
  // проверяется. Домены вне этого клубка уже ацикличны — фиксируем это, чтобы новый код не завёл
  // цикл среди них. Пакеты ядра в срез не входят; aop тоже (его рёбра — артефакт compile-time
  // weaving AspectJ, а не исходных зависимостей), он просто не попадает в назначенные срезы.

  static final Set<String> ACYCLIC_DOMAINS = Set.of(
    "cfg", "cli", "client", "codelenses", "commands", "databind", "events", "infrastructure",
    "jsonrpc", "mcp", "recognizer", "reporters", "utils", "websocket"
  );

  static final SliceAssignment ACYCLIC_DOMAIN_SLICES = new SliceAssignment() {
    @Override
    public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
      var prefix = ROOT_PACKAGE + ".";
      var packageName = javaClass.getPackageName();
      if (packageName.startsWith(prefix)) {
        var rest = packageName.substring(prefix.length());
        var dot = rest.indexOf('.');
        var topLevel = dot < 0 ? rest : rest.substring(0, dot);
        if (ACYCLIC_DOMAINS.contains(topLevel)) {
          return SliceIdentifier.of(topLevel);
        }
      }
      return SliceIdentifier.ignore();
    }

    @Override
    public String getDescription() {
      return "ацикличные доменные пакеты вне ядра";
    }
  };

  @ArchTest
  static final ArchRule acyclic_domains_stay_free_of_cycles = slices()
    .assignedFrom(ACYCLIC_DOMAIN_SLICES)
    .should().beFreeOfCycles();

  // --- Внедрение зависимостей ---------------------------------------------------------------------
  // Предпочтительно конструкторное внедрение (Lombok @RequiredArgsConstructor). @Autowired на полях
  // и сеттерах — нежелателен. Исключения — классы из списка ниже, где конструкторное внедрение
  // невозможно: AspectJ-аспекты и logback-appender (создаются вне Spring-контейнера), self-инъекция
  // в code lens (циклична), именованные бины/провайдеры. Новый @Autowired вне списка валит сборку.

  private static final Set<String> ALLOWED_AUTOWIRED_CLASSES = Set.of(
    ROOT_PACKAGE + ".aop.MeasuresAspect",
    ROOT_PACKAGE + ".aop.SentryAspect",
    ROOT_PACKAGE + ".codelenses.DebugTestCodeLensSupplier",
    ROOT_PACKAGE + ".codelenses.RunAllTestsCodeLensSupplier",
    ROOT_PACKAGE + ".codelenses.RunTestCodeLensSupplier",
    ROOT_PACKAGE + ".context.DocumentContext",
    ROOT_PACKAGE + ".context.computer.CognitiveComplexityComputer",
    ROOT_PACKAGE + ".context.computer.CyclomaticComplexityComputer",
    ROOT_PACKAGE + ".diagnostics.AbstractMetadataDiagnostic",
    ROOT_PACKAGE + ".infrastructure.LanguageClientAwareAppender",
    ROOT_PACKAGE + ".reporters.ReportersAggregator"
  );

  @ArchTest
  static final ArchRule no_members_should_be_injected_via_autowired = noMembers()
    .that().areDeclaredInClassesThat(
      DescribedPredicate.describe(
        "вне списка легитимных @Autowired",
        (JavaClass javaClass) -> !ALLOWED_AUTOWIRED_CLASSES.contains(javaClass.getFullName())))
    .should().beAnnotatedWith(Autowired.class)
    .because("предпочтительно конструкторное внедрение (Lombok @RequiredArgsConstructor); "
      + "оставшиеся @Autowired — AspectJ-аспекты, logback-appender и self-инъекция, "
      + "которые нельзя внедрить через конструктор");

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
