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
package com.github._1c_syntax.bsl.languageserver.types.oscript;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты централизованных обходов отношений наследования: разбор аннотаций
 * замокан через {@link OScriptExtends}, проверяются сами алгоритмы (скан
 * наследников, транзитивная реализация интерфейсов, защита от циклов).
 */
class TypeRelationIndexTest {

  private final OScriptExtends oScriptExtends = mock(OScriptExtends.class);
  private final TypeRelationIndex index = new TypeRelationIndex(oScriptExtends);

  private static DocumentContext osDocument(String uri) {
    var documentContext = mock(DocumentContext.class);
    when(documentContext.getUri()).thenReturn(URI.create(uri));
    when(documentContext.getFileType()).thenReturn(FileType.OS);
    return documentContext;
  }

  @Test
  void isInterfaceAndSupertypeNameDelegateToAnnotations() {
    var doc = osDocument("file:///Класс.os");
    when(oScriptExtends.isInterface(doc)).thenReturn(true);
    when(oScriptExtends.parentClassName(doc)).thenReturn(Optional.of("Родитель"));

    assertThat(index.isInterface(doc)).isTrue();
    assertThat(index.supertypeName(doc)).contains("Родитель");
  }

  @Test
  void supertypeResolvesParentNameThroughProvidedFunction() {
    var child = osDocument("file:///Кошка.os");
    var parent = osDocument("file:///Млекопитающее.os");
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("Млекопитающее"));

    var resolved = index.supertype(child,
      name -> "Млекопитающее".equals(name) ? Optional.of(parent) : Optional.empty());

    assertThat(resolved).contains(parent);
  }

  @Test
  void subtypesCollectsOsDocumentsThatDeclareGivenParent() {
    var parent = osDocument("file:///Млекопитающее.os");
    var child = osDocument("file:///Кошка.os");
    var unrelated = osDocument("file:///Дерево.os");
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("Млекопитающее"));
    when(oScriptExtends.parentClassName(unrelated)).thenReturn(Optional.empty());

    var subtypes = index.subtypes(parent, List.of(parent, child, unrelated),
      doc -> doc == parent ? List.of("Млекопитающее") : List.of());

    assertThat(subtypes).containsExactly(child);
  }

  @Test
  void implementsAnyMatchesDirectInterface() {
    var candidate = osDocument("file:///Реализация.os");
    when(oScriptExtends.implementedInterfaceNames(candidate)).thenReturn(List.of("МойИнтерфейс"));
    when(oScriptExtends.parentClassName(candidate)).thenReturn(Optional.empty());

    assertThat(index.implementsAny(candidate, Set.of("мойинтерфейс"), name -> Optional.empty())).isTrue();
  }

  @Test
  void implementsAnyMatchesInterfaceDeclaredOnAbstractParent() {
    var child = osDocument("file:///Наследник.os");
    var parent = osDocument("file:///АбстрактныйКласс.os");
    when(oScriptExtends.implementedInterfaceNames(child)).thenReturn(List.of());
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("АбстрактныйКласс"));
    when(oScriptExtends.implementedInterfaceNames(parent)).thenReturn(List.of("МойИнтерфейс"));
    when(oScriptExtends.parentClassName(parent)).thenReturn(Optional.empty());

    var result = index.implementsAny(child, Set.of("мойинтерфейс"),
      name -> "АбстрактныйКласс".equals(name) ? Optional.of(parent) : Optional.empty());

    assertThat(result).isTrue();
  }

  @Test
  void implementsAnyMatchesBaseInterfaceThroughInterfaceHierarchy() {
    var candidate = osDocument("file:///РеализацияПроизводного.os");
    var derived = osDocument("file:///ПроизводныйИнтерфейс.os");
    // Кандидат реализует производный интерфейс, который &Расширяет базовый.
    when(oScriptExtends.implementedInterfaceNames(candidate)).thenReturn(List.of("ПроизводныйИнтерфейс"));
    when(oScriptExtends.parentClassName(candidate)).thenReturn(Optional.empty());
    when(oScriptExtends.parentClassName(derived)).thenReturn(Optional.of("БазовыйИнтерфейс"));

    var result = index.implementsAny(candidate, Set.of("базовыйинтерфейс"),
      name -> "ПроизводныйИнтерфейс".equals(name) ? Optional.of(derived) : Optional.empty());

    assertThat(result).isTrue();
  }

  @Test
  void implementsAnyTerminatesOnInterfaceHierarchyCycleWithoutMatch() {
    var candidate = osDocument("file:///Реализация.os");
    var ifaceA = osDocument("file:///ИнтерфейсА.os");
    var ifaceB = osDocument("file:///ИнтерфейсБ.os");
    when(oScriptExtends.implementedInterfaceNames(candidate)).thenReturn(List.of("ИнтерфейсА"));
    when(oScriptExtends.parentClassName(candidate)).thenReturn(Optional.empty());
    // Цикл в иерархии интерфейсов: А → Б → А.
    when(oScriptExtends.parentClassName(ifaceA)).thenReturn(Optional.of("ИнтерфейсБ"));
    when(oScriptExtends.parentClassName(ifaceB)).thenReturn(Optional.of("ИнтерфейсА"));

    var result = index.implementsAny(candidate, Set.of("искомый"),
      name -> switch (name) {
        case "ИнтерфейсА" -> Optional.of(ifaceA);
        case "ИнтерфейсБ" -> Optional.of(ifaceB);
        default -> Optional.empty();
      });

    assertThat(result).isFalse();
  }

  @Test
  void implementsAnyTerminatesOnExtendsCycleWithoutMatch() {
    var a = osDocument("file:///A.os");
    var b = osDocument("file:///B.os");
    when(oScriptExtends.implementedInterfaceNames(a)).thenReturn(List.of());
    when(oScriptExtends.implementedInterfaceNames(b)).thenReturn(List.of());
    when(oScriptExtends.parentClassName(a)).thenReturn(Optional.of("B"));
    when(oScriptExtends.parentClassName(b)).thenReturn(Optional.of("A"));

    var result = index.implementsAny(a, Set.of("мойинтерфейс"),
      name -> switch (name) {
        case "A" -> Optional.of(a);
        case "B" -> Optional.of(b);
        default -> Optional.empty();
      });

    assertThat(result).isFalse();
  }

  @Test
  void inheritedMembersReturnsParentMembers() {
    var child = osDocument("file:///Кошка.os");
    var childRef = new TypeRef(TypeKind.USER, "Кошка");
    var parentRef = new TypeRef(TypeKind.USER, "Млекопитающее");
    var parentMember = MemberDescriptor.property("БазовоеСвойство");
    when(oScriptExtends.parentClassName(child)).thenReturn(Optional.of("Млекопитающее"));

    var members = index.inheritedMembers(child, childRef,
      name -> Optional.of(parentRef),
      ref -> ref.equals(parentRef) ? List.of(parentMember) : List.of());

    assertThat(members).containsExactly(parentMember);
  }

  @Test
  void inheritedMembersEmptyWhenNoParentDeclared() {
    var doc = osDocument("file:///Корень.os");
    var ref = new TypeRef(TypeKind.USER, "Корень");
    when(oScriptExtends.parentClassName(doc)).thenReturn(Optional.empty());

    assertThat(index.inheritedMembers(doc, ref, name -> Optional.empty(), r -> List.of())).isEmpty();
  }

  @Test
  void inheritedMembersBreaksRecursiveCycle() {
    var doc = osDocument("file:///Сам.os");
    var ownRef = new TypeRef(TypeKind.USER, "Сам");
    var parentRef = new TypeRef(TypeKind.USER, "Родитель");
    when(oScriptExtends.parentClassName(doc)).thenReturn(Optional.of("Родитель"));

    // membersOf повторно входит в inheritedMembers для того же ownRef — guard
    // должен оборвать рекурсию и вернуть пустой набор на повторном входе.
    var members = index.inheritedMembers(doc, ownRef,
      name -> Optional.of(parentRef),
      ref -> index.inheritedMembers(doc, ownRef, n -> Optional.of(parentRef), r -> List.of()));

    assertThat(members).isEmpty();
  }
}
