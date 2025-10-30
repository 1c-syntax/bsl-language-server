# Компиляция AOT и нативные образы (GraalVM)

BSL Language Server поддерживает AOT (Ahead-of-Time) компиляцию и создание нативных образов с помощью GraalVM.

## AOT-компиляция

AOT-компиляция позволяет предварительно оптимизировать приложение во время сборки, что ускоряет запуск при использовании обычной JVM.

### Процесс AOT

AOT-обработка автоматически выполняется при сборке проекта:

```bash
./gradlew build
```

При этом Spring AOT генерирует оптимизированный код в директории `build/generated/aotSources/`.

### Запуск с AOT на JVM

Для использования AOT-оптимизированного кода при запуске на обычной JVM:

```bash
java -Dspring.aot.enabled=true -jar build/libs/bsl-language-server-exec.jar
```

## Нативная компиляция с GraalVM

Нативная компиляция создает самостоятельный исполняемый файл, который:
- Запускается в несколько раз быстрее
- Использует меньше памяти
- Не требует установленной JVM

### Требования

Для нативной компиляции необходимо:

1. **GraalVM JDK 17 или выше**
   ```bash
   # Установка через SDKMAN
   sdk install java 17.0.9-graal
   sdk use java 17.0.9-graal
   ```

2. **Native Image tool**
   ```bash
   # Установка native-image (если не установлен)
   gu install native-image
   ```

3. **Необходимые системные инструменты** (для Linux):
   ```bash
   # Ubuntu/Debian
   sudo apt-get install build-essential libz-dev zlib1g-dev
   
   # Fedora/CentOS/RHEL
   sudo dnf install gcc glibc-devel zlib-devel
   ```

### Сборка нативного образа

```bash
./gradlew nativeCompile
```

Результат будет находиться в `build/native/nativeCompile/bsl-language-server`.

### Запуск нативного образа

```bash
./build/native/nativeCompile/bsl-language-server
```

Или с параметрами:

```bash
./build/native/nativeCompile/bsl-language-server --analyze --src ./src
```

## Тестирование

### Тестирование с AOT

Тесты автоматически используют AOT при запуске:

```bash
./gradlew test
```

### Нативное тестирование

Для компиляции и запуска тестов в нативном режиме:

```bash
./gradlew nativeTest
```

## Особенности и ограничения

### AspectJ

Проект использует AspectJ с compile-time weaving. Для корректной работы AOT добавлена зависимость `aspectjweaver`.

### Reflection и Dynamic Proxies

Spring AOT автоматически генерирует метаданные для reflection и dynamic proxies. Если возникают проблемы, можно добавить hints вручную через `RuntimeHintsRegistrar`.

### Метаданные репозитория

Проект использует GraalVM Reachability Metadata Repository для автоматической конфигурации популярных библиотек:

```kotlin
graalvmNative {
    metadataRepository {
        enabled.set(true)
    }
}
```

## Конфигурация сборки

Параметры нативной компиляции настроены в `build.gradle.kts`:

```kotlin
graalvmNative {
    binaries {
        named("main") {
            imageName.set("bsl-language-server")
            mainClass.set("com.github._1c_syntax.bsl.languageserver.BSLLSPLauncher")
            buildArgs.addAll(
                "--verbose",
                "--no-fallback",
                "-H:+ReportExceptionStackTraces"
            )
        }
    }
    metadataRepository {
        enabled.set(true)
    }
}
```

## Полезные ссылки

- [Документация Spring Boot Native](https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html)
- [Документация GraalVM Native Image](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring AOT Processing](https://docs.spring.io/spring-boot/gradle-plugin/aot.html)
