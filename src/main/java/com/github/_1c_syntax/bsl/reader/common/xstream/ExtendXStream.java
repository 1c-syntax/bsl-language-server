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
package com.github._1c_syntax.bsl.reader.common.xstream;

import com.github._1c_syntax.bsl.mdclasses.ConfigurationTree;
import com.github._1c_syntax.bsl.mdclasses.ExternalDataProcessor;
import com.github._1c_syntax.bsl.mdclasses.ExternalReport;
import com.github._1c_syntax.bsl.mdo.AccountingRegister;
import com.github._1c_syntax.bsl.mdo.AccumulationRegister;
import com.github._1c_syntax.bsl.mdo.Bot;
import com.github._1c_syntax.bsl.mdo.BusinessProcess;
import com.github._1c_syntax.bsl.mdo.CalculationRegister;
import com.github._1c_syntax.bsl.mdo.Catalog;
import com.github._1c_syntax.bsl.mdo.ChartOfAccounts;
import com.github._1c_syntax.bsl.mdo.ChartOfCalculationTypes;
import com.github._1c_syntax.bsl.mdo.ChartOfCharacteristicTypes;
import com.github._1c_syntax.bsl.mdo.CommandGroup;
import com.github._1c_syntax.bsl.mdo.CommonAttribute;
import com.github._1c_syntax.bsl.mdo.CommonCommand;
import com.github._1c_syntax.bsl.mdo.CommonForm;
import com.github._1c_syntax.bsl.mdo.CommonModule;
import com.github._1c_syntax.bsl.mdo.CommonPicture;
import com.github._1c_syntax.bsl.mdo.CommonTemplate;
import com.github._1c_syntax.bsl.mdo.Constant;
import com.github._1c_syntax.bsl.mdo.DataProcessor;
import com.github._1c_syntax.bsl.mdo.DefinedType;
import com.github._1c_syntax.bsl.mdo.Document;
import com.github._1c_syntax.bsl.mdo.DocumentJournal;
import com.github._1c_syntax.bsl.mdo.DocumentNumerator;
import com.github._1c_syntax.bsl.mdo.EventSubscription;
//noinspection AmbiguousImport — нужен наш Enum, не java.lang.Enum
import com.github._1c_syntax.bsl.mdo.ExchangePlan;
import com.github._1c_syntax.bsl.mdo.ExternalDataSource;
import com.github._1c_syntax.bsl.mdo.FilterCriterion;
import com.github._1c_syntax.bsl.mdo.FunctionalOption;
import com.github._1c_syntax.bsl.mdo.FunctionalOptionsParameter;
import com.github._1c_syntax.bsl.mdo.HTTPService;
import com.github._1c_syntax.bsl.mdo.InformationRegister;
import com.github._1c_syntax.bsl.mdo.IntegrationService;
import com.github._1c_syntax.bsl.mdo.Interface;
import com.github._1c_syntax.bsl.mdo.Language;
import com.github._1c_syntax.bsl.mdo.MD;
import com.github._1c_syntax.bsl.mdo.PaletteColor;
import com.github._1c_syntax.bsl.mdo.Report;
import com.github._1c_syntax.bsl.mdo.Role;
import com.github._1c_syntax.bsl.mdo.ScheduledJob;
import com.github._1c_syntax.bsl.mdo.Sequence;
import com.github._1c_syntax.bsl.mdo.SessionParameter;
import com.github._1c_syntax.bsl.mdo.SettingsStorage;
import com.github._1c_syntax.bsl.mdo.Style;
import com.github._1c_syntax.bsl.mdo.StyleItem;
import com.github._1c_syntax.bsl.mdo.Subsystem;
import com.github._1c_syntax.bsl.mdo.Task;
import com.github._1c_syntax.bsl.mdo.WSReference;
import com.github._1c_syntax.bsl.mdo.WebService;
import com.github._1c_syntax.bsl.mdo.WebSocketClient;
import com.github._1c_syntax.bsl.mdo.XDTOPackage;
import com.github._1c_syntax.bsl.mdo.storage.DataCompositionSchema;
import com.github._1c_syntax.bsl.mdo.storage.RoleData;
import com.github._1c_syntax.bsl.mdo.storage.XdtoPackageData;
import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import com.github._1c_syntax.bsl.mdo.support.ApplicationRunMode;
import com.github._1c_syntax.bsl.mdo.support.AutoRecordType;
import com.github._1c_syntax.bsl.mdo.support.CodeSeries;
import com.github._1c_syntax.bsl.mdo.support.ConfigurationExtensionPurpose;
import com.github._1c_syntax.bsl.mdo.support.DataLockControlMode;
import com.github._1c_syntax.bsl.mdo.support.DataSeparation;
import com.github._1c_syntax.bsl.mdo.support.FormType;
import com.github._1c_syntax.bsl.mdo.support.IndexingType;
import com.github._1c_syntax.bsl.mdo.support.InterfaceCompatibilityMode;
import com.github._1c_syntax.bsl.mdo.support.MessageDirection;
import com.github._1c_syntax.bsl.mdo.support.ObjectBelonging;
import com.github._1c_syntax.bsl.mdo.support.ReturnValueReuse;
import com.github._1c_syntax.bsl.mdo.support.ReuseSessions;
import com.github._1c_syntax.bsl.mdo.support.RoleRight;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import com.github._1c_syntax.bsl.mdo.support.TransferDirection;
import com.github._1c_syntax.bsl.mdo.support.UseMode;
import com.github._1c_syntax.bsl.mdo.support.UsePurposes;
import com.github._1c_syntax.bsl.reader.MDReader;
import com.github._1c_syntax.bsl.reader.common.converter.AllStringConverter;
import com.github._1c_syntax.bsl.reader.common.converter.CommonAttributeUseContentConverter;
import com.github._1c_syntax.bsl.reader.common.converter.CommonConverter;
import com.github._1c_syntax.bsl.reader.common.converter.CommonModuleConverter;
import com.github._1c_syntax.bsl.reader.common.converter.CompatibilityModeConverter;
import com.github._1c_syntax.bsl.reader.common.converter.ConfigurationConverter;
import com.github._1c_syntax.bsl.reader.common.converter.DataCompositionSchemaConverter;
import com.github._1c_syntax.bsl.reader.common.converter.DataSetConverter;
import com.github._1c_syntax.bsl.reader.common.converter.EnumConverter;
import com.github._1c_syntax.bsl.reader.common.converter.ExchangePlanAutoRecordConverter;
import com.github._1c_syntax.bsl.reader.common.converter.MethodHandlerConverter;
import com.github._1c_syntax.bsl.reader.common.converter.MultiLanguageStringConverter;
import com.github._1c_syntax.bsl.reader.common.converter.QuerySourceConverter;
import com.github._1c_syntax.bsl.reader.common.converter.RoleDataConverter;
import com.github._1c_syntax.bsl.reader.common.converter.SubsystemConverter;
import com.github._1c_syntax.bsl.reader.common.converter.ValueTypeConverter;
import com.github._1c_syntax.bsl.reader.common.converter.ValueTypeDescriptionConverter;
import com.github._1c_syntax.bsl.reader.common.converter.ValueTypeQualifierConverter;
import com.github._1c_syntax.bsl.reader.common.converter.XdtoPackageDataConverter;
import com.github._1c_syntax.bsl.reader.designer.converter.DesignerConverter;
import com.github._1c_syntax.bsl.reader.edt.converter.EDTConverter;
import com.github._1c_syntax.bsl.types.DateFractions;
import com.github._1c_syntax.bsl.types.ScriptVariant;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.SingleValueConverter;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.basic.BooleanConverter;
import com.thoughtworks.xstream.converters.basic.IntConverter;
import com.thoughtworks.xstream.converters.basic.NullConverter;
import com.thoughtworks.xstream.converters.basic.StringConverter;
import com.thoughtworks.xstream.converters.collections.ArrayConverter;
import com.thoughtworks.xstream.converters.collections.CollectionConverter;
import com.thoughtworks.xstream.converters.collections.MapConverter;
import com.thoughtworks.xstream.converters.collections.SingletonCollectionConverter;
import com.thoughtworks.xstream.converters.collections.SingletonMapConverter;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;
import com.thoughtworks.xstream.converters.reflection.ReflectionConverter;
import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.mapper.CachingMapper;
import com.thoughtworks.xstream.mapper.CannotResolveClassException;
import com.thoughtworks.xstream.mapper.ClassAliasingMapper;
import com.thoughtworks.xstream.mapper.DefaultImplementationsMapper;
import com.thoughtworks.xstream.mapper.DefaultMapper;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.SecurityMapper;
import com.thoughtworks.xstream.security.WildcardTypePermission;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.HasName;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.nativeimage.ImageInfo;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Расширение функциональности XStream.
 * <p>
 * Shadow-копия из {@code io.github.1c-syntax:mdclasses} с native-friendly веткой:
 * в native-image не используется {@link ClassGraph#scan()} (он валится на JRT FS
 * при попытке прочитать boot-модули JDK), классы и конвертеры регистрируются
 * по статически известному списку. На JVM поведение совпадает с оригиналом.
 */
@Slf4j
@Getter
public class ExtendXStream extends XStream {

  /**
   * Список MD-классов из {@code com.github._1c_syntax.bsl.mdo} (без подпакета
   * {@code children}), для регистрации XStream alias-ов. Список соответствует
   * результату {@code ClassGraph.getClassesImplementing(MD.class.getName())} в
   * mdclasses 0.18.0 — поддерживать синхронно при апдейте библиотеки.
   */
  private static final List<Class<? extends MD>> NATIVE_MD_CLASSES = List.of(
    AccountingRegister.class,
    AccumulationRegister.class,
    Bot.class,
    BusinessProcess.class,
    CalculationRegister.class,
    Catalog.class,
    ChartOfAccounts.class,
    ChartOfCalculationTypes.class,
    ChartOfCharacteristicTypes.class,
    CommandGroup.class,
    CommonAttribute.class,
    CommonCommand.class,
    CommonForm.class,
    CommonModule.class,
    CommonPicture.class,
    CommonTemplate.class,
    Constant.class,
    DataProcessor.class,
    DefinedType.class,
    Document.class,
    DocumentJournal.class,
    DocumentNumerator.class,
    com.github._1c_syntax.bsl.mdo.Enum.class,
    EventSubscription.class,
    ExchangePlan.class,
    ExternalDataSource.class,
    FilterCriterion.class,
    FunctionalOption.class,
    FunctionalOptionsParameter.class,
    HTTPService.class,
    InformationRegister.class,
    IntegrationService.class,
    Interface.class,
    Language.class,
    PaletteColor.class,
    Report.class,
    Role.class,
    ScheduledJob.class,
    Sequence.class,
    SessionParameter.class,
    SettingsStorage.class,
    Style.class,
    StyleItem.class,
    Subsystem.class,
    Task.class,
    WSReference.class,
    WebService.class,
    WebSocketClient.class,
    XDTOPackage.class
  );

  /**
   * Реестр конвертеров для трёх известных пар (пакет → класс-маркер аннотации),
   * с которыми {@link mdclasses 0.18.0} вызывает {@link #registerConverters} в native-режиме.
   * <p>
   * Содержимое соответствует результату {@code ClassGraph.getClassesWithAnnotation(...)} на JVM
   * — поддерживать синхронно при апдейте mdclasses.
   */
  private static final Map<String, List<Class<?>>> NATIVE_CONVERTERS = buildNativeConverters();

  private static Map<String, List<Class<?>>> buildNativeConverters() {
    var map = new HashMap<String, List<Class<?>>>();
    map.put(
      key("com.github._1c_syntax.bsl.reader.common.converter", CommonConverter.class),
      List.of(
        AllStringConverter.class,
        CommonAttributeUseContentConverter.class,
        CommonModuleConverter.class,
        CompatibilityModeConverter.class,
        ConfigurationConverter.class,
        DataCompositionSchemaConverter.class,
        DataSetConverter.class,
        ExchangePlanAutoRecordConverter.class,
        MethodHandlerConverter.class,
        MultiLanguageStringConverter.class,
        QuerySourceConverter.class,
        RoleDataConverter.class,
        SubsystemConverter.class,
        ValueTypeConverter.class,
        ValueTypeDescriptionConverter.class,
        ValueTypeQualifierConverter.class,
        XdtoPackageDataConverter.class
      )
    );
    map.put(
      key("com.github._1c_syntax.bsl.reader.designer.converter", DesignerConverter.class),
      List.of(
        com.github._1c_syntax.bsl.reader.designer.converter.ExchangePlanConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.ExternalSourceConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.FormAttributeConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.FormElementConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.FormHandlerConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.ManagedFormDataConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.MDChildConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.MDConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.MdoReferenceConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.MetaDataObjectConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.RoleConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.StandardAttributeConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.TemplateConverter.class,
        com.github._1c_syntax.bsl.reader.designer.converter.XDTOPackageConverter.class
      )
    );
    map.put(
      key("com.github._1c_syntax.bsl.reader.edt.converter", EDTConverter.class),
      List.of(
        com.github._1c_syntax.bsl.reader.edt.converter.CommonTemplateConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.ExternalDataSourceConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.ExternalSourceConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.FormHandlerConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.FormItemConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.ManagedFormDataConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.MDChildConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.MDConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.MdoReferenceConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.ObjectTemplateConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.RoleConverter.class,
        com.github._1c_syntax.bsl.reader.edt.converter.XDTOPackageConverter.class
      )
    );
    return Map.copyOf(map);
  }

  private static String key(String pkg, Class<? extends Annotation> annotation) {
    return pkg + "|" + annotation.getName();
  }

  public ExtendXStream(MDReader reader, ClassLoaderReference classLoaderReference, Mapper mapper) {
    super(new PureJavaReflectionProvider(), new ExtendStaxDriver(reader), classLoaderReference, mapper);
    init();
  }

  public ExtendXStream(MDReader reader, QNameMap qNameMap, ClassLoaderReference classLoaderReference, Mapper mapper) {
    super(new PureJavaReflectionProvider(), new ExtendStaxDriver(reader, qNameMap), classLoaderReference, mapper);
    init();
  }

  @Override
  @Nullable
  public Object fromXML(File file) {
    Object result = null;
    if (file.exists()) {
      try {
        result = super.fromXML(file);
      } catch (ConversionException e) {
        LOGGER.warn("Can't read file '{}' - it's broken (skipped) \n", file, e);
      } catch (CannotResolveClassException e) {
        LOGGER.debug("Can't read file '{}' - unknown class (skipped) \n", file, e);
      } catch (StreamException e) {
        LOGGER.warn("Can't read file '{}' - it's broken (skipped)", file, e);
      } catch (Exception e) {
        LOGGER.warn("Can't read file '{}' - unexpected error (skipped): {}", file, e.getMessage(), e);
      }
    }
    return result;
  }

  @Nullable
  public Class<?> getRealClass(String className) {
    return getMapper().realClass(className);
  }

  @Nullable
  public static Class<?> getRealClass(HierarchicalStreamReader reader, String className) {
    return getCurrentMDReader(reader).getXstream().getRealClass(className);
  }

  @Nullable
  public static Object read(HierarchicalStreamReader reader, Path contentPath) {
    return getCurrentMDReader(reader).read(contentPath);
  }

  @Nullable
  public static Object read(HierarchicalStreamReader reader, Path contentPath, String fullName) {
    return getCurrentMDReader(reader).read(contentPath, fullName);
  }

  public static Path getCurrentPath(HierarchicalStreamReader reader) {
    return ((ExtendReaderWrapper) reader).getPath();
  }

  public static MDReader getCurrentMDReader(HierarchicalStreamReader reader) {
    return ((ExtendReaderWrapper) reader).getMDReader();
  }

  @SuppressWarnings("unchecked")
  public static <T> T readValue(UnmarshallingContext context, Class<T> clazz) {
    return (T) context.convertAnother(null, clazz);
  }

  /**
   * Регистрирует конверторы нужного типа, фильтруя по пакету и аннотации.
   * <p>
   * На JVM выполняет рантайм-скан classpath через ClassGraph (поведение
   * оригинального mdclasses 0.18.0). В native-image идёт по статически
   * известному списку из {@link #NATIVE_COMMON_CONVERTERS}, потому что
   * ClassGraph валится на JRT-файлсистеме при попытке прочитать boot-модули JDK.
   */
  public static void registerConverters(ExtendXStream xStream, String convertersPackageName, Class<?> annotation) {
    if (ImageInfo.inImageRuntimeCode()) {
      registerConvertersStatic(xStream, convertersPackageName, annotation);
      return;
    }
    try (var scanResult = new ClassGraph()
      .enableClassInfo()
      .enableAnnotationInfo()
      .acceptPackages(convertersPackageName)
      .scan()) {

      var classes = scanResult.getClassesWithAnnotation(annotation.getName());
      classes.stream()
        .map(getObjectsFromInfoClass())
        .forEach(xStream::registerMDCConverter);
    }
  }

  private static void registerConvertersStatic(ExtendXStream xStream, String convertersPackageName, Class<?> annotation) {
    var classes = NATIVE_CONVERTERS.get(convertersPackageName + "|" + annotation.getName());
    if (classes == null) {
      throw new IllegalStateException(
        "Unexpected registerConverters call in native-image: package=" + convertersPackageName
          + ", annotation=" + annotation.getName()
          + ". Обновите NATIVE_CONVERTERS в ExtendXStream под актуальный mdclasses.");
    }
    classes.stream()
      .map(ExtendXStream::instantiateNoArg)
      .forEach(xStream::registerMDCConverter);
  }

  @Override
  protected void setupConverters() {
    registerConverter(new NullConverter(), PRIORITY_VERY_HIGH);
    registerConverter(new IntConverter(), PRIORITY_NORMAL);
    registerConverter(new BooleanConverter(), PRIORITY_NORMAL);
    registerConverter(new StringConverter(), PRIORITY_LOW);

    registerConverter(new CollectionConverter(getMapper()));
    registerConverter(new ArrayConverter(getMapper()), PRIORITY_NORMAL);
    registerConverter(new MapConverter(getMapper()), PRIORITY_NORMAL);
    registerConverter(new SingletonCollectionConverter(getMapper()), PRIORITY_NORMAL);
    registerConverter(new SingletonMapConverter(getMapper()), PRIORITY_NORMAL);

    registerConverter(new ReflectionConverter(getMapper(), getReflectionProvider()), PRIORITY_VERY_LOW);

    registerConverters(this,
      "com.github._1c_syntax.bsl.reader.common.converter",
      CommonConverter.class);

    registerConverter(new EnumConverter<>(ApplicationRunMode.class));
    registerConverter(new EnumConverter<>(AutoRecordType.class));
    registerConverter(new EnumConverter<>(ConfigurationExtensionPurpose.class));
    registerConverter(new EnumConverter<>(DataLockControlMode.class));
    registerConverter(new EnumConverter<>(DataSeparation.class));
    registerConverter(new EnumConverter<>(FormType.class));
    registerConverter(new EnumConverter<>(IndexingType.class));
    registerConverter(new EnumConverter<>(MessageDirection.class));
    registerConverter(new EnumConverter<>(ObjectBelonging.class));
    registerConverter(new EnumConverter<>(ReturnValueReuse.class));
    registerConverter(new EnumConverter<>(ReuseSessions.class));
    registerConverter(new EnumConverter<>(RoleRight.class));
    registerConverter(new EnumConverter<>(ScriptVariant.class));
    registerConverter(new EnumConverter<>(TemplateType.class));
    registerConverter(new EnumConverter<>(TransferDirection.class));
    registerConverter(new EnumConverter<>(UseMode.class));
    registerConverter(new EnumConverter<>(UsePurposes.class));
    registerConverter(new EnumConverter<>(FormElementType.class));
    registerConverter(new EnumConverter<>(InterfaceCompatibilityMode.class));
    registerConverter(new EnumConverter<>(DateFractions.class));
    registerConverter(new EnumConverter<>(CodeSeries.class));
  }

  private void init() {
    setMode(XStream.NO_REFERENCES);
    addPermission(new WildcardTypePermission(new String[]{"com.github._1c_syntax.**"}));

    registerClasses();
  }

  protected void registerMDCConverter(Object converter) {
    if (converter instanceof Converter simpleConverter) {
      registerConverter(simpleConverter);
    } else if (converter instanceof SingleValueConverter singleValueConverter) {
      registerConverter(singleValueConverter);
    } else {
      throw new IllegalArgumentException("Unknown converter type " + converter);
    }
  }

  private void registerClasses() {
    if (ImageInfo.inImageRuntimeCode()) {
      registerClassesStatic();
    } else {
      registerClassesViaClassGraph();
    }

    alias("Rights", RoleData.class);
    alias("package", XdtoPackageData.class);
    alias("DataCompositionSchema", DataCompositionSchema.class);
    alias("Configuration", ConfigurationTree.class);
    alias("ExternalDataProcessor", ExternalDataProcessor.class);
    alias("ExternalReport", ExternalReport.class);
  }

  private void registerClassesViaClassGraph() {
    try (var scanResult = new ClassGraph()
      .enableClassInfo()
      .acceptPackages("com.github._1c_syntax.bsl.mdo")
      .rejectPackages("com.github._1c_syntax.bsl.mdo.children")
      .scan()) {

      scanResult.getClassesImplementing(MD.class.getName())
        .filter(classInfo -> !classInfo.isInterface())
        .forEach((ClassInfo clazzInfo) -> {
          var clazz = getClassFromClassInfo(clazzInfo);
          var simpleName = clazzInfo.getSimpleName();
          alias(simpleName, clazz);
        });
    }
  }

  private void registerClassesStatic() {
    for (var clazz : NATIVE_MD_CLASSES) {
      alias(clazz.getSimpleName(), clazz);
    }
  }

  @Nullable
  private static Class<?> getClassFromClassInfo(HasName classInfo) {
    try {
      return Class.forName(classInfo.getName());
    } catch (ClassNotFoundException e) {
      LOGGER.error("Cannot resolve class {}\n", classInfo.getName(), e);
      return null;
    }
  }

  private static Function<ClassInfo, Object> getObjectsFromInfoClass() {
    return (ClassInfo classInfo) -> {
      try {
        var clazz = Class.forName(classInfo.getName());
        return clazz.getDeclaredConstructors()[0].newInstance();
      } catch (ClassNotFoundException | InvocationTargetException | IllegalAccessException
        | InstantiationException e) {
        LOGGER.error("Cannot resolve class {}\n", classInfo.getName(), e);
        throw new IllegalArgumentException("Cannot resolve class");
      }
    };
  }

  private static Object instantiateNoArg(Class<?> clazz) {
    try {
      return clazz.getDeclaredConstructors()[0].newInstance();
    } catch (InvocationTargetException | IllegalAccessException | InstantiationException e) {
      LOGGER.error("Cannot instantiate converter {}\n", clazz.getName(), e);
      throw new IllegalArgumentException("Cannot instantiate converter " + clazz.getName(), e);
    }
  }

  public static Mapper buildMapper(ClassLoaderReference classLoaderReference) {
    Mapper mapper = new DefaultMapper(classLoaderReference);
    mapper = new ClassAliasingMapper(mapper);
    mapper = new DefaultImplementationsMapper(mapper);
    mapper = new SecurityMapper(mapper);
    mapper = new CachingMapper(mapper);
    return mapper;
  }
}
