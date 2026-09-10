```markdown
# flink Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches you the core development patterns and conventions used in the `flink` Java codebase. You'll learn about file naming, import/export styles, commit patterns, and how to write and organize tests. While no specific automation workflows were detected, this guide provides best practices and suggested commands to streamline your development process.

## Coding Conventions

### File Naming
- **Convention:** PascalCase is used for file names.
- **Example:**  
  ```java
  public class DataStreamProcessor { ... }
  // File: DataStreamProcessor.java
  ```

### Import Style
- **Convention:** Use relative imports within the project.
- **Example:**  
  ```java
  import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
  ```

### Export Style
- **Convention:** Use named exports (public classes and methods).
- **Example:**  
  ```java
  public class JobManager { ... }
  ```

### Commit Patterns
- **Type:** ticket-reference (commits reference tickets/issues)
- **Prefix:** Commits often start with a ticket reference (e.g., `[FLINK-12345]`)
- **Average Length:** 59 characters
- **Example:**  
  ```
  [FLINK-12345] Fix null pointer exception in DataStreamProcessor
  ```

## Workflows

### Creating a New Feature or Bugfix
**Trigger:** When you need to add a new feature or fix a bug.
**Command:** `/new-feature`

1. Create a new branch named after the ticket (e.g., `FLINK-12345-add-feature`).
2. Implement your changes following the coding conventions.
3. Write or update tests in corresponding `*.test.*` files.
4. Commit your changes with a ticket reference in the message.
5. Push your branch and open a pull request.

### Reviewing and Merging Changes
**Trigger:** When reviewing or merging code.
**Command:** `/review`

1. Check that file names use PascalCase.
2. Ensure imports are relative and only necessary dependencies are included.
3. Verify that exports are named (public classes/methods).
4. Confirm that tests exist for new or changed code.
5. Ensure commit messages reference the relevant ticket.

## Testing Patterns

- **Framework:** Unknown (custom or standard Java testing frameworks may be used)
- **File Pattern:** Test files follow the `*.test.*` naming convention.
- **Example:**  
  ```
  DataStreamProcessor.test.java
  ```
- **Best Practice:** Place tests alongside or in a dedicated test directory, and ensure coverage for all new features and bugfixes.

## Commands
| Command        | Purpose                                         |
|----------------|-------------------------------------------------|
| /new-feature   | Start a new feature or bugfix workflow          |
| /review        | Review and merge changes according to conventions|
```
