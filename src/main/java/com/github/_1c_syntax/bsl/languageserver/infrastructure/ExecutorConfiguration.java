package com.github._1c_syntax.bsl.languageserver.infrastructure;

import io.sentry.spring.jakarta.SentryTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ExecutorConfiguration {

  @Bean
  public SentryTaskDecorator sentryTaskDecorator() {
    return new SentryTaskDecorator();
  }

  @Bean
  public ContextPropagatingTaskDecorator contextPropagatingTaskDecorator() {
    return new ContextPropagatingTaskDecorator();
  }

  @Bean
  public TaskDecorator compositeTaskDecorator(
    ContextPropagatingTaskDecorator contextPropagatingTaskDecorator,
    SentryTaskDecorator sentryTaskDecorator
  ) {
    return runnable -> sentryTaskDecorator.decorate(contextPropagatingTaskDecorator.decorate(runnable));
  }

  @Bean("textDocumentServiceExecutor")
  public ThreadPoolTaskExecutor myExecutor(TaskDecorator compositeTaskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//    executor.setCorePoolSize(10);
//    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("text-document-service-");
    executor.setTaskDecorator(compositeTaskDecorator);
    executor.initialize();
    return executor;
  }
}
