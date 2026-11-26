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

import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.mdo.HTTPService;
import com.github._1c_syntax.bsl.mdo.IntegrationService;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.children.HTTPServiceMethod;
import com.github._1c_syntax.bsl.mdo.children.IntegrationServiceChannel;
import com.github._1c_syntax.bsl.mdo.children.WebServiceOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Развёртывает generic-события платформенных типов модулей сервисов в
 * конкретные имена обработчиков, объявленных в конфигурации:
 * <ul>
 *   <li>{@code Модуль HTTP-сервиса.<Имя обработчика>} ← {@code HTTPService.urlTemplates.methods.handler}
 *       с сигнатурой {@code (Запрос: HTTPСервисЗапрос)};</li>
 *   <li>{@code Модуль Web-сервиса.<Имя обработчика>} ← {@code WebService.operations.procedureName}
 *       с параметрами {@code WebServiceOperation.parameters};</li>
 *   <li>{@code Модуль сервиса интеграции.<Имя обработчика полученного сообщения>}
 *       ← {@code IntegrationService.integrationServiceChannels.receiveMessageProcessing}.</li>
 * </ul>
 * Generic-события прилетают из HBK с placeholder'ом в имени; здесь они
 * материализуются именами из mdclasses + кастомные сигнатуры подменяются на
 * реальные параметры из XML.
 */
@Component
@WorkspaceScope
@RequiredArgsConstructor
public class ServiceModuleEventRegistrar {

  private final TypeRegistry typeRegistry;

  /**
   * Развернуть generic-события сервисных модулей для всех MD-объектов конфигурации.
   * <p>
   * У модуля сервиса есть владелец — сам сервис, и обработчики каждого объявлены только в
   * нём. Синтакс-помощник этого не выражает: у него один тип на все сервисы вида
   * ({@code Модуль Web-сервиса}) с единственным членом-шаблоном {@code <Имя обработчика>},
   * тогда как у справочников параметризовано само имя типа
   * ({@code СправочникМенеджер.<Имя справочника>}). Поэтому специализация по владельцу
   * заводится здесь: на каждый сервис — свой тип {@code <тип модуля>.<Имя сервиса>} с
   * операциями только этого сервиса. Иначе одноимённые операции разных сервисов (а в
   * типовых конфигурациях у версий одного сервиса они называются одинаково) складывались
   * бы в один член, и чей контракт уцелеет, решал бы порядок обхода метаданных.
   *
   * @param children объекты метаданных конфигурации.
   */
  public void register(Iterable<MD> children) {
    for (var md : children) {
      registerServiceHandlerEvents("Модуль HTTP-сервиса", "Имя обработчика",
        md, collectHttpHandlers(md));
      registerServiceHandlerEvents("Модуль Web-сервиса", "Имя обработчика",
        md, collectWebProcedures(md));
      registerServiceHandlerEvents("Модуль сервиса интеграции",
        "Имя обработчика полученного сообщения", md, collectIntegrationHandlers(md));
    }
  }

  private List<HandlerSpec> collectHttpHandlers(MD md) {
    if (!(md instanceof HTTPService http)) {
      return List.of();
    }
    var sink = new ArrayList<HandlerSpec>();
    http.getUrlTemplates().forEach(tpl -> tpl.getMethods().forEach((HTTPServiceMethod m) -> {
      if (!m.getHandler().isBlank()) {
        sink.add(new HandlerSpec(m.getHandler(), httpServiceMethodSignature()));
      }
    }));
    return sink;
  }

  private static List<HandlerSpec> collectWebProcedures(MD md) {
    if (!(md instanceof WebService web)) {
      return List.of();
    }
    var sink = new ArrayList<HandlerSpec>();
    web.getOperations().forEach((WebServiceOperation op) -> {
      if (!op.getProcedureName().isBlank()) {
        sink.add(new HandlerSpec(op.getProcedureName(), webOperationSignature(op)));
      }
    });
    return sink;
  }

  private static List<HandlerSpec> collectIntegrationHandlers(MD md) {
    if (!(md instanceof IntegrationService isvc)) {
      return List.of();
    }
    var sink = new ArrayList<HandlerSpec>();
    isvc.getIntegrationServiceChannels().forEach((IntegrationServiceChannel ch) -> {
      if (!ch.getReceiveMessageProcessing().isBlank()) {
        sink.add(new HandlerSpec(ch.getReceiveMessageProcessing(),
          integrationChannelSignature()));
      }
    });
    return sink;
  }

  /**
   * Материализует generic-event типа модуля по placeholder'у и подменяет signatures
   * на сигнатуры с реальными параметрами обработчиков этого сервиса из mdclasses.
   * <p>
   * Описание/двуязычие/sinceVersion event'а наследуются от HBK-шаблона: общий
   * для всего семейства источник правды.
   * <p>
   * Члены вешаются не на общий тип вида, а на тип этого сервиса
   * ({@code Модуль Web-сервиса.Обмен}), зарегистрированный специализацией от общего.
   *
   * @param typeQualifiedName имя платформенного типа модуля этого вида сервиса.
   * @param placeholder       имя параметра generic-члена в шаблоне HBK.
   * @param service           объект метаданных сервиса — владелец модуля.
   * @param specs             обработчики этого сервиса.
   */
  private void registerServiceHandlerEvents(String typeQualifiedName, String placeholder,
                                            MD service, List<HandlerSpec> specs) {
    if (specs.isEmpty()) {
      return;
    }
    var genericRef = typeRegistry.resolve(typeQualifiedName).orElse(null);
    if (genericRef == null || service.getName().isBlank()) {
      return;
    }
    var names = specs.stream().map(HandlerSpec::name).distinct().toList();
    var templates = typeRegistry.expandedMembers(genericRef, Map.of(),
      Map.of(placeholder, names), FileType.BSL);
    if (templates.isEmpty()) {
      return;
    }
    // Имена обработчиков в пределах одного сервиса уникальны: у операции ровно одна
    // процедура, а двух операций с одним именем в сервисе не бывает.
    var sigByName = specs.stream()
      .collect(Collectors.toMap(HandlerSpec::name, HandlerSpec::signature, (a, b) -> a));
    var withSignatures = templates.stream()
      .map((MemberDescriptor m) -> {
        var sig = sigByName.get(m.name());
        return sig == null ? m : m.withSignatures(List.of(sig));
      })
      .toList();
    var serviceRef = typeRegistry.registerSpecialization(
      typeQualifiedName + "." + service.getName(), genericRef, Map.of(), FileType.BSL);
    typeRegistry.registerMemberSource(serviceRef, () -> withSignatures, FileType.BSL);
  }

  /** Сигнатура HTTP-обработчика: один параметр {@code Запрос} типа {@code HTTPСервисЗапрос}. */
  private SignatureDescriptor httpServiceMethodSignature() {
    var requestRef = typeRegistry.resolve("HTTPСервисЗапрос").orElse(TypeRef.UNKNOWN);
    var param = new ParameterDescriptor(
      BilingualString.of("Запрос", "Request"),
      TypeSet.of(requestRef), false, BilingualString.EMPTY, "");
    return new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");
  }

  /**
   * Сигнатура обработчика операции Web-сервиса: имена параметров из XML.
   * Типы пока не сопоставляем — XSD-тип параметра ({@code {ns}type}) требует
   * отдельной мапы в BSL-типы; до её появления оставляем пусто.
   */
  private static SignatureDescriptor webOperationSignature(WebServiceOperation op) {
    var params = op.getParameters().stream()
      .map(p -> new ParameterDescriptor(
        BilingualString.of(p.getName(), p.getName()),
        TypeSet.EMPTY, false, BilingualString.EMPTY, ""))
      .toList();
    return new SignatureDescriptor(params, TypeSet.EMPTY, "");
  }

  /** Сигнатура обработчика канала: один параметр {@code Сообщение}. */
  private static SignatureDescriptor integrationChannelSignature() {
    var param = new ParameterDescriptor(
      BilingualString.of("Сообщение", "Message"),
      TypeSet.EMPTY, false, BilingualString.EMPTY, "");
    return new SignatureDescriptor(List.of(param), TypeSet.EMPTY, "");
  }

  /** Пара «имя обработчика → его сигнатура» для регистрации event'ов на типе модуля. */
  private record HandlerSpec(String name, SignatureDescriptor signature) {
  }
}
