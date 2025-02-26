package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.ServerContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.mdo.MDObject;
import com.github._1c_syntax.bsl.mdo.ModuleOwner;
import com.github._1c_syntax.bsl.types.MDOType;
import com.github._1c_syntax.mdclasses.mdo.MDCommonModule;
import com.github._1c_syntax.mdclasses.mdo.MDOHasChildren;
import com.github._1c_syntax.mdclasses.mdo.attributes.AbstractMDOAttribute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class ScopeResolver {

  private final ServerContext serverContext;

  public IScope getPlatformScope() {
    return IScope.EMPTY;
  }

  public IScope createScope(IScopeOwner owner) {
    if (owner.getOwner() instanceof MDOType) {
      return new MDOTypeScope((MDOType) owner.getOwner(), serverContext);
    } else if (owner.getOwner() instanceof MDObject) {
      return createScope((MDObject) owner.getOwner());
    }
    return IScope.EMPTY;
  }

  public IScope createScope(DocumentContext document) {
    return IScope.EMPTY;
  }

  IScope createFilteredScope(IScope scope, Set<Capability> capabilities) {
    return scope;
  }

  ConfigurationScope createConfigurationScope() {
    return new ConfigurationScope(serverContext);
  }

  IScope createScope(MDObject object) {
    var builder = UnionContext.builder();

    if (object instanceof MDCommonModule) {
      builder.innerScope(new CommonModuleScope((MDCommonModule) object, serverContext));
    } else if (object instanceof ModuleOwner) {
      var uri = Utils.getManagerModuleURI((ModuleOwner) object, serverContext);
      if (uri != null) {
        builder.innerScope(new ModuleScope(uri, serverContext));
      }
    }
//    if (object instanceof MDOHasChildren) {
//      builder.innerScope(new MDOHasChildrenScope((MDOHasChildren) object));
//    }

    return builder.build();
  }

  Set<Capability> getCapabilities(DocumentContext document) {
    return Collections.emptySet();
  }

  @RequiredArgsConstructor
  static class MDOTypeScope extends BaseScope {
    private final MDOType type;
    private final ServerContext serverContext;

    @Override
    public Stream<IScopeOwner> getProperties() {
      return serverContext.getConfiguration().getOrderedTopMDObjects().get(type).stream()
        .map(IScopeOwner::create);
    }

    @Override
    public Optional<IScopeOwner> getProperty(String name) {
      return serverContext.getConfiguration().getOrderedTopMDObjects().get(type).stream()
        .filter(abstractMDObjectBase -> abstractMDObjectBase.getName().equalsIgnoreCase(name))
        .map(IScopeOwner::create)
        .findFirst();
    }
  }

  @RequiredArgsConstructor
  static class CommonModuleScope extends BaseScope {

    private final MDCommonModule module;
    private final ServerContext serverContext;

    @Override
    public Stream<MethodSymbol> getMethods() {
      return Utils.getMethods(module, serverContext).stream();
    }

    @Override
    public Optional<MethodSymbol> getMethod(String name) {
      return Utils.getMethod(module, serverContext, name);
    }
  }

  @RequiredArgsConstructor
  static class ModuleScope extends BaseScope {
    private final URI moduleUri;
    private final ServerContext serverContext;

    @Override
    public Stream<MethodSymbol> getMethods() {
      return Utils.getMethods(moduleUri, serverContext).stream();
    }

    @Override
    public Optional<MethodSymbol> getMethod(String name) {
      return Utils.getMethod(moduleUri, serverContext, name);
    }
  }

  @RequiredArgsConstructor
  static class MDOHasChildrenScope extends BaseScope {
    private final MDOHasChildren object;

    @Override
    public Stream<IScopeOwner> getProperties() {
      return object.getChildren().stream()
        .filter(AbstractMDOAttribute.class::isInstance)
        .map(IScopeOwner::create);
    }

    @Override
    public Optional<IScopeOwner> getProperty(String name) {
      return object.getChildren().stream()
        .filter(AbstractMDOAttribute.class::isInstance)
        .filter(it -> it.getName().equalsIgnoreCase(name))
        .map(IScopeOwner::create)
        .findFirst();
    }
  }
}
