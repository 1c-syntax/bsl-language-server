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
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.variable.VariableKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.bsl.languageserver.types.oscript.autumn.AutumnComponentInferencer;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.ExtendsAnnotations;
import com.github._1c_syntax.bsl.languageserver.types.oscript.extends_.OScriptExtends;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Типы, которые переменной задают фреймворки OneScript: внедрение «ОСени» и наследование
 * библиотеки extends.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OScriptFrameworkTypeResolverTest {

  private static final String PARENT_CLASS = "БазовыйКласс";
  private static final TypeRef PARENT_REF = new TypeRef(TypeKind.USER, PARENT_CLASS);

  @Mock
  private TypeRegistry typeRegistry;

  @Mock
  private AutumnComponentInferencer autumnComponentInferencer;

  @Mock
  private OScriptExtends oScriptExtends;

  @Mock
  private DocumentContext documentContext;

  @Mock
  private VariableSymbol variable;

  @InjectMocks
  private OScriptFrameworkTypeResolver frameworkTypes;

  @Test
  void injectedTypeIsTakenForModuleField() {
    // given
    var injected = TypeSet.of(new TypeRef(TypeKind.USER, "Логгер"));
    givenVariable(VariableKind.MODULE, FileType.OS);
    when(autumnComponentInferencer.inferInjectedType(any(), eq("Лог"), eq(FileType.OS)))
      .thenReturn(injected);

    // when
    var types = frameworkTypes.injectedType(variable);

    // then
    assertThat(types).isEqualTo(injected);
  }

  @Test
  void injectedTypeIsNotTakenForLocalVariable() {
    // given: внедрять можно поле модуля или параметр, но не локальную переменную.
    givenVariable(VariableKind.LOCAL, FileType.OS);

    // when
    var types = frameworkTypes.injectedType(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void parentHolderTypeIsParentClass() {
    // given
    givenVariable(VariableKind.MODULE, FileType.OS);
    givenParentClass();
    when(oScriptExtends.isParentHolder(variable)).thenReturn(true);

    // when
    var types = frameworkTypes.parentHolderType(variable);

    // then
    assertThat(types.refs()).containsExactly(PARENT_REF);
  }

  @Test
  void parentHolderTypeIsEmptyForVariableThatIsNotHolder() {
    // given
    givenVariable(VariableKind.MODULE, FileType.OS);
    when(oScriptExtends.isParentHolder(variable)).thenReturn(false);

    // when
    var types = frameworkTypes.parentHolderType(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void parentHolderTypeIsEmptyInBslFile() {
    // given: наследование библиотеки extends есть только в OneScript.
    givenVariable(VariableKind.MODULE, FileType.BSL);
    when(oScriptExtends.isParentHolder(variable)).thenReturn(true);

    // when
    var types = frameworkTypes.parentHolderType(variable);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void implicitParentFieldIsTypedAsParentClass() {
    // given: поля _ОбъектРодитель в исходниках наследника нет — его создаёт фреймворк.
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    givenParentClass();

    // when
    var types = frameworkTypes.implicitParentFieldType(
      ExtendsAnnotations.IMPLICIT_PARENT_FIELD, documentContext);

    // then
    assertThat(types.refs()).containsExactly(PARENT_REF);
  }

  @Test
  void otherNameIsNotTypedAsParentClass() {
    // given
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    givenParentClass();

    // when
    var types = frameworkTypes.implicitParentFieldType("ПростоПеременная", documentContext);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  @Test
  void implicitParentFieldIsEmptyWhenInheritanceIsNotDeclared() {
    // given
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    when(oScriptExtends.parentClassName(documentContext)).thenReturn(Optional.empty());

    // when
    var types = frameworkTypes.implicitParentFieldType(
      ExtendsAnnotations.IMPLICIT_PARENT_FIELD, documentContext);

    // then
    assertThat(types).isEqualTo(TypeSet.EMPTY);
  }

  private void givenVariable(VariableKind kind, FileType fileType) {
    when(variable.getKind()).thenReturn(kind);
    when(variable.getName()).thenReturn("Лог");
    when(variable.getAnnotations()).thenReturn(List.of());
    when(variable.getOwner()).thenReturn(documentContext);
    when(documentContext.getFileType()).thenReturn(fileType);
  }

  private void givenParentClass() {
    when(oScriptExtends.parentClassName(any())).thenReturn(Optional.of(PARENT_CLASS));
    when(typeRegistry.resolve(PARENT_CLASS, FileType.OS)).thenReturn(Optional.of(PARENT_REF));
  }
}
