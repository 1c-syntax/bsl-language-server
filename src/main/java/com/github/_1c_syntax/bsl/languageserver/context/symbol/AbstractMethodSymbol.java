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
package com.github._1c_syntax.bsl.languageserver.context.symbol;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.description.MethodDescription;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Общая структура полей для callable-символов уровня модуля и реализация
 * контракта {@link MethodSymbol}. Конкретные подтипы добавляют только
 * {@link org.eclipse.lsp4j.SymbolKind} и вызов соответствующего
 * {@link SymbolTreeVisitor}-метода в {@link #accept(SymbolTreeVisitor)}.
 */
@Getter
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"children", "parent"})
public abstract class AbstractMethodSymbol implements MethodSymbol {

  /**
   * Имя метода.
   */
  @EqualsAndHashCode.Include
  private final String name;

  /**
   * {@link DocumentContext}, в котором объявлен метод.
   */
  @EqualsAndHashCode.Include
  private final DocumentContext owner;

  /**
   * Строка начала тела метода — от {@code Процедура}/{@code Функция} (или
   * первой аннотации) до закрывающего ключевого слова.
   */
  @Getter(AccessLevel.NONE)
  private final int startLine;

  /**
   * Колонка начала тела метода (см. {@link #startLine}).
   */
  @Getter(AccessLevel.NONE)
  private final int startCharacter;

  /**
   * Строка завершения метода — закрывающее ключевое слово.
   */
  @Getter(AccessLevel.NONE)
  private final int endLine;

  /**
   * Колонка завершения метода (см. {@link #endLine}).
   */
  @Getter(AccessLevel.NONE)
  private final int endCharacter;

  /**
   * Строка имени метода (subName) — используется как selectionRange и для
   * матчинга позиции к символу.
   */
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  private final int subNameLine;

  /**
   * Колонка начала subName (см. {@link #subNameLine}).
   */
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  private final int subNameStartCharacter;

  /**
   * Колонка конца subName (см. {@link #subNameLine}).
   */
  @Getter(AccessLevel.NONE)
  @EqualsAndHashCode.Include
  private final int subNameEndCharacter;

  /**
   * Родительский символ — область ({@code #Область}), модуль или вложенная область.
   * Выставляется снаружи (после построения дерева), поэтому {@code @Setter}.
   */
  @Setter
  @Builder.Default
  private Optional<SourceDefinedSymbol> parent = Optional.empty();

  /**
   * Вложенные символы (локальные переменные, области внутри метода и т.п.).
   */
  @Builder.Default
  private final List<SourceDefinedSymbol> children = new ArrayList<>();

  /**
   * {@code true} для функции (с {@code Возврат}), {@code false} для процедуры.
   */
  private final boolean function;

  /**
   * Объявлен ли метод с ключевым словом {@code Экспорт}.
   */
  private final boolean export;

  /**
   * Распаренная BSL-doc-документация метода (комментарии перед сигнатурой).
   */
  private final Optional<MethodDescription> description;

  /**
   * Помечен ли метод как устаревший (через doc-комментарии).
   */
  private final boolean deprecated;

  /**
   * Объявлен ли метод с ключевым словом {@code Асинх} ({@code Async}).
   */
  private final boolean async;

  /**
   * Объявленные параметры метода в порядке объявления.
   */
  @Builder.Default
  private final List<ParameterDefinition> parameters = Collections.emptyList();

  /**
   * Compiler-directive ({@code &НаСервере}, {@code &НаКлиенте}, …) если
   * стоит перед методом. В OScript обычно отсутствует.
   */
  @Builder.Default
  private final Optional<CompilerDirectiveKind> compilerDirectiveKind = Optional.empty();

  /**
   * Аннотации метода: всё, что стоит со {@code &} перед сигнатурой и не
   * является compiler-directive (в OScript — пользовательские аннотации
   * вроде {@code &Желудь}).
   */
  @Builder.Default
  private final List<Annotation> annotations = Collections.emptyList();

  @Override
  public Range getRange() {
    return Ranges.create(startLine, startCharacter, endLine, endCharacter);
  }

  @Override
  public Range getSubNameRange() {
    return Ranges.create(subNameLine, subNameStartCharacter, subNameLine, subNameEndCharacter);
  }

  @Override
  public Range getSelectionRange() {
    return getSubNameRange();
  }

  @Override
  public Optional<RegionSymbol> getRegion() {
    return getParent()
      .filter(RegionSymbol.class::isInstance)
      .map(RegionSymbol.class::cast);
  }

  /**
   * Кастомизация generated {@link AbstractMethodSymbolBuilder}: позволяет
   * передавать {@code range}/{@code subNameRange} как {@code Range} вместо
   * четырёх int-полей. Параметризация по подтипам {@code C}/{@code B}
   * сохраняет fluent-цепочку builder'а потомков.
   *
   * @param <C> тип конструируемого символа-наследника
   * @param <B> тип самого builder'а-наследника (self-type для fluent-цепочки)
   */
  public abstract static class AbstractMethodSymbolBuilder<
    C extends AbstractMethodSymbol,
    B extends AbstractMethodSymbolBuilder<C, B>
    > {

    public B range(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      this.startLine = start.getLine();
      this.startCharacter = start.getCharacter();
      this.endLine = end.getLine();
      this.endCharacter = end.getCharacter();
      return self();
    }

    public B subNameRange(Range range) {
      var start = range.getStart();
      var end = range.getEnd();
      this.subNameLine = start.getLine();
      this.subNameStartCharacter = start.getCharacter();
      this.subNameEndCharacter = end.getCharacter();
      return self();
    }
  }
}
