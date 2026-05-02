# Test Guidelines

## Chapter 1: Testing Principles and Methodology

### 0. Technology Stack Constraints:
- Language: Java 25+ / Groovy 4.0+
- Testing Framework: Spock Framework 2.0+

### 1. Structure and Semantics (Given-When-Then)
- **Block Semantics**: Strictly follow the `given:`, `when:`, and `then:` blocks. Every test method must have a descriptive name, and every Spock block must have a text label written in clear, domain-specific English that conveys intent.
- **Comment Rule**: Prefer self-documenting code. Express intent through test method names and block labels rather than inline comments. For highly complex logic, use block comments to explain the *why*, which must not be removed unless the updated code or a replacement comment perfectly preserves the original context.
- **Strict Ordering**: The order of test methods within a test class must exactly mirror the order of the corresponding methods in the source class.
- **Focused Assertions**: Shotgun assertions are prohibited; each test method must verify only one core logical behavior.

### 2. Data-Driven and Parameterized Testing Patterns
- You must heavily utilize Spock's `where:` blocks for data-driven testing.
- **Dynamic Cartesian Products (Preferred for Inputs)**: To achieve exhaustive combinatorial coverage for multiple input variables, you are highly encouraged to use Groovy's Data Pipes (`<<`) combined with `.combinations()` (e.g., `[listA, listB].combinations()`). This prevents Data Table bloat when generating massive test matrices.
- **Data Tables (For Explicit Mapping)**: When specific inputs map to highly specific, non-computable expected outputs (or exception types), use Data Tables with double pipes (`||`) to clearly separate inputs from expected states.
- **No `@Unroll`**: Do not use the `@Unroll` annotation, as it is the default behavior in Spock 2.0+.

### 3. Visibility-Agnostic Testing Strategy (White-Box Approach)
- **Ignore Access Modifiers (Unified Coverage)**: In the Spock + Groovy environment, discard the traditional dogma of "only testing public methods." Treat **every method** in the class (whether public, private, or protected) as an independent, first-class logical unit. Utilize Groovy's dynamic dispatch to call target methods directly, applying the suite-level coverage planning outlined in Chapter 2.
  - Reflection is strictly prohibited in tests; rely on Groovy's dynamic dispatch for direct method invocation and field access instead.
- **Public Methods (UseCase-Driven Contract Testing)**: Beyond being tested as fundamental logic units, public methods bear the responsibility of providing business contracts. Therefore, you must **additionally** write comprehensive, UseCase-oriented tests for public methods. Focus on verifying the invocation order of external collaborators, parameter passing, and the final business semantics exposed to callers.

### 4. F.I.R.S.T. Principles & Restrained Mocking Strategy
- **Fast**: Avoid `@SpringBootTest` for full-context loading. If Spring components are involved, strictly utilize slice annotations (e.g., `@WebMvcTest`) combined with `@MockitoBean` to isolate boundary dependencies.
- **Isolated**: Ensure zero state pollution between tests. Test data for each iteration must be completely independent.
- **Realistic (Reject Over-Mocking)**: **Prioritize real objects.** For Value Objects, utility classes, DTOs, simple in-memory domain models, or lightweight dependencies with no side effects, you must construct and use actual instances. The use of the `new` keyword, the `Builder` pattern, and static factory methods like `of()` is the standard and highly encouraged approach.
- **Repeatable (Precisely Isolate Heavy Dependencies)**: Reserve Spock's `Mock()` or `Stub()` exclusively for "heavy dependencies" that cross architectural boundaries, are difficult to construct in UTs, or introduce high latency (e.g., Repositories, RPC/HTTP Clients, File Systems, Message Queues).
- **Self-Validating**: Rely entirely on Spock's powerful AST implicit assertions by writing boolean expressions directly in the `then:` block. Do not introduce redundant external assertion libraries.

### 5. State-Based Testing Priority & Interaction Specs
- **State-Based Testing First**: After sending a command to a real object, prioritize asserting the **state changes** or **return values** of the target object or its real collaborators within the `then:` block.
- **Interaction Testing Limits**: You may use Spock's cardinality contract syntax (e.g., `1 * mockService.doSomething(_) >> mockResult`) in the `then:` block only when a dependency is explicitly defined as a mocked "heavy boundary component". It is strictly forbidden to mock or spy on simple classes merely to verify internal implementation details.

---

## Chapter 2: Test Case Classification and Physical File Organization Strategy

To prevent a single test file from growing into a "God Class" and to ensure all test cases reside in their optimal physical locations, we adopt the following file splitting and naming strategy:

### 1. Core Contract & Boundary Defense (Main Test File)
*   **File Naming Convention**: `[TargetClass]Spec.groovy` (Named identically to the target source class, e.g., `OrderServiceSpec.groovy`)
*   **Role**: Serves as the "living documentation" of the target class, focusing on happy-path orchestration and boundary defense for outward-facing (including package-private) API contracts.

This file MUST contain the following two sections:

#### A. Happy-Path Orchestration for Public Contracts
1. Identify `public` methods and extract their core, happy-path business execution flows.
2. Define `given:`, `when:`, and `then:` blocks for all outward-facing use cases, adhering to the single-case writing rules in Chapter 1.
3. In the `given:` block, define the prerequisite data that heavy boundary mock objects must return under the normal execution path.
4. In the `then:` block, strictly enforce State-Based Testing as the primary assertion (verifying return values or state mutations in domain models).
5. In the `then:` block, for heavy boundary components touched by the use case (e.g., database writes), use precise cardinality assertions (e.g., `1 * repository.save({ it.status == 'SUCCESS' })`) to ensure the absolute correctness of the interaction sequence.

#### B. Negative Paths & Absolute Defense of Public Contracts
1. Extract all exception branches that `public` methods might trigger (e.g., invalid parameters, state machine validation failures, underlying dependency errors).
2. Define `given:`, `when:`, and `then:` blocks for all exception scenarios, adhering to the single-case writing rules in Chapter 1.
3. **In the `where:` block**, utilize double-pipe (`||`) data tables. List specific invalid input combinations on the left side, and explicitly map them to the expected Exception types and specific ErrorCodes on the right side.
4. In the `then:` block, use `def ex = thrown(ExpectedException)` to catch the exception, and assert the semantic information it carries (Message/Code).
5. At the end of the `then:` block, you must append **zero-interaction assertions** for write-operation mocks (e.g., `0 * repository.save(_)` or `0 * _`). This absolutely guarantees that no dirty data or partial commits occur during an exception flow.

### 2. Exhaustive Method-Level Algorithmic Testing (Dedicated Algorithm Test File)
*   **File Naming Convention**: `[TargetClass]AlgorithmSpec.groovy`
*   **Role**: Acts as the logic "meat-grinder" from a pure-function perspective. Physically isolates Spock's data-driven exhaustive boundary tests to keep the main test file clean.

**Content Constraints:**
1. Extract all methods in the class (including `private` and `protected`). Treat each method as an independent pure function for testing purposes.
2. For the method currently under test, extract all of its explicit input parameters and implicitly read internal field states as independent variables ($x_1, x_2 \dots x_n$).
3. Define a value pool for each variable. This pool must include: normal business extremes, max/min limits, 0, negative numbers, nulls, empty collections/strings, and other special boundary conditions relevant to the object.
4. Generate the Cartesian product of all possible input parameter combinations and map them to their expected results.
5. At the suite level, continuously deduplicate scenarios and merge equivalent cases to prevent test matrix bloat and redundant assertions.
6. Suite-level coverage must encompass normal paths, boundary conditions, and exceptional paths.

### 3. Complex Integration Tests (Mandatory Dedicated Files)
*   **File Naming Convention**: Prefer `[Feature]IntegrationSpec.groovy` under `src/test/groovy` as the default style in this repository. Use `[TargetClass]IntegrationTest.java` only when Java-based integration infrastructure strictly requires it.
*   **Role**: Isolates integration concerns into dedicated files, strictly separated from algorithm-focused and contract-focused unit tests. Split across multiple files as needed based on the specific integration domain.
*   **When to Write Integration Tests and What to Verify**:
    - **Spring bean wiring and auto-configuration**: Boot a real Spring context using slice annotations, assert that conditional beans are appropriately registered or absent, and verify that user-provided beans correctly override auto-configured defaults.
    - **Cross-module API consumption**: Import a target module as a dependency and invoke its public API from a consumer module to confirm that the integration contract holds end-to-end across module boundaries.
    - **JVM-global or process-level state isolation**: When a test must mutate static or JVM-wide state that cannot be easily reset, spawn a dedicated subprocess to contain the mutation and prevent test pollution.
    - **Build plugin behavior**: Use Gradle TestKit to execute real Gradle builds against a minimal temporary project, then assert on task outcomes, generated/transformed class files, and expected console output.
    - **End-to-end business contracts**: Treat the tested entry-point class as the starting node and exercise the full call chain through real collaborators, asserting the final externally observable outcome of the entire business flow.
    - **Regression tests for confirmed bugs**: Upon fixing a bug, write a dedicated integration test that reproduces the exact failure scenario. The test name must explicitly reference the defect tracker ID (e.g., Jira ticket) so the case cannot be silently removed.
    - **Cross-boundary infrastructure**: Any scenario that cannot be adequately verified in a unit test due to hard dependencies on real infrastructure (e.g., file systems, message queues, external processes) belongs here.
*   **Execution and Isolation Requirements**:
    - **Prioritize Real Infrastructure**: Use genuine integration boundaries and avoid replacing them with mocks. Actively utilize tools like Testcontainers or embedded brokers to run against real instances rather than in-memory fakes.
    - **Resource Cleanup**: Ensure meticulous resource cleanup after each test (e.g., closing application contexts, terminating spawned processes, and cleanly dropping connections).
    - **Observable Behavior**: Keep assertions strictly focused on the externally observable behavior and side effects of the integrated system, rather than internal implementation details.

---

Here is the English version of Chapter 3, tailored to match the highly rigorous, uncompromising, and professional tone established in the previous chapters.

---

## Chapter 3: Test Case Evolution and Maintenance Specifications

This chapter defines the operational guidelines and strict disciplines that must be observed when adding, modifying, or refactoring test cases within the existing test suite.

### 1. Absolute Compliance for New Tests
*   **Mandatory Adherence to All Guidelines**: Any newly authored test cases (whether for new features or to cover previously omitted scenarios) must **unconditionally and strictly** comply with all architectural design and semantic rules defined in Chapters 1 and 2.
*   **Zero-Tolerance Principle**: Bypassing these specifications under the pretext of "tight deadlines," "temporary validation," or "simple logic" is strictly prohibited (e.g., omitting `given-when-then` labels, arbitrarily booting Spring contexts in unit tests, or using hardcoded values instead of data-driven tables).

### 2. Lossless Refactoring of Legacy Tests
*   **Opportunistic Normalization**: When modifying existing test cases that do not conform to current guidelines—whether due to business requirement changes or code review interventions—you **must** concurrently refactor them into a compliant structure (e.g., consolidating scattered, repetitive method calls into a data table within a `where:` block).
*   **The "Lossless" Red Line**: During normalization and refactoring, **it is absolutely forbidden to lose any original testing intent, test data (boundary values, extreme values), exception branches, or assertion logic**. The refactoring process must solely be an upgrade of physical form and organizational structure; it must never result in a reduction or compromise of the original business validation scope.

### 3. Strict Boundaries for Modification Scope
*   **No Out-of-Bounds Modifications**: In the absence of explicit instructions, it is **strictly forbidden** to modify test cases that fall outside the immediate scope of the current business requirement or defect fix.
*   **Isolated Changes**: The historical stability of the test suite must be preserved. Do not perform unauthorized "drive-by refactorings" to prevent introducing unpredictable side effects into unrelated domains or disrupting the working context of other developers.

### 4. Rigorous Proposal Mechanism for Driven Evolution
*   **Propose First, Execute Later**: When receiving explicit instructions to improve, upgrade, or update a specific batch of test cases, you must not execute the changes blindly. You **must first provide a rigorous Modification Plan**.
*   **Mandatory Plan Contents**:
    1.  **Current State Diagnosis**: Explicitly identify which specific rules the current test cases violate (e.g., excessive mocking, lack of state-based assertions).
    2.  **Refactoring Strategy**: Explain the exact standardized techniques to be applied (e.g., introducing dynamic Cartesian products to replace redundant code, or physically isolating heavy integration tests into dedicated files).
    3.  **Intent Preservation Guarantee**: Explicitly declare how the core assertions of the legacy tests will be mapped and retained, absolutely ensuring **zero loss of testing intent**.
*   **Execution Constraints**: Once the proposal is approved, the final code implementation must adhere 100% to all technical constraints outlined in Chapters 1 and 2.
