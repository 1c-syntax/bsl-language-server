package com.github._1c_syntax.bsl.languageserver.context.symbol;

public interface SymbolTreeVisitor {
  void visitRegion(RegionSymbol region);

  void visitMethod(MethodSymbol method);

  void visitVariable(VariableSymbol variable);
}
