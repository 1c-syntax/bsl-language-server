# Space at the beginning of the comment

Between comment symbols "//" and comment text has to be a space.

**Reference**: [Standard: Modules text, Item 7.3](https://its.1c.ru/db/v8std#content:456:hdoc)

Exception from the rule is ***comments-annotations***, comments starting with special symbols sequence.

## Parameters

- `commentsAnnotation` - `String` - Ignore comments starting with defined sub-strings. The list separated with comma. By default: `//@,//(c),//©`.
