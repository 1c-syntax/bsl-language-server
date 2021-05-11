package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import org.apache.commons.lang3.NotImplementedException;

public class StrictEqualityComparer implements NodeEqualityComparer {

  @Override
  public boolean equal(BslExpression first, BslExpression second) {
    if(first == second)
      return true;

    if(first.getClass() != second.getClass() || first.getNodeType() != second.getNodeType())
      return false;

    switch (first.getNodeType()){
      case LITERAL:
        return literalsEqual((TerminalSymbolNode)first, (TerminalSymbolNode)second);
      case IDENTIFIER:
        return identifiersEqual((TerminalSymbolNode)first, (TerminalSymbolNode)second);
      default:
        throw new NotImplementedException();
    }

  }

  private boolean identifiersEqual(TerminalSymbolNode first, TerminalSymbolNode second) {
    return first.getRepresentingAst().getText().equalsIgnoreCase(second.getRepresentingAst().getText());
  }

  private boolean literalsEqual(TerminalSymbolNode first, TerminalSymbolNode second) {
      throw new NotImplementedException();
  }

}
