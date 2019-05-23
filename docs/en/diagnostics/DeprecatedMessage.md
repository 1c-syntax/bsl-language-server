# Restriction on using of the obsolete method "Message"

To display messages to the user in all cases, you should use the MessageUser object, even when the message is not “bound” to some form control. The Message method should not be used.

*When used the Standard Subsystems Library it is recommended use procedure MessageUser from common module CommonPurposeClientServer, which use object UserMessage.*

Refference: [Standard: Limit use of the obsolete method Message](https://its.1c.ru/db/v8std#content:418:hdoc)
