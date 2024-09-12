package com.github._1c_syntax.bsl.languageserver.jsonrpc;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString; /**
 * DTO uri модуля
 * <br>
 * См. {@link com.github._1c_syntax.bsl.languageserver.BSLTextDocumentService#moduleUri(ModuleInfo)}
 */
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class ModuleUri {
  /**
   * URI модуля.
   */
  private String uri;
}
