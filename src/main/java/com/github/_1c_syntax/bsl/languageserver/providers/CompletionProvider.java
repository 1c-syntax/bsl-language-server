package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.symbol.SourceDefinedSymbol;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import com.github._1c_syntax.bsl.languageserver.types.KnownTypes;
import com.github._1c_syntax.bsl.languageserver.types.TypeResolver;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLLexer;
import lombok.RequiredArgsConstructor;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CompletionProvider {

  private final TypeResolver typeResolver;
  private final KnownTypes knownTypes;

  public Either<List<CompletionItem>, CompletionList> getCompletions(DocumentContext documentContext, CompletionParams params) {

    var completionList = new CompletionList();

    var position = params.getPosition();
    var terminalNode = Trees.findTerminalNodeContainsPosition(documentContext.getAst(), position).orElseThrow();

    if (terminalNode.getSymbol().getType() != BSLLexer.DOT) {
      return Either.forRight(completionList);
    }

    var previousToken = Trees.getPreviousTokenFromDefaultChannel(documentContext.getTokens(), terminalNode.getSymbol().getTokenIndex() - 1);
    var completionItems = previousToken
      .map(Ranges::create)
      .map(Range::getStart)
      .map(previousTokenPosition -> typeResolver.findTypes(documentContext.getUri(), previousTokenPosition))
      .stream()
      .flatMap(Collection::stream)
      .map(knownTypes::getSymbolByType)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .flatMap(symbol -> getChildren(symbol).stream())
      .map(symbol -> {
        var completionItem = new CompletionItem();
        completionItem.setLabel(symbol.getName());
        completionItem.setKind(getCompletionItemKind(symbol.getSymbolKind()));

        return completionItem;
      })
      .toList();

    completionList.setItems(completionItems);

    return Either.forRight(completionList);
  }

  private CompletionItemKind getCompletionItemKind(SymbolKind symbolKind) {
    return switch (symbolKind) {
      case Class -> CompletionItemKind.Class;
      case Method -> CompletionItemKind.Method;
      case Variable -> CompletionItemKind.Variable;
      case Module -> CompletionItemKind.Module;
      default -> CompletionItemKind.Text;
    };
  }

  private List<? extends Symbol> getChildren(Symbol symbol) {
    if (!(symbol instanceof SourceDefinedSymbol sourceDefinedSymbol)) {
      return Collections.emptyList();
    }

    return sourceDefinedSymbol.getChildren();
  }
}
