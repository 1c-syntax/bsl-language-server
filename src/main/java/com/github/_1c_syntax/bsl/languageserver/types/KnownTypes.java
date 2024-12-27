package com.github._1c_syntax.bsl.languageserver.types;

import com.github._1c_syntax.bsl.languageserver.context.events.DocumentContextContentChangedEvent;
import com.github._1c_syntax.bsl.languageserver.context.symbol.Symbol;
import org.apache.commons.io.FilenameUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class KnownTypes {

  private final Map<Type, Symbol> knownTypes = new ConcurrentHashMap<>();

  public void addType(Type type, Symbol symbol) {
    knownTypes.put(type, symbol);
  }

  public Optional<Symbol> getSymbolByType(Type type) {
    return Optional.ofNullable(knownTypes.get(type));
  }

  public void clear() {
    knownTypes.clear();
  }

  @EventListener
  public void handleEvent(DocumentContextContentChangedEvent event) {
    var documentContext = event.getSource();
    // TODO: this logic should be moved to somewhere else. It will break for BSL files.
    var typeName = FilenameUtils.getBaseName(documentContext.getUri().getPath());
    var module = documentContext.getSymbolTree().getModule();

    addType(new Type(typeName), module);
  }
}
