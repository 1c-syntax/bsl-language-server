package com.github._1c_syntax.bsl.languageserver.scope;

import lombok.Getter;

public class IScopeOwner {

  public static IScopeOwner create(Object owner) {
    return new IScopeOwner(owner);
  }

  private IScopeOwner(Object owner) {
    this.owner = owner;
  }

  @Getter
  private final Object owner;
}
