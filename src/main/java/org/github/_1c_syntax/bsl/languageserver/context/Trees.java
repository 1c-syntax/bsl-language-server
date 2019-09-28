package org.github._1c_syntax.bsl.languageserver.context;

import org.antlr.v4.runtime.ParserRuleContext;

import javax.annotation.CheckForNull;

public class Trees {

  /** Ищем предка элемента по указанному типу BSLParser
   * Пример:
   * ParserRuleContext parent = Trees.getAncestorByRuleIndex(ctx, BSLParser.RULE_statement);
  */
  @CheckForNull
  public static ParserRuleContext getAncestorByRuleIndex(ParserRuleContext element, int type) {
    ParserRuleContext parent = element.getParent();
    if (parent == null) {
      return null;
    }
    if (parent.getRuleIndex() == type) {
      return parent;
    }
    return getAncestorByRuleIndex(parent, type);
  }

}
