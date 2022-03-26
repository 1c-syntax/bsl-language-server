# Events subsystem

Events can occur in an application that require the response of loosely coupled objects.

For example: BSL Language Server configuration file (`.bsl-language-server.json`) has a `traceLog` field. In it, you can specify the path to the file to display a detailed log of the interaction between the server and the client. When a configuration is changed, a "server configuration changed" event is generated, and all components can reread it and reconfigure themselves if this event is important to them. In this example, the log output component changes the path to the output file.

The subsystem consists of three components:

* events;
* post events;
* event subscription.

Ключевым отличием от обычной работы со Spring Events является вынос публикации события из прикладного кода в изолированный слой с применением аспектно-ориентированного программирования.

> A summary of Spring Events in the article https://www.baeldung.com/spring-events.

## Events

All events are children of [`ApplicationEvent`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEvent.html). The event class must be placed in the `events` subpackage of the package whose object can generate this event.

For example, change event `com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration`  is located in the package `com.github._1c_syntax.bsl.languageserver.configuration.events` and and has the name `LanguageServerConfigurationChangedEvent`.

In the event class it is recommended:

* объявлять конструктор, принимающий в себя "источник" события - объект, на котором сработало данное событие, и вызывающий `super`-конструктор;
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

Для публикации событий используется аспект `EventPublisherAspect` пакета `com.github._1c_syntax.bsl.languageserver.aop`

> Brief information about aspect-oriented programming you can find in page https://www.baeldung.com/aspectj.

Для перехвата событий в аспекте может быть объявлен advice с перехватом вызовов методов и/или обращений к свойствам объекта. В теле advice должен быть создан объект события и опубликован через [`ApplicationEventPublisher`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEventPublisher.html).

> Для формирования pointcut-выражения рекомендуется использовать заготовки методов в классе `Pointcuts`, расположенном в пакете `com.github._1c_syntax.bsl.languageserver.aop`

Пример перехвата события, срабатывающего на обновление конфигурации сервера, можно посмотреть ниже:

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

Для подписи на событие компонент может либо реализовать интерфейс [`ApplicationListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html) либо объявить публичный метод, помеченный аннотацией [`@EventListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/EventListener.html), принимающий в качестве параметра конкретный класс события.

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
