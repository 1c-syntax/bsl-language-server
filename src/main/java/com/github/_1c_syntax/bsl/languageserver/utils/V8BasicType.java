package com.github._1c_syntax.bsl.languageserver.utils;

import java.util.Optional;


public enum V8BasicType implements V8Type {
  STRING_TYPE("Строка"), DATE_TYPE("Дата"), NUMBER_TYPE("Число"),
  BOOLEAN_TYPE("Булево"), NULL_TYPE("NULL"), UNDEFINED_TYPE("Неопределено");

  public String getName() {
    return name;
  }

  private String name;

  V8BasicType(String name) {
    this.name = name;
  }

  @Override
  public String presentation() {
    return name;
  }

  @Override
  public String toString() {
    return presentation();
  }

}
