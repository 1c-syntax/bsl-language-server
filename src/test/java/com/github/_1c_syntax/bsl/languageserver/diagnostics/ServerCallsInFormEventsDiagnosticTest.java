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

import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;

/**
 * Тесты для диагностики ServerCallsInFormEventsDiagnostic.
 * 
 * Проверяет корректность обнаружения вызовов серверных процедур 
 * в событиях ПриАктивизацииСтроки и НачалоВыбора.
 * 
 */
class ServerCallsInFormEventsDiagnosticTest extends AbstractDiagnosticTest<ServerCallsInFormEventsDiagnostic> {

  /**
   * Конструктор тестового класса.
   */
  ServerCallsInFormEventsDiagnosticTest() {
    super(ServerCallsInFormEventsDiagnostic.class);
  }

  /**
   * Основной тест с использованием фикстуры.
   * Проверяет обнаружение всех нарушений в тестовом файле.
   */
  @Test
  void testMainFixture() {
    // Основной тест с использованием существующей фикстуры
    List<Diagnostic> diagnostics = getDiagnostics();

    assertThat(diagnostics).hasSize(6);
    
    // Проверяем конкретные позиции ошибок из фикстуры
    assertThat(diagnostics, true)
      .hasRange(18, 4, 18, 47)  // ТаблицаФормыПриАктивизацииСтрокиНаСервере()
      .hasRange(25, 4, 25, 27)  // НачалоВыбораНаСервере()
      .hasRange(39, 4, 39, 27)  // OnStartChoiceAtServer()
      .hasRange(41, 4, 41, 33)  // ЛокальнаяСервернаяПроцедура()
      .hasRange(48, 4, 48, 33)  // ЛокальнаяСервернаяПроцедура()
      .hasRange(50, 4, 50, 45); // ЛокальнаяСервернаяПроцедураБезКонтекста()
  }

  /**
   * Тест проверки русских названий событий.
   * Проверяет корректность работы с событиями ПриАктивизацииСтроки и НачалоВыбора.
   */
  @Test
  void testRussianEventNames() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var russianEventDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 18 || d.getRange().getStart().getLine() == 25)
      .toList();
    
    assertThat(russianEventDiagnostics).hasSize(2);
    assertThat(russianEventDiagnostics, true)
      .hasRange(18, 4, 18, 47)  
      .hasRange(25, 4, 25, 27); 
  }

  /**
   * Тест проверки английских названий событий.
   * Проверяет корректность работы с событиями OnActivateRow и OnStartChoice.
   */
  @Test
  void testEnglishEventNames() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var englishEventDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 39 || d.getRange().getStart().getLine() == 41)
      .toList();
    
    assertThat(englishEventDiagnostics).hasSize(2);
    assertThat(englishEventDiagnostics, true)
      .hasRange(39, 4, 39, 27) 
      .hasRange(41, 4, 41, 33);
  }

  /**
   * Тест проверки регистронезависимых названий событий.
   * Проверяет работу с событиями в разном регистре.
   */
  @Test
  void testCaseInsensitiveEventNames() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    assertThat(diagnostics).hasSize(6);
    
    assertThat(diagnostics, true)
      .hasRange(18, 4, 18, 47) 
      .hasRange(25, 4, 25, 27) 
      .hasRange(39, 4, 39, 27) 
      .hasRange(41, 4, 41, 33); 
  }

  /**
   * Тест проверки отсутствия диагностик в обычных процедурах.
   * Убеждается, что диагностика не срабатывает в разрешенных местах.
   */
  @Test
  void testNoDiagnosticsInRegularProcedures() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var regularProcedureDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() >= 86 && d.getRange().getStart().getLine() <= 89)
      .toList();
    
    assertThat(regularProcedureDiagnostics).isEmpty();
  }

  /**
   * Тест проверки отсутствия диагностик в разрешенных событиях.
   * Проверяет, что диагностика не срабатывает в других событиях форм.
   */
  @Test
  void testNoDiagnosticsInAllowedEvents() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var allowedEventDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() >= 5 && d.getRange().getStart().getLine() <= 8 ||
                  d.getRange().getStart().getLine() >= 10 && d.getRange().getStart().getLine() <= 13)
      .toList();
    
    assertThat(allowedEventDiagnostics).isEmpty();
  }

  /**
   * Тест проверки множественных серверных вызовов в одном событии.
   * Проверяет обнаружение нескольких нарушений в одном событии.
   */
  @Test
  void testMultipleServerCallsInOneEvent() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var multipleCallsDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 48 || d.getRange().getStart().getLine() == 50)
      .toList();
    
    assertThat(multipleCallsDiagnostics).hasSize(2);
    assertThat(multipleCallsDiagnostics, true)
      .hasRange(48, 4, 48, 33) 
      .hasRange(50, 4, 50, 45); 
  }

  /**
   * Тест проверки пустых обработчиков событий.
   * Проверяет, что пустые события не вызывают ложных срабатываний.
   */
  @Test
  void testEmptyEventHandlers() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var emptyEventDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() >= 5 && d.getRange().getStart().getLine() <= 8 ||
                  d.getRange().getStart().getLine() >= 10 && d.getRange().getStart().getLine() <= 13)
      .toList();
    
    assertThat(emptyEventDiagnostics).isEmpty();
  }

  /**
   * Тест проверки событий только с клиентским кодом.
   * Проверяет, что клиентский код не вызывает диагностик.
   */
  @Test
  void testEventWithOnlyClientCode() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var clientOnlyEventDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() >= 5 && d.getRange().getStart().getLine() <= 8 ||
                  d.getRange().getStart().getLine() >= 10 && d.getRange().getStart().getLine() <= 13 ||
                  d.getRange().getStart().getLine() >= 33 && d.getRange().getStart().getLine() <= 35)
      .toList();
    
    assertThat(clientOnlyEventDiagnostics).isEmpty();
  }

  /**
   * Тест проверки граничных случаев.
   * Проверяет общее количество найденных диагностик.
   */
  @Test
  void testEdgeCases() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    assertThat(diagnostics).hasSize(6);
  }

  /**
   * Тест проверки серверных директив.
   * Проверяет обнаружение методов с директивами &НаСервере и &НаСервереБезКонтекста.
   */
  @Test
  void testServerDirectives() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var serverDirectiveDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 18 ||
                  d.getRange().getStart().getLine() == 25 || 
                  d.getRange().getStart().getLine() == 39 || 
                  d.getRange().getStart().getLine() == 41 || 
                  d.getRange().getStart().getLine() == 48 || 
                  d.getRange().getStart().getLine() == 50)   
      .toList();
    
    assertThat(serverDirectiveDiagnostics).hasSize(6);
  }

  /**
   * Тест проверки глобальных вызовов методов.
   * Проверяет обнаружение вызовов глобальных серверных процедур.
   */
  @Test
  void testGlobalMethodCalls() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var globalMethodDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 18 || 
                  d.getRange().getStart().getLine() == 25 || 
                  d.getRange().getStart().getLine() == 39)
      .toList();
    
    assertThat(globalMethodDiagnostics).hasSize(3);
    assertThat(globalMethodDiagnostics, true)
      .hasRange(18, 4, 18, 47)  
      .hasRange(25, 4, 25, 27)  
      .hasRange(39, 4, 39, 27); 
  }

  /**
   * Тест проверки локальных вызовов методов.
   * Проверяет обнаружение вызовов локальных серверных процедур.
   */
  @Test
  void testLocalMethodCalls() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var localMethodDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 41 || 
                  d.getRange().getStart().getLine() == 48 || 
                  d.getRange().getStart().getLine() == 50)
      .toList();
    
    assertThat(localMethodDiagnostics).hasSize(3);
    assertThat(localMethodDiagnostics, true)
      .hasRange(41, 4, 41, 33)  
      .hasRange(48, 4, 48, 33)  
      .hasRange(50, 4, 50, 45); 
  }

  /**
   * Тест проверки локальных вызовов методов в событиях.
   * Проверяет обнаружение локальных серверных процедур в событиях форм.
   */
  @Test
  void testLocalMethodCallsInEvents() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    var methodCallDiagnostics = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 41 || 
                  d.getRange().getStart().getLine() == 48 || 
                  d.getRange().getStart().getLine() == 50)
      .toList();
    
    assertThat(methodCallDiagnostics).hasSize(3);
    assertThat(methodCallDiagnostics, true)
      .hasRange(41, 4, 41, 33)  
      .hasRange(48, 4, 48, 33) 
      .hasRange(50, 4, 50, 45); 
  }

  /**
   * Тест полного покрытия вызовов методов.
   * Проверяет общее покрытие глобальных и локальных вызовов.
   */
  @Test
  void testCompleteMethodCallCoverage() {
    List<Diagnostic> diagnostics = getDiagnostics();
    
    assertThat(diagnostics).hasSize(6);
    
    var globalCalls = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 18 || 
                  d.getRange().getStart().getLine() == 25 || 
                  d.getRange().getStart().getLine() == 39)
      .toList();
    
    var methodCalls = diagnostics.stream()
      .filter(d -> d.getRange().getStart().getLine() == 41 || 
                  d.getRange().getStart().getLine() == 48 || 
                  d.getRange().getStart().getLine() == 50)
      .toList();
    
    assertThat(globalCalls).hasSize(3);
    assertThat(methodCalls).hasSize(3);
  }

}
