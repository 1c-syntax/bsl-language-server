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
package com.github._1c_syntax.bsl.languageserver.types.compilation;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.compilation.ExecutionContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.CompilerDirectiveKind;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.lsp4j.Position;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Общие правила определения контекстов компиляции и переходов между ними.
 * <p>
 * Возможности модуля определяются его метаданными и доступностью типов в синтакс-помощнике.
 * Директива ограничивает контексты метода, а препроцессор — контексты конкретного объявления.
 * Выбор проверяемых режимов проекта выполняется отдельно и не расширяет возможности модуля.
 * <p>
 * Объявления и границы веток препроцессора кешируются по версии символьного дерева без
 * удержания AST. Флаги метаданных и настройки корня читаются при запросе; кеш не фиксирует
 * их значения. Условия объявлений доступны и после выгрузки AST.
 */
@Component
@WorkspaceScope
public class CompilationContextResolver {

  private final ModuleCompilationContexts modules;
  private final CompilationContextIndex index = new CompilationContextIndex();

  /**
   * Объявление метода без привязки к текущим флагам метаданных.
   * <p>
   * Одно имя может иметь несколько объявлений в разных ветках препроцессора;
   * имя хранится в ключе карты {@link #declarations(DocumentContext)}.
   * Записи, полученные из {@link #declarations(DocumentContext)}, содержат неизменяемые множества;
   * прямой конструктор не копирует переданное множество.
   *
   * @param directive исходная директива компиляции; пустой {@link Optional}, если она отсутствует.
   * @param branch контексты ветки объявления: все контексты для безусловного объявления,
   *     пустое множество для недостижимой ветки, {@code null} для неизвестного условия.
   * @param exported признак экспорта метода.
   */
  public record Declaration(Optional<CompilerDirectiveKind> directive,
                            @Nullable Set<ExecutionContext> branch, boolean exported) { }

  /**
   * Контексты метода после применения директивы к возможностям модуля.
   * <p>
   * Исходный профиль {@link #methodContext} не включает препроцессор; результат
   * {@link #contextAt} дополнительно ограничен веткой в запрошенной позиции.
   * Пользовательский выбор проверяемых режимов применяется потребителем отдельно.
   * Пустое множество означает известное отсутствие контекстов, а не недостаток данных.
   * Результаты резолвера содержат неизменяемое множество; прямой конструктор его не копирует.
   *
   * @param directive эффективная директива, включая серверную по умолчанию для управляемой
   *     формы или команды; пустой {@link Optional}, если нет ни исходной директивы, ни значения по умолчанию.
   * @param contexts контексты, разрешённые модулем и директивой.
   */
  public record MethodContext(Optional<CompilerDirectiveKind> directive, Set<ExecutionContext> contexts) { }

  /**
   * Создать резолвер рабочей области.
   *
   * @param typeRegistry реестр типов той же рабочей области
   */
  public CompilationContextResolver(TypeRegistry typeRegistry) {
    modules = new ModuleCompilationContexts(typeRegistry);
  }

  /**
   * Получить все объявления методов из текущего символьного дерева без обращения к AST.
   * <p>
   * Ключи сравниваются без учёта регистра; одноимённые объявления сохраняются в одном списке.
   * Кеш привязан к дереву, поэтому перестроенный документ получает новую запись.
   *
   * @param document документ — владелец объявлений.
   * @return неизменяемая карта с неизменяемыми списками; пустая, если в дереве нет методов,
   *     в том числе до первого разбора документа, когда дерево содержит только символ модуля.
   */
  public Map<String, List<Declaration>> declarations(DocumentContext document) {
    return index.declarations(document);
  }

  /**
   * Режимы, поддержка которых включена для проекта, до ограничений конкретного модуля.
   *
   * @param document документ рабочей области с её общими настройками.
   * @return все контексты либо множество без файловых вариантов толстого клиента.
   */
  public Set<ExecutionContext> supportedContexts(DocumentContext document) {
    return ModuleCompilationContexts.supportedContexts(document);
  }

  /**
   * Определить контексты компиляции конкретного объявления метода.
   * <p>
   * Пересекает возможности модуля, директиву метода и сохранённое условие его ветки.
   * Одноимённые объявления не объединяются. AST не требуется; настройки включения
   * диагностик и автоматически выбранные режимы проекта на результат не влияют.
   *
   * @param method метод текущего символьного дерева документа.
   * @return неизменяемые контексты, включая пустое множество для исключённого объявления;
   *     пустой Optional при недостатке метаданных, неизвестном условии или для OneScript.
   */
  public Optional<Set<ExecutionContext>> declarationContexts(MethodSymbol method) {
    var owner = method.getOwner();
    if (owner.getFileType() != FileType.BSL) {
      return Optional.empty();
    }
    var branch = owner.getSymbolTreeNoLock().getMethodPreprocessorContexts(method);
    if (branch == null) {
      return Optional.empty();
    }
    return methodContext(owner, method.getCompilerDirectiveKind()).map((MethodContext profile) -> {
      var contexts = ExecutionContext.copyOf(profile.contexts());
      contexts.retainAll(branch);
      return Set.copyOf(contexts);
    });
  }

  /**
   * Получить возможные контексты участка кода до применения его внутренних инструкций препроцессора.
   * <p>
   * Учитываются возможности модуля, директива охватывающего метода и ветка его объявления.
   * Неизвестные ограничения сохраняют все ещё возможные режимы, а не исключают код.
   * Результат задаёт верхнюю границу доступности; настройки проверяемых диагностикой режимов
   * на него не влияют. Для OneScript контексты платформы не применяются.
   *
   * @param document документ с построенным символьным деревом.
   * @param node тело метода, модуля или узел внутри него.
   * @return неизменяемые возможные контексты BSL, включая пустое множество для исключённого
   *     объявления; пустой Optional для OneScript.
   */
  public Optional<Set<ExecutionContext>> possibleContexts(DocumentContext document, ParseTree node) {
    if (document.getFileType() != FileType.BSL) {
      return Optional.empty();
    }
    var sub = CompilationContextIndex.enclosingMethod(node);
    if (sub == null) {
      var contexts = moduleContexts(document);
      return Optional.of(contexts.isEmpty() ? supportedContexts(document) : Set.copyOf(contexts));
    }
    var tree = document.getSymbolTree();
    var method = tree.getMethodSymbol(sub);
    if (method.isEmpty()) {
      return Optional.of(supportedContexts(document));
    }
    var contexts = ExecutionContext.copyOf(methodContext(document, method.get().getCompilerDirectiveKind())
      .map(MethodContext::contexts).orElse(supportedContexts(document)));
    var branch = tree.getMethodPreprocessorContexts(method.get());
    if (branch != null) {
      contexts.retainAll(branch);
    }
    return Optional.of(Set.copyOf(contexts));
  }

  /**
   * Найти режимы, которыми текущая ветка сужает возможные контексты охватывающего тела.
   * <p>
   * Используется для позиционного вывода типов: повторная проверка того же символа должна
   * выбирать значения из соответствующей предыдущей ветки. Без сужения достаточно общего
   * результата тела; неизвестное условие также сохраняет его.
   *
   * @param document документ с AST и символьным деревом.
   * @param node узел внутри охватывающего тела.
   * @param position позиция запроса внутри этого тела.
   * @return суженные режимы, в том числе пустое множество для исключённого кода;
   *     пустой Optional, если дополнительного ограничения нет или это OneScript.
   */
  public Optional<Set<ExecutionContext>> narrowedContextsAt(DocumentContext document, ParseTree node,
                                                           Position position) {
    if (document.getFileType() != FileType.BSL) {
      return Optional.empty();
    }
    var branch = index.preprocessorContextsAt(document, position);
    if (branch == null || branch.equals(ExecutionContext.ALL)) {
      return Optional.empty();
    }
    var possible = possibleContexts(document, node).orElseThrow();
    if (branch.containsAll(possible)) {
      return Optional.empty();
    }
    var narrowed = ExecutionContext.copyOf(possible);
    narrowed.retainAll(branch);
    return Optional.of(Set.copyOf(narrowed));
  }

  /**
   * Определить контекст кода в позиции, включая незавершённое обращение в completion.
   * <p>
   * Профиль берётся у охватывающего метода либо тела модуля. Границы веток собираются один раз
   * на версию документа; запрос не требует существующего вызова под курсором.
   *
   * @param document документ с AST.
   * @param position позиция в коде, включая пробелы и незавершённые идентификаторы.
   * @return профиль с пересечением контекстов метода и текущей ветки, либо пустой Optional
   *     при неизвестном контексте, внутри директивы препроцессора или для OneScript.
   */
  public Optional<MethodContext> contextAt(DocumentContext document, Position position) {
    if (document.getFileType() != FileType.BSL) {
      return Optional.empty();
    }
    var symbol = document.getSymbolTree().getSymbolAtPosition(position);
    var method = symbol instanceof MethodSymbol enclosing ? Optional.of(enclosing)
      : symbol.getRootParent(MethodSymbol.class).map(MethodSymbol.class::cast);
    var profile = methodContext(document, method.flatMap(MethodSymbol::getCompilerDirectiveKind));
    if (profile.isEmpty()) {
      return Optional.empty();
    }
    var branch = index.preprocessorContextsAt(document, position);
    if (branch == null) {
      return Optional.empty();
    }
    var contexts = ExecutionContext.copyOf(profile.orElseThrow().contexts());
    contexts.retainAll(branch);
    return Optional.of(new MethodContext(profile.orElseThrow().directive(), Set.copyOf(contexts)));
  }

  /**
   * Перевести доступность API из СП в режимы его использования, включая файловые части форм.
   *
   * @param availabilities известная доступность члена или типа платформы.
   * @return поддерживаемые режимы; пустой вход означает отсутствие сведений, а не запрет.
   */
  public Set<ExecutionContext> platformContexts(Collection<Availability> availabilities) {
    return ModuleCompilationContexts.platformContexts(availabilities);
  }

  /**
   * Контексты доступности значения типа, включая прикладные специализации.
   * <p>
   * Для общего модуля используются флаги метаданных и возможность серверного вызова;
   * разбор его файла не требуется. Для остальных типов используются сведения СП,
   * унаследованные от generic-типа. Отсутствие данных не доказывает недоступность.
   *
   * @param document документ, задающий конфигурацию и язык файла.
   * @param type тип значения.
   * @return известные контексты, включая пустой набор; пустой Optional при недостатке сведений.
   */
  public Optional<Set<ExecutionContext>> typeContexts(DocumentContext document, TypeRef type) {
    return modules.typeContexts(document, type);
  }

  /**
   * Выбрать режимы для параметров диагностики со значением {@code auto}.
   * <p>
   * Назначение использования ограничивает мобильные и платформенные режимы, а основной режим
   * запуска — обычный или управляемые клиенты. Без этих метаданных соответствующее ограничение
   * не применяется. Мобильный автономный сервер исключён до поддержки его состава в MDClasses.
   * <p>
   * Результат задаёт выбор проверок проекта, а не контексты компиляции отдельного модуля.
   *
   * @param document документ, через который доступны метаданные корня проекта.
   * @return неизменяемое множество автоматически выбранных режимов.
   */
  public Set<ExecutionContext> automaticContexts(DocumentContext document) {
    return ModuleCompilationContexts.automaticContexts(document);
  }

  /**
   * Определить возможные контексты компиляции модуля.
   * <p>
   * Для общих модулей используются флаги метаданных, для форм и команд — вид формы и настройки
   * совместимости приложений. Для остальных объектных модулей доступность берётся из СП;
   * для модулей приложения, сеанса и внешнего соединения учитываются их собственные правила.
   * Отсутствующая доступность объектного типа не заменяется предположением о сервере.
   *
   * @param document документ с определённым видом модуля и доступными метаданными.
   * @return неизменяемое множество контекстов; пустое, если контексты определить нельзя
   *     либо флаги модуля не разрешают ни одного. Директива метода, препроцессор и выбор
   *     проверяемых режимов здесь не учитываются.
   */
  public Set<ExecutionContext> moduleContexts(DocumentContext document) {
    return modules.moduleContexts(document);
  }

  /**
   * Ограничить контексты модуля директивой компиляции метода.
   * <p>
   * Управляемые формы и команды без директивы используют {@code НаСервере}.
   * Директива общего модуля не может включить контекст, запрещённый его флагами.
   *
   * @param document документ — владелец метода.
   * @param directive исходная директива метода; пустой {@link Optional}, если её нет.
   * @return профиль с неизменяемым множеством, в том числе пустым при известном исключении
   *     всех контекстов; {@link Optional#empty()}, если контексты модуля неизвестны
   *     или сочетание вида модуля и директивы не поддерживается. Ветки препроцессора
   *     из {@link Declaration#branch()} вызывающий код пересекает с результатом отдельно.
   */
  public Optional<MethodContext> methodContext(DocumentContext document, Optional<CompilerDirectiveKind> directive) {
    var contexts = ExecutionContext.copyOf(moduleContexts(document));
    if (contexts.isEmpty()) {
      return Optional.empty();
    }
    if (usesFormDirectives(document)) {
      return MethodCompilationContexts.formMethodContext(document, directive.orElse(CompilerDirectiveKind.AT_SERVER), contexts);
    }
    if (directive.isEmpty()) {
      return Optional.of(new MethodContext(directive, Set.copyOf(contexts)));
    }
    return MethodCompilationContexts.commonMethodContext(document, directive.orElseThrow(), contexts);
  }

  /**
   * Проверить вызов найденного исходного метода, сохраняя различие между существованием и доступностью.
   *
   * @param documentContext документ вызывающего кода.
   * @param caller профиль вызывающего метода.
   * @param owner документ с объявлением цели.
   * @param definition проверяемое альтернативное объявление.
   * @param method профиль цели; пусто, если контексты её компиляции неизвестны.
   * @param context режим вызывающего кода; серверный переход учитывается отдельно.
   * @return доступность объявления, включая причину запрета либо недостаток сведений.
   */
  public CallAvailability availability(DocumentContext documentContext, MethodContext caller,
                                       DocumentContext owner, Declaration definition, Optional<MethodContext> method,
                                       ExecutionContext context) {
    var sameModule = owner.getUri().equals(documentContext.getUri());
    var common = owner.getMdObject().filter(CommonModule.class::isInstance).map(CommonModule.class::cast);
    if (!sameModule && common.isPresent() && !canSeeCommonModule(common.get(), context)) {
      return CallAvailability.MODULE_UNAVAILABLE;
    }
    var branch = definition.branch();
    if (method.isEmpty() || branch == null) {
      return CallAvailability.UNKNOWN;
    }
    return knownAvailability(sameModule, caller, owner, method.orElseThrow(), branch, context);
  }

  private CallAvailability knownAvailability(boolean sameModule, MethodContext caller, DocumentContext owner,
                                             MethodContext method, Collection<ExecutionContext> branch,
                                             ExecutionContext context) {
    if (sameModule && usesFormDirectives(owner)
      && !directivesCompatible(caller.directive().orElseThrow(), method.directive().orElseThrow(), context)) {
      return CallAvailability.METHOD_UNAVAILABLE;
    }
    var target = targetContext(sameModule, owner, method, context);
    return method.contexts().contains(target) && branch.contains(target)
      ? CallAvailability.AVAILABLE : CallAvailability.METHOD_UNAVAILABLE;
  }

  /**
   * Проверить ограничения доступа к контексту внутри одной управляемой формы или команды.
   * Даже при подходящем режиме метод без контекста формы не получает доступ к её
   * клиентским методам и методам с директивой {@code НаСервере}.
   * Вызывается только для профилей форм и команд: их эффективные директивы уже определены,
   * включая серверную директиву по умолчанию.
   */
  private static boolean directivesCompatible(CompilerDirectiveKind caller,
                                             CompilerDirectiveKind callee,
                                             ExecutionContext context) {
    var withoutContext = caller == CompilerDirectiveKind.AT_SERVER_NO_CONTEXT
      || caller == CompilerDirectiveKind.AT_CLIENT_AT_SERVER_NO_CONTEXT;
    if (callee == CompilerDirectiveKind.AT_CLIENT) {
      return ExecutionContext.CLIENTS.contains(context) && !withoutContext;
    }
    return callee != CompilerDirectiveKind.AT_SERVER || !withoutContext;
  }


  /**
   * Определить, применяются ли правила директив управляемых форм и команд.
   * <p>
   * Для модуля формы без сведений о виде формы предполагается управляемая форма.
   *
   * @param document проверяемый документ.
   * @return {@code true} для команд и управляемых форм, {@code false} для остальных модулей.
   */
  public boolean usesFormDirectives(DocumentContext document) {
    return ModuleCompilationContexts.usesFormDirectives(document);
  }

  /**
   * Проверить видимость общего модуля из контекста вызывающего кода.
   * <p>
   * На клиенте и во внешнем соединении серверный модуль может быть виден через
   * флаг «Вызов сервера», даже если локально не компилируется. В файловом варианте
   * достаточно серверного или соответствующего локального флага.
   *
   * @param module метаданные общего модуля.
   * @param context контекст вызывающего кода.
   * @return видимость модуля; существование метода и контекст его выполнения здесь не проверяются.
   */
  public boolean canSeeCommonModule(CommonModule module, ExecutionContext context) {
    return ModuleCompilationContexts.canSeeCommonModule(module, context);
  }

  /**
   * Перевести контекст вызова в контекст целевого метода.
   * <p>
   * Учитывает серверный вызов внешнего общего модуля, директивы форм и команд,
   * а также переход между раздельными частями формы и общим файловым контекстом.
   * Сам переход не доказывает доступность метода: её нужно проверить по профилю и ветке объявления.
   *
   * @param caller документ вызывающего кода.
   * @param owner документ целевого метода.
   * @param method профиль целевого метода.
   * @param context контекст в точке вызова.
   * @return контекст, в котором следует проверять объявление и выводить тип результата метода.
   */
  public ExecutionContext targetContext(DocumentContext caller, DocumentContext owner, MethodContext method,
                                        ExecutionContext context) {
    return targetContext(owner.getUri().equals(caller.getUri()), owner, method, context);
  }

  private ExecutionContext targetContext(boolean sameModule, DocumentContext owner, MethodContext method,
                                         ExecutionContext context) {
    if (usesFormDirectives(owner)) {
      return formTargetContext(method, context);
    }
    if (context.isFileMode()) {
      return context.sharedFileContext();
    }
    if (!sameModule && hasServerTransition(owner, context)) {
      return serverFor(context);
    }
    return context;
  }

  private static ExecutionContext formTargetContext(MethodContext method, ExecutionContext context) {
    var formContext = switch (context) {
      case THICK_CLIENT_MANAGED_FILE -> ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT;
      case THICK_CLIENT_ORDINARY_FILE -> ExecutionContext.THICK_CLIENT_ORDINARY_FILE_CLIENT;
      default -> context;
    };
    if (ExecutionContext.CLIENTS.contains(formContext)
      && method.directive().filter(CompilationContextResolver::isServerDirective).isPresent()) {
      return serverFor(formContext);
    }
    return formContext;
  }

  private static boolean isServerDirective(CompilerDirectiveKind directive) {
    return directive == CompilerDirectiveKind.AT_SERVER || directive == CompilerDirectiveKind.AT_SERVER_NO_CONTEXT;
  }

  private boolean hasServerTransition(DocumentContext owner, ExecutionContext context) {
    if (!isClientOrConnection(context)) {
      return false;
    }
    var common = owner.getMdObject().filter(CommonModule.class::isInstance).map(CommonModule.class::cast);
    if (common.isEmpty()) {
      return false;
    }
    var module = common.orElseThrow();
    return module.isServer() && module.isServerCall() && !moduleContexts(owner).contains(context);
  }

  private static boolean isClientOrConnection(ExecutionContext context) {
    return ExecutionContext.CLIENTS.contains(context)
      || context.optionContext() == ExecutionContext.EXTERNAL_CONNECTION;
  }

  /**
   * Отличить переход на серверную сторону от локального изменения представления контекста.
   * Общий файловый контекст совмещает клиент и сервер и сам по себе переходом не является.
   * Доступность вызова проверяется отдельно; переход не означает обязательного сетевого обмена.
   *
   * @param caller контекст вызывающего кода.
   * @param target контекст цели после {@link #targetContext}.
   * @return {@code true} для перехода с клиента или внешнего соединения на серверную сторону.
   */
  public static boolean isServerTransition(ExecutionContext caller, ExecutionContext target) {
    return caller != target
      && (ExecutionContext.CLIENTS.contains(caller) || ExecutionContext.EXTERNAL_PREPROCESSOR.contains(caller))
      && ExecutionContext.SERVER_CONTEXTS.contains(target) && !ExecutionContext.CLIENTS.contains(target);
  }

  /**
   * Найти клиентские режимы и режимы соединения, допускающие серверный переход к методу.
   * <p>
   * Для формы или команды рассматривается вызов из её клиентской части, для общего модуля —
   * внешний вызов экспортного метода. Локальная доступность общего модуля не считается
   * серверным переходом. Возможность перехода не гарантирует доступность из любой вызывающей
   * процедуры: ограничения её директив проверяются отдельно.
   *
   * @param method целевой метод.
   * @param declarationContexts известные контексты этого объявления, включая условие препроцессора.
   * @return режимы, для которых доказана возможность серверного перехода; пусто, если переход
   *     не найден либо профиль метода неизвестен.
   */
  public Set<ExecutionContext> serverCallContexts(MethodSymbol method, Collection<ExecutionContext> declarationContexts) {
    var owner = method.getOwner();
    var form = usesFormDirectives(owner);
    if (!form && !exportsServerCall(method)) {
      return Set.of();
    }
    return methodContext(owner, method.getCompilerDirectiveKind())
      .map((MethodContext profile) -> serverCallContexts(owner, form, profile, declarationContexts))
      .orElse(Set.of());
  }

  private static boolean exportsServerCall(MethodSymbol method) {
    return method.isExport() && method.getOwner().getMdObject()
      .filter(CommonModule.class::isInstance).map(CommonModule.class::cast)
      .filter(CommonModule::isServer).filter(CommonModule::isServerCall).isPresent();
  }

  private Set<ExecutionContext> serverCallContexts(DocumentContext owner, boolean form, MethodContext profile,
                                                   Collection<ExecutionContext> declarationContexts) {
    var result = ExecutionContext.copyOf(form ? moduleContexts(owner) : supportedContexts(owner));
    result.removeIf((ExecutionContext context) -> {
      if (!isClientOrConnection(context) || (!form && context.isFileMode())) {
        return true;
      }
      var target = targetContext(form, owner, profile, context);
      return !isServerTransition(context, target) || !declarationContexts.contains(target);
    });
    return Set.copyOf(result);
  }

  /**
   * Выбрать серверную сторону вызова из клиентского контекста.
   *
   * @param client клиентский контекст; отдельные файловые части формы сохраняют свой вариант.
   * @return сервер мобильного приложения для его клиента, файловая серверная часть для
   *     файловой клиентской части формы или {@link ExecutionContext#SERVER} в остальных случаях.
   */
  public static ExecutionContext serverFor(ExecutionContext client) {
    return switch (client) {
      case MOBILE_APPLICATION_CLIENT -> ExecutionContext.MOBILE_APPLICATION_SERVER;
      case THICK_CLIENT_MANAGED_FILE_CLIENT -> ExecutionContext.THICK_CLIENT_MANAGED_FILE_SERVER;
      case THICK_CLIENT_ORDINARY_FILE_CLIENT -> ExecutionContext.THICK_CLIENT_ORDINARY_FILE_SERVER;
      default -> ExecutionContext.SERVER;
    };
  }

}
