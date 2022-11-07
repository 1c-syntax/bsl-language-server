package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.Builder;
import lombok.Singular;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

@Builder
public class UnionContext extends BaseScope {

  @Singular
  private Collection<IScope> innerScopes;

  @Override
  public Stream<MethodSymbol> getMethods() {
    return innerScopes.stream()
      .flatMap(IScope::getMethods);
  }

  @Override
  public Stream<IScopeOwner> getProperties() {
    return innerScopes.stream()
      .flatMap(IScope::getProperties);
  }

  @Override
  public Optional<MethodSymbol> getMethod(String name) {
    return innerScopes.stream()
      .map(it -> it.getMethod(name))
      .filter(Optional::isPresent)
      .findFirst()
      .orElseGet(Optional::empty);
  }

  @Override
  public Optional<IScopeOwner> getProperty(String name) {
    return innerScopes.stream()
      .map(it -> it.getProperty(name))
      .filter(Optional::isPresent)
      .findFirst()
      .orElseGet(Optional::empty);
  }
}
