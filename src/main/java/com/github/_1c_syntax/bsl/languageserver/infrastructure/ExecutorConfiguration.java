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

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor textDocumentServiceExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "text-document-service-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor workspaceServiceExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "workspace-service-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor sentryExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "sentry-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor populateContextExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "populate-context-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor computeConfigurationExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "compute-configuration-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor diagnosticComputerExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "diagnostic-computer-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor analyzeOnStartExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "analyze-on-start-");
  }

  @Bean(destroyMethod = "shutdown")
  public ThreadPoolTaskExecutor semanticTokensExecutor(TaskDecorator compositeTaskDecorator) {
    return getThreadPoolTaskExecutor(compositeTaskDecorator, "semantic-tokens-");
  }

  private static ThreadPoolTaskExecutor getThreadPoolTaskExecutor(
    TaskDecorator compositeTaskDecorator,
    String threadNamePrefix
  ) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//    executor.setCorePoolSize(10);
//    executor.setMaxPoolSize(20);
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix(threadNamePrefix);
    executor.setTaskDecorator(compositeTaskDecorator);
    executor.initialize();
    return executor;
  }
}