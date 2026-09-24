package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public interface IScope {
  IScope EMPTY = new BaseScope();

  Set<Capability> getCapabilities();

  Stream<MethodSymbol> getMethods();

  Stream<IScopeOwner> getProperties();

  Optional<MethodSymbol> getMethod(String name);

  Optional<IScopeOwner> getProperty(String name);
}
