# Method size (MethodSize)

<!-- Блоки выше заполняются автоматически, не трогать -->
## Description

There are cumbersome methods (procedures and functions) which mskes it impossible to work effectively precisely because of their huge size.  
A large method often arises when a developer adds new functionality to a method. “Why should I put the parameter check in a separate method, if I can write it here?”, “Why do I need to create a separate method for the search of maximum element in the array, let’s leave it here. So the code is clearer”, and other misconceptions.

There are two rules for refactoring a large method:

- If when writing a method you want to add a comment to the code, you must put this functionality in a separate method
- If the method takes more than 50-100 lines of code, you should determine the tasks and subtasks that it performs and try to put the subtasks in a separate method

## Sources

- [Software Architecture Refactoring: Layering](http://citforum.ru/SE/project/refactor/)
- [Martin Fowler: Refactoring](https://www.refactoring.com/)
- [Refactoring and opt-out tools (RU)](https://v8.1c.ru/o7/201312ref/index.htm)
- [Refactoring tools in 1C (RU)](https://www.koderline.ru/expert/programming/article-vspomogatelnye-funktsii-v-1s/#anchor6)
