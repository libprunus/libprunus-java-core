package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Routing rule that maps classes to controlled toString rewriting behavior.
 *
 * <p>A profile is declared on a {@link LogRegistry} class.
 *
 * <p>Profile declaration order has no semantic meaning. If multiple {@link ToStringProfile}
 * declarations can match the same class, that is a configuration error.
 *
 * <p>For parent-package fallback with dedicated subpackage behavior, combine {@link
 * #includePackages()} with {@link #excludePackages()}: define a broad parent prefix in {@code
 * includePackages}, list excluded child prefixes in {@code excludePackages}, and define separate,
 * non-overlapping profiles for the excluded child prefixes.
 *
 * <p>A profile matches a class only when <em>all</em> conditions are true:
 *
 * <ul>
 *   <li>{@link #includePackages()} is non-empty after blank-entry removal, and the class
 *       fully-qualified name satisfies at least one package prefix rule.
 *   <li>{@link #excludePackages()} is checked after blank-entry removal, and the class
 *       fully-qualified name satisfies none of the listed package prefix rules.
 *   <li>{@link #includeClassSuffixes()} is non-empty after blank-entry removal, and the simple class
 *       name ends with at least one of the listed suffixes.
 * </ul>
 *
 * <p>If either condition produces an empty list (omitted, empty array, or all-blank entries), the
 * profile matches no classes.
 *
 * <p>If no profile matches a class, no toString rewrite is applied for that class. Field-level
 * family annotations ({@link Sensitive}, {@link DoNotLog}, {@link DoLog}) on members of an
 * unmatched class have no effect on the class's own toString &mdash; the class retains its
 * declared (or inherited from {@code Object}) toString implementation. The same field-level
 * annotations remain effective on the field if the field is rendered as an <em>inherited</em>
 * field through a matched subclass (see "Field shadowing across layers" below).
 *
 * <p>The container annotation {@link ToStringProfiles} is generated automatically by the compiler
 * when multiple profiles are declared.
 *
 * <h2>Rendering behavior on a matched class</h2>
 *
 * <p>The framework generates a {@code toString} method on each matched class. The method body
 * walks the matched class downward through its inheritance chain (matched class first, then its
 * superclass, then that superclass's superclass, and so on), emitting eligible fields per layer.
 * The matched class is the &ldquo;root&rdquo; layer; its superclasses contributing inherited
 * fields are &ldquo;inherited&rdquo; layers. A field is eligible when <em>all</em> of the
 * following hold:
 *
 * <ul>
 *   <li>It is an instance field. Static, transient, synthetic, and compiler-generated fields
 *       (including names beginning with {@code $}, and the synthetic outer-class reference on
 *       non-static inner classes) are excluded everywhere.
 *   <li>The access modifier filter passes:
 *       <ul>
 *         <li>Root layer: {@code public}, {@code protected}, {@code package-private}, and
 *             {@code private} fields are all eligible.
 *         <li>Inherited layer: {@code public} and {@code protected} fields are always eligible.
 *             A {@code package-private} field declared in an inherited layer is eligible only
 *             when the inherited layer's declaring class shares a package with the matched
 *             class &mdash; mirroring JVM access semantics. A {@code private} field declared
 *             in an inherited layer is never eligible.
 *       </ul>
 *   <li>The field's effective family resolution (see &ldquo;Annotation effects&rdquo; below) is
 *       not {@link DoNotLog}.
 * </ul>
 *
 * <p>The rendered form is:
 *
 * <pre>{@code SimpleName(fieldA=valueA, fieldB=valueB, ...)}</pre>
 *
 * <p>The order of fields <em>within</em> a single layer is not specified by this contract.
 * Neither {@link Class#getDeclaredFields()} nor the JVM class-file format mandates that the
 * fields of a class be presented in source order, and the framework does not impose its own
 * ordering on top. The order is stable for a given compiled class but should not be relied on
 * across compiler versions or build environments. The order of <em>layers</em> (matched class
 * before its superclass, and so on) is contractual.
 *
 * <h3>Records</h3>
 *
 * <p>Records are handled identically to ordinary classes. A record component is, at the field
 * level, a {@code private final} instance field; the field side of this contract applies to the
 * underlying field. All eligibility, family-resolution, and shadowing rules apply unchanged.
 * The single observable consequence of records' design is that all record components are
 * inherently {@code private} &mdash; so on the root layer the access modifier filter is
 * trivially satisfied.
 *
 * <h3>Annotation effects</h3>
 *
 * <p>{@link Sensitive}, {@link DoNotLog}, and {@link DoLog} placed on a class or on individual
 * fields control per-field rendering. The effective family for each field is determined by the
 * algorithm documented on {@link Sensitive} (declaration-class chain, closeness rule,
 * layer-by-layer traversal). Per-family outcome:
 *
 * <ul>
 *   <li>{@link Sensitive}: the field renders as the masked sentinel.
 *   <li>{@link DoNotLog}: the field slot is dropped from the output.
 *   <li>{@link DoLog} (or no family annotation anywhere in the chain): the field renders
 *       verbatim.
 * </ul>
 *
 * <h3>Field shadowing across layers</h3>
 *
 * <p>When two or more layers contribute fields whose simple names collide (e.g., a child
 * redeclares a parent's field name), each collision-participant in the output is qualified with
 * its declaring class's simple name in parentheses to disambiguate:
 *
 * <pre>{@code Child(name(Child)=childValue, name(Parent)=parentValue, ...)}</pre>
 *
 * <p>Non-colliding field names appear without the parenthetical qualifier. The qualifier applies
 * to whichever slots survive into the output set, regardless of which family resolved them.
 * Each shadowed field independently resolves its own family annotation against its own
 * declaring-class chain &mdash; the child's field-level annotation does not affect the parent's
 * field, and vice versa.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
@Repeatable(ToStringProfiles.class)
public @interface ToStringProfile {

    /**
     * Package name prefixes to match against the fully-qualified class name.
     *
     * <p>A class matches if its fully-qualified name equals a prefix exactly, or starts with {@code
     * prefix + "."}. A prefix that already ends with {@code "."} is matched as a plain starts-with
     * check. For example, {@code "com.example"} matches {@code com.example.Foo} and {@code
     * com.example.sub.Foo}, but not {@code com.exampleother.Foo}.
     *
     * <p>If omitted, empty, or all entries are blank, the profile matches no classes. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return package name prefixes to match
     */
    String[] includePackages() default {};

    /**
     * Package name prefixes to exclude after {@link #includePackages()} has matched.
     *
     * <p>A class is excluded if its fully-qualified name equals an exclude prefix exactly, or starts
     * with {@code excludePrefix + "."}. A prefix that already ends with {@code "."} is matched as a
     * plain starts-with check.
     *
     * <p>If omitted, empty, or all entries are blank, no package exclusion is applied. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return package name prefixes to exclude
     */
    String[] excludePackages() default {};

    /**
     * Simple class name suffixes to match, for example {@code {"Dto", "Response"}}.
     *
     * <p>A class matches if its simple name ends with at least one of the listed suffixes (OR
     * semantics within this list). This condition is combined with {@link #includePackages()} using
     * AND: both must match for the profile to apply.
     *
     * <p>If omitted, empty, or all entries are blank, the profile matches no classes. Blank or
     * empty-string entries ({@code ""}, {@code " "}) are silently discarded during configuration
     * processing.
     *
     * @return simple class name suffixes to match
     */
    String[] includeClassSuffixes() default {};
}
