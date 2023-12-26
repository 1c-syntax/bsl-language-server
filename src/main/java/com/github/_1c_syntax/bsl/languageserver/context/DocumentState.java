package com.github._1c_syntax.bsl.languageserver.context;

/**
 * Состояние документа в контексте.
 */
public enum DocumentState {
  /**
   * В документе отсутствует контент или он был очищен.
   */
  WITHOUT_CONTENT,
  /**
   * В документе присутствует контент.
   */
  WITH_CONTENT
}
