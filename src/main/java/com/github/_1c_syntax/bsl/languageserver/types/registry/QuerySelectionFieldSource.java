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
package com.github._1c_syntax.bsl.languageserver.types.registry;

import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.parser.SDBLParser;
import com.github._1c_syntax.bsl.parser.SDBLTokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Поля выборки произвольного запроса динамического списка.
 * <p>
 * У такого списка колонки строки — это поля верхнего {@code ВЫБРАТЬ}, а не поля
 * основной таблицы: та задаёт лишь динамическое чтение. Имя поля берётся из
 * алиаса, а без него — из имени колонки; тип — у таблицы из {@code ИЗ}, через
 * тот же {@link QueryTableResolver} (рекурсии не возникает: таблицу он
 * спрашивает без списка, и этот источник для неё молчит).
 * <p>
 * Вычисляемое поле ({@code ВЫБОР…КОНЕЦ}, {@code ВЫРАЗИТЬ}) остаётся колонкой без
 * типа — имя у него есть только с алиасом, без алиаса такое поле пропускается.
 */
@Slf4j
@Component
@WorkspaceScope
@Order(QuerySelectionFieldSource.ORDER)
@RequiredArgsConstructor
class QuerySelectionFieldSource implements QueryTableFieldSource {

  static final int ORDER = 15;

  /**
   * Резолвер спрашивается лениво: он же и владеет этим источником, а
   * внедрение напрямую замкнуло бы бины друг на друга.
   */
  private final ObjectProvider<QueryTableResolver> resolverProvider;

  @Override
  public List<MemberDescriptor> fields(QueryTableRequest request) {
    var list = request.list();
    if (list == null || !list.isCustomQuery() || list.getQueryText().isBlank()) {
      return List.of();
    }
    var query = topLevelQuery(list.getQueryText());
    if (query == null) {
      return List.of();
    }
    var sources = new QuerySources(query.from);
    var result = new ArrayList<MemberDescriptor>();
    for (var field : selectedFields(query)) {
      collectField(field, sources, result);
    }
    return List.copyOf(result);
  }

  /**
   * Верхний запрос текста. У объединения имена колонок задаёт первый запрос,
   * поэтому берётся он; пакет у динамического списка не встречается, но если
   * текст разобрался пакетом — берётся последний запрос.
   *
   * @return запрос; {@code null}, если текст не разобрался.
   */
  private static SDBLParser.@Nullable QueryContext topLevelQuery(String queryText) {
    SDBLParser.QueryPackageContext ast;
    try {
      ast = new SDBLTokenizer(queryText).getAst();
    } catch (RuntimeException e) {
      LOGGER.debug("Не удалось разобрать текст запроса динамического списка", e);
      return null;
    }
    if (ast == null || ast.queries().isEmpty()) {
      return null;
    }
    var queries = ast.queries().get(ast.queries().size() - 1);
    var selectQuery = queries.selectQuery();
    if (selectQuery == null || selectQuery.subquery() == null) {
      return null;
    }
    return selectQuery.subquery().main;
  }

  private static List<SDBLParser.SelectedFieldContext> selectedFields(SDBLParser.QueryContext query) {
    return query.columns == null || query.columns.fields == null ? List.of() : query.columns.fields;
  }

  private void collectField(SDBLParser.SelectedFieldContext field,
                            QuerySources sources,
                            List<MemberDescriptor> sink) {
    var asterisk = field.asteriskField();
    if (asterisk != null) {
      sink.addAll(asteriskFields(asterisk, sources));
      return;
    }
    var column = singleColumn(field);
    var alias = field.alias() == null ? "" : field.alias().name.getText();
    if (alias.isBlank() && column == null) {
      // Вычисляемое поле без алиаса имени не имеет — колонки из него не выйдет.
      return;
    }
    var name = alias.isBlank() ? lastColumnName(column) : alias;
    if (name.isBlank()) {
      return;
    }
    sink.add(MdoMemberFactory.property(BilingualString.of(name), columnTypes(column, sources)));
  }

  /**
   * Колонка, из которой поле состоит целиком. У вычисляемого поля колонок
   * может не быть вовсе либо быть несколько — тип такого поля неизвестен.
   */
  private static SDBLParser.@Nullable ColumnContext singleColumn(SDBLParser.SelectedFieldContext field) {
    var expression = field.expressionField();
    if (expression == null || expression.expression() == null) {
      return null;
    }
    return expression.expression().column();
  }

  private static String lastColumnName(SDBLParser.@Nullable ColumnContext column) {
    if (column == null || column.columnNames == null || column.columnNames.isEmpty()) {
      return "";
    }
    return column.columnNames.get(column.columnNames.size() - 1).getText();
  }

  /**
   * Типы колонки — у поля её таблицы. Берутся, только если колонка обращается
   * к источнику напрямую ({@code Спр.Наименование}): у пути вглубь
   * ({@code Спр.Владелец.Код}) тип поля определяется цепочкой, а не таблицей.
   */
  private TypeSet columnTypes(SDBLParser.@Nullable ColumnContext column, QuerySources sources) {
    if (column == null || column.mdoName == null || column.columnNames.size() != 1) {
      return TypeSet.EMPTY;
    }
    var tableName = sources.tableOf(column.mdoName.getText());
    if (tableName.isBlank()) {
      return TypeSet.EMPTY;
    }
    return resolverProvider.getObject().fields(tableName).stream()
      .filter(member -> member.matches(lastColumnName(column)))
      .findFirst()
      .map(MemberDescriptor::returnTypes)
      .orElse(TypeSet.EMPTY);
  }

  /**
   * Поля звёздочки: {@code Таблица.*} — поля названного источника, голая
   * {@code *} — поля всех источников запроса.
   */
  private List<MemberDescriptor> asteriskFields(SDBLParser.AsteriskFieldContext asterisk, QuerySources sources) {
    var tableNames = asterisk.tableName == null
      ? sources.allTables()
      : List.of(sources.tableOf(asterisk.tableName.getText()));
    var result = new ArrayList<MemberDescriptor>();
    for (var tableName : tableNames) {
      if (!tableName.isBlank()) {
        result.addAll(resolverProvider.getObject().fields(tableName));
      }
    }
    return result;
  }

  /**
   * Источники запроса: {@code алиас (lower) → имя таблицы}. Таблица без алиаса
   * адресуется собственным именем, поэтому лежит и под ним.
   */
  private static final class QuerySources {

    private final Map<String, String> tablesByAlias = new LinkedHashMap<>();

    private QuerySources(SDBLParser.@Nullable DataSourcesContext dataSources) {
      if (dataSources != null && dataSources.tables != null) {
        dataSources.tables.forEach(this::collect);
      }
    }

    private void collect(SDBLParser.DataSourceContext dataSource) {
      var nested = dataSource.dataSource();
      if (nested != null) {
        collect(nested);
      }
      var tableName = tableNameOf(dataSource);
      if (!tableName.isBlank()) {
        var alias = dataSource.alias() == null ? tableName : dataSource.alias().name.getText();
        tablesByAlias.putIfAbsent(alias.toLowerCase(Locale.ROOT), tableName);
      }
      if (dataSource.joins != null) {
        dataSource.joins.stream()
          .map(SDBLParser.JoinPartContext::dataSource)
          .filter(Objects::nonNull)
          .forEach(this::collect);
      }
    }

    /**
     * Имя таблицы источника так, как его пишет запрос
     * ({@code Справочник.Номенклатура}, {@code РегистрНакопления.Продажи.Остатки}).
     * Пусто у источника, за которым таблицы нет: подзапроса, временной таблицы,
     * параметра.
     */
    private static String tableNameOf(SDBLParser.DataSourceContext dataSource) {
      var virtualTable = dataSource.virtualTable();
      if (virtualTable != null) {
        return virtualTable.mdo() == null || virtualTable.virtualTableName == null
          ? ""
          : mdoName(virtualTable.mdo()) + "." + virtualTable.virtualTableName.getText();
      }
      var table = dataSource.table();
      if (table == null || table.mdo() == null) {
        return "";
      }
      return table.objectTableName == null
        ? mdoName(table.mdo())
        : mdoName(table.mdo()) + "." + table.objectTableName.getText();
    }

    private static String mdoName(SDBLParser.MdoContext mdo) {
      return mdo.type.getText() + "." + mdo.tableName.getText();
    }

    private String tableOf(String alias) {
      return tablesByAlias.getOrDefault(alias.toLowerCase(Locale.ROOT), "");
    }

    private List<String> allTables() {
      return List.copyOf(tablesByAlias.values());
    }
  }
}
