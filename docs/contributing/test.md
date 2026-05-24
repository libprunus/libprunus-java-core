# Test Guidelines

## Chapter 1: Testing Principles and Methodology

### 0. Technology Stack Constraints:
- Language: Java 25+ / Groovy 4.0+
- Testing Framework: Spock Framework 2.0+

### 1. Structure and Semantics (Given-When-Then)
- **Block Semantics**: Strictly follow the `given:`, `when:`, and `then:` blocks. Every test method must have a descriptive name that on its own conveys the full scenario intent.
- **Block Labels (default: omit)**: Do **not** attach text labels to `given:`/`when:`/`then:`/`where:` blocks by default — a well-named test method makes them redundant. Add labels only when a block carries multiple logical steps that a reader needs to disambiguate. Labels annotate step-level granularity inside a complex scenario; they do not compensate for an imprecise method name — the method name must always carry the overall scenario intent on its own. When required, each label must explain that block's specific role in clear, domain-specific English, not merely restate "given/when/then".
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
  - In Groovy specs, reflection is prohibited; use Groovy's dynamic dispatch for direct method invocation and field access instead.
- **Public Methods (UseCase-Driven Contract Testing)**: Beyond being tested as fundamental logic units, public methods bear the responsibility of providing business contracts. Therefore, in addition to the unit-level coverage, write UseCase-oriented tests for public methods that focus on the invocation order of external collaborators, parameter passing, and the final business semantics exposed to callers. The two angles may share a single test method when the same case naturally proves both — duplicate cases are not the goal; complete coverage is.

### 4. F.I.R.S.T. Principles & Restrained Mocking Strategy
- **Fast**: Avoid `@SpringBootTest` for full-context loading. If Spring components are involved, strictly utilize slice annotations (e.g., `@WebMvcTest`) combined with `@MockitoBean` to isolate boundary dependencies.
- **Isolated**: Ensure zero state pollution between tests. Test data for each iteration must be completely independent.
- **Realistic (Reject Over-Mocking)**: **Prioritize real objects.** For Value Objects, utility classes, DTOs, simple in-memory domain models, or lightweight dependencies with no side effects, you must construct and use actual instances. The use of the `new` keyword, the `Builder` pattern, and static factory methods like `of()` is the standard and highly encouraged approach.
- **Repeatable (Precisely Isolate Heavy Dependencies)**: Reserve Spock's `Mock()` or `Stub()` exclusively for "heavy dependencies" that cross architectural boundaries, are difficult to construct in UTs, or introduce high latency (e.g., Repositories, RPC/HTTP Clients, File Systems, Message Queues).
- **Self-Validating**: Rely entirely on Spock's powerful AST implicit assertions by writing boolean expressions directly in the `then:` block. Do not introduce redundant external assertion libraries.

### 5. State-Based Testing Priority & Interaction Specs
- **State-Based Testing First**: After sending a command to a real object, prioritize asserting the **state changes** or **return values** of the target object or its real collaborators within the `then:` block.
- **Interaction Testing Limits**: You may use Spock's cardinality contract syntax (e.g., `1 * mockService.doSomething(_) >> mockResult`) in the `then:` block only when a dependency is explicitly defined as a mocked "heavy boundary component". It is strictly forbidden to mock or spy on simple classes merely to verify internal implementation details.

### 6. Meaningful Assertions (Positive and Negative Coverage)
- **Semantic Weight**: Every assertion must carry real verification value. Trivially-true checks (e.g., restating preconditions already established in `given:`, asserting `result != null` for an obviously non-null return, or comparing a constant against itself) are strictly prohibited—they consume test surface without proving anything.
- **Standard Library Scope Exclusion**: Do not write tests whose only failure mode is a JDK/JVM change. Inherited `Enum.valueOf` mapping and its IAE/NPE behavior, NPEs from null dereference without an explicit project-level null guard, and JDK-emitted exception messages are out of scope. A standard library call enters scope only when it carries project data or project-layered behavior — e.g., `Enum.values()` used to lock the project's declared constant set and order, or `Optional.orElseThrow` raising an exception with a project ErrorCode.
- **Dual-Direction Verification**: For each verified behavior, the `then:` block must cover both the **positive direction** (the expected state, return value, or interaction that **must** occur) and the **negative direction** (the alternative outcomes, residual state, or side effects that **must not** occur). Examples:
  - Alongside `result.status == APPROVED`, also assert `result.errors.isEmpty()` and `result.rejectionReason == null`.
  - Alongside `1 * notifier.notifyApproved(_)`, also assert `0 * notifier.notifyRejected(_)` (or close the interaction set with `0 * _`).
  - Alongside `repository.findById(id).isPresent()`, also assert that unrelated aggregates were not touched.
- **Symmetric Exception Coverage**: When asserting that an exception is thrown, you must additionally assert that the success-path side effects did not occur (no repository writes, no domain state mutation, no downstream notifications, no partial commits). This generalizes the zero-interaction discipline mandated in Chapter 2 §1.B and applies to every exception-flow test, not only public-contract negative paths.
- **Rationale**: Positive assertions prove the code did what it should; negative assertions prove the code did not silently do something it shouldn't. A test that only asserts positive outcomes can pass even when the system emits extra writes, fires duplicate notifications, or leaves stale state behind.

---

## Chapter 2: Test Case Classification and Physical File Organization Strategy

To prevent a single test file from growing into a "God Class" and to ensure all test cases reside in their optimal physical locations, we adopt the following file splitting and naming strategy.

### 0. Package Co-location Policy

**Permitted to share a package with production Java source code:**

1. `[TargetClass]Spec.groovy` — unit test files named after their production target.
2. `[TargetClass]AlgorithmSpec.groovy` — algorithm exhaustion test files.
3. `[TargetClass]IntegrationSpec.groovy`, `[TargetClass][TestIntent]IntegrationSpec.groovy`, or `[TestIntent]IntegrationSpec.groovy` — integration test files.
4. Java test utility or fixture classes **that cannot function without package-private access** to production code (e.g., classes that must access package-private constructors, fields, or methods). Such classes must be placed in an appropriate sub-package under the production package (e.g., `fixture`, `testutil`) rather than directly in the production package unless package-private access is the sole reason.

**Prohibited from sharing a package with production Java source code:**

- All other test helper classes, base classes, data builders, or fixture classes that do not require package-private access.
- Any test-only infrastructure class that can be placed under a `testutil`, `fixture`, or similar sub-package without loss of functionality.

This rule applies uniformly across all modules. Handling of violations follows the refactoring discipline defined in Chapter 3.

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
5. **If the use case touches mocked boundary components** (per §1.4), append **zero-interaction assertions** at the end of the `then:` block for their write operations (e.g., `0 * repository.save(_)` or `0 * _`) to guarantee no dirty data or partial commits during the exception flow. When the use case operates entirely on real collaborators, fulfill the same intent via state assertions on those collaborators (per §1.6 "Symmetric Exception Coverage") instead of introducing mocks solely to verify their absence.

### 2. Exhaustive Method-Level Algorithmic Testing (Dedicated Algorithm Test File)
*   **File Naming Convention**: `[TargetClass]AlgorithmSpec.groovy`
*   **Role**: Acts as the logic "meat-grinder" from a pure-function perspective. Physically isolates Spock's data-driven exhaustive boundary tests to keep the main test file clean.
*   **When to Split**: This file is opt-in, not a default companion to every `[TargetClass]Spec.groovy`. Create it only when algorithmic / boundary-exhaustion coverage for one or more methods would otherwise overwhelm the main spec. If the method's coverage fits naturally inside the main spec as a single `where:` table, keep it there — splitting empty algorithm files for every class is a structural anti-pattern.

**Content Constraints (when an Algorithm spec is warranted):**
1. Extract the methods whose algorithmic complexity drove the split (including `private` and `protected`). Treat each as an independent pure function for testing purposes; do not mechanically copy in methods already adequately covered by the main spec.
2. For the method currently under test, extract all of its explicit input parameters and implicitly read internal field states as independent variables (x1, x2, ..., xn).
3. Define a value pool for each variable. This pool must include: normal business extremes, max/min limits, 0, negative numbers, nulls, empty collections/strings, and other special boundary conditions relevant to the object.
4. Generate the Cartesian product of all possible input parameter combinations and map them to their expected results.
5. At the suite level, continuously deduplicate scenarios and merge equivalent cases to prevent test matrix bloat and redundant assertions.
6. Suite-level coverage must encompass normal paths, boundary conditions, and exceptional paths.

### 3. Complex Integration Tests (Mandatory Dedicated Files)
*   **File Naming Convention**: Three forms are permitted under `src/test/groovy`:
    - `[TargetClass]IntegrationSpec.groovy` — use when the integration test focuses on a single class and the file length remains manageable and the intent is sufficiently concentrated.
    - `[TargetClass][TestIntent]IntegrationSpec.groovy` — use when a single class warrants multiple integration files covering distinct aspects (e.g., `AotByteBuddyDispatcherCleanupIntegrationSpec`, `AotByteBuddyDispatcherClasspathIntegrationSpec`).
    - `[TestIntent]IntegrationSpec.groovy` — use when the test spans multiple classes or entry points under a single cross-cutting concern (e.g., end-to-end flows, regression scenarios, infrastructure contracts).
    
    Use `[TargetClass]IntegrationTest.java` only when Java-based integration infrastructure strictly requires it.
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

## Chapter 3: Test Suite Evolution

- **New tests**: comply with Chapters 1 and 2.
- **Legacy tests**: when you modify a test for a business or code-review reason, opportunistically refactor it into a compliant shape — but never drop original test intent, boundary data, exception branches, or assertion logic in the process.
- **Modification scope**: do not touch tests outside the immediate scope of the current change. No drive-by refactorings.
- **Bulk normalization**: before reworking a batch of legacy tests, write down which rules each case violates, the normalization technique to apply, and how every original case's intent is preserved. The plan is the design check — use it for self-review, code review, or alignment with whoever requested the sweep.
