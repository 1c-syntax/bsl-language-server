package com.github._1c_syntax.bsl.languageserver.scope;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import java.util.Optional;
import java.util.stream.Stream;

public class SimpleScope extends BaseScope {
  protected final CaseInsensitiveMap<String, MethodSymbol> methods = new CaseInsensitiveMap<>();
  protected final CaseInsensitiveMap<String, IScopeOwner> properties = new CaseInsensitiveMap<>();

  @Override
  public Stream<MethodSymbol> getMethods() {
    return methods.values().stream();
  }

  @Override
  public Stream<IScopeOwner> getProperties() {
    return properties.values().stream();
  }

  @Override
  public Optional<MethodSymbol> getMethod(String name) {
    return Optional.of(methods.get(name));
  }

  @Override
  public Optional<IScopeOwner> getProperty(String name) {
    return Optional.ofNullable(properties.get(name));
  }
}
