# AOT Compilation and Native Images (GraalVM)

BSL Language Server supports AOT (Ahead-of-Time) compilation and native image creation using GraalVM.

## AOT Compilation

AOT compilation allows pre-optimizing the application during build time, which speeds up startup when using a regular JVM.

### AOT Process

AOT processing is automatically performed when building the project:

```bash
./gradlew build
```

Spring AOT generates optimized code in the `build/generated/aotSources/` directory.

### Running with AOT on JVM

To use AOT-optimized code when running on a regular JVM:

```bash
java -Dspring.aot.enabled=true -jar build/libs/bsl-language-server-exec.jar
```

## Native Compilation with GraalVM

Native compilation creates a standalone executable that:
- Starts several times faster
- Uses less memory
- Does not require an installed JVM

### Requirements

For native compilation, you need:

1. **GraalVM JDK 17 or higher**
   ```bash
   # Installation via SDKMAN
   sdk install java 17.0.9-graal
   sdk use java 17.0.9-graal
   ```

2. **Native Image tool**
   ```bash
   # Install native-image (if not installed)
   gu install native-image
   ```

3. **Required system tools** (for Linux):
   ```bash
   # Ubuntu/Debian
   sudo apt-get install build-essential libz-dev zlib1g-dev
   
   # Fedora/CentOS/RHEL
   sudo dnf install gcc glibc-devel zlib-devel
   ```

### Building a Native Image

```bash
./gradlew nativeCompile
```

The result will be located at `build/native/nativeCompile/bsl-language-server`.

### Running the Native Image

```bash
./build/native/nativeCompile/bsl-language-server
```

Or with parameters:

```bash
./build/native/nativeCompile/bsl-language-server --analyze --src ./src
```

## Testing

### Testing with AOT

Tests automatically use AOT when running:

```bash
./gradlew test
```

### Native Testing

To compile and run tests in native mode:

```bash
./gradlew nativeTest
```

## Features and Limitations

### AspectJ

The project uses AspectJ with compile-time weaving. For correct AOT operation, the `aspectjweaver` dependency has been added.

### Reflection and Dynamic Proxies

Spring AOT automatically generates metadata for reflection and dynamic proxies. If issues arise, hints can be added manually via `RuntimeHintsRegistrar`.

### Metadata Repository

The project uses the GraalVM Reachability Metadata Repository for automatic configuration of popular libraries:

```kotlin
graalvmNative {
    metadataRepository {
        enabled.set(true)
    }
}
```

## Build Configuration

Native compilation parameters are configured in `build.gradle.kts`:

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

## Useful Links

- [Spring Boot Native Documentation](https://docs.spring.io/spring-boot/how-to/native-image/developing-your-first-application.html)
- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring AOT Processing](https://docs.spring.io/spring-boot/gradle-plugin/aot.html)
