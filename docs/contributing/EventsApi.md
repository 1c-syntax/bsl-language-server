# Подсистема событий

В приложении могут возникать события, требующие реакции от слабосвязанных между собой объектов.

Например: в файле конфигурации BSL Language Server (`.bsl-language-server.json`) есть поле `traceLog`, в котором можно указать путь к файлу для вывода подробного лога взаимодействия между сервером и клиентом. При изменении конфигурации генерируется событие "конфигурация сервера изменена", и все заинтересованные в таком событии компоненты могут перечитать ее и переконфигурировать себя. В частности компонент вывода лога изменяет путь к файлу, в который осуществляется вывод.

Подсистема состоит из трех компонентов:

* события;
* публикация событий;
* подпись на событие.

Ключевым отличием от обычной работы со Spring Events является вынос публикации события из прикладного кода в изолированный слой с применением аспектно-ориентированного программирования.

> Краткую информацию о Spring Events можно почерпнуть в статье https://www.baeldung.com/spring-events.

## События

Все события являются наследником [`ApplicationEvent`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationEvent.html). 
Класс события необходимо размещать в подпакете `events` того пакета, объект которого может сгенерировать это событие.

Например, событие изменения `com.github._1c_syntax.bsl.languageserver.configuration.LanguageServerConfiguration` располагается в пакете `com.github._1c_syntax.bsl.languageserver.configuration.events` и называется `LanguageServerConfigurationChangedEvent`.

В классе события рекомендуется:

* объявлять конструктор, принимающий в себя "источник" события - объект, на котором сработало данное событие, и вызывающий `super`-конструктор;
* переопределять метод `getSource`, возвращая `source`, приведенный к типу источника.

```java
/**
 * Описание события изменения конфигурации.
 * <p>
 * В качестве источника события содержит ссылку на конфигурацию.
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

## Публикация событий

Для публикации событий используется аспект `EventPublisherAspect` пакета `com.github._1c_syntax.bsl.languageserver.aop`

> Краткую информацию об аспектах и аспектно-ориентированном программировании можно почерпнуть в статье https://www.baeldung.com/aspectj.

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

## Подпись на события

Для подписи на событие компонент может либо реализовать интерфейс [`ApplicationListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/ApplicationListener.html) либо объявить публичный метод, помеченный аннотацией [`@EventListener`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/context/event/EventListener.html), принимающий в качестве параметра конкретный класс события.

Ниже представлен пример обработки события `LanguageServerConfigurationChangedEvent` через аннотации:

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
