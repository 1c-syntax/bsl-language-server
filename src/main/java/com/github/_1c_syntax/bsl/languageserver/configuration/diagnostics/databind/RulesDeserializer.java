package com.github._1c_syntax.bsl.languageserver.configuration.diagnostics.databind;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Slf4j
public class RulesDeserializer extends JsonDeserializer<Map<String, Either<Boolean, Map<String, Object>>>> {
  @Override
  public Map<String, Either<Boolean, Map<String, Object>>> deserialize(
    JsonParser p,
    DeserializationContext context
  ) throws IOException {

    JsonNode diagnostics = p.getCodec().readTree(p);

    if (diagnostics == null) {
      return Collections.emptyMap();
    }

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Either<Boolean, Map<String, Object>>> diagnosticsMap = new HashMap<>();

    Iterator<Map.Entry<String, JsonNode>> diagnosticsNodes = diagnostics.fields();
    diagnosticsNodes.forEachRemaining((Map.Entry<String, JsonNode> entry) -> {
      JsonNode diagnosticConfig = entry.getValue();
      if (diagnosticConfig.isBoolean()) {
        diagnosticsMap.put(entry.getKey(), Either.forLeft(diagnosticConfig.asBoolean()));
      } else {
        Map<String, Object> diagnosticConfiguration = getDiagnosticConfiguration(mapper, entry.getValue());
        diagnosticsMap.put(entry.getKey(), Either.forRight(diagnosticConfiguration));
      }
    });

    return diagnosticsMap;
  }

  private static Map<String, Object> getDiagnosticConfiguration(
    ObjectMapper mapper,
    JsonNode diagnosticConfig
  ) {
    Map<String, Object> diagnosticConfiguration;
    try {
      JavaType type = mapper.getTypeFactory().constructType(new TypeReference<Map<String, Object>>() {});
      diagnosticConfiguration = mapper.readValue(mapper.treeAsTokens(diagnosticConfig), type);
    } catch (IOException e) {
      LOGGER.error("Can't deserialize diagnostic configuration", e);
      return null;
    }
    return diagnosticConfiguration;
  }

}
