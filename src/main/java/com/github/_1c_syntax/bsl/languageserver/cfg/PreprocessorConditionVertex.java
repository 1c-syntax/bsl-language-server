package com.github._1c_syntax.bsl.languageserver.cfg;

import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.bsl.parser.BSLParserRuleContext;
import lombok.EqualsAndHashCode;

import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
public class PreprocessorConditionVertex extends CfgVertex {
  private final BSLParserRuleContext astNode;

  public PreprocessorConditionVertex(BSLParser.Preproc_ifContext ifClause) {
    astNode = ifClause;
  }

  public PreprocessorConditionVertex(BSLParser.Preproc_elsifContext elsIfClause) {
    astNode = elsIfClause;
  }

  @Override
  public Optional<BSLParserRuleContext> getAst() {
    return Optional.of(astNode);
  }
}
