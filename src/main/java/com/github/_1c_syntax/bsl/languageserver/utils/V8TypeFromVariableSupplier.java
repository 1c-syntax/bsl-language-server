package com.github._1c_syntax.bsl.languageserver.utils;

import java.util.Optional;
import java.util.Set;

public interface V8TypeFromVariableSupplier extends V8TypeSupplier {
  Optional<Set<V8Type>> getTypesFromVariable(String variableName);
}
