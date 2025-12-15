# Adding parameters for diagnostic

Some diagnostics may have parameters that allow end users to customize the algorithm.  
For example: the parameter can be the maximum string length for diagnostics for long strings or a list of numeric constants that are not magic numbers.

## Diagnostic parameter implementation

To implement the diagnostic parameter, you need to make changes to the diagnostic files.

### Diagnostic class change

Add a constant field to the diagnostic class, in which the default value of the parameter will be saved. For example `private static final String DEFAULT_COMMENTS_ANNOTATION = "//@";`.

It is necessary to add a private field in which the parameter value will be stored. The field needs to be annotated `@DiagnosticParameter` In the annotation, you must specify the type of the parameter and the default value (the value is always a string type).  
For example

```java
@DiagnosticParameter(
    type = String.class,
    defaultValue = "" + DEFAULT_COMMENTS_ANNOTATION
  )
  private String commentsAnnotation = DEFAULT_COMMENTS_ANNOTATION;

```

If the parameter is primitive or of type String and it is set by a simple setter, then that's it.  
If the parameter is "hard", for example, a pattern string for a regular expression that must be evaluated before setting, then you need to implement a method for setting parameter values `configure`.

For example, there are two parameters:

- `commentAsCode` - recognize comments as code, type boolean
- `excludeMethods` - мmethods that do not need to be checked, type ArrayList

Then the method of setting the parameter values will look:

```java
 @Override
  public void configure(Map<String, Object> configuration) {
    // to set "simple properties" including "commentAsCode"
    super.configure(configuration);

    // setting the "hard" property "excludeMethods"
    String excludeMethodsString =
      (String) configuration.getOrDefault("excludeMethods", EXCLUDE_METHODS_DEFAULT);
    this.excludeMethods = new ArrayList<>(Arrays.asList(excludeMethodsString.split(",")));
  }

```

### Test change

It is necessary to add a test to change the diagnostic settings.  
The test is added to the diagnostic test class _(a separate method for each combination of diagnostic setting options)_. At the beginning of the test, you need to set the value of the diagnostic parameter, the subsequent steps are similar to the general rules for writing tests.  
To set a diagnostic parameter from a test, you need to get the default diagnostic configuration using the method `getDefaultConfiguration()` from the metadata of the current diagnostic `diagnosticInstance.getInfo()`. The next step is to change the parameter value by adding to the configuration collection, and then reconfigure using the method `configure`.  
Example

```java
// get current configuration
Map<String, Object> configuration = diagnosticInstance.getInfo().getDefaultConfiguration();

// setting the new value
configuration.put("commentsAnnotation", "//(с)");

// reconfiguring
diagnosticInstance.configure(configuration);

```

### Adding a Parameter Description

For correct operation, it is necessary to add a parameter description for each language in the diagnostic resource files.  
Diagnostic resources are located in files`resources/com/github/_1c_syntax/bsl/languageserver/diagnostics/<DIAGNOSTIC_KEY>_en.properties` and `resources/com/github/_1c_syntax/bsl/languageserver/diagnostics/<DIAGNOSTIC_KEY>_ru.properties`.  
In each file, add a new line with the parameter name and description

```ini
commentsAnnotation=Skip annotation comments starting with the specified substrings. A comma separated list. For example: //@,//(c)
```

### Documentation change

Adding information about diagnostic parameters to the documentation occurs automatically during build.
