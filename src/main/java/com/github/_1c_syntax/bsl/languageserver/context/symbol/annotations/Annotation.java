package com.github._1c_syntax.bsl.languageserver.context.symbol.annotations;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс хранит информацию об аннотации.
 * См. {@link com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol}
 */

@Value
@Builder
public class Annotation {
  String name;
  AnnotationKind kind;

  @Builder.Default
  List<AnnotationParameterDefinition> parameters = new ArrayList<>();
}
