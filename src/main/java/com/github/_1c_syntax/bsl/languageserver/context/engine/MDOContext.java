package com.github._1c_syntax.bsl.languageserver.context.engine;

import com.github._1c_syntax.bsl.context.context.BSLContext;
import com.github._1c_syntax.bsl.context.entity.AbstractMethod;
import com.github._1c_syntax.bsl.context.entity.AbstractType;
import com.github._1c_syntax.mdclasses.metadata.Configuration;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class MDOContext implements BSLContext {

  private final Configuration configuration;

  public MDOContext(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void initialize() {
  }

  @Override
  public Map<String, AbstractMethod> getMethods(URI uri) {
    return configuration.getGlobalMethodContext();
  }

  @Override
  public Optional<AbstractMethod> getMethod(URI uri, String name) {
    return configuration.getGlobalMethod(name);
  }

  @Override
  public Map<String, AbstractType> getTypes(URI uri) {
    return Collections.emptyMap();
  }

  @Override
  public Optional<AbstractType> getType(URI uri, String name) {
    return Optional.empty();
  }

  @Override
  public void shutdown() {
  }
}
