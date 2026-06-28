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
  // ПОЛНАЯ, БЕЗ ПРОБЕЛОВ карта разрешённых зависимостей между пакетами. Каждый пакет проекта —
  // отдельный слой, и для КАЖДОГО задан положительный whitelist «от чего он вправе зависеть»
  // (mayOnlyAccessLayers), либо «не зависит ни от чего» (mayNotAccessAnyLayer) для листьев. Списки
  // выведены из РОЛИ пакета (на что он по замыслу опирается), а не из текущих import'ов, поэтому
  // правило отлавливает и уже существующий код, нарушивший замысел, а не «проходит по построению».
  //
  // Полнота гарантируется двумя решениями:
  //   1. Рассматриваются только зависимости внутрь самого проекта —
  //      consideringOnlyDependenciesInAnyPackage(ROOT..). Внешние библиотеки (lsp4j, spring, antlr,
  //      JDK) под правило не попадают; ВСЕ внутрипроектные рёбра — попадают.
  //   2. Слоем объявлен каждый top-level пакет (плюс выделенный листовой слой DiagnosticsMetadata).
  //      Поэтому любое ребро A→B между пакетами проекта проверяется outgoing-списком слоя A —
  //      «дыр» вида feature→foundation, не покрытых ни одним списком, не остаётся.
  // Единственный неназначенный пакет — aop (рёбра AspectJ compile-time weaving, не исходные
  // зависимости): в байткоде в него никто не входит (ссылки на него — только в Javadoc @link),
  // поэтому он не нарушает полноту. websocket — лист без внутренних зависимостей.
  //
  // Известные циклы оставлены ОСОЗНАННО (правило их допускает, но они помечены как долг):
  //   - Configuration↔Context: configuration читает рабочую область context, context читает
  //     конфигурацию. Разрывается позже инверсией событий.
  //   - References↔Types: взаимные ссылки индексов. Разрывается позже.
  // Появление НОВЫХ циклов среди уже ацикличных пакетов ловит отдельное правило ниже
  // (acyclic_domains_stay_free_of_cycles).
  //
  // Замечание про inline-константы: codeactions и diagnostics ссылаются на DiagnosticProvider.SOURCE
  // (public static final String) — javac встраивает значение, ребра в байткоде нет, поэтому Providers
  // в их whitelist отсутствует (и не должен — фичи не зависят от фасада providers).

  @ArchTest
  static final ArchRule layer_dependencies_are_respected = layeredArchitecture()
    .consideringOnlyDependenciesInAnyPackage(ROOT_PACKAGE + "..")
    // Рёбра в пакет aop — артефакт AspectJ compile-time weaving (вплетённые вызовы аспектов в
    // байткоде, исходных import'ов нет), а не проектные зависимости. Исключаем их, оставляя aop
    // единственным неназначенным пакетом.
    .ignoreDependency(DescribedPredicate.alwaysTrue(), resideInAPackage(ROOT_PACKAGE + ".aop.."))

    // --- Слои: каждый top-level пакет проекта ---
    // Точка входа и «головы»
    .layer("Application").definedBy(ROOT_PACKAGE)
    .layer("Lsp").definedBy(ROOT_PACKAGE + ".lsp..")
    .layer("Mcp").definedBy(ROOT_PACKAGE + ".mcp..")
    .layer("Cli").definedBy(ROOT_PACKAGE + ".cli..")
    // Фасад LSP-возможностей и публичный фасад встраивания
    .layer("Providers").definedBy(ROOT_PACKAGE + ".providers..")
    .layer("Binding").definedBy(ROOT_PACKAGE + ".binding..")
    .layer("Reporters").definedBy(ROOT_PACKAGE + ".reporters..")
    // Поставщики отдельных фич
    .layer("CodeActions").definedBy(ROOT_PACKAGE + ".codeactions..")
    .layer("CodeLenses").definedBy(ROOT_PACKAGE + ".codelenses..")
    .layer("Commands").definedBy(ROOT_PACKAGE + ".commands..")
    .layer("Completion").definedBy(ROOT_PACKAGE + ".completion..")
    .layer("Color").definedBy(ROOT_PACKAGE + ".color..")
    .layer("DocumentHighlight").definedBy(ROOT_PACKAGE + ".documenthighlight..")
    .layer("DocumentLink").definedBy(ROOT_PACKAGE + ".documentlink..")
    .layer("Folding").definedBy(ROOT_PACKAGE + ".folding..")
    .layer("Hover").definedBy(ROOT_PACKAGE + ".hover..")
    .layer("InlayHints").definedBy(ROOT_PACKAGE + ".inlayhints..")
    .layer("Rename").definedBy(ROOT_PACKAGE + ".rename..")
    .layer("SemanticTokens").definedBy(ROOT_PACKAGE + ".semantictokens..")
    // Домены. Diagnostics — движок (всё diagnostics, КРОМЕ листового словаря metadata);
    // DiagnosticsMetadata — сам словарь, выделен отдельным слоем-листом.
    .layer("Diagnostics").definedBy(
      resideInAPackage(ROOT_PACKAGE + ".diagnostics..")
        .and(resideOutsideOfPackage(ROOT_PACKAGE + ".diagnostics.metadata..")))
    .layer("DiagnosticsMetadata").definedBy(ROOT_PACKAGE + ".diagnostics.metadata..")
    .layer("Context").definedBy(ROOT_PACKAGE + ".context..")
    .layer("References").definedBy(ROOT_PACKAGE + ".references..")
    .layer("Types").definedBy(ROOT_PACKAGE + ".types..")
    .layer("Configuration").definedBy(ROOT_PACKAGE + ".configuration..")
    .layer("Cfg").definedBy(ROOT_PACKAGE + ".cfg..")
    .layer("Formatting").definedBy(ROOT_PACKAGE + ".formatting..")
    // Листовые foundation-пакеты
    .layer("Infrastructure").definedBy(ROOT_PACKAGE + ".infrastructure..")
    .layer("Client").definedBy(ROOT_PACKAGE + ".client..")
    .layer("Databind").definedBy(ROOT_PACKAGE + ".databind..")
    .layer("Events").definedBy(ROOT_PACKAGE + ".events..")
    .layer("Jsonrpc").definedBy(ROOT_PACKAGE + ".jsonrpc..")
    .layer("Recognizer").definedBy(ROOT_PACKAGE + ".recognizer..")
    .layer("Utils").definedBy(ROOT_PACKAGE + ".utils..")
    .layer("Websocket").definedBy(ROOT_PACKAGE + ".websocket..")

    // --- Outgoing whitelist'ы: от чего каждый слой вправе зависеть ---

    // Точка входа и головы
    .whereLayer("Application").mayOnlyAccessLayers("Cli")
    .whereLayer("Cli").mayOnlyAccessLayers(
      "Configuration", "Context", "Infrastructure", "Mcp", "Providers", "Reporters", "Utils")
    .whereLayer("Mcp").mayOnlyAccessLayers(
      "Configuration", "Context", "Infrastructure", "Providers", "Types", "Utils")
    // Lsp вдобавок резолвит протокольные Data-DTO (CodeLensData/CompletionData/InlayHintData)
    // в ответ на resolve-запросы, поэтому зависит от соответствующих фич.
    .whereLayer("Lsp").mayOnlyAccessLayers(
      "Client", "CodeLenses", "Completion", "Configuration", "Context", "Events", "Infrastructure",
      "InlayHints", "Jsonrpc", "Providers", "Utils")

    // Фасад providers композирует все фичи и домены под собой
    .whereLayer("Providers").mayOnlyAccessLayers(
      "Client", "CodeActions", "CodeLenses", "Color", "Commands", "Completion", "Configuration",
      "Context", "DocumentHighlight", "DocumentLink", "Events", "Folding", "Formatting", "Hover",
      "Infrastructure", "InlayHints", "References", "Rename", "SemanticTokens", "Types", "Utils")

    // Потребители движка диагностик: публичный фасад встраивания (binding, бывш. корневой BSLLSBinding,
    // используется внешним SonarQube-плагином) и репортёры. Так заперт бывший цикл
    // configuration↔diagnostics / context↔diagnostics — фундамент зависит лишь от словаря metadata.
    .whereLayer("Binding").mayOnlyAccessLayers(
      "Configuration", "Context", "Diagnostics", "DiagnosticsMetadata")
    .whereLayer("Reporters").mayOnlyAccessLayers(
      "Configuration", "Context", "Diagnostics", "DiagnosticsMetadata")

    // Поставщики отдельных фич
    .whereLayer("CodeActions").mayOnlyAccessLayers(
      "CodeLenses", "Configuration", "Context", "Diagnostics", "DiagnosticsMetadata", "Types", "Utils")
    .whereLayer("CodeLenses").mayOnlyAccessLayers(
      "Client", "Commands", "Configuration", "Context", "Databind", "Events", "Types")
    .whereLayer("Commands").mayOnlyAccessLayers("Databind", "InlayHints")
    .whereLayer("Completion").mayOnlyAccessLayers("Configuration", "Context", "Types")
    .whereLayer("Color").mayOnlyAccessLayers("Configuration", "Context", "Utils")
    .whereLayer("DocumentHighlight").mayOnlyAccessLayers("Context", "References", "Utils")
    .whereLayer("DocumentLink").mayOnlyAccessLayers(
      "Configuration", "Context", "DiagnosticsMetadata", "Types", "Utils")
    .whereLayer("Folding").mayOnlyAccessLayers("Configuration", "Context", "Utils")
    .whereLayer("Hover").mayOnlyAccessLayers("Configuration", "Context", "References", "Types")
    .whereLayer("InlayHints").mayOnlyAccessLayers(
      "Configuration", "Context", "Databind", "Hover", "References", "Types", "Utils")
    .whereLayer("Rename").mayOnlyAccessLayers(
      "Client", "Configuration", "Context", "Events", "References")
    .whereLayer("SemanticTokens").mayOnlyAccessLayers(
      "Configuration", "Context", "Events", "References", "Types", "Utils")

    // Движок диагностик
    .whereLayer("Diagnostics").mayOnlyAccessLayers(
      "Cfg", "Configuration", "Context", "DiagnosticsMetadata", "Formatting", "Infrastructure",
      "Recognizer", "References", "Types", "Utils")

    // Домены-фундамент. Configuration↔Context и References↔Types — известные циклы (см. комментарий).
    .whereLayer("Configuration").mayOnlyAccessLayers(
      "Context", "DiagnosticsMetadata", "Infrastructure", "Utils")
    .whereLayer("Context").mayOnlyAccessLayers(
      "Client", "Configuration", "DiagnosticsMetadata", "Infrastructure", "Utils")
    .whereLayer("References").mayOnlyAccessLayers(
      "Configuration", "Context", "Infrastructure", "Types", "Utils")
    .whereLayer("Types").mayOnlyAccessLayers(
      "Configuration", "Context", "Infrastructure", "References", "Utils")
    .whereLayer("Cfg").mayOnlyAccessLayers("Utils")
    .whereLayer("Formatting").mayOnlyAccessLayers("Configuration", "Utils")

    // Листья: ни от чего внутри проекта не зависят
    .whereLayer("Infrastructure").mayOnlyAccessLayers("Client")
    .whereLayer("DiagnosticsMetadata").mayNotAccessAnyLayer()
    .whereLayer("Client").mayNotAccessAnyLayer()
    .whereLayer("Databind").mayNotAccessAnyLayer()
    .whereLayer("Events").mayNotAccessAnyLayer()
    .whereLayer("Jsonrpc").mayNotAccessAnyLayer()
    .whereLayer("Recognizer").mayNotAccessAnyLayer()
    .whereLayer("Utils").mayNotAccessAnyLayer()
    .whereLayer("Websocket").mayNotAccessAnyLayer()

    .as("Полная карта зависимостей: каждый пакет проекта — слой с положительным whitelist'ом "
      + "разрешённых зависимостей, выведенным из его роли");

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
