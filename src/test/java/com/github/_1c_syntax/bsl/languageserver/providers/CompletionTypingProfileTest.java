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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.AbstractServerContextAwareTest;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Профилировочный «бенчмарк» полного LSP-сценария набора текста в большом модуле.
 * <p>
 * Эмулирует то, что делает сервер на каждый keystroke в редакторе (vscode/code-server):
 * клиент шлёт {@code didChange} → сервер применяет инкрементальное изменение и
 * перестраивает {@link DocumentContext} ({@code rebuildDocument}: ре-токенизация +
 * парсинг всего модуля + пересборка symbol tree), а на вводе точки —
 * {@code textDocument/completion} с выводом типа ресивера (dot-completion).
 * <p>
 * Сценарий: в конец большого общего модуля (по умолчанию {@code УправлениеДоступом}
 * из конфигурации SSL 3.2) дописывается процедура с локальной структурой, после чего
 * посимвольно «набирается» {@code СтруктураДоступа.} — каждый символ = отдельный
 * {@code didChange}+rebuild всего модуля, а на финальной точке срабатывает автодополнение.
 * <p>
 * Замер разделён на две фазы, чтобы отделить стоимость перестроения документа
 * ({@code didChange}) от стоимости самого автодополнения:
 * <ul>
 *   <li>фаза REBUILD — латентность {@code rebuildDocument} на каждый keystroke;</li>
 *   <li>фаза COMPLETION — латентность {@code getCompletion} на {@code СтруктураДоступа.}.</li>
 * </ul>
 * Для каждой фазы снимается отдельная запись JDK Flight Recorder (sampling-профайлер,
 * встроен в JDK, без нативных зависимостей) и печатается таблица «горячих» методов.
 * <p>
 * Тест выключен по умолчанию и запускается только при наличии системного свойства
 * {@code -Dbsl.profile=true} (см. конфигурацию задачи {@code test} в build.gradle.kts,
 * которая под этим флагом поднимает heap и пробрасывает свойства {@code bsl.profile.*}).
 * Параметры запуска (все необязательные):
 * <ul>
 *   <li>{@code bsl.profile.root} — корень конфигурации (по умолчанию {@code /tmp/ssl_3_2/src/cf});</li>
 *   <li>{@code bsl.profile.module} — относительный путь модуля
 *       (по умолчанию {@code CommonModules/УправлениеДоступом/Ext/Module.bsl});</li>
 *   <li>{@code bsl.profile.identifier} — набираемый идентификатор (по умолчанию {@code СтруктураДоступа});</li>
 *   <li>{@code bsl.profile.warmup} — число прогревочных проходов (по умолчанию 20);</li>
 *   <li>{@code bsl.profile.iterations} — число измеряемых проходов (по умолчанию 150);</li>
 *   <li>{@code bsl.profile.jfrDir} — каталог для {@code .jfr}-файлов (по умолчанию {@code /tmp}).</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "bsl.profile", matches = "true")
class CompletionTypingProfileTest extends AbstractServerContextAwareTest {

  private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";

  @Autowired
  private CompletionProvider completionProvider;

  @Test
  void profileTypingAndCompletionOnLargeModule() throws Exception {
    try {
      runProfile();
    } finally {
      flushReport();
    }
  }

  private void runProfile() throws Exception {
    var root = System.getProperty("bsl.profile.root", "/tmp/ssl_3_2/src/cf");
    var moduleRel = System.getProperty("bsl.profile.module",
      "CommonModules/УправлениеДоступом/Ext/Module.bsl");
    var identifier = System.getProperty("bsl.profile.identifier", "СтруктураДоступа");
    var warmup = Integer.getInteger("bsl.profile.warmup", 20);
    var iterations = Integer.getInteger("bsl.profile.iterations", 150);
    // Один запрос автодополнения — доли миллисекунды, поэтому для набора достаточного
    // числа JFR-семплов фаза COMPLETION гоняется отдельным (бо́льшим) числом повторов.
    var completionIterations = Integer.getInteger("bsl.profile.completionIterations", 20000);
    var jfrDir = System.getProperty("bsl.profile.jfrDir", "/tmp");

    var modulePath = Path.of(root).resolve(moduleRel);
    Files.createDirectories(Path.of(jfrDir));

    log("=".repeat(78));
    log("BSL LS completion typing profile");
    log("  configuration root : " + root);
    log("  target module      : " + moduleRel);
    log("  module size        : %,d bytes / %,d lines".formatted(
      Files.size(modulePath), countLines(Files.readString(modulePath))));
    log("  typed identifier   : " + identifier + ".");
    log("  warmup / iterations: " + warmup + " / " + iterations);
    log("=".repeat(78));

    // populate=false: метаданные конфигурации (типы Справочники/Документы/...) грузятся
    // лениво при первом обращении, поэтому полный парсинг всех модулей не нужен —
    // эмулируем редактор с одним открытым большим модулем.
    var t0 = System.nanoTime();
    initServerContext(Path.of(root), false);
    var doc = registerDocument(modulePath);
    log("context init (workspace registered) : %,.1f ms".formatted(ms(System.nanoTime() - t0)));

    var baseContent = doc.getContent();

    // Каркас: дописанная в конец модуля процедура с локальной структурой.
    // Между объявлением структуры и набираемой строкой — реальная конструкция
    // `Новый Структура("Ключ1, Ключ2, Ключ3")`, чтобы вывод типа ресивера дал
    // именно Структуру с объявленными ключами (нетривиальная dot-completion).
    var prefix = baseContent
      + "\n\nПроцедура __PROFILE_AUTOCOMPLETE__()\n"
      + "\tСтруктураДоступа = Новый Структура(\"Ключ1, Ключ2, Ключ3\");\n";
    var suffix = "\nКонецПроцедуры\n";
    var cursorLine = countChar(prefix, '\n'); // строка, на которой идёт набор (0-based)

    var typedSequence = buildTypedSequence(identifier); // "С","Ст",...,"СтруктураДоступа","СтруктураДоступа."

    // Прогрев: JIT-компиляция парсера/инференса, наполнение ленивых кэшей конфигурации.
    log("warming up...");
    int version = 1;
    for (int i = 0; i < warmup; i++) {
      version = typeOnePass(doc, prefix, suffix, typedSequence, version, null);
    }

    var completionAtDot = prefix + "\t" + identifier + "." + suffix;
    var dotPosition = new Position(cursorLine, 1 + identifier.length() + 1);
    var sanity = completionProvider.getCompletion(doc, completionParams(doc, dotPosition)).getItems();
    log("sanity: dot-completion returned %,d items (e.g. %s)".formatted(
      sanity.size(), sampleLabels(sanity)));
    log("");

    // ---------- Фаза REBUILD (стоимость didChange на каждый keystroke) ----------
    var rebuildTimes = new ArrayList<Long>(iterations * typedSequence.size());
    var rebuildJfr = Path.of(jfrDir, "completion-rebuild.jfr");
    try (var recording = startRecording(rebuildJfr)) {
      for (int i = 0; i < iterations; i++) {
        version = typeOnePass(doc, prefix, suffix, typedSequence, version, rebuildTimes);
      }
      recording.stop();
      recording.dump(rebuildJfr);
    }

    // ---------- Фаза COMPLETION (стоимость автодополнения на `СтруктураДоступа.`) ----------
    // Документ зафиксирован в состоянии с точкой; повторяем только запрос автодополнения.
    context.rebuildDocument(doc, completionAtDot, ++version);
    var completionTimes = new ArrayList<Long>(completionIterations);
    var itemCounts = new ArrayList<Integer>(completionIterations);
    var completionJfr = Path.of(jfrDir, "completion-dot.jfr");
    var params = completionParams(doc, dotPosition);
    try (var recording = startRecording(completionJfr)) {
      for (int i = 0; i < completionIterations; i++) {
        var s = System.nanoTime();
        var items = completionProvider.getCompletion(doc, params).getItems();
        completionTimes.add(System.nanoTime() - s);
        itemCounts.add(items.size());
      }
      recording.stop();
      recording.dump(completionJfr);
    }

    // ---------- Отчёт ----------
    log("");
    printLatency("didChange → rebuildDocument (per keystroke)", rebuildTimes);
    printLatency("textDocument/completion (dot, per request)", completionTimes);
    log("  completion items per request: %d".formatted(itemCounts.isEmpty() ? 0 : itemCounts.get(0)));
    log("");
    printHotspots("REBUILD hotspots (didChange, self time, JFR sampling)", rebuildJfr);
    log("");
    printHotspots("COMPLETION hotspots (dot-completion, self time, JFR sampling)", completionJfr);
    log("");
    log("JFR recordings written:");
    log("  " + rebuildJfr.toAbsolutePath());
    log("  " + completionJfr.toAbsolutePath());
    log("=".repeat(78));
  }

  /**
   * Один проход набора идентификатора: на каждый промежуточный вариант — отдельный
   * {@code rebuildDocument} (эмуляция одного {@code didChange} на keystroke).
   *
   * @param sink если не {@code null}, в него складываются длительности каждого rebuild (нс).
   */
  private int typeOnePass(DocumentContext doc, String prefix, String suffix,
                          List<String> typedSequence, int version,
                          List<Long> sink) {
    var v = version;
    for (var typed : typedSequence) {
      var content = prefix + "\t" + typed + suffix;
      var s = System.nanoTime();
      context.rebuildDocument(doc, content, ++v);
      if (sink != null) {
        sink.add(System.nanoTime() - s);
      }
    }
    return v;
  }

  private DocumentContext registerDocument(Path modulePath) throws Exception {
    var uri = modulePath.toUri();
    var content = Files.readString(modulePath);
    var documentContext = context.addDocument(uri);
    context.rebuildDocument(documentContext, content, 1);
    return documentContext;
  }

  private CompletionParams completionParams(DocumentContext doc, Position position) {
    var params = new CompletionParams();
    params.setTextDocument(new TextDocumentIdentifier(doc.getUri().toString()));
    params.setPosition(position);
    return params;
  }

  private static Recording startRecording(Path path) throws java.io.IOException {
    var recording = new Recording();
    // Sampling-профайлер: семплируем стеки выполняющихся java-потоков.
    // Запись держим в disk-репозитории и выгружаем явным dump(path) после stop()
    // (setDestination не используем, чтобы не было двойной записи на stop/close).
    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
    recording.setToDisk(true);
    recording.start();
    return recording;
  }

  private static List<String> buildTypedSequence(String identifier) {
    var result = new ArrayList<String>(identifier.length() + 1);
    for (int i = 1; i <= identifier.length(); i++) {
      result.add(identifier.substring(0, i));
    }
    result.add(identifier + "."); // финальная точка — триггер автодополнения
    return result;
  }

  private void printLatency(String title, List<Long> nanos) {
    if (nanos.isEmpty()) {
      log(title + ": нет данных");
      return;
    }
    var sorted = new ArrayList<>(nanos);
    sorted.sort(Comparator.naturalOrder());
    var n = sorted.size();
    double mean = sorted.stream().mapToLong(Long::longValue).average().orElse(0);
    log(title + " (n=%,d)".formatted(n));
    log("  min %7.2f | p50 %7.2f | p90 %7.2f | p95 %7.2f | p99 %7.2f | max %8.2f | mean %7.2f  (ms)"
      .formatted(
        ms(sorted.get(0)),
        ms(percentile(sorted, 50)),
        ms(percentile(sorted, 90)),
        ms(percentile(sorted, 95)),
        ms(percentile(sorted, 99)),
        ms(sorted.get(n - 1)),
        mean / 1_000_000.0));
  }

  private static long percentile(List<Long> sorted, int p) {
    if (sorted.isEmpty()) {
      return 0;
    }
    var idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
    return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
  }

  /**
   * Агрегирует JFR-семплы по «листовому» (верхнему) кадру стека — это self-time
   * горячих методов. Печатает топ-25.
   */
  private void printHotspots(String title, Path jfrFile) throws Exception {
    var leafCounts = new LinkedHashMap<String, Integer>();
    var total = 0;
    try (var recordingFile = new RecordingFile(jfrFile)) {
      while (recordingFile.hasMoreEvents()) {
        RecordedEvent event = recordingFile.readEvent();
        if (!EXECUTION_SAMPLE.equals(event.getEventType().getName())) {
          continue;
        }
        var stack = event.getStackTrace();
        if (stack == null) {
          continue;
        }
        var frames = stack.getFrames();
        if (frames.isEmpty()) {
          continue;
        }
        var leaf = frameName(frames.get(0));
        leafCounts.merge(leaf, 1, Integer::sum);
        total++;
      }
    }
    log(title + "  (samples=%,d)".formatted(total));
    if (total == 0) {
      log("  (семплов не набрано — увеличьте iterations)");
      return;
    }
    var finalTotal = total;
    leafCounts.entrySet().stream()
      .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
      .limit(25)
      .forEach(e -> log("  %5.1f%%  %,6d  %s".formatted(
        100.0 * e.getValue() / finalTotal, e.getValue(), e.getKey())));
  }

  private static String frameName(RecordedFrame frame) {
    if (!frame.isJavaFrame() || frame.getMethod() == null) {
      return "<non-java>";
    }
    var method = frame.getMethod();
    var type = method.getType() == null ? "?" : method.getType().getName();
    var shortType = type.substring(type.lastIndexOf('.') + 1);
    return shortType + "." + method.getName();
  }

  private static String sampleLabels(List<CompletionItem> items) {
    return items.stream().limit(6).map(CompletionItem::getLabel).reduce((a, b) -> a + ", " + b).orElse("");
  }

  private static int countLines(String s) {
    return countChar(s, '\n') + 1;
  }

  private static int countChar(String s, char c) {
    var count = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        count++;
      }
    }
    return count;
  }

  private static double ms(long nanos) {
    return nanos / 1_000_000.0;
  }

  private static final StringBuilder REPORT = new StringBuilder();

  private static void log(String message) {
    // Задача test в build.gradle.kts логирует только standard_error, поэтому пишем
    // в System.err (видно в логе Gradle) и параллельно копим отчёт для выгрузки в файл.
    System.err.println("[profile] " + message);
    REPORT.append(message).append('\n');
  }

  private static void flushReport() {
    var reportPath = System.getProperty("bsl.profile.report", "/tmp/completion-profile-report.txt");
    try {
      Files.writeString(Path.of(reportPath), REPORT.toString());
      System.err.println("[profile] report written: " + Path.of(reportPath).toAbsolutePath());
    } catch (java.io.IOException e) {
      System.err.println("[profile] failed to write report: " + e.getMessage());
    }
  }
}
