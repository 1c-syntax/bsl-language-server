package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface IScope {
  IScope EMPTY = new IScope() {

    @Override
    public Set<Capability> getCapabilities() {
      return Collections.emptySet();
    }

    @Override
    public Stream<MethodSymbol> getMethods() {
      return Stream.empty();
    }

    @Override
    public Stream<IScopeOwner> getProperties() {
      return Stream.empty();
    }

    @Override
    public Optional<MethodSymbol> getMethod(String name) {
      return Optional.empty();
    }

    @Override
    public IScopeOwner getProperty(String name) {
      return null;
    }
  };

  Set<Capability> getCapabilities();

  Stream<MethodSymbol> getMethods();

  Stream<IScopeOwner> getProperties();

  Optional<MethodSymbol> getMethod(String name);

  IScopeOwner getProperty(String name);
}
