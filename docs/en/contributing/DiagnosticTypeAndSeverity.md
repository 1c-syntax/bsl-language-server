# Diagnostics types and severity

Each diagnostic is of a specific type and has a specific importance.   
In order to select the type and importance for the generated diagnostics, a detailed semantic description is given below.

## Severity

The severity of diagnostics is set in the annotation `@DiagnosticMetadata` by the `severity` parameter and takes values ​​of the `DiagnosticSeverity` type.

Possible use cases (basic cases, unless otherwise stated):

### Blocker (BLOCKER)

Errors that render the application inoperable. Used only for `Errors` and `Vulnerabilities`.

### Critical (CRITICAL)

Incorrectly working key business logic, errors in the security system, problems leading to the temporary inoperability of the application or its components. Used only for `Errors` and `Vulnerabilities`.

### Important (MAJOR)

Some of the core business logic doesn't work correctly, but there are workarounds; poor quality code that leads to poor performance, efficiency, floating bugs. Used only for `Errors` and `Code Defects`.

### Minor (MINOR)

The business logic of the application is not violated, a floating error, low-quality, poorly supported code, bugs in rarely used functionality. Used only for `Errors` and `Code Defects`.

### Information (INFO)

A trivial error not related to the business logic of the application; poorly reproducible problem; unobtrusive, having no effect on the overall quality of the product. Used only for `Code Defects`.

## Diagnostics type

The severity of diagnostics is set in the annotation `@DiagnosticMetadata` by the `type` parameter and takes values ​​of the `DiagnosticType` type.

### Vulnerability (VULNERABILITY)

This type of diagnostics includes security errors. They should always be of severity `Blocker` if there is a known compromise, or `Critical` if there is none or the value of the leak is not high.   
Examples

- the compromise of personal data is a blocking vulnerability, since in addition to violation of the law, the information obtained can be used in various illegal actions.
- compromise of user reporting settings is a critical vulnerability, since can tell an attacker how to collect an important report, but does not give access to fulfill the request.

### Potential vulnerability (SECURITY_HOTSPOT)

Diagnostics of this type highlight security-sensitive code fragments that require additional manual analysis. After the analysis, either the problem that needs to be corrected will be confirmed, or a decision is made that there is no problem. Diagnostics with this type should always have severity `Critical`.

Examples

- Accessing the parameters of the user's computer operating system is not always a vulnerability. For example, the version of the OS architecture can be used to load the correct version of the library included with the application.
- User privilege escalation can occur in a planned manner while the operation is being performed in accordance with the business process, which is not a vulnerability. In other situations, disabling user access control is completely a vulnerability.

### Code defect (CODE_SMELL)

`Code defect` does not lead to errors in the program, but complicates further development, the ability to adapt and extend functionality. Can be of any importance except `Blocker` and `Critical`. Needs fixing as important in the refactoring process.

### Error (ERROR)

This category includes real errors during the user's work. They can be of any importance, except `Info`, while:

- `Blocker` means there is no workaround, urgently needs to be fixed. An example is uncompiled code or calling a method that does not exist.
- `Critical` means there is a known workaround (for example, disabling functionality with an error), but requiring the fastest possible fix.
