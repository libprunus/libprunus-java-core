package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type, method, field, or parameter as sensitive: any value rendered for the target in
 * framework-produced log output is replaced with a masked sentinel.
 *
 * <p>{@code @Sensitive} is one of three mutually exclusive family annotations
 * &mdash; {@code @Sensitive}, {@link DoNotLog}, {@link DoLog} &mdash; that share a single unified
 * resolution algorithm. This Javadoc is the canonical reference for that algorithm;
 * {@link DoNotLog} and {@link DoLog} defer here and document only their own per-policy effect.
 *
 * <h2>Escape hatch: complete exclusion from processing</h2>
 *
 * <p>The resolution algorithm below applies only to targets that are eligible for framework
 * processing. A class or method carrying {@link org.libprunus.core.annotation.AutomatedProcessingIgnore}
 * is excluded from processing entirely &mdash; no {@code toString} is generated for an ignored
 * class, no ENTER/EXIT is emitted for an ignored method, and the family-annotation resolution
 * defined below has no effect on the ignored target. This is the contract-level off switch for a
 * single class or method.
 *
 * <p><b>Evaluation order:</b> the escape hatch is consulted <em>before</em> the family-annotation
 * resolution algorithm begins. If the hatch applies to a target, family resolution does not run
 * for that target, and any family annotations on the target or its ancestors are not consulted.
 * This order matters even when family resolution would have produced the same observable outcome
 * (e.g., walking the chain to a type-level {@link DoNotLog}) &mdash; the hatch is a hard
 * short-circuit, not a fallback.
 *
 * <p>Family annotations are <em>not</em> a substitute for
 * {@link org.libprunus.core.annotation.AutomatedProcessingIgnore}. In particular,
 * {@link DoNotLog} at the type level is per-member resolution, not a class-level off switch
 * &mdash; see {@link DoNotLog} for why.
 *
 * <h2>Effective policy resolution</h2>
 *
 * <p>For a given target (field, method, parameter, or rendering call site), the effective family
 * policy is resolved by applying the closeness rule within the scope visible to the target. The
 * scope is bounded by the Java OOP model:
 *
 * <ul>
 *   <li><b>Fields</b> and <b>self-declared methods</b> (methods declared on a class that do
 *       <em>not</em> override any supertype method): scope is the target's directly-declaring
 *       class only. No supertype's type-level, method-level, or parameter-level annotation is
 *       consulted.
 *   <li><b>Override methods</b> (methods that override a supertype's same-signature method): scope
 *       extends layer-by-layer along the <em>override chain</em> &mdash; supertypes (class or
 *       interface) that declare the same-signature abstract method being overridden participate
 *       at the corresponding layer.
 * </ul>
 *
 * <p>This boundary mirrors Java's own inheritance model: Java has no field inheritance (so a
 * subclass's annotations cannot retroactively alter the policy of an inherited field), Java's
 * annotation model is default-not-inherited (so a subclass's self-declared new method is not
 * silently governed by a supertype's class-level annotation), but Java does have method
 * inheritance and override (so an override is, semantically, the same logical method as the
 * supertype declaration, and the supertype's annotations on that method are part of the
 * contract). The four subsections below define this in full.
 *
 * <h3>1. Declaration-class chain (not runtime-class chain)</h3>
 *
 * <p>A field or method has a fixed identity defined by the class that <em>declares</em> it in
 * source (and, for methods, its signature). The family policy is resolved against that declaring
 * class and that class's supertype chain (superclass + directly implemented interfaces). The
 * <em>runtime receiver class</em> never participates &mdash; no annotation on any subclass of
 * the declaring class can retroactively alter the policy of a member declared on the superclass.
 *
 * <p>Calling {@code c.m()} on a {@code C extends P} where {@code C} does not override {@code m}
 * executes {@code P}'s {@code m}; its policy is resolved against {@code P}. An override on
 * {@code C} executes {@code C}'s own {@code m}; its policy is resolved against {@code C}. The
 * runtime receiver class {@code C} does not change the policy of {@code m} when {@code m} is
 * declared on {@code P}.
 *
 * <p>For an <b>override method</b>, the declaring class is the override's owner class. Layer 1
 * of the chain is that owner class itself; layer 2 includes its direct superclass <b>and</b> the
 * directly implemented interfaces that declare the abstract method being overridden. A type-level
 * {@code @Sensitive} on such an interface participates in family resolution at layer 2 just as
 * if it were declared on a superclass.
 *
 * <h3>2. Closeness rule within a single layer</h3>
 *
 * <p>Within one chain layer, the rule is &ldquo;the annotation closest to the rendered target
 * wins.&rdquo; The two sides have different target hierarchies and therefore different priority
 * chains.
 *
 * <h4>Method side (ENTER/EXIT; render targets = parameter or return value)</h4>
 *
 * <ol>
 *   <li>parameter-level annotation (applies to parameter targets only);
 *   <li>method-level annotation (applies to both parameter and return-value targets);
 *   <li>type-level annotation (applies to both parameter and return-value targets).
 * </ol>
 *
 * <p>Parameter target priority: parameter &gt; method &gt; type.
 * Return-value target priority: method &gt; type (parameter-level annotations do not apply to
 * return values).
 *
 * <h4>Field side (toString; render target = field)</h4>
 *
 * <ol>
 *   <li>field-level annotation;
 *   <li>type-level annotation.
 * </ol>
 *
 * <p>Field target priority: field &gt; type.
 *
 * <p>On either side, the first matching annotation in the applicable order wins for that layer
 * and resolution terminates immediately at that layer &mdash; farther layers (superclass,
 * interfaces, etc.) do not participate. If no annotation matches at any closeness scope, the
 * layer contributes nothing and resolution proceeds to the next layer.
 *
 * <p>The "type-level annotation" closeness scope is bounded by the Java OOP model (see
 * &sect;Effective policy resolution above):
 *
 * <ul>
 *   <li>For <b>fields</b> and <b>self-declared methods</b>: the type-level scope is restricted to
 *       the target's directly-declaring class only. A type-level annotation on any supertype of
 *       the declaring class does <b>not</b> participate.
 *   <li>For <b>override methods</b>: the layer-by-layer traversal defined in &sect;3 extends up
 *       the override chain. A type-level annotation on a supertype that declares the
 *       same-signature method being overridden participates at the corresponding layer.
 * </ul>
 *
 * <p>This boundary is symmetric across families: {@code @Sensitive}, {@link DoNotLog}, and
 * {@link DoLog} all follow it.
 *
 * <h3>3. Layer-by-layer traversal with three-family unification</h3>
 *
 * <p>A "layer" is the set of all supertypes at the same distance from the starting class. The
 * starting class is the field's or method's declaring class. Layer 1 is that starting class
 * itself. Layer 2 is the union of layer 1's direct superclass and its directly implemented
 * interfaces. Layer 3 is the union of the direct supertypes of every class in layer 2. The
 * traversal proceeds layer by layer outward.
 *
 * <p>If a layer contributes nothing (no family annotation at any of the applicable closeness
 * scopes on any of its members), resolution advances to the next layer. The first layer that
 * contributes a policy returns it. The walk terminates either when a policy is found or when the
 * chain is exhausted (yielding implicit plain rendering).
 *
 * <p>All three families participate uniformly: {@code @Sensitive}, {@link DoNotLog}, and
 * {@link DoLog} each terminate the walk the moment they are reached. {@link DoLog} (plain
 * rendering) is not "weaker" than {@code @Sensitive} (masking) or {@link DoNotLog} (suppression)
 * &mdash; closer wins regardless of family.
 *
 * <h3>4. Same-layer multi-family conflict</h3>
 *
 * <p>If two or more <em>different</em> family annotations appear at the same chain layer (for
 * example, one on a superclass and a different one on a directly implemented interface at the
 * same depth), resolution fails fast as a configuration error rather than one being silently
 * picked. Multiple <em>identical</em> family annotations at the same layer (e.g.,
 * {@code @Sensitive} on both a superclass and an interface) are <b>not</b> a conflict.
 *
 * <h2>Mutual exclusion per target</h2>
 *
 * <p>Declaring more than one annotation from the {@code @Sensitive} / {@link DoNotLog} /
 * {@link DoLog} family on the same target is a configuration error.
 *
 * <h2>Parameter targets</h2>
 *
 * <p>{@code PARAMETER} targets are honored only for parameters of public instance methods.
 * Parameters of constructors, lambda expressions, and non-public methods are not honored. The
 * annotation is not a configuration error in those positions &mdash; it is silently ignored.
 *
 * <h2>{@code @Sensitive}-specific effect</h2>
 *
 * <p>When the effective family resolution lands on {@code @Sensitive}:
 * <ul>
 *   <li>Field values are rendered as the masked sentinel in toString output.
 *   <li>Parameter values are rendered as the masked sentinel in ENTER lines.
 *   <li>Method return values are rendered as the masked sentinel in EXIT lines.
 * </ul>
 *
 * <p>The masked sentinel is the literal string {@code "***"}. It does not vary with the original
 * value's type or length (i.e., it is not the original value with characters substituted). The
 * literal is part of this contract: downstream consumers (grep / regex matching the log stream)
 * may rely on it.
 *
 * <p><b>null values:</b> the masked sentinel is emitted unconditionally, including when the
 * underlying value is {@code null}. The mask does <em>not</em> short-circuit on null.
 * Distinguishing "null" from "non-null" in masked output would leak the field's nullity to log
 * readers &mdash; exactly the side-channel the mask exists to close.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Sensitive {}
