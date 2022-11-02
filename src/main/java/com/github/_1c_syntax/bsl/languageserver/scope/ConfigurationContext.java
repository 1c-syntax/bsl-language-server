package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

public class ConfigurationContext extends AbstractContext {

  private final ServerContext serverContext;
  private final ScopeResolver scopeResolver;

  public ConfigurationContext(ServerContext serverContext, ScopeResolver scopeResolver) {
    this.serverContext = serverContext;
    this.scopeResolver = scopeResolver;
    for (var module : serverContext.getConfiguration().getCommonModules().values()) {
      if (module.isGlobal()) {
        appendGlobalCommonModule(module);
      } else {
        appendCommonModule(module);
      }
    }
  }

  void appendCommonModule(MDCommonModule module) {
    properties.put(module.getName(), IScopeOwner.create(module, scopeResolver.createSCope(module)));
  }

  void appendGlobalCommonModule(MDCommonModule module) {
    for (var method : getMethods(module, serverContext)) {
      methods.put(method.getName(), method);
    }
  }

  static Collection<MethodSymbol> getMethods(MDCommonModule module, ServerContext serverContext) {
    var url = module.getModules().get(0).getUri();
    var document = serverContext.getDocument(url);
    if (document == null) {
      return Collections.emptyList();
    }

    return document.getSymbolTree().getMethods();
  }

  static Optional<MethodSymbol> getMethod(MDCommonModule module, ServerContext serverContext, String name) {
    var url = module.getModules().get(0).getUri();
    var document = serverContext.getDocument(url);
    if (document == null) {
      return Optional.empty();
    }
    return document.getSymbolTree().getMethodSymbol(name);
  }
}
