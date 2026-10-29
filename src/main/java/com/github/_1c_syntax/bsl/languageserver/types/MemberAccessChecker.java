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
package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.registry.GlobalScopeProvider;
import lombok.RequiredArgsConstructor;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.lsp4j.Range;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Проверка существования и доступности членов без формирования диагностических сообщений.
 * <p>
 * Контекст уточняет тип получателя; найденное объявление не исчезает из поиска из-за
 * недоступности. Отсутствующий член и запрещённый вызов одной цели в одном режиме
 * поэтому не дают двух ошибок. Разные получатели или режимы могут иметь разные ошибки.
 * <p>
 * Состояние обхода и кеши принадлежат одному вызову {@link #check}; компонент не удерживает AST.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class MemberAccessChecker {
  private final CompilationContextResolver contextResolver;
  private final TypeService typeService;
  private final GlobalScopeProvider globalScopeProvider;

  /**
   * Доказанная проблема обращения; допустимые и неразрешённые обращения проблем не создают.
   */
  public sealed interface Problem permits UnknownMember, UnavailableMember {
  }

  /**
   * Член отсутствует у известных типов получателя либо неизвестно голое имя.
   *
   * @param range         диапазон имени.
   * @param name          имя обращения.
   * @param receiverTypes типы без этого члена; пустой набор для голого имени.
   * @param context       режим, в котором установлена ошибка; пусто для проверки без известного режима.
   */
  public record UnknownMember(Range range, String name, TypeSet receiverTypes,
                              Optional<ExecutionContext> context) implements Problem {
  }

  /**
   * Известный метод или свойство, обращение к которому запрещено в данном режиме.
   *
   * @param range             диапазон имени обращения.
   * @param name              имя члена.
   * @param context           режим вызывающего кода.
   * @param kind              метод или свойство.
   * @param moduleName        имя общего модуля, если все альтернативные объявления принадлежат ему.
   * @param moduleUnavailable недоступен сам общий модуль, а не только его метод.
   */
  public record UnavailableMember(Range range, String name, ExecutionContext context, MemberKind kind,
                                  Optional<String> moduleName, boolean moduleUnavailable) implements Problem {
  }

  /**
   * Проверить обращения файла в выбранных режимах.
   * <p>
   * На одно обращение возвращается не более одной проблемы существования члена
   * и по одной проблеме доступности вызова на каждый контекст. При неизвестном контексте
   * сохраняется обычная проверка имён; доступность вызова не предполагается.
   * OneScript использует собственную достижимость препроцессора и проверку имён без режимов 1С.
   *
   * @param document        документ с доступным AST.
   * @param enabledContexts выбранные пользователем режимы; не расширяют возможности модуля.
   * @param checks          требуемые виды проверки.
   * @return неизменяемый список проблем в порядке исходного текста.
   */
  public List<Problem> check(DocumentContext document, Set<ExecutionContext> enabledContexts,
                             Set<MemberAccessCheckKind> checks) {
    var contexts = new MemberAccessContexts(document, contextResolver, typeService);
    var collector = new MemberAccessCollector(document, enabledContexts, checks, contexts,
      contextResolver, globalScopeProvider);
    ParseTreeWalker.DEFAULT.walk(collector, document.getAst());
    return new MemberAccessAnalysis(document, checks, contexts, contextResolver, typeService, globalScopeProvider)
      .checkAccesses(collector.accesses());
  }

}
