package com.github._1c_syntax.bsl.languageserver.context.symbol;

import lombok.Builder;
import lombok.Value;
import org.eclipse.lsp4j.Range;

@Value
@Builder
public class VariableUsage implements Usage {

  Range range;
  Kind kind;

}
