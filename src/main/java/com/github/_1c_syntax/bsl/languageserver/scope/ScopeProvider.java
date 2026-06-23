package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.utils.Lazy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScopeProvider {

  private final ScopeResolver resolver;
  private final Lazy<ConfigurationScope> configurationScope = new Lazy<>(this::createConfigurationScope);

  public IScope getGlobalScope() {
    return UnionContext.builder()
      .innerScope(getConfigurationScope())
      .innerScope(resolver.getPlatformScope())
      .build();
  }

  public IScope getConfigurationScope() {
    return configurationScope.getOrCompute();
  }

  public IScope getScope(DocumentContext document) {
    return UnionContext.builder()
      .innerScope(resolver.createFilteredScope(getGlobalScope(), resolver.getCapabilities(document)))
      .innerScope(resolver.createScope(document))
      .build();
  }

  public IScope getScope(IScopeOwner owner) {
    return resolver.createScope(owner);
  }

  private ConfigurationScope createConfigurationScope(){
    return resolver.createConfigurationScope();
  }
}
