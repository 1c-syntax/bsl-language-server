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

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.index.AssignmentByReceiverIndex;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.registry.FormByNameResolver;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.languageserver.utils.expressiontree.MethodCallNode;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Знания о формах, нужные при выводе типа выражения.
 * <p>
 * Форма — единственный вид модуля, у которого прикладной смысл выражения не выводится
 * из объявлений платформы: {@code ОткрытьФорму("Справочник.Товары.ФормаСписка")}
 * объявлено возвращающим обобщённую {@code ФормаКлиентскогоПриложения}, элемент формы
 * бывает назван строкой ({@code Элементы["Кнопка"]}), а вид созданного в коде элемента
 * задаётся отдельной строкой кода. Всё это — свойства 1С, а не свойства узлов выражения,
 * поэтому живёт здесь, а не в {@link ExpressionTypeInferencer}.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class FormExpressionInference {

  /**
   * Платформенные функции, первым параметром которых идёт имя формы (в нижнем регистре,
   * оба языка). {@code ОткрытьФормуМодально} устарела, но в коде на режиме совместимости
   * встречается и типизируется так же.
   */
  private static final Set<String> FORM_OPENING_FUNCTIONS = Set.of(
    "получитьформу", "getform",
    "открытьформу", "openform",
    "открытьформумодально", "openformmodal");

  /** Свойство элемента формы, задающее его вид: {@code Элемент.Вид = ВидГруппыФормы.Страницы}. */
  private static final String KIND_PROPERTY_RU = "Вид";
  private static final String KIND_PROPERTY_EN = "Kind";

  private final TypeRegistry typeRegistry;
  private final FormByNameResolver formByNameResolver;
  private final AssignmentByReceiverIndex assignmentByReceiverIndex;

  /**
   * Тип формы, открываемой по имени: {@code ОткрытьФорму("Справочник.Контрагенты.ФормаОбъекта")}
   * даёт тип именно этой формы, а не обобщённую {@code ФормаКлиентскогоПриложения}, —
   * значит, дальше работает разыменование её реквизитов, элементов и параметров.
   * <p>
   * Требуется строковый литерал: имя, собранное в переменной, статически неизвестно —
   * тогда остаётся обобщённый тип возврата платформенной функции.
   *
   * @param documentContext документ с вызовом.
   * @param methodName      имя вызываемой функции.
   * @param call            узел вызова.
   * @return типы конкретной формы; {@code null}, если это не открытие формы по литералу
   *     либо форма не найдена.
   */
  public @Nullable TypeSet openedFormType(DocumentContext documentContext, String methodName, MethodCallNode call) {
    if (!isFormOpeningFunction(methodName)) {
      return null;
    }
    var formName = firstStringLiteral(call);
    if (formName == null) {
      return null;
    }
    return formByNameResolver.resolve(documentContext, formName)
      .map(TypeSet::of)
      .orElse(null);
  }

  private static boolean isFormOpeningFunction(String methodName) {
    return FORM_OPENING_FUNCTIONS.contains(methodName.toLowerCase(Locale.ROOT));
  }

  /**
   * Уточнение типа вызова у получателя, известное только про формы:
   * <ul>
   *   <li>форма у методов модуля менеджера ({@code Справочники.Товары.ПолучитьФормуСписка()}) —
   *       какую форму открывать, известно из её роли у объекта;</li>
   *   <li>{@code Элементы.Найти("Кнопка")} — это тот же элемент формы, что и
   *       {@code Элементы.Кнопка}, только имя записано строкой.</li>
   * </ul>
   *
   * @param documentContext документ с вызовом.
   * @param receiver        типы получателя.
   * @param memberName      имя вызываемого метода.
   * @param call            узел вызова.
   * @return уточнённый тип; {@code null}, если правило неприменимо.
   */
  public @Nullable TypeSet refinedCallTypes(DocumentContext documentContext, TypeSet receiver,
                                            String memberName, MethodCallNode call) {
    var managerForm = formByNameResolver.resolveManagerForm(documentContext, receiver, memberName,
      firstStringLiteral(call), documentContext.getFileType());
    if (managerForm.isPresent()) {
      return TypeSet.of(managerForm.get());
    }
    if (isFindByName(memberName)) {
      return memberByLiteralName(documentContext, receiver, firstStringLiteral(call));
    }
    return null;
  }

  /**
   * Член получателя, названный строковым литералом: {@code Элементы["Кнопка"]} — это ровно
   * тот же элемент формы, что и {@code Элементы.Кнопка}.
   * <p>
   * Правило срабатывает только когда член с таким именем у получателя действительно
   * есть, поэтому по коллекциям, где индекс — не имя члена ({@code Массив[0]},
   * {@code ТЧ.Найти(Значение)}), оно молча не срабатывает и работает общий путь.
   *
   * @param documentContext документ с выражением.
   * @param receiver        типы получателя.
   * @param literalName     имя из строкового литерала; {@code null}, если это не литерал.
   * @return типы найденного члена; {@code null}, если имя не литерал либо такого
   *     члена у получателя нет.
   */
  public @Nullable TypeSet memberByLiteralName(DocumentContext documentContext, TypeSet receiver,
                                               @Nullable String literalName) {
    if (literalName == null || literalName.isBlank()) {
      return null;
    }
    var name = literalName.trim();
    var fileType = documentContext.getFileType();
    var result = TypeSet.EMPTY;
    for (var ref : receiver.refs()) {
      for (var member : typeRegistry.getMembers(ref, fileType)) {
        if (member.kind() == MemberKind.PROPERTY && !member.generic() && member.matches(name)) {
          result = result.union(member.returnTypes());
        }
      }
    }
    return result.isEmpty() ? null : result;
  }

  /**
   * Присваивания вида элементу формы ({@code Элемент.Вид = ВидГруппыФормы.ОбычнаяГруппа})
   * по позиции их начала — то же, чем для полей структуры служат операторы-мутаторы.
   *
   * @param variable переменная-элемент формы.
   * @return присваивания по позициям, в порядке следования в документе.
   */
  public Map<Position, BSLParser.AssignmentContext> kindAssignmentsOf(VariableSymbol variable) {
    var owner = variable.getOwner();
    var ast = safeGetAst(owner);
    if (ast == null) {
      return Map.of();
    }
    Map<Position, BSLParser.AssignmentContext> assignments = new LinkedHashMap<>();
    for (var assignment : assignmentByReceiverIndex.byReceiver(owner.getUri(), ast, variable.getName())) {
      if (assignedKindName(assignment) != null) {
        assignments.put(Ranges.create(assignment).getStart(), assignment);
      }
    }
    return assignments;
  }

  /**
   * Применить к типу элемента формы уточнение по присвоенному виду.
   * <p>
   * У элемента, созданного в коде ({@code Элементы.Добавить("Г", Тип("ГруппаФормы"))}),
   * конкретный вид задаётся отдельной строкой, и именно от вида зависит почти вся
   * специфика элемента: {@code ЦветФона} объявлен в расширении обычной группы, а не в
   * {@code ГруппаФормы}.
   * <p>
   * Имя специализированного типа собирается напрямую: под каждый вид элемента
   * зарегистрирован тип {@code <база>.<ИмяВида>}, а значение перечисления
   * {@code ВидГруппыФормы.ОбычнаяГруппа} несёт ровно это имя. Таблица соответствий
   * поэтому не нужна, а проверка «тип есть в реестре» отсекает присваивания, к видам
   * элементов отношения не имеющие.
   *
   * @param variable   переменная-получатель.
   * @param assignment присваивание вида; {@code null} — по позиции ничего не нашлось.
   * @param incoming   тип переменной перед присваиванием.
   * @return уточнённый тип; исходный, если уточнять нечем.
   */
  public TypeSet applyKindAssignment(VariableSymbol variable, BSLParser.@Nullable AssignmentContext assignment,
                                     TypeSet incoming) {
    if (assignment == null || incoming.refs().isEmpty()) {
      return incoming;
    }
    var kindName = assignedKindName(assignment);
    if (kindName == null) {
      return incoming;
    }
    var fileType = variable.getOwner().getFileType();
    var refined = TypeSet.EMPTY;
    for (var ref : incoming.refs()) {
      var kindRef = typeRegistry.resolve(ref.qualifiedName() + "." + kindName, fileType);
      if (kindRef.isPresent()) {
        refined = refined.union(TypeSet.of(kindRef.get()));
      }
    }
    return refined.isEmpty() ? incoming : refined;
  }

  /**
   * Имя значения перечисления, присваиваемого свойству {@code Вид} получателя:
   * {@code Элемент.Вид = ВидГруппыФормы.ОбычнаяГруппа} → {@code ОбычнаяГруппа}.
   *
   * @return имя вида; {@code null}, если присваивается не {@code Вид} либо правая
   *     часть — не обращение к значению перечисления.
   */
  private static @Nullable String assignedKindName(BSLParser.AssignmentContext assignment) {
    var lValue = assignment.lValue();
    // Ровно один сегмент после имени: `Элемент.Вид`, а не `Элементы.Кнопка.Вид`.
    var acceptor = lValue == null ? null : lValue.acceptor();
    if (acceptor == null || !acceptor.modifier().isEmpty()) {
      return null;
    }
    var property = acceptor.accessProperty();
    if (property == null) {
      return null;
    }
    var propertyName = property.IDENTIFIER().getText();
    if (!KIND_PROPERTY_RU.equalsIgnoreCase(propertyName) && !KIND_PROPERTY_EN.equalsIgnoreCase(propertyName)) {
      return null;
    }
    var expression = assignment.expression();
    var text = expression == null ? "" : expression.getText();
    var dot = text.lastIndexOf('.');
    return dot <= 0 || dot == text.length() - 1 ? null : text.substring(dot + 1);
  }

  /** Поиск члена по имени — {@code Элементы.Найти("Кнопка")}. */
  private static boolean isFindByName(String methodName) {
    return "Найти".equalsIgnoreCase(methodName) || "Find".equalsIgnoreCase(methodName);
  }

  /** Первый аргумент вызова, если это строковый литерал; иначе {@code null}. */
  private static @Nullable String firstStringLiteral(MethodCallNode call) {
    var arguments = call.arguments();
    return arguments.isEmpty() ? null : OpenDataObjectInference.stringLiteralOf(arguments.get(0));
  }

  /** AST документа; {@code null}, если разбор недоступен. */
  private static BSLParser.@Nullable FileContext safeGetAst(DocumentContext documentContext) {
    try {
      return documentContext.getAst();
    } catch (RuntimeException e) {
      return null;
    }
  }
}
