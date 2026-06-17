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

import com.github._1c_syntax.bsl.context.platform.EnAttachments;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.languageserver.types.model.BilingualString;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.languageserver.infrastructure.WorkspaceScope;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.MemberKind;
import com.github._1c_syntax.bsl.languageserver.types.model.ParameterDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.PlatformMetadata;
import com.github._1c_syntax.bsl.languageserver.types.model.SignatureDescriptor;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeKind;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeRef;
import com.github._1c_syntax.bsl.languageserver.types.model.TypeSet;
import com.github._1c_syntax.utils.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.Set;

/**
 * Поставщик платформенных типов на основе синтакс-помощника установленной
 * платформы 1С (через библиотеку {@code bsl-context}). Источник
 * {@link ContextProvider} берётся из {@link BslContextHolder} — общий
 * workspace-scoped кэш парсинга HBK.
 * <p>
 * Соответствие категорий bsl-context → {@link TypeKind} type-system v2:
 * <ul>
 *   <li>{@code PRIMITIVE_TYPE} → {@link TypeKind#PRIMITIVE} — примитивы языка
 *       (Строка, Число, Дата, Булево, Тип, Null, Неопределено, Произвольный);</li>
 *   <li>{@code TYPE} → {@link TypeKind#PLATFORM} — платформенные типы со
 *       свойствами и методами;</li>
 *   <li>{@code ENUM} → {@link TypeKind#PLATFORM} — системные перечисления;
 *       значения публикуются как {@link MemberKind#PROPERTY} с типом самого
 *       перечисления (для dot-completion'а);</li>
 *   <li>{@code GLOBAL_CONTEXT} и {@code LANGUAGE_KEYWORD} — типами не являются,
 *       пропускаются. Глобальный контекст и языковые keyword'ы потребляет
 *       {@link GlobalScopeProvider}.</li>
 * </ul>
 * <p>
 * События ({@code ContextType.events()}) публикуются как члены типа c
 * {@link MemberKind#EVENT}; у событий нет возвращаемого значения
 * (handler-контракт), сигнатура хранит только параметры. Из метаданных
 * методов/свойств/конструкторов часть полей ({@code syntaxText},
 * {@code defaultValue} и т.п.) пока не носится LS-дескрипторами — они
 * доступны в bsl-context, см. {@code tmp/PLAN.md} в bsl-context.
 */
@Slf4j
@Component
@WorkspaceScope
public class BslContextPlatformTypesProvider implements PlatformTypesProvider {

  private final BslContextHolder contextHolder;
  private final Lazy<List<TypeDecl>> cached;

  public BslContextPlatformTypesProvider(BslContextHolder contextHolder) {
    this.contextHolder = contextHolder;
    this.cached = new Lazy<>(() -> build(contextHolder.get().orElse(null)));
  }

  @Override
  public Collection<TypeDecl> getTypes() {
    return cached.getOrCompute();
  }

  @Override
  public FileType getFileType() {
    return FileType.BSL;
  }

  private static List<TypeDecl> build(@Nullable ContextProvider provider) {
    if (provider == null) {
      return List.of();
    }
    var enLookup = enLookupOf(provider);
    var contexts = provider.getContexts();
    var result = new ArrayList<TypeDecl>(contexts.size());
    for (var context : contexts) {
      var decl = toTypeDecl(context, enLookup);
      if (decl != null) {
        result.add(decl);
      }
    }
    return List.copyOf(result);
  }

  /**
   * Возвращает функцию доступа к en-описаниям контекста. Для
   * {@link com.github._1c_syntax.bsl.context.platform.PlatformContextProvider}
   * — это {@code getDescriptionEn}; для других имплементаций — всегда пустая
   * строка (en-HBK не загружен или используется внешний адаптер).
   */
  static Function<Object, EnAttachments> enLookupOf(ContextProvider provider) {
    if (provider instanceof PlatformContextProvider pcp) {
      return pcp::getEnAttachments;
    }
    return ctx -> EnAttachments.EMPTY;
  }

  @Nullable
  private static TypeDecl toTypeDecl(Context context, Function<Object, EnAttachments> enLookup) {
    var kind = mapKind(context);
    if (kind == null) {
      return null;
    }
    var name = bilingualName(context.name());
    var members = collectMembers(context, enLookup);
    var en = enLookup.apply(context);
    var description = BilingualString.of(safe(descriptionOf(context)), safe(en.description()));
    var classRef = new TypeRef(kind, name.primary());
    var constructors = constructorsOf(context, classRef, enLookup);
    var defaultElementTypes = collectionElementTypes(context);
    var supportsForEach = context instanceof ContextCollection coll && coll.supportsForEach();
    var supportsIndexAccess = context instanceof ContextCollection coll && coll.supportsIndexAccess();
    var forEachRu = context instanceof ContextCollection coll ? coll.forEachDescription() : "";
    var indexAccessRu = context instanceof ContextCollection coll ? coll.indexAccessDescription() : "";
    var forEachDescription = BilingualString.of(safe(forEachRu), safe(en.forEachDescription()));
    var indexAccessDescription = BilingualString.of(safe(indexAccessRu), safe(en.indexAccessDescription()));
    var typeParameters = context.typeParameters();
    var isEnum = context instanceof ContextEnum;
    return new TypeDecl(kind, name, members,
      description, constructors,
      defaultElementTypes, supportsForEach, supportsIndexAccess,
      forEachDescription, indexAccessDescription, typeParameters, isEnum);
  }

  /**
   * Извлечь типы элементов коллекции из bsl-context. Для не-коллекций возвращает
   * пустой список — параметр {@code defaultElementTypes} тогда не несёт смысла.
   * <p>
   * Конкретные {@link TypeKind} элементов берутся через {@link #mapRefKind(Context)} —
   * это устраняет риск рассинхронизации с маппингом, применяемым к самим типам.
   */
  private static List<TypeRef> collectionElementTypes(Context context) {
    if (!(context instanceof ContextCollection collection)) {
      return List.of();
    }
    var elements = collection.collectionElementTypes();
    if (elements == null || elements.isEmpty()) {
      return List.of();
    }
    var refs = new ArrayList<TypeRef>(elements.size());
    for (var element : elements) {
      refs.add(new TypeRef(mapRefKind(element), element.name().getName()));
    }
    return List.copyOf(refs);
  }

  private static String descriptionOf(Context context) {
    if (context instanceof ContextType type) {
      return type.description();
    }
    return "";
  }

  /**
   * Маппит конструкторы платформенного типа из bsl-context
   * ({@link ContextConstructor}) в {@link SignatureDescriptor} type-system v2.
   * Возвращаемый тип конструктора — сам тип ({@code classRef}); параметры —
   * как у обычных методов.
   */
  private static List<SignatureDescriptor> constructorsOf(Context context, TypeRef classRef,
                                                          Function<Object, EnAttachments> enLookup) {
    if (!(context instanceof ContextType type)) {
      return List.of();
    }
    var ctors = type.constructors();
    if (ctors == null || ctors.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<SignatureDescriptor>(ctors.size());
    for (var ctor : ctors) {
      var parameters = new ArrayList<ParameterDescriptor>(ctor.parameters().size());
      for (var parameter : ctor.parameters()) {
        parameters.add(toParameterDescriptor(parameter, enLookup));
      }
      var ctorDescBilingual = BilingualString.of(
        safe(ctor.description()), safe(enLookup.apply(ctor).description()));
      result.add(new SignatureDescriptor(
        parameters, TypeSet.of(classRef), ctorDescBilingual));
    }
    return List.copyOf(result);
  }

  /**
   * Помечает тип как «возможный ресивер dot-выражения через глобальное имя»
   * по эвристике: внутри типа есть generic-property вида {@code <Имя X>}.
   * Это резервный сигнал для LS — основной путь связывания
   * {@code Справочники} ↔ {@code СправочникиМенеджер} идёт через
   * {@link GlobalScopeProvider} из свойств глобального контекста.
   */
  @Nullable
  private static TypeKind mapKind(Context context) {
    return switch (context.kind()) {
      case PRIMITIVE_TYPE -> TypeKind.PRIMITIVE;
      case TYPE, COLLECTION, ENUM -> TypeKind.PLATFORM;
      // GLOBAL_CONTEXT и LANGUAGE_KEYWORD типами не являются — обрабатываются
      // отдельно (GlobalScopeProvider).
      case GLOBAL_CONTEXT, LANGUAGE_KEYWORD -> null;
    };
  }

  private static List<MemberDescriptor> collectMembers(Context context,
                                                       Function<Object, EnAttachments> enLookup) {
    if (context instanceof ContextType type) {
      var members = new ArrayList<MemberDescriptor>(
        type.methods().size() + type.properties().size() + type.events().size());
      for (var property : type.properties()) {
        members.add(toMemberDescriptor(property, enLookup));
      }
      for (var method : type.methods()) {
        members.add(toMemberDescriptor(method, enLookup));
      }
      for (var event : type.events()) {
        members.add(toMemberDescriptor(event, enLookup));
      }
      return List.copyOf(members);
    }
    if (context instanceof ContextEnum enumeration) {
      var values = enumeration.values();
      var members = new ArrayList<MemberDescriptor>(values.size());
      var enumRef = new TypeRef(TypeKind.PLATFORM, context.name().getName());
      // Тип элементов набора берётся из bsl-context (страница enum: значения
      // имеют такой-то тип). Если общий тип задан (как у библиотек картинок,
      // стилей, цветов), значения возвращают его; иначе сам enumRef.
      var valueRef = enumeration.valueType()
        .map(n -> new TypeRef(TypeKind.PLATFORM, n.getName()))
        .orElse(enumRef);
      for (var value : values) {
        members.add(toMemberDescriptor(value, valueRef, enLookup));
      }
      return List.copyOf(members);
    }
    return Collections.emptyList();
  }

  static MemberDescriptor toMemberDescriptor(ContextProperty property,
                                             Function<Object, EnAttachments> enLookup) {
    var returnTypes = typeSet(property.types());
    // Имя для primary берём ru-сторону — bilingualName ниже всё равно покрывает обе локали.
    var name = property.name().getName();
    MemberDescriptor descriptor;
    if (property.isGeneric()) {
      var firstRef = returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
      descriptor = MemberDescriptor.genericProperty(name, firstRef, property.description());
    } else {
      descriptor = MemberDescriptor.property(name, returnTypes, property.description());
    }
    return descriptor.withMetadata(metadataOf(property))
      .withBilingualName(bilingualName(property.name()))
      .withBilingualDescription(BilingualString.of(
        safe(property.description()), safe(enLookup.apply(property).description())))
      .withStandardLibrary(true);
  }

  private static String safe(@Nullable String s) {
    return s == null ? "" : s;
  }

  /** Backward-compatible overload (без en-описаний). */
  static MemberDescriptor toMemberDescriptor(ContextProperty property) {
    return toMemberDescriptor(property, ctx -> EnAttachments.EMPTY);
  }

  static MemberDescriptor toMemberDescriptor(ContextMethod method,
                                             Function<Object, EnAttachments> enLookup) {
    var returnTypes = typeSet(method.returnValues());
    var primaryReturnType = returnTypes.refs().stream().findFirst().orElse(TypeRef.UNKNOWN);
    var signatures = toSignatures(method.signatures(), primaryReturnType, enLookup);
    return new MemberDescriptor(
      bilingualName(method.name()),
      MemberKind.METHOD,
      BilingualString.of(safe(method.description()), safe(enLookup.apply(method).description())),
      returnTypes,
      signatures,
      null,
      false,
      metadataOf(method, enLookup),
      method.isAsync()
    ).withStandardLibrary(true);
  }

  /** Преобразует {@link ContextName} в {@link BilingualString}. */
  private static BilingualString bilingualName(@Nullable ContextName name) {
    if (name == null) {
      return BilingualString.EMPTY;
    }
    return BilingualString.of(safe(name.getName()), safe(name.getAlias()));
  }

  /** Backward-compatible overload (без en-описаний). */
  static MemberDescriptor toMemberDescriptor(ContextMethod method) {
    return toMemberDescriptor(method, ctx -> EnAttachments.EMPTY);
  }

  /**
   * Платформенное событие типа. У события нет возвращаемого значения (handler-контракт),
   * поэтому {@code returnTypes = EMPTY}; сигнатуры — только параметры.
   */
  static MemberDescriptor toMemberDescriptor(ContextEvent event,
                                             Function<Object, EnAttachments> enLookup) {
    var signatures = toSignatures(event.signatures(), TypeRef.UNKNOWN, enLookup);
    return new MemberDescriptor(
      bilingualName(event.name()),
      MemberKind.EVENT,
      BilingualString.of(safe(event.description()), safe(enLookup.apply(event).description())),
      TypeSet.EMPTY,
      signatures,
      null,
      event.isGeneric(),
      metadataOf(event),
      false
    ).withStandardLibrary(true);
  }


  private static MemberDescriptor toMemberDescriptor(ContextEnumValue value, TypeRef valueRef,
                                                     Function<Object, EnAttachments> enLookup) {
    var name = value.name().getName();
    // generic-флаг приходит из bsl-context (ContextEnumValue#isGeneric()) —
    // это шаблоны вроде БиблиотекаКартинок.<Имя картинки>, которые потом
    // материализуются из конфигурации.
    var descriptor = value.isGeneric()
      ? MemberDescriptor.genericProperty(name, valueRef, value.description())
      : MemberDescriptor.property(name, valueRef, value.description());
    return descriptor.withMetadata(metadataOf(value))
      .withBilingualName(bilingualName(value.name()))
      .withBilingualDescription(BilingualString.of(
        safe(value.description()), safe(enLookup.apply(value).description())))
      .withStandardLibrary(true);
  }

  /**
   * Извлекает платформенные метаданные из {@link ContextMethod} с учётом
   * en-аттачментов из bilingual-merger'а. Каждое текст-поле упаковано в
   * {@link BilingualString}, examples/seeAlso — в {@code List<BilingualString>}.
   */
  private static PlatformMetadata metadataOf(ContextMethod method,
                                             Function<Object, EnAttachments> enLookup) {
    var en = enLookup.apply(method);
    return new PlatformMetadata(
      method.sinceVersion(),
      method.deprecatedSinceVersion(),
      method.recommendedReplacements(),
      BslContextEnumMapping.mapAvailabilities(method.availabilities()),
      null,
      BilingualString.of(safe(method.returnValueDescription()), safe(en.returnValueDescription())),
      BilingualString.of(safe(method.notes()), safe(en.notes())),
      zipBilingual(method.examples(), en.examples()),
      zipBilingual(method.seeAlso(), en.seeAlso())
    );
  }

  /** Compat overload — без en-аттачментов (одноязычные ru-поля). */
  private static PlatformMetadata metadataOf(ContextMethod method) {
    return metadataOf(method, ctx -> EnAttachments.EMPTY);
  }

  /**
   * Парная упаковка ru- и en-списков в {@code List<BilingualString>}.
   * Если en-список пуст или короче ru — соответствующая en-сторона пуста.
   */
  private static List<BilingualString> zipBilingual(List<String> ru, List<String> en) {
    if (ru.isEmpty()) {
      return List.of();
    }
    var out = new ArrayList<BilingualString>(ru.size());
    for (int i = 0; i < ru.size(); i++) {
      var ruItem = ru.get(i);
      var enItem = i < en.size() ? en.get(i) : "";
      out.add(BilingualString.of(safe(ruItem), safe(enItem)));
    }
    return List.copyOf(out);
  }

  /**
   * Извлекает платформенные метаданные из {@link ContextEvent}.
   */
  private static PlatformMetadata metadataOf(ContextEvent event) {
    return new PlatformMetadata(
      event.sinceVersion(),
      event.deprecatedSinceVersion(),
      event.recommendedReplacements(),
      BslContextEnumMapping.mapAvailabilities(event.availabilities()),
      null,
      "",
      "",
      List.of(),
      List.of()
    );
  }

  /**
   * Извлекает платформенные метаданные из {@link ContextProperty}.
   */
  private static PlatformMetadata metadataOf(ContextProperty property) {
    return new PlatformMetadata(
      property.sinceVersion(),
      property.deprecatedSinceVersion(),
      property.recommendedReplacements(),
      BslContextEnumMapping.mapAvailabilities(property.availabilities()),
      BslContextEnumMapping.mapAccessMode(property.accessMode()),
      "",
      "",
      List.of(),
      List.of()
    );
  }

  /**
   * Извлекает платформенные метаданные из {@link ContextEnumValue}.
   */
  private static PlatformMetadata metadataOf(ContextEnumValue value) {
    return new PlatformMetadata(
      value.sinceVersion(),
      value.deprecatedSinceVersion(),
      value.recommendedReplacements(),
      Set.of(),
      null,
      "",
      "",
      List.of(),
      List.of()
    );
  }

  static List<SignatureDescriptor> toSignatures(
    List<ContextMethodSignature> signatures,
    TypeRef returnType,
    Function<Object, EnAttachments> enLookup
  ) {
    if (signatures.isEmpty()) {
      return List.of();
    }
    var result = new ArrayList<SignatureDescriptor>(signatures.size());
    for (var signature : signatures) {
      var parameters = new ArrayList<ParameterDescriptor>(signature.parameters().size());
      for (var parameter : signature.parameters()) {
        parameters.add(toParameterDescriptor(parameter, enLookup));
      }
      var sigDescBilingual = BilingualString.of(
        safe(signature.description()), safe(enLookup.apply(signature).description()));
      result.add(new SignatureDescriptor(parameters, TypeSet.of(returnType), sigDescBilingual));
    }
    return List.copyOf(result);
  }

  private static ParameterDescriptor toParameterDescriptor(ContextSignatureParameter parameter,
                                                           Function<Object, EnAttachments> enLookup) {
    return new ParameterDescriptor(
      bilingualName(parameter.name()),
      typeSet(parameter.types()),
      !parameter.isRequired(),
      BilingualString.of(safe(parameter.description()), safe(enLookup.apply(parameter).description())),
      safe(parameter.defaultValue()),
      parameter.isVariadic()
    );
  }

  private static TypeSet typeSet(List<Context> contexts) {
    if (contexts.isEmpty()) {
      return TypeSet.EMPTY;
    }
    var refs = new ArrayList<TypeRef>(contexts.size());
    for (var c : contexts) {
      refs.add(new TypeRef(mapRefKind(c), c.name().getName()));
    }
    return TypeSet.of(refs);
  }

  private static TypeKind mapRefKind(Context context) {
    return switch (context.kind()) {
      case PRIMITIVE_TYPE -> TypeKind.PRIMITIVE;
      case TYPE, COLLECTION, ENUM -> TypeKind.PLATFORM;
      case GLOBAL_CONTEXT, LANGUAGE_KEYWORD -> TypeKind.UNKNOWN;
    };
  }
}
