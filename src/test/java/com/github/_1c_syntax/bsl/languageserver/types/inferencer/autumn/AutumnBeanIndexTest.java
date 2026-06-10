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
package com.github._1c_syntax.bsl.languageserver.types.inferencer.autumn;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContextProvider;
import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.ConstructorSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTree;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.Annotation;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationKind;
import com.github._1c_syntax.bsl.languageserver.context.symbol.annotations.AnnotationParameterDefinition;
import com.github._1c_syntax.bsl.languageserver.references.model.AnnotationRepository;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.EntryKind;
import com.github._1c_syntax.bsl.languageserver.types.oscript.OScriptLibraryIndex.LibraryEntry;
import com.github._1c_syntax.bsl.languageserver.types.registry.TypeRegistry;
import com.github._1c_syntax.utils.Absolute;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutumnBeanIndexTest {

  @Mock
  private OScriptLibraryIndex libraryIndex;

  @Mock
  private ServerContextProvider serverContextProvider;

  @Mock
  private TypeRegistry typeRegistry;

  private final List<LibraryEntry> entries = new ArrayList<>();

  private AutumnBeanIndex beanIndex;

  private void init() {
    when(libraryIndex.findEntries(EntryKind.CLASS)).thenReturn(entries);
    var metaResolver = new AutumnMetaAnnotationResolver(new AnnotationRepository());
    beanIndex = new AutumnBeanIndex(libraryIndex, serverContextProvider, typeRegistry, metaResolver);
  }

  @Test
  void returnsEmptyForBlankNames() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolve("").isEmpty()).isTrue();
    assertThat(beanIndex.resolve("   ").isEmpty()).isTrue();
  }

  @Test
  void returnsEmptyForUnknownBean() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolve("НетТакого").isEmpty()).isTrue();
  }

  @Test
  void skipsEntryWithoutServerContext() {
    // given
    var uri = registerEntry("Безконтекста");
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.empty());
    init();

    // when / then
    assertThat(beanIndex.resolve("Безконтекста").isEmpty()).isTrue();
  }

  @Test
  void skipsEntryWithoutDocument() {
    // given
    var uri = registerEntry("БезДокумента");
    var serverContext = mock(ServerContext.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(null);
    init();

    // when / then
    assertThat(beanIndex.resolve("БезДокумента").isEmpty()).isTrue();
  }

  @Test
  void skipsComponentWhenTypeUnresolved() {
    // given
    var type = new TypeRef(TypeKind.USER, "Компонент");
    // тип НЕ зарегистрирован в реестре -> ownerType == null
    registerClass("Компонент", null, method(component(null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Компонент").isEmpty()).isTrue();
    assertThat(type).isNotNull();
  }

  @Test
  void registersComponentByDefaultName() {
    // given
    var type = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", type, method(component(null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);
  }

  @Test
  void skipsAnnotationDefinitionClass() {
    // given: класс-определение аннотации — &Аннотация("Компонент") &Желудь
    var type = new TypeRef(TypeKind.USER, "АннотацияКомпонент");
    registerClass("АннотацияКомпонент", type, method(marker("Компонент"), component(null)));
    init();

    // when / then: класс-определение аннотации желудём не является
    assertThat(beanIndex.resolve("АннотацияКомпонент").isEmpty()).isTrue();
    assertThat(beanIndex.resolve("Компонент").isEmpty()).isTrue();
  }

  @Test
  void registersComponentByExplicitName() {
    // given
    var type = new TypeRef(TypeKind.USER, "РеальныйКласс");
    registerClass("РеальныйКласс", type, method(component("ЛогическоеИмя")));
    init();

    // when / then
    assertThat(beanIndex.resolve("ЛогическоеИмя").refs()).containsExactly(type);
  }

  @Test
  void registersQualifierAlias() {
    // given
    var type = new TypeRef(TypeKind.USER, "Василий");
    registerClass("Василий", type, method(component(null), qualifier("Васян")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Васян").refs()).containsExactly(type);
  }

  @Test
  void prefersPrimaryOnConflict() {
    // given
    var sid = new TypeRef(TypeKind.USER, "СидВишес");
    var johnny = new TypeRef(TypeKind.USER, "ДжонниРоттен");
    registerClass("ДжонниРоттен", johnny, method(component(null), qualifier("Панк")));
    registerClass("СидВишес", sid, method(component(null), primary(), qualifier("Панк")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Панк").refs()).containsExactly(sid);
  }

  @Test
  void unionsCandidatesWithoutPrimary() {
    // given
    var first = new TypeRef(TypeKind.USER, "Первый");
    var second = new TypeRef(TypeKind.USER, "Второй");
    registerClass("Первый", first, method(component(null), qualifier("Общий")));
    registerClass("Второй", second, method(component(null), qualifier("Общий")));
    init();

    // when / then
    assertThat(beanIndex.resolve("Общий").refs()).containsExactlyInAnyOrder(first, second);
  }

  @Test
  void registersFactoryByMethodNameWhenNameAndTypeAbsent() {
    // given: &Завязь без параметров -> имя желудя и тип берутся из имени метода (как autumn)
    var type = new TypeRef(TypeKind.USER, "СоединениеСБазой");
    when(typeRegistry.resolve("СоединениеСБазой")).thenReturn(Optional.of(type));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СоединениеСБазой", factory(null, null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("СоединениеСБазой").refs()).containsExactly(type);
  }

  @Test
  void registersFactoryWithExplicitCollectionType() {
    // given
    var array = new TypeRef(TypeKind.PLATFORM, "Массив");
    when(typeRegistry.resolve("Массив")).thenReturn(Optional.of(array));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СписокЖелудей", factory("СписокЖелудей", "Массив")));
    init();

    // when / then
    assertThat(beanIndex.resolve("СписокЖелудей").refs()).containsExactly(array);
  }

  @Test
  void factoryMethodBeansForUriReturnsMethodsOfFile() {
    // given: метод &Завязь "СоздатьСписок" объявляет желудь "СписокЖелудей"
    when(typeRegistry.resolve("Массив")).thenReturn(Optional.of(new TypeRef(TypeKind.PLATFORM, "Массив")));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СоздатьСписок", factory("СписокЖелудей", "Массив")));
    init();

    // when
    var factoryBeans = beanIndex.factoryMethodBeansForUri(Absolute.uri("file:///beans/Фабрика.os"));

    // then: метод &Завязь и имя (lowercase) производимого им желудя
    assertThat(factoryBeans).singleElement().satisfies(factoryMethod -> {
      assertThat(factoryMethod.factoryMethodName()).isEqualTo("СоздатьСписок");
      assertThat(factoryMethod.beanNames()).containsExactly("списокжелудей");
    });
  }

  @Test
  void factoryMethodBeansForUriEmptyForComponentOnlyFile() {
    // given: класс-компонент без фабричных методов &Завязь
    registerClass("Логгер", new TypeRef(TypeKind.USER, "Логгер"), method(component(null)));
    init();

    // when / then
    assertThat(beanIndex.factoryMethodBeansForUri(Absolute.uri("file:///beans/Логгер.os"))).isEmpty();
  }

  @Test
  void componentBeanNamesForUriReturnsComponentNamesWithQualifier() {
    // given: компонент с прозвищем — оба имени принадлежат конструктору-производителю
    registerClass("Логгер", new TypeRef(TypeKind.USER, "Логгер"),
      method(component("Лог"), qualifier("ОсновнойЛог")));
    init();

    // when
    var names = beanIndex.componentBeanNamesForUri(Absolute.uri("file:///beans/Логгер.os"));

    // then
    assertThat(names).containsExactlyInAnyOrder("лог", "основнойлог");
  }

  @Test
  void componentBeanNamesForUriExcludesFactoryBeans() {
    // given: дуб с фабричным методом — имя фабричного желудя не входит в компонентные имена
    when(typeRegistry.resolve("Массив")).thenReturn(Optional.of(new TypeRef(TypeKind.PLATFORM, "Массив")));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СоздатьСписок", factory("СписокЖелудей", "Массив")));
    init();

    // when
    var names = beanIndex.componentBeanNamesForUri(Absolute.uri("file:///beans/Фабрика.os"));

    // then: компонентный желудь — сам дуб (по имени класса), фабричный "списокжелудей" исключён
    assertThat(names).containsExactly("фабрика");
  }

  @Test
  void skipsFactoryWhenTypeIsExplicitlyBlank() {
    // given: явный Тип="" — в autumn Тип("") это ошибка, валидного типа нет. Резолв имени
    // метода застаблен в тип: если бы код ошибочно фолбэчил пустой Тип на имя метода,
    // желудь бы зарегистрировался — тест это поймает.
    when(typeRegistry.resolve("СоздатьХлеб")).thenReturn(Optional.of(new TypeRef(TypeKind.USER, "Хлеб")));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СоздатьХлеб", factory("Хлеб", "")));
    init();

    // when / then: желудь не регистрируется (пустой Тип не резолвится в тип)
    assertThat(beanIndex.resolve("Хлеб").isEmpty()).isTrue();
  }

  @Test
  void skipsFactoryWhenTypeUnresolved() {
    // given
    when(typeRegistry.resolve("Неизвестный")).thenReturn(Optional.empty());
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("Неизвестный", factory(null, null)));
    init();

    // when / then
    assertThat(beanIndex.resolve("Неизвестный").isEmpty()).isTrue();
  }

  @Test
  void rebuildsContributionOfChangedDocument() {
    // given: фабрика регистрирует желудь "Старый"
    var uri = Absolute.uri("file:///beans/Фабрика.os");
    var entry = new LibraryEntry(uri, "Фабрика", EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    var beanType = new TypeRef(TypeKind.USER, "ТипЖелудя");
    when(typeRegistry.resolve("ТипЖелудя")).thenReturn(Optional.of(beanType));
    // класс-дуб: завязи сканируются только внутри него
    var constructor = method(oak());
    when(symbolTree.getConstructor()).thenReturn(Optional.of(constructor));
    var oldMethod = namedMethod("Создать", factory("Старый", "ТипЖелудя"));
    when(symbolTree.getMethods()).thenReturn(List.of(constructor, oldMethod));
    init();
    assertThat(beanIndex.resolve("Старый").refs()).containsExactly(beanType);

    // when: документ отредактирован — желудь переименован в "Новый"
    var newMethod = namedMethod("Создать", factory("Новый", "ТипЖелудя"));
    when(symbolTree.getMethods()).thenReturn(List.of(constructor, newMethod));
    when(document.getFileType()).thenReturn(FileType.OS);
    when(document.getUri()).thenReturn(uri);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    beanIndex.handleDocumentChange(event);

    // then: старое имя больше не резолвится, новое — резолвится
    assertThat(beanIndex.resolve("Старый").isEmpty()).isTrue();
    assertThat(beanIndex.resolve("Новый").refs()).containsExactly(beanType);
  }

  @Test
  void rebuildsEntireIndexWhenAnnotationDefinitionChanges() {
    // given: индекс построен, желудь Логгер зарегистрирован
    var oldType = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", oldType, method(component(null)));
    init();
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(oldType);

    // when: изменён .os-класс-определения аннотации (конструктор несёт &Аннотация).
    // Это может изменить роль аннотации в любом классе, поэтому ожидаем полный ребилд.
    var newType = new TypeRef(TypeKind.USER, "ЛоггерV2");
    when(typeRegistry.resolve("Логгер")).thenReturn(Optional.of(newType));
    var definitionDoc = mock(DocumentContext.class);
    var definitionTree = mock(SymbolTree.class);
    var definitionCtor = mock(ConstructorSymbol.class);
    when(definitionCtor.getAnnotations()).thenReturn(List.of(marker("Компонент")));
    when(definitionTree.getConstructor()).thenReturn(Optional.of(definitionCtor));
    when(definitionDoc.getFileType()).thenReturn(FileType.OS);
    when(definitionDoc.getSymbolTree()).thenReturn(definitionTree);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(definitionDoc);
    beanIndex.handleDocumentChange(event);

    // then: индекс пересобран целиком — Логгер резолвится в новый тип
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(newType);
  }

  @Test
  void ignoresBslDocumentChange() {
    // given
    var type = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", type, method(component(null)));
    init();
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);

    // when: изменён .bsl-документ — к желудям отношения не имеет
    var document = mock(DocumentContext.class);
    when(document.getFileType()).thenReturn(FileType.BSL);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    beanIndex.handleDocumentChange(event);

    // then: индекс не тронут
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);
  }

  @Test
  void ignoresChangeOfNonClassDocument() {
    // given: индекс построен по классу-желудю
    var type = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", type, method(component(null)));
    init();
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);

    // when: изменён .os-документ, который зарегистрирован только как МОДУЛЬ (не класс)
    var moduleUri = Absolute.uri("file:///beans/Модуль.os");
    when(libraryIndex.findEntriesByUri(moduleUri))
      .thenReturn(List.of(new LibraryEntry(moduleUri, "Модуль", EntryKind.MODULE, "lib", false)));
    var document = mock(DocumentContext.class);
    when(document.getFileType()).thenReturn(FileType.OS);
    when(document.getUri()).thenReturn(moduleUri);
    var symbolTree = mock(SymbolTree.class);
    when(symbolTree.getConstructor()).thenReturn(Optional.empty());
    when(document.getSymbolTree()).thenReturn(symbolTree);
    var event = mock(DocumentContextContentChangedEvent.class);
    when(event.getSource()).thenReturn(document);
    beanIndex.handleDocumentChange(event);

    // then: индекс не изменился (модульный документ желудей не даёт)
    assertThat(beanIndex.resolve("Логгер").refs()).containsExactly(type);
  }

  @Test
  void resolveDeclarationsReturnsComponentProducer() {
    // given
    var type = new TypeRef(TypeKind.USER, "Логгер");
    registerClass("Логгер", type, method(component(null)));
    init();

    // when
    var declarations = beanIndex.resolveDeclarations("Логгер");

    // then
    assertThat(declarations).singleElement().satisfies(declaration -> {
      assertThat(declaration.type()).isEqualTo(type);
      assertThat(declaration.isConstructor()).isTrue();
      assertThat(declaration.producerMethodName()).isEqualTo("ПриСозданииОбъекта");
      assertThat(declaration.primary()).isFalse();
      assertThat(declaration.sourceUri()).isEqualTo(Absolute.uri("file:///beans/Логгер.os"));
    });
  }

  @Test
  void resolveDeclarationsReturnsFactoryProducerWithMethodName() {
    // given
    var type = new TypeRef(TypeKind.USER, "СоединениеСБазой");
    when(typeRegistry.resolve("СоединениеСБазой")).thenReturn(Optional.of(type));
    registerClass("Фабрика", new TypeRef(TypeKind.USER, "Фабрика"),
      method(oak()), namedMethod("СоединениеСБазой", factory(null, null)));
    init();

    // when
    var declarations = beanIndex.resolveDeclarations("СоединениеСБазой");

    // then
    assertThat(declarations).singleElement().satisfies(declaration -> {
      assertThat(declaration.type()).isEqualTo(type);
      assertThat(declaration.isConstructor()).isFalse();
      assertThat(declaration.producerMethodName()).isEqualTo("СоединениеСБазой");
      assertThat(declaration.sourceUri()).isEqualTo(Absolute.uri("file:///beans/Фабрика.os"));
    });
  }

  @Test
  void resolveDeclarationsPrefersPrimaryOnConflict() {
    // given
    var sid = new TypeRef(TypeKind.USER, "СидВишес");
    var johnny = new TypeRef(TypeKind.USER, "ДжонниРоттен");
    registerClass("ДжонниРоттен", johnny, method(component(null), qualifier("Панк")));
    registerClass("СидВишес", sid, method(component(null), primary(), qualifier("Панк")));
    init();

    // when
    var declarations = beanIndex.resolveDeclarations("Панк");

    // then
    assertThat(declarations).singleElement().satisfies(declaration -> {
      assertThat(declaration.type()).isEqualTo(sid);
      assertThat(declaration.primary()).isTrue();
    });
  }

  @Test
  void resolveAllDeclarationsReturnsAllCandidatesWithoutPrimaryFilter() {
    // given: два желудя под общим прозвищем «Панк», один помечен &Верховный
    var sid = new TypeRef(TypeKind.USER, "СидВишес");
    var johnny = new TypeRef(TypeKind.USER, "ДжонниРоттен");
    registerClass("ДжонниРоттен", johnny, method(component(null), qualifier("Панк")));
    registerClass("СидВишес", sid, method(component(null), primary(), qualifier("Панк")));
    init();

    // when: для членов коллекции нужны ВСЕ кандидаты, а не только приоритетный
    var declarations = beanIndex.resolveAllDeclarations("Панк");

    // then
    assertThat(declarations).extracting(AutumnBeanIndex.BeanDeclaration::type)
      .containsExactlyInAnyOrder(sid, johnny);
  }

  @Test
  void resolveAllDeclarationsReturnsEmptyForUnknownOrBlankName() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolveAllDeclarations("НетТакого")).isEmpty();
    assertThat(beanIndex.resolveAllDeclarations("")).isEmpty();
  }

  @Test
  void resolveDeclarationsReturnsEmptyForUnknownOrBlankName() {
    // given
    init();

    // when / then
    assertThat(beanIndex.resolveDeclarations("НетТакого")).isEmpty();
    assertThat(beanIndex.resolveDeclarations("")).isEmpty();
  }

  // --- helpers ---------------------------------------------------------------

  private URI registerEntry(String qualifiedName) {
    var uri = Absolute.uri("file:///beans/" + qualifiedName + ".os");
    var entry = new LibraryEntry(uri, qualifiedName, EntryKind.CLASS, "lib", false);
    entries.add(entry);
    when(libraryIndex.findEntriesByUri(uri)).thenReturn(List.of(entry));
    return uri;
  }

  /**
   * Зарегистрировать .os-класс: аннотации компонента берутся с конструктора,
   * а методы (например, {@code &Завязь}-фабрики) сканируются дополнительно.
   */
  private void registerClass(String qualifiedName, TypeRef ownerType,
                             ConstructorSymbol constructor, MethodSymbol... methods) {
    var uri = registerEntry(qualifiedName);
    var serverContext = mock(ServerContext.class);
    var document = mock(DocumentContext.class);
    var symbolTree = mock(SymbolTree.class);
    when(serverContextProvider.getServerContext(uri)).thenReturn(Optional.of(serverContext));
    when(serverContext.getDocument(uri)).thenReturn(document);
    when(document.getSymbolTree()).thenReturn(symbolTree);
    when(symbolTree.getConstructor()).thenReturn(Optional.ofNullable(constructor));
    var allMethods = new ArrayList<MethodSymbol>();
    if (constructor != null) {
      allMethods.add(constructor);
    }
    allMethods.addAll(List.of(methods));
    when(symbolTree.getMethods()).thenReturn(allMethods);
    when(typeRegistry.resolve(qualifiedName)).thenReturn(Optional.ofNullable(ownerType));
  }

  /** Конструктор класса — носитель аннотаций компонента (&Желудь/&Дуб/&Прозвище/&Верховный). */
  private static ConstructorSymbol method(Annotation... annotations) {
    var constructor = mock(ConstructorSymbol.class);
    lenient().when(constructor.getAnnotations()).thenReturn(List.of(annotations));
    lenient().when(constructor.getName()).thenReturn("ПриСозданииОбъекта");
    return constructor;
  }

  private static MethodSymbol namedMethod(String name, Annotation... annotations) {
    var method = mock(MethodSymbol.class);
    lenient().when(method.getName()).thenReturn(name);
    lenient().when(method.getAnnotations()).thenReturn(List.of(annotations));
    return method;
  }

  private static Annotation component(String name) {
    return annotation(AutumnAnnotations.COMPONENT, name);
  }

  private static Annotation oak() {
    return annotation(AutumnAnnotations.OAK, null);
  }

  private static Annotation marker(String customName) {
    return annotation(AutumnAnnotations.ANNOTATION_MARKER, customName);
  }

  private static Annotation qualifier(String alias) {
    return annotation(AutumnAnnotations.QUALIFIER, alias);
  }

  private static Annotation primary() {
    return Annotation.builder().name(AutumnAnnotations.PRIMARY).kind(AnnotationKind.CUSTOM).build();
  }

  private static Annotation factory(String name, String type) {
    var parameters = new ArrayList<AnnotationParameterDefinition>();
    if (name != null) {
      parameters.add(new AnnotationParameterDefinition("Значение", Either.forLeft(name), true));
    }
    if (type != null) {
      parameters.add(new AnnotationParameterDefinition("Тип", Either.forLeft(type), true));
    }
    return Annotation.builder()
      .name(AutumnAnnotations.FACTORY)
      .kind(AnnotationKind.CUSTOM)
      .parameters(parameters)
      .build();
  }

  private static Annotation annotation(String annotationName, String value) {
    var builder = Annotation.builder().name(annotationName).kind(AnnotationKind.CUSTOM);
    if (value != null) {
      builder.parameters(List.of(new AnnotationParameterDefinition("", Either.forLeft(value), true)));
    }
    return builder.build();
  }
}
