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
package com.github._1c_syntax.bsl.languageserver.types.inferencer;

import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.AssignedTypes;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Change;
import com.github._1c_syntax.bsl.languageserver.types.inferencer.VariableFlowAnalyzer.Environment;
import org.antlr.v4.runtime.ParserRuleContext;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Кеш правых частей и наблюдавшихся чтений одного поиска неподвижной точки. */
final class FlowAssignmentMemo {
  /**
   * Типы правых частей с зависимостями от прочитанных окружений.
   * Карта живёт только в этом поиске неподвижной точки тела: ячейки других тел
   * на его время фиксированы. Результат переиспользуется, пока все наблюдавшиеся
   * чтения дают прежние типы, независимо от изменений остальных переменных.
   */
  @SuppressWarnings("java:S6411") // Position — координата LSP; сравнение порядка для этого кеша не требуется.
  private final Map<Position, AssignmentResult> assignedTypes = new HashMap<>();
  /** Приёмник чтений текущей правой части; вложенный расчёт дополняет родительский список. */
  private @Nullable List<VariableRead> assignmentReads;

  /**
   * Чтение переменной во время вычисления правой части.
   *
   * @param statement оператор, перед которым запрошено окружение.
   * @param index номер переменной в окружениях этого тела.
   * @param type прочитанный тип; {@code null}, если окружение ещё не рассчитано.
   *     Появление окружения также обесценивает сохранённый результат.
   */
  private record VariableRead(ParserRuleContext statement, int index, @Nullable TypeSet type) { }

  /**
   * Результат правой части и снимок её зависимостей для повторных проходов.
   *
   * @param type выведенный тип в текущем приближении.
   * @param reads неизменяемый список наблюдавшихся чтений, включая вложенные расчёты.
   */
  private record AssignmentResult(TypeSet type, List<VariableRead> reads) { }


  /** Запомнить чтение в текущем вычислении, включая ещё неизвестное окружение. */
  void recordRead(ParserRuleContext statement, int index, @Nullable TypeSet type) {
    if (assignmentReads != null) {
      assignmentReads.add(new VariableRead(statement, index, type));
    }
  }

  /**
   * Пересчитать правую часть, если изменился хотя бы один прочитанный ею тип.
   * Изменение других переменных заставляет пересчитать вершину, но на эту правую
   * часть не влияет. Неизвестное ещё окружение тоже запоминается: когда расчёт до
   * него дойдёт, {@code null} сменится известным типом и прежний ответ будет пересчитан.
   * <p>
   * При использовании сохранённого ответа его зависимости передаются внешнему расчёту:
   * вложенная правая часть не должна скрывать от него прочитанные переменные.
   *
   * @param change присваивание с позицией, однозначной внутри этого тела.
   * @param statement оператор, содержащий присваивание.
   * @return тип правой части в текущем приближении окружений.
   */
  @SuppressWarnings("java:S6411") // Окружения индексируются исходными узлами AST по идентичности.
  TypeSet at(Change change, ParserRuleContext statement,
             Map<ParserRuleContext, Environment> beforeStatement, AssignedTypes assigned) {
    var cached = assignedTypes.get(change.position());
    if (cached != null && readsUnchanged(cached.reads(), beforeStatement)) {
      if (assignmentReads != null) {
        assignmentReads.addAll(cached.reads());
      }
      return cached.type();
    }
    var previousReads = assignmentReads;
    var reads = new ArrayList<VariableRead>(0);
    assignmentReads = reads;
    try {
      var type = assigned.at(change.variable(), statement, change.position());
      assignedTypes.put(change.position(), new AssignmentResult(type, List.copyOf(reads)));
      return type;
    } finally {
      assignmentReads = previousReads;
      if (previousReads != null) {
        previousReads.addAll(reads);
      }
    }
  }

  /**
   * Проверить каждое сохранённое чтение в его собственной точке программы.
   * Отсутствующее окружение отличается от известного пустого типа. Пустой список
   * означает, что результат не зависел от чтения переменных этого тела.
   */
  @SuppressWarnings("java:S6411") // Ключ AST не имеет порядка и используется только для поиска по идентичности.
  private static boolean readsUnchanged(List<VariableRead> reads,
                                        Map<ParserRuleContext, Environment> beforeStatement) {
    for (var read : reads) {
      var environment = beforeStatement.get(read.statement());
      var type = environment == null ? null : environment.get(read.index());
      if (!Objects.equals(type, read.type())) {
        return false;
      }
    }
    return true;
  }
}
