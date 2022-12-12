package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import com.github._1c_syntax.bsl.languageserver.cfg.PreprocessorConstraints;
import com.github._1c_syntax.bsl.languageserver.utils.bsl.Preprocessor;
import com.github._1c_syntax.bsl.parser.BSLParser;
import lombok.Getter;
import lombok.ToString;

@ToString
public class PreprocessorSymbolNode extends BslExpression {

  @Getter
  private final PreprocessorConstraints symbol;

  PreprocessorSymbolNode(BSLParser.Preproc_symbolContext ctx) {
    super(ExpressionNodeType.LITERAL);
    symbol = Preprocessor.getPreprocessorConstraint(ctx);
  }
}
