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
import com.github._1c_syntax.bsl.languageserver.types.model.Availability;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.registry.ConfigurationModuleMembersProvider;
import com.github._1c_syntax.bsl.languageserver.types.registry.ModulePlatformTypeNames;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.mdo.support.ApplicationRunMode;
import com.github._1c_syntax.bsl.mdo.support.UsePurposes;
import com.github._1c_syntax.bsl.types.ModuleType;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** Возможности модулей и типов из метаданных конфигурации и синтакс-помощника. */
@RequiredArgsConstructor
final class ModuleCompilationContexts {
  private final TypeRegistry typeRegistry;

  /**
   * Режимы, поддержка которых включена для проекта, до ограничений конкретного модуля.
   *
   * @param document документ рабочей области с её общими настройками.
   * @return все контексты либо множество без файловых вариантов толстого клиента.
   */
  public Set<ExecutionContext> supportedContexts(DocumentContext document) {
    return document.getServerContext().getLanguageServerConfiguration().isSupportThickClientFileMode()
      ? ExecutionContext.ALL : ExecutionContext.WITHOUT_THICK_CLIENT_FILE;
  }

  /**
   * Перевести доступность API из СП в режимы его использования, включая файловые части форм.
   *
   * @param availabilities известная доступность члена или типа платформы.
   * @return поддерживаемые режимы; пустой вход означает отсутствие сведений, а не запрет.
   */
  public Set<ExecutionContext> platformContexts(Collection<Availability> availabilities) {
    var direct = fromAvailability(availabilities);
    var result = ExecutionContext.copyOf(direct);
    for (var context : ExecutionContext.ALL) {
      if (direct.contains(context.sharedFileContext())
          || context.isFileMode() && ExecutionContext.SERVER_CONTEXTS.contains(context)
            && direct.contains(ExecutionContext.SERVER)) {
        result.add(context);
      }
    }
    return Set.copyOf(result);
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
    if (type.equals(TypeRef.ANY) || type.equals(TypeRef.UNKNOWN)) {
      return Optional.empty();
    }
    if (type.kind() == TypeKind.CONFIGURATION) {
      var module = document.getServerContext().findCommonModule(type.qualifiedName());
      if (module.isPresent()) {
        var contexts = ExecutionContext.copyOf(supportedContexts(document));
        contexts.removeIf(context -> !canSeeCommonModule(module.orElseThrow(), context));
        return Optional.of(Set.copyOf(contexts));
      }
    }
    var available = typeRegistry.getTypeMetadata(type, document.getFileType()).availabilities();
    if (available.isEmpty()) {
      return Optional.empty();
    }
    var contexts = ExecutionContext.copyOf(platformContexts(available));
    contexts.retainAll(supportedContexts(document));
    return Optional.of(Set.copyOf(contexts));
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
    var contexts = ExecutionContext.copyOf(supportedContexts(document));
    // До чтения автономного состава в MDClasses и его учёта здесь этот контекст доступен только через on.
    // https://github.com/1c-syntax/mdclasses/issues/728
    contexts.remove(ExecutionContext.MOBILE_STANDALONE_SERVER);
    var solution = document.getServerContext().getConfiguration();
    if (solution.isEmpty()) {
      return Set.copyOf(contexts);
    }
    var configuration = solution.getMergedConfiguration();
    if (!configuration.getUsePurposes().isEmpty()) {
      var mobile = EnumSet.of(ExecutionContext.MOBILE_APPLICATION_CLIENT, ExecutionContext.MOBILE_APPLICATION_SERVER);
      if (!configuration.getUsePurposes().contains(UsePurposes.MOBILE_PLATFORM_APPLICATION)) {
        contexts.removeAll(mobile);
      }
      if (!configuration.getUsePurposes().contains(UsePurposes.PLATFORM_APPLICATION)) {
        contexts.retainAll(mobile);
      }
    }
    if (configuration.getDefaultRunMode() == ApplicationRunMode.MANAGED_APPLICATION) {
      contexts.removeIf(context -> context.optionContext() == ExecutionContext.THICK_CLIENT_ORDINARY);
    } else if (configuration.getDefaultRunMode() == ApplicationRunMode.ORDINARY_APPLICATION) {
      var managed = EnumSet.of(ExecutionContext.THIN_CLIENT, ExecutionContext.WEB_CLIENT,
        ExecutionContext.MOBILE_CLIENT, ExecutionContext.THICK_CLIENT_MANAGED);
      contexts.removeIf(context -> managed.contains(context.optionContext()));
    } else {
      // Неизвестный режим запуска не ограничивает возможные клиенты.
    }
    return Set.copyOf(contexts);
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
    var contexts = ExecutionContext.copyOf(moduleContextsFromMetadata(document));
    contexts.retainAll(supportedContexts(document));
    return Set.copyOf(contexts);
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
    if (document.getModuleType() == ModuleType.CommandModule) {
      return true;
    }
    return document.getModuleType() == ModuleType.FormModule && document.getMdObject()
      .filter(Form.class::isInstance).map(Form.class::cast)
      .map(form -> form.getFormType() == FormType.MANAGED).orElse(true);
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
    if (context.isFileMode()) {
      return module.isServer() || hasFileModeFlag(module, context.optionContext());
    }
    if (ExecutionContext.SERVERS.contains(context)) {
      return module.isServer();
    }
    return hasClientFlag(module, context) || (module.isServer() && module.isServerCall());
  }

  private static boolean hasFileModeFlag(CommonModule module, ExecutionContext context) {
    return switch (context) {
      case THICK_CLIENT_MANAGED -> module.isClientManagedApplication();
      case THICK_CLIENT_ORDINARY -> module.isClientOrdinaryApplication();
      case EXTERNAL_CONNECTION -> module.isExternalConnection();
      default -> false;
    };
  }

  private static boolean hasClientFlag(CommonModule module, ExecutionContext context) {
    return switch (context) {
      case EXTERNAL_CONNECTION -> module.isExternalConnection();
      case THICK_CLIENT_ORDINARY -> module.isClientOrdinaryApplication();
      default -> module.isClientManagedApplication();
    };
  }

  private Set<ExecutionContext> moduleContextsFromMetadata(DocumentContext document) {
    return switch (document.getModuleType()) {
      case CommonModule -> document.getMdObject().filter(CommonModule.class::isInstance).map(CommonModule.class::cast)
        .map(ModuleCompilationContexts::commonModuleContexts).orElse(Set.of());
      case FormModule -> formContexts(document);
      case CommandModule -> addOrdinaryManagedFormContext(document, managedContexts());
      default -> platformOrApplicationContexts(document);
    };
  }

  private Set<ExecutionContext> formContexts(DocumentContext document) {
    if (usesFormDirectives(document)) {
      return addOrdinaryManagedFormContext(document, managedContexts());
    }
    var configuration = document.getServerContext().getConfiguration().getMergedConfiguration();
    return configuration.isUseOrdinaryFormInManagedApplication()
      ? EnumSet.of(ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
        ExecutionContext.THICK_CLIENT_MANAGED, ExecutionContext.THICK_CLIENT_MANAGED_FILE)
      : EnumSet.of(ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.THICK_CLIENT_ORDINARY_FILE);
  }

  private Set<ExecutionContext> platformOrApplicationContexts(DocumentContext document) {
    var platform = platformContexts(document);
    if (!platform.isEmpty()) {
      return platform;
    }
    return switch (document.getModuleType()) {
      case ManagedApplicationModule -> withFileContexts(ExecutionContext.MANAGED_CLIENTS,
        ExecutionContext.THICK_CLIENT_MANAGED_FILE);
      case OrdinaryApplicationModule -> EnumSet.of(
        ExecutionContext.THICK_CLIENT_ORDINARY, ExecutionContext.THICK_CLIENT_ORDINARY_FILE);
      case ExternalConnectionModule -> ExecutionContext.EXTERNAL_PREPROCESSOR;
      // Доступность события УстановкаПараметровСеанса не задаёт компиляцию модуля сеанса.
      case SessionModule -> withFileContexts(ExecutionContext.SERVERS,
        ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
        ExecutionContext.EXTERNAL_CONNECTION_FILE);
      default -> Set.of();
    };
  }

  /**
   * Контексты компиляции по флагам общего модуля. «Вызов сервера» здесь не участвует:
   * он разрешает обращение к серверному модулю, но не компиляцию его кода на клиенте.
   */
  private static Set<ExecutionContext> commonModuleContexts(CommonModule module) {
    var result = EnumSet.noneOf(ExecutionContext.class);
    if (module.isServer()) {
      result.addAll(ExecutionContext.SERVERS);
      result.addAll(Set.of(ExecutionContext.THICK_CLIENT_MANAGED_FILE, ExecutionContext.THICK_CLIENT_ORDINARY_FILE,
        ExecutionContext.EXTERNAL_CONNECTION_FILE));
    }
    if (module.isClientManagedApplication()) {
      result.addAll(ExecutionContext.MANAGED_CLIENTS);
      result.add(ExecutionContext.THICK_CLIENT_MANAGED_FILE);
    }
    if (module.isClientOrdinaryApplication()) {
      result.add(ExecutionContext.THICK_CLIENT_ORDINARY);
      result.add(ExecutionContext.THICK_CLIENT_ORDINARY_FILE);
    }
    if (module.isExternalConnection()) {
      result.add(ExecutionContext.EXTERNAL_CONNECTION);
      result.add(ExecutionContext.EXTERNAL_CONNECTION_FILE);
    }
    return Set.copyOf(result);
  }

  private static Set<ExecutionContext> addOrdinaryManagedFormContext(DocumentContext document,
                                                             Set<ExecutionContext> contexts) {
    var configuration = document.getServerContext().getConfiguration().getMergedConfiguration();
    if (configuration.isUseManagedFormInOrdinaryApplication()) {
      var expanded = ExecutionContext.copyOf(contexts);
      expanded.add(ExecutionContext.THICK_CLIENT_ORDINARY);
      expanded.add(ExecutionContext.THICK_CLIENT_ORDINARY_FILE_CLIENT);
      return Set.copyOf(expanded);
    }
    return contexts;
  }

  private static Set<ExecutionContext> managedContexts() {
    var contexts = ExecutionContext.copyOf(ExecutionContext.MANAGED_CLIENTS);
    contexts.addAll(ExecutionContext.SERVERS);
    contexts.addAll(Set.of(ExecutionContext.THICK_CLIENT_MANAGED_FILE_CLIENT,
      ExecutionContext.THICK_CLIENT_MANAGED_FILE_SERVER, ExecutionContext.THICK_CLIENT_ORDINARY_FILE_SERVER));
    return Set.copyOf(contexts);
  }

  private Set<ExecutionContext> platformContexts(DocumentContext document) {
    var names = new ArrayList<String>();
    document.getMdObject().ifPresent(md ->
      ConfigurationModuleMembersProvider.selfTypeQualifiedName(document.getModuleType(), md).ifPresent((String qualifiedName) -> {
        var name = switch (md.getMdoType()) {
          case EXTERNAL_DATA_SOURCE_TABLE, EXTERNAL_DATA_SOURCE_CUBE -> "ВнешнийИсточникДанных" + qualifiedName;
          default -> qualifiedName;
        };
        names.add(name);
        var dot = name.indexOf('.');
        if (dot > 0) {
          names.add(name.substring(0, dot));
        }
      }));
    var family = ModulePlatformTypeNames.familyName(document.getModuleType());
    if (family != null) {
      names.add(family);
    }
    for (var name : names) {
      var types = new ArrayList<>(typeRegistry.findAllGenericsByFamilyCore(name));
      typeRegistry.resolve(name).ifPresent(types::add);
      var contexts = EnumSet.noneOf(ExecutionContext.class);
      for (var type : types) {
        contexts.addAll(fromAvailability(typeRegistry.getTypeMetadata(type, FileType.BSL).availabilities()));
      }
      if (!contexts.isEmpty()) {
        return Set.copyOf(contexts);
      }
    }
    return Set.of();
  }

  private static Set<ExecutionContext> withFileContexts(Set<ExecutionContext> original,
                                                       ExecutionContext... fileContexts) {
    var contexts = ExecutionContext.copyOf(original);
    Collections.addAll(contexts, fileContexts);
    return Set.copyOf(contexts);
  }

  /** Перевод доступности из СП в контексты компиляции. */
  private static Set<ExecutionContext> fromAvailability(Collection<Availability> availabilities) {
    var result = EnumSet.noneOf(ExecutionContext.class);
    for (var availability : availabilities) {
      addAvailability(result, availability);
    }
    return Set.copyOf(result);
  }

  private static void addAvailability(Set<ExecutionContext> result, Availability availability) {
    switch (availability) {
      case SERVER -> result.add(ExecutionContext.SERVER);
      case THIN_CLIENT -> result.add(ExecutionContext.THIN_CLIENT);
      case WEB_CLIENT -> result.add(ExecutionContext.WEB_CLIENT);
      case MOBILE_CLIENT -> result.add(ExecutionContext.MOBILE_CLIENT);
      case THICK_CLIENT -> addThickClientContexts(result);
      case EXTERNAL_CONNECTION -> result.addAll(ExecutionContext.EXTERNAL_PREPROCESSOR);
      case MOBILE_APPLICATION_CLIENT -> result.add(ExecutionContext.MOBILE_APPLICATION_CLIENT);
      case MOBILE_APPLICATION_SERVER -> result.add(ExecutionContext.MOBILE_APPLICATION_SERVER);
      case MOBILE_STANDALONE_SERVER -> result.add(ExecutionContext.MOBILE_STANDALONE_SERVER);
    }
  }

  private static void addThickClientContexts(Set<ExecutionContext> result) {
    result.add(ExecutionContext.THICK_CLIENT_MANAGED);
    result.add(ExecutionContext.THICK_CLIENT_ORDINARY);
    result.add(ExecutionContext.THICK_CLIENT_MANAGED_FILE);
    result.add(ExecutionContext.THICK_CLIENT_ORDINARY_FILE);
  }
}
