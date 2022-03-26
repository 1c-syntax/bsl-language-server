# Events subsystem

Events can occur in an application that require the response of loosely coupled objects.

For example: BSL Language Server configuration file (`.bsl-language-server.json`) has a `traceLog` field. In it, you can specify the path to the file to display a detailed log of the interaction between the server and the client. When a configuration is changed, a "server configuration changed" event is generated, and all components can reread it and reconfigure themselves if this event is important to them. In this example, the log output component changes the path to the output file.

The subsystem consists of three components:

* events;
* post events;
* event subscription.

The main difference from Spring Events is the transfer of event publishing from application code to an isolated layer using aspect-oriented programming.

> A summary of Spring Events in the article https://www.baeldung.com/spring-events.

## Events

All events are children of [`ApplicationEvent`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEvent.html). The event class must be placed in the `events` subpackage of the package whose object can generate this event.

For example, change event `com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration`  is located in the package `com.github._1c_syntax.bsl.languageserver.configuration.events` and and has the name `LanguageServerConfigurationChangedEvent`.

In the event class it is recommended:

* declare a constructor that takes the "source" of the event (the object on which the event fired) and calls the `super` constructor;
* override the `getSource` method, returning `source` cast to the source type.

```java
/**
 * Configuration change event.
 * <p>
 * Contains a link to the configuration as an event source.
 */
public class LanguageServerConfigurationChangedEvent extends ApplicationEvent {
  public LanguageServerConfigurationChangedEvent(LanguageServerConfiguration configuration) {
    super(configuration);
  }

  @Override
  public LanguageServerConfiguration getSource() {
    return (LanguageServerConfiguration) super.getSource();
  }
}
```

## Events publication

The `EventPublisherAspect` aspect of the `com.github._1c_syntax.bsl.languageserver.aop` package is used to publish events

> Brief information about aspect-oriented programming you can find in page https://www.baeldung.com/aspectj.

To intercept events in an aspect, advice can be declared to intercept method calls and/or accesses to object properties. 
An event object must be created in the advice body and published via [`ApplicationEventPublisher`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEventPublisher.html).

> To form a pointcut expression, you need to use templates in the `Pointcuts` class of the `com.github._1c_syntax.bsl.languageserver.aop` package

An example of intercepting the server configuration update event:

```java
@Aspect
public class EventPublisherAspect {
  @AfterReturning("Pointcuts.isLanguageServerConfiguration() && (Pointcuts.isResetCall() || Pointcuts.isUpdateCall())")
  public void languageServerConfigurationUpdated(JoinPoint joinPoint) {
    var configuration = (LanguageServerConfiguration) joinPoint.getThis();
    applicationEventPublisher.publishEvent(new LanguageServerConfigurationChangedEvent(configuration));
  }
}
```

## Events subscriptions

To subscribe to an event, a component can implement the [`ApplicationListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html) interface or declare a public method annotated with [`@EventListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/EventListener.html) and taking a specific event class as a parameter.

Below is an example of handling the `LanguageServerConfigurationChangedEvent` event via annotations:

```java
@Component
public class FileAwarePrintWriter {
  /**
   * Обработчик события {@link LanguageServerConfigurationChangedEvent}.
   *
   * @param event Событие
   */
  @EventListener
  public void handleEvent(LanguageServerConfigurationChangedEvent event) {
    setFile(event.getSource().getTraceLog());
  }
}
```
