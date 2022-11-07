package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;

import java.util.Collection;
import java.util.Set;

public class ConfigurationScope extends SimpleScope {

  private static final Collection<MDOType> rootProperties = Set.of(
    MDOType.EXCHANGE_PLAN,
    MDOType.CONSTANT,
    MDOType.CATALOG,
    MDOType.DOCUMENT,
    MDOType.DOCUMENT_JOURNAL,
    MDOType.ENUM,
    MDOType.REPORT,
    MDOType.DATA_PROCESSOR,
    MDOType.CHART_OF_CHARACTERISTIC_TYPES,
    MDOType.CHART_OF_ACCOUNTS,
    MDOType.CHART_OF_CALCULATION_TYPES,
    MDOType.INFORMATION_REGISTER,
    MDOType.ACCUMULATION_REGISTER,
    MDOType.ACCOUNTING_REGISTER,
    MDOType.CALCULATION_REGISTER,
    MDOType.BUSINESS_PROCESS,
    MDOType.TASK,
    MDOType.EXTERNAL_DATA_SOURCE);

  private final ServerContext serverContext;

  public ConfigurationScope(ServerContext serverContext) {
    this.serverContext = serverContext;

    for (var module : serverContext.getConfiguration().getCommonModules().values()) {
      if (module.isGlobal()) {
        appendGlobalCommonModule(module);
      } else {
        appendCommonModule(module);
      }
    }

    for (var type : rootProperties) {
      var property = IScopeOwner.create(type);
      properties.put(type.getGroupName(), property);
      properties.put(type.getGroupNameRu(), property);
    }

  }

  private void appendCommonModule(MDCommonModule module) {
    properties.put(module.getName(), IScopeOwner.create(module));
  }

  private void appendGlobalCommonModule(MDCommonModule module) {
    for (var method : Utils.getMethods(module, serverContext)) {
      methods.put(method.getName(), method);
    }
  }
}
