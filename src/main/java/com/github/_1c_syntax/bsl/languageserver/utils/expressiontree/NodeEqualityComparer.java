package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

public interface NodeEqualityComparer {
  boolean equal(BslExpression first, BslExpression second);
}
