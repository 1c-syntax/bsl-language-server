package com.github._1c_syntax.bsl.languageserver.util;

import org.springframework.beans.BeansException;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

@SpringBootApplication
public class TestApplicationContext implements ApplicationContextAware {
  private static ApplicationContext CONTEXT;

  @Override
  public void setApplicationContext(ApplicationContext context) throws BeansException {
    CONTEXT = context;
  }

  public static <T> T getBean(Class<T> requiredType) {
    return CONTEXT.getBean(requiredType);
  }

}
