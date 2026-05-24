package org.libprunus.core.log.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Suppresses logging output for the target entirely: the matched target produces no value in the
 * rendered log entry.
 *
 * <p>{@code @DoNotLog} is one of three mutually exclusive family annotations. See
 * {@link Sensitive} for the canonical resolution algorithm shared by all three families
 * (declaration-class chain, closeness rule, layer-by-layer traversal, same-layer multi-family
 * conflict, mutual exclusion, parameter-target restrictions).
 *
 * <h2>{@code @DoNotLog}-specific effect</h2>
 *
 * <p>When the effective family resolution lands on {@code @DoNotLog}, the per-target effect is:
 * <ul>
 *   <li><b>parameter-level</b>: the parameter slot is dropped from the ENTER line. The method
 *       still emits ENTER and EXIT, just without that parameter.
 *   <li><b>method-level</b>: the closeness rule (parameter &gt; method &gt; type) governs each
 *       render target independently. A method-level {@code @DoNotLog} establishes the default
 *       for targets that have no closer annotation:
 *       <ul>
 *         <li>parameter targets without a parameter-level family annotation inherit
 *             {@code @DoNotLog} from the method scope &mdash; their slots drop from the ENTER
 *             line;
 *         <li>the return-value target inherits {@code @DoNotLog} from the method scope (there
 *             is no closer scope) &mdash; the return slot drops from the EXIT line.
 *       </ul>
 *       <p>As a <em>rendering optimization</em>, when both of the following hold the framework
 *       emits <b>neither</b> ENTER nor EXIT for the method:
 *       <ol>
 *         <li>the method's effective method-level family policy resolves to {@code @DoNotLog}
 *             (either declared directly on the method, declared at the type level of the method's
 *             directly-declaring class, or &mdash; only for override methods &mdash; inherited
 *             via the layer-by-layer traversal along the override chain defined in
 *             {@link Sensitive}); <b>and</b>
 *         <li>no parameter on the method signature carries any parameter-level family
 *             annotation ({@link Sensitive}, {@link DoLog}, or {@code @DoNotLog}).
 *       </ol>
 *       <p>This optimization is observationally indistinguishable from emitting ENTER and EXIT
 *       lines with all slots dropped. It is <b>not</b> an independent "whole-method skip"
 *       semantic mode &mdash; closer {@link Sensitive} / {@link DoLog} at any scope can always
 *       opt a target back in, in which case the method emits ENTER and EXIT normally.
 *   <li><b>field-level</b>: the field slot is dropped from toString output.
 *   <li><b>type-level</b>: applied to a class matched by {@link MethodLoggingProfile} or
 *       {@link ToStringProfile}, members whose own resolution lands on {@code @DoNotLog} via the
 *       algorithm in {@link Sensitive} are dropped. Members carrying their own closer
 *       {@link Sensitive} or {@link DoLog} are unaffected &mdash; the closeness rule of
 *       {@link Sensitive} wins.
 * </ul>
 *
 * <p>Type-level {@code @DoNotLog} is <em>not</em> a class-level off switch: a {@code toString}
 * is still generated, ENTER/EXIT is still emitted for matched methods whose effective family
 * resolves to anything other than {@code @DoNotLog}, and individual members can still opt back
 * in via closer {@link Sensitive} or {@link DoLog}. To truly exclude a class from framework
 * processing (no {@code toString}, no ENTER/EXIT, no member-level overrides), use
 * {@link org.libprunus.core.annotation.AutomatedProcessingIgnore} on the class, or arrange the
 * profile's {@code includePackages} / {@code excludePackages} / {@code includeClassSuffixes} so
 * the class is not matched in the first place.
 *
 * <p><b>null values:</b> a dropped slot is dropped whether the underlying value is {@code null}
 * or not.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface DoNotLog {}
