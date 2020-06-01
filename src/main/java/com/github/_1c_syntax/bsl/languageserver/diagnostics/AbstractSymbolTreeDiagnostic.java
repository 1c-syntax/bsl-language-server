package com.github._1c_syntax.bsl.languageserver.diagnostics;

import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.RegionSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SymbolTreeVisitor;
import com.github._1c_syntax.bsl.languageserver.context.symbol.VariableSymbol;
import com.github._1c_syntax.bsl.languageserver.diagnostics.metadata.DiagnosticInfo;

import java.util.List;

public abstract class AbstractSymbolTreeDiagnostic extends AbstractDiagnostic implements SymbolTreeVisitor {
  public AbstractSymbolTreeDiagnostic(DiagnosticInfo info) {
    super(info);
  }

  @Override
  protected void check() {
    visitChildren(documentContext.getSymbolTree().getChildren());
  }

  void visitChildren(List<Symbol> children) {
    children.forEach(this::visit);
  }

  void visit(Symbol symbol){
    symbol.accept(this);
  }

  @Override
  public void visitRegion(RegionSymbol region) {
    visitChildren(region.getChildren());
  }

  @Override
  public void visitMethod(MethodSymbol method) {
    visitChildren(method.getChildren());
  }

  @Override
  public void visitVariable(VariableSymbol variable) {
    visitChildren(variable.getChildren());
  }
}
