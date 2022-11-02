package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ScopeResolver {

  private final ServerContext serverContext;

  public IScope getScope(DocumentContext document) {
    return UnionContext.builder()
      .innerScope(getFilteredScope(getGlobalScope(), getCapabilities(document)))
      .innerScope(getPrivateScope(document))
      .build();

  }

  public IScope getGlobalScope() {
    return UnionContext.builder()
      .innerScope(getConfigurationScope())
      .innerScope(getPlatformScope())
      .build();
  }

  public IScope getConfigurationScope() {
    return new ConfigurationContext(serverContext, this);
  }

  public IScope getPlatformScope() {
    return IScope.EMPTY;
  }

  public IScope getPrivateScope(DocumentContext document) {
    return IScope.EMPTY;
  }

  private IScope getFilteredScope(IScope scope, Set<Capability> capabilities) {
    return IScope.EMPTY;
  }

  private Set<Capability> getCapabilities(DocumentContext document) {
    return Collections.emptySet();
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

  IScope createSCope(MDCommonModule module) {
    return new CommonModuleScope(module, serverContext);
  }

  @RequiredArgsConstructor
  static class CommonModuleScope implements IScope {

    private final MDCommonModule module;
    private final ServerContext serverContext;

    @Override
    public Set<Capability> getCapabilities() {
      return Collections.emptySet();
    }

    @Override
    public Stream<MethodSymbol> getMethods() {
      return ScopeResolver.getMethods(module, serverContext).stream();
    }

    @Override
    public Stream<IScopeOwner> getProperties() {
      return Stream.empty();
    }

    @Override
    public Optional<MethodSymbol> getMethod(String name) {
      return ScopeResolver.getMethod(module, serverContext, name);
    }

    @Override
    public IScopeOwner getProperty(String name) {
      return null;
    }
  }
}
