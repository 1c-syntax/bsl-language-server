/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2025
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
@Primary
@Scope("prototype")
public @interface DiagnosticMetadata {
  /**
   * Тип диагностики
   */
  DiagnosticType type() default DiagnosticType.ERROR;

  /**
   * Серьезность замечания
   */
  DiagnosticSeverity severity() default DiagnosticSeverity.MINOR;

  /**
   * Область применения диагностики по диалекту языка (bsl или oscript)
   */
  DiagnosticScope scope() default DiagnosticScope.ALL;

  /**
   * Типы модулей, анализируемых диагностикой
   */
  ModuleType[] modules() default {};

  /**
   * Время, необходимое для исправления замечания
   */
  int minutesToFix() default 0;

  /**
   * Признак включения диагностики в профиле по умолчанию
   */
  boolean activatedByDefault() default true;

  /**
   * Версия платформы 1С:Предприятие, с которой диагностика применяется
   */
  DiagnosticCompatibilityMode compatibilityMode() default DiagnosticCompatibilityMode.UNDEFINED;

  /**
   * Перечень меток (тегов) диагностики
   */
  DiagnosticTag[] tags() default {};

  /**
   * Замечания диагностики могут быть прикреплены на уровень анализируемого проекта (в частности в SonarQube)
   */
  boolean canLocateOnProject() default false;

  /**
   * Надбавка ко времени исправления замечания за повышенную сложность
   */
  double extraMinForComplexity() default 0;

  /**
   * LSP-уровень серьезности диагностики.
   * Если не указан, рассчитывается автоматически на основе type и severity.
   */
  org.eclipse.lsp4j.DiagnosticSeverity lspSeverity() default org.eclipse.lsp4j.DiagnosticSeverity.Hint;
}
