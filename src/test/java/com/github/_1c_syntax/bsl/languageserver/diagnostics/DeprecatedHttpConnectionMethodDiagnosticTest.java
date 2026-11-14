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
package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticMessage;
import com.github._1c_syntax.bsl.languageserver.types.TypeService;
import com.github._1c_syntax.bsl.languageserver.types.compilation.CompilationContextResolver;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.util.TestUtils;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.Form;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.types.ModuleType;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.lsp4j.Diagnostic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.github._1c_syntax.bsl.languageserver.util.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class DeprecatedHttpConnectionMethodDiagnosticTest
  extends AbstractDiagnosticTest<DeprecatedHttpConnectionMethodDiagnostic> {

  private static final String HTTP_CONNECTION = "HTTPСоединение";

  private static final String CLIENT_METHOD_TEMPLATE = """
    Процедура Тест()
        Соединение = Новый HTTPСоединение("example.com");
        Соединение.%s(Запрос, Ответ);
    КонецПроцедуры
    """;

  @Autowired
  private CompilationContextResolver compilationContextResolver;

  DeprecatedHttpConnectionMethodDiagnosticTest() {
    super(DeprecatedHttpConnectionMethodDiagnostic.class);
  }

  @Test
  void reportsOnlyClientCodeInManagedForm() {
    var documentContext = formDocumentContext(getText());
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // Фикстура: тело модуля, &НаСервере, &НаСервереБезКонтекста,
    // &НаКлиентеНаСервере, &НаКлиентеНаСервереБезКонтекста и метод без директивы
    // не срабатывают; срабатывает только метод с &НаКлиенте.
    assertThat(diagnostics).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "ВызватьHTTPМетод", "CallHTTPMethod",
    "Записать", "Put",
    "Изменить", "Patch",
    "ОтправитьДляОбработки", "Post",
    "Получить", "Get",
    "ПолучитьЗаголовки", "Head",
    "Удалить", "Delete"
  })
  void reportsClientCallOnHttpConnection(String methodName) {
    var documentContext = formDocumentContext(
      "&НаКлиенте\n" + CLIENT_METHOD_TEMPLATE.formatted(methodName));
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
    var diagnostic = diagnostics.get(0);
    assertThat(DiagnosticMessage.getStringValue(diagnostic.getMessage())).contains(methodName);
    assertThat(diagnostic.getRange())
      .isEqualTo(Ranges.create(3, 15, 3, 15 + methodName.length()));
  }

  @Test
  void reportsNestedCallInArguments() {
    var documentContext = formDocumentContext(getText("DeprecatedHttpConnectionMethodNested"));
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // Обход не должен обрываться на внешнем вызове: вложенный Получить виден.
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void doesNotFireForOtherOwner() {
    var documentContext = formDocumentContext(getText("DeprecatedHttpConnectionMethodArray"));
    var diagnostics = diagnosticWithOwners("Массив").getDiagnostics(documentContext);

    // Массив.Получить/Удалить совпадают по имени, но владелец — не HTTPСоединение.
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void reportsWhenAnyOwnerIsHttpConnection() {
    var documentContext = formDocumentContext(
      "&НаКлиенте\n" + CLIENT_METHOD_TEMPLATE.formatted("Получить"));
    var diagnostics = diagnosticWithOwners("Массив", HTTP_CONNECTION).getDiagnostics(documentContext);

    // Получатель выведен как union: достаточно одного HTTPСоединения среди кандидатов.
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void doesNotFireForServerDirectives() {
    var documentContext = formDocumentContext(
      "&НаСервере\n" + CLIENT_METHOD_TEMPLATE.formatted("Получить"));
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void doesNotFireForMixedCompilerDirectives() {
    var documentContext = formDocumentContext(
      "&НаКлиентеНаСервере\n" + CLIENT_METHOD_TEMPLATE.formatted("Получить"));
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // Метод компилируется и для сервера, где асинхронного аналога нет.
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void doesNotFireForMethodWithoutDirectiveInManagedForm() {
    var documentContext = formDocumentContext(CLIENT_METHOD_TEMPLATE.formatted("Получить"));
    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // В управляемой форме метод без директивы исполняется на сервере.
    assertThat(diagnostics).isEmpty();
  }

  @Test
  void doesNotFireInServerModule() {
    var documentContext = spy(TestUtils.getDocumentContext(
      CLIENT_METHOD_TEMPLATE.formatted("Получить"), context));
    doReturn(ModuleType.ObjectModule).when(documentContext).getModuleType();

    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void reportsInOrdinaryFormWithoutDirectives() {
    var documentContext = formDocumentContext(getText("DeprecatedHttpConnectionMethodOrdinaryForm"));
    var form = mock(Form.class);
    when(form.getFormType()).thenReturn(FormType.ORDINARY);
    doReturn(Optional.of(form)).when(documentContext).getMdObject();

    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // В обычных формах директивы не применяются, весь модуль клиентский.
    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void reportsInClientOnlyCommonModule() {
    var documentContext = commonModuleDocumentContext(
      CommonModule.builder().clientManagedApplication(true).build());

    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    assertThat(diagnostics).hasSize(1);
  }

  @Test
  void doesNotFireInMixedCommonModule() {
    var documentContext = commonModuleDocumentContext(
      CommonModule.builder().clientManagedApplication(true).server(true).build());

    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    assertThat(diagnostics).isEmpty();
  }

  @Test
  void doesNotFireWithoutCommonModuleMetadata() {
    var documentContext = commonModuleDocumentContext(null);

    var diagnostics = diagnosticWithOwners(HTTP_CONNECTION).getDiagnostics(documentContext);

    // Контекст общего модуля не подтверждён — диагностика не срабатывает.
    assertThat(diagnostics).isEmpty();
  }

  private DeprecatedHttpConnectionMethodDiagnostic diagnosticWithOwners(String... qualifiedNames) {
    var typeService = mock(TypeService.class);
    List<TypeService.TypedMember> members = Arrays.stream(qualifiedNames)
      .map(qualifiedName -> new TypeService.TypedMember(
        new TypeRef(TypeKind.PLATFORM, qualifiedName),
        MemberDescriptor.method("Получить"),
        Ranges.create(0, 0, 8),
        0))
      .toList();
    when(typeService.membersAt(any(DocumentContext.class), any(TerminalNode.class)))
      .thenReturn(members);

    var diagnostic = new DeprecatedHttpConnectionMethodDiagnostic(typeService, compilationContextResolver);
    diagnostic.setInfo(diagnosticInstance.getInfo());
    return diagnostic;
  }

  private DocumentContext formDocumentContext(String text) {
    var documentContext = spy(TestUtils.getDocumentContext(text, context));
    doReturn(ModuleType.FormModule).when(documentContext).getModuleType();
    return documentContext;
  }

  private DocumentContext commonModuleDocumentContext(CommonModule mdObject) {
    var documentContext = spy(TestUtils.getDocumentContext(
      CLIENT_METHOD_TEMPLATE.formatted("Получить"), context));
    doReturn(ModuleType.CommonModule).when(documentContext).getModuleType();
    doReturn(Optional.ofNullable(mdObject)).when(documentContext).getMdObject();
    return documentContext;
  }
}
