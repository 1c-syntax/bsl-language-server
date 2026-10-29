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
package com.github._1c_syntax.bsl.languageserver.completion;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;
import java.util.IdentityHashMap;
import java.util.Map;
import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.AVAILABLE;
import static com.github._1c_syntax.bsl.languageserver.completion.CompletionPriority.UNAVAILABLE;

/**
 * Приоритеты доступности и подписи контекстов для одного запроса completion.
 * <p>
 * Общая оценка используется для отбора и сортировки: частично доступные следуют за обычными,
 * полностью недоступные — последними, если пользователь включил их показ.
 * Недостаток сведений не понижает приоритет. Исходные корзины, признак устаревания
 * и порядок имён сохраняются внутри этих групп.
 * Приписка к документации описывает компиляцию метода, а стрелка — серверный переход именно из
 * запрошенной позиции. Смешанные или неполные сведения о вызове дают условную стрелку.
 * Все кеши ограничены запросом; объект нельзя сохранять в провайдере между запросами.
 */
public final class CompletionContextRanker {
  private final DocumentContext document;
  private final CompletionContextEvaluation evaluation;
  private final Map<CompletionItem, Integer> priorities = new IdentityHashMap<>();
  private final Map<CompletionItem, CompletionMethodHint> hints = new IdentityHashMap<>();

  /**
   * Подготовить контекст один раз для всех предлагаемых имён.
   *
   * @param document документ запроса.
   * @param position позиция курсора, в том числе после незавершённой точки.
   * @param resolver общая модель компиляции и серверных переходов.
   * @param types служба вывода типов и поиска членов.
   */
  public CompletionContextRanker(DocumentContext document, Position position, CompilationContextResolver resolver,
                                 TypeService types) {
    this.document = document;
    evaluation = new CompletionContextEvaluation(document, position, resolver, types);
  }

  /**
   * Оценить член без уточнения получателя: глобальную функцию, свойство или неявный self-член.
   *
   * @param item сформированный пункт completion.
   * @param member предлагаемый член.
   */
  public void rankMember(CompletionItem item, MemberDescriptor member) {
    if (member.kind() == MemberKind.METHOD && evaluation.hasScope()) {
      var hint = new CompletionMethodHint();
      remember(item, evaluation.rankMethod(member, hint));
      hints.put(item, hint);
    } else {
      remember(item, evaluation.rankMember(member));
    }
  }

  /**
   * Оценить конкретное локальное объявление, сохраняя различие одноимённых веток.
   *
   * @param item пункт локального метода.
   * @param method объявление, чья сигнатура вставляется этим пунктом.
   */
  public void rankLocalMethod(CompletionItem item, MethodSymbol method) {
    if (!evaluation.hasScope()) {
      return;
    }
    var hint = new CompletionMethodHint();
    remember(item, evaluation.rankLocalMethod(method, hint));
    hints.put(item, hint);
  }

  /**
   * Оценить тип после «Новый» либо составное имя метаданных.
   *
   * @param item пункт типа или имени конфигурации.
   * @param name имя, разрешаемое службой типов.
   */
  public void rankType(CompletionItem item, String name) {
    if (evaluation.hasScope()) {
      remember(item, evaluation.rankType(name));
    }
  }

  /**
   * Оценить член после точки по типам получателя в каждом актуальном режиме.
   *
   * @param item пункт dot-completion.
   * @param member член из исходного объединения типов.
   */
  public void rankDotMember(CompletionItem item, MemberDescriptor member) {
    if (!evaluation.hasScope()) {
      return;
    }
    var hint = member.kind() == MemberKind.METHOD
      ? new CompletionMethodHint() : null;
    remember(item, evaluation.rankDotMember(item.getLabel(), member, hint));
    if (hint != null) {
      hints.put(item, hint);
    }
  }

  /**
   * Заведомо ли пункт недоступен во всех проверенных контекстах и для всех известных целей.
   * <p>
   * Частичная доступность и недостаток сведений не дают основания скрывать предложение.
   * Используется уже рассчитанная оценка; дополнительного разрешения типов не выполняется.
   *
   * @param item ранее оценённый пункт либо пункт без ограничений, например ключевое слово.
   * @return {@code true}, если пункт можно исключить из списка по контексту компиляции.
   */
  public boolean isUnavailable(CompletionItem item) {
    return priorities.getOrDefault(item, AVAILABLE) == UNAVAILABLE;
  }

  /**
   * Добавить группу доступности и пометку серверного вызова, сохранив имя, сигнатуру и текст вставки.
   *
   * @param items отобранные предложения, включая переменные и ключевые слова без ограничений.
   * @param labelDetailsSupport поддерживает ли клиент подпись справа от сигнатуры.
   * @return пояснения для добавления после документации метода; ключи сравниваются по идентичности.
   */
  public Map<CompletionItem, String> apply(Iterable<CompletionItem> items, boolean labelDetailsSupport) {
    var language = document.getScriptVariantLanguage();
    Map<CompletionItem, String> descriptions = new IdentityHashMap<>();
    for (var item : items) {
      if (!priorities.isEmpty()) {
        item.setSortText(priorities.getOrDefault(item, AVAILABLE) + "_" + item.getSortText());
      }
      var hint = hints.get(item);
      if (hint != null) {
        var description = hint.apply(item, language, labelDetailsSupport);
        if (!description.isBlank()) {
          descriptions.put(item, description);
        }
      }
    }
    return descriptions;
  }

  private void remember(CompletionItem item, int priority) {
    if (priority != AVAILABLE) {
      priorities.put(item, priority);
    }
  }

}
