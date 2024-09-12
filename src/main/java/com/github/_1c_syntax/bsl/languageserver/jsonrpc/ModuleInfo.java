package com.github._1c_syntax.bsl.languageserver.jsonrpc;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * DTO Информации о модуле
 * <br>
 * См. {@link com.github._1c_syntax.bsl.languageserver.BSLTextDocumentService#moduleInfo(ModuleUri)}
 */
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class ModuleInfo {
  /**
   * Идентификатор владельца модуля.
   */
  private String ownerId;
  /**
   * Внутренний идентификатор типа модуля.
   */
  private String propertyId;
}
