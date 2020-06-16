package com.github._1c_syntax.bsl.languageserver.utils;

import java.util.Optional;

public interface V8TypeFromPresentationSupplier extends V8TypeSupplier {
  Optional<V8Type> getTypeFromPresentation(String presentation);
}
