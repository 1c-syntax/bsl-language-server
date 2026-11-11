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

import com.github._1c_syntax.bsl.parser.SDBLParser;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Источники одного запроса: {@code алиас (lower) → имя таблицы}. Таблица без
 * алиаса адресуется собственным именем, поэтому лежит и под ним.
 * <p>
 * Собираются все источники предложения {@code ИЗ} — и вложенные в соединения
 * тоже. Источник, за которым таблицы нет (подзапрос, временная таблица,
 * параметр), в набор не попадает: полей у такого имени не спросить.
 */
final class QuerySources {

  private final Map<String, String> tablesByAlias = new LinkedHashMap<>();

  /**
   * Разбирает источники запроса.
   *
   * @param dataSources предложение {@code ИЗ}; {@code null}, если его в запросе нет.
   */
  QuerySources(SDBLParser.@Nullable DataSourcesContext dataSources) {
    if (dataSources != null && dataSources.tables != null) {
      dataSources.tables.forEach(this::collect);
    }
  }

  /**
   * Имя таблицы, стоящей за алиасом.
   *
   * @param alias алиас источника либо его собственное имя.
   * @return имя таблицы; пусто, если такого источника в запросе нет.
   */
  String tableOf(String alias) {
    return tablesByAlias.getOrDefault(alias.toLowerCase(Locale.ROOT), "");
  }

  /**
   * Имена всех таблиц запроса в порядке объявления.
   *
   * @return имена таблиц; пусто, если таблиц у запроса нет.
   */
  List<String> allTables() {
    return List.copyOf(tablesByAlias.values());
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
    var external = dataSource.externalDataSourceTable();
    if (external != null) {
      // Имя такой таблицы разбирается отдельным правилом целиком
      // (ВнешнийИсточникДанных.X.Таблица.Y), и собирать его заново из частей незачем.
      return external.getText();
    }
    var virtualTable = dataSource.virtualTable();
    if (virtualTable != null) {
      return virtualTableName(virtualTable);
    }
    var table = dataSource.table();
    return table == null ? "" : tableName(table);
  }

  /**
   * Имя виртуальной таблицы: имя объекта и вид таблицы за ним
   * ({@code РегистрНакопления.Продажи.Остатки}).
   */
  private static String virtualTableName(SDBLParser.VirtualTableContext virtualTable) {
    var mdo = virtualTable.mdo();
    if (mdo == null || virtualTable.virtualTableName == null) {
      return "";
    }
    return mdoName(mdo) + "." + virtualTable.virtualTableName.getText();
  }

  /**
   * Имя обычной таблицы: имя объекта, а у табличной части — ещё и её имя
   * ({@code Документ.Заказ.Товары}).
   */
  private static String tableName(SDBLParser.TableContext table) {
    var mdo = table.mdo();
    if (mdo == null) {
      return "";
    }
    if (table.objectTableName == null) {
      return mdoName(mdo);
    }
    return mdoName(mdo) + "." + table.objectTableName.getText();
  }

  private static String mdoName(SDBLParser.MdoContext mdo) {
    return mdo.type.getText() + "." + mdo.tableName.getText();
  }
}
