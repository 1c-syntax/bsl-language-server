package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.Builder;
import lombok.Singular;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Builder
public class UnionContext implements IScope{
  @Singular
  private Collection<IScope>innerScopes;

  @Override
  public Set<Capability> getCapabilities() {
    return null;
  }

  @Override
  public Stream<MethodSymbol> getMethods() {
    return null;
  }

  @Override
  public Stream<IScopeOwner> getProperties() {
    return null;
  }

  @Override
  public Optional<MethodSymbol> getMethod(String name) {
    return Optional.empty();
  }

  @Override
  public IScopeOwner getProperty(String name) {
    return null;
  }
}
