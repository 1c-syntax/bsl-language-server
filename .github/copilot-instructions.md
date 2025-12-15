# Copilot Instructions for BSL Language Server

## Project Overview

BSL Language Server is an implementation of the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) for 1C (BSL) - the 1C:Enterprise 8 language and [OneScript](http://oscript.io).

This is a Java-based language server that provides code analysis, diagnostics, code actions, and other language features for 1C development.

**Key Resources:**
- Project site: https://1c-syntax.github.io/bsl-language-server
- Documentation: [docs/index.md](../docs/index.md) (Russian), [docs/en/index.md](../docs/en/index.md) (English)
- Contributor's Guide: [docs/en/contributing/index.md](../docs/en/contributing/index.md)

## Technology Stack

- **Language:** Java 17
- **Build System:** Gradle with Kotlin DSL
- **Frameworks:** Spring Boot
- **Key Technologies:**
  - ANTLR for parsing
  - Lombok for reducing boilerplate
  - JUnit for testing
  - Language Server Protocol implementation

## Environment Setup

### Prerequisites
- Java Development Kit 17
- Gradle (wrapper included in the repository)

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Running Quality Checks
```bash
./gradlew check
```

## Development Workflow

### For Diagnostics Development

When developing new diagnostics or modifying existing ones:

1. **Study the Documentation:**
   - Review [Diagnostic Development Workflow](../docs/en/contributing/DiagnosticDevWorkFlow.md)
   - Check [Diagnostic Structure](../docs/en/contributing/DiagnosticStructure.md)
   - Understand [Diagnostic Types and Severity](../docs/en/contributing/DiagnosticTypeAndSeverity.md)

2. **Implementation Guidelines:**
   - Each diagnostic should have:
     - Java implementation class
     - Resource bundle for localized messages
     - Unit tests
     - Documentation
   - Follow naming conventions from existing diagnostics
   - Use appropriate diagnostic tags and severity levels

3. **Testing:**
   - Write comprehensive unit tests for each diagnostic
   - Include test cases for edge cases
   - Follow existing test patterns in the codebase

4. **Documentation:**
   - Update diagnostic documentation in both Russian and English
   - Include examples of problematic code and fixes
   - Document configuration parameters if applicable

### For Core Functionality Development

1. **Code Structure:**
   - Main source: `src/main/java`
   - Tests: `src/test/java`
   - Resources: `src/main/resources`, `src/test/resources`

2. **Testing:**
   - Always run tests before submitting changes: `./gradlew test`
   - Maintain or improve test coverage
   - Use appropriate test frameworks (JUnit, AssertJ, Mockito)

## Code Style and Conventions

### General Guidelines
- **Follow the [Style Guide](../docs/en/contributing/StyleGuide.md)**
- Use Lombok annotations to reduce boilerplate code
- Enable annotation processing in your IDE
- Use EditorConfig for consistent formatting (`.editorconfig` is provided)

### Import Management
- Optimize imports before committing
- Use automatic import optimization provided by the IDE
- DO NOT optimize imports across the entire project unless specifically working on that task

### Naming Conventions
- Follow Java naming conventions
- Use meaningful, descriptive names
- Keep class and method names concise but clear

### Documentation
- Write JavaDoc for public APIs
- Include comments for complex logic
- Keep documentation up to date with code changes

## Important Files and Directories

- `build.gradle.kts` - Main build configuration
- `src/main/java/com/github/_1c_syntax/bsl/languageserver/` - Main source code
  - `diagnostics/` - Diagnostic implementations
  - `context/` - Language context and parsing
  - `providers/` - LSP providers
- `src/main/resources/` - Resources, including diagnostic descriptions
- `src/test/java/` - Unit tests
- `docs/` - Documentation in Russian
- `docs/en/` - Documentation in English

## Continuous Integration

The project uses GitHub Actions for CI/CD:
- **Quality Assurance:** `qa.yml` - Runs tests and code quality checks
- **Pre-QA:** `pre-qa.yml` - Quick validation
- **CodeQL:** `codeql-analysis.yml` - Security analysis
- **Releases:** `release.yml` - Automated release process

Always ensure your changes pass all CI checks before finalizing.

## Bilingual Requirements

This project maintains documentation in **both Russian and English**. When making changes:
- Update documentation in both languages
- Maintain consistency between translations
- Resource bundles should have both Russian and English versions

## Testing Strategy

### Unit Tests
- Test each diagnostic with multiple scenarios
- Include positive and negative test cases
- Test configuration parameters if applicable
- Use the existing test infrastructure and patterns

### Integration Tests
- Some diagnostics may require integration testing
- Follow patterns from existing integration tests

### Running Specific Tests
```bash
# Run all tests
./gradlew test

# Run tests for a specific class
./gradlew test --tests "ClassName"

# Run tests matching a pattern
./gradlew test --tests "*DiagnosticTest"
```

## Common Tasks

### Adding a New Diagnostic
1. Create diagnostic class in `src/main/java/.../diagnostics/`
2. Create resource bundles in `src/main/resources/.../diagnostics/`
3. Add unit tests in `src/test/java/.../diagnostics/`
4. Add documentation in `docs/diagnostics/` and `docs/en/diagnostics/`
5. Register the diagnostic if needed
6. Run tests and ensure they pass

### Fixing a Bug
1. Write a failing test that reproduces the bug
2. Fix the bug with minimal changes
3. Ensure all tests pass
4. Update documentation if the behavior changes

### Updating Dependencies
- Dependencies are managed in `build.gradle.kts`
- Always test thoroughly after dependency updates
- Check for breaking changes in release notes

## Security Considerations

- Never commit sensitive data or credentials
- Be cautious with external dependencies
- Review security alerts from CodeQL and Dependabot
- Follow secure coding practices

## Additional Resources

- [FAQ](../docs/en/faq.md)
- [System Requirements](../docs/en/systemRequirements.md)
- [Events API](../docs/en/contributing/EventsApi.md)
- [Performance Measurement](../docs/en/contributing/Measures.md)
- [Diagnostic Tags](../docs/en/contributing/DiagnosticTag.md)
- [Adding Parameters to Diagnostics](../docs/en/contributing/DiagnostcAddSettings.md)
- [Adding QuickFixes](../docs/en/contributing/DiagnosticQuickFix.md)

## Notes for AI Coding Agents

- This is a mature, production-quality project with high standards
- Maintain backward compatibility unless explicitly breaking changes are planned
- Follow the existing patterns and conventions strictly
- When in doubt, refer to similar existing code for guidance
- Always run the full test suite before considering a task complete
- Respect the bilingual nature of documentation and resources
