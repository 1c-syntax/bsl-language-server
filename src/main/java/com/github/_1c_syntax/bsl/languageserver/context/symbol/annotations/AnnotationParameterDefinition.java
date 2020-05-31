package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import lombok.Value;

/**
 * Класс хранит информацию о параметре аннотации.
 * См. {@link Annotation}
 */
@Value
public class AnnotationParameterDefinition {
  String name;
  String value;
  boolean optional;
}
