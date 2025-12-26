# Diagnostic development workflow

1. Go to the section [issues](https://github.com/1c-syntax/bsl-language-server/issues) and select your task from the list. It is necessary to choose tasks that do not have an executor, i.e. `Assignees` is not specified (the block is on the right, almost under the heading).
   1. If there is no task for diagnostics, then you need to create it by clicking the `New issue` button
2. Write in the comments to the task to the maintainers that you want to take the task to work. After your nickname appears in the task in the `Assignees` block, you can proceed.
3. You need to create a fork of the repository in `GitHub`, clone the repository to your computer and create a new feature branch (development by git flow).
   1. If the fork has already been created earlier, then you need to update the `develop` branch from the primary repository. The easiest way to do this is as follows
      1. The local repository has two remote repository addresses: yours and primary
      2. Get updates from both repositories `git fetch --progress "--all" --prune`
      3. In the local repository, switch to the branch `develop`
      4. Reset the branch state to the state of the primary repository (`git reset --hard`)
      5. Pushin the branch to your remote repository
4. To create all the necessary files in the right places, you need to run the command `gradlew newDiagnostic --key="KeyDiagnostic"`, instead `KeyDiagnostic` you must specify the key of your diagnostic. Details in help `gradlew -q help --task newDiagnostic`. Parameters:

   * `--key` - Diagnostic key
   * `--nameRu` - Russian description
   * `--nameEn` - English description

At startup, a list of available diagnostic tags is displayed. You must enter 1-3 tags from the space separated ones.

5. To develop
6. After completion of the development of diagnostics: it is necessary to check the changes (after testing), and also perform a number of service tasks.  
   To simplify, a special command has been created that can be run in the console `gradlew precommit` or from the Gradle taskbar `precommit`. Task includes subtasks

- check - checking and testing the entire project
- licenseFormat - installation of a license block in java source files
- updateJsonSchema - json schema update

7. If everything is done correctly, you need to commit the changes and push to your remote repository.
8. You need to create `Pull request` from your feature branch to the `develop` branch of the primary repository and fill in the information in the description.
9. Before closing `Pull request`, the maintainers will conduct a Code review. Correction of errors must be done in the same feature branch, GitHub will automatically add changes to the created `Pull request`.
10. Closing `Pull request` confirms the completion of the task.
