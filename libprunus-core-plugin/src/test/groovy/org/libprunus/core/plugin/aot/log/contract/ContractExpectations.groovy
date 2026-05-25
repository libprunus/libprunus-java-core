package org.libprunus.core.plugin.aot.log.contract

/**
 * Computes the expected toString output for the 3-layer inheritance contract fixtures
 * Inh3C&lt;C&gt;FromP&lt;P&gt;Gp&lt;GP&gt;Dto extending Inh3P&lt;P&gt;FromGp&lt;GP&gt; extending Inh3Gp&lt;GP&gt;.
 *
 * <p>Encodes the declaration-class-chain semantics: each layer's effective class policy
 * is the first non-Plain class annotation walking that layer's own ancestor chain
 * (the declaring layer's own type-level annotation, if any, beats any ancestor's),
 * independent of which subclass renders the instance.
 */
final class ContractExpectations {

    private static final List<String> ROOT_ACCESSES = ['public', 'protected', 'package', 'private']
    // The inheritance3 fixtures place every layer (child / parent / grandparent) in the same
    // runtime package (`package contract;`). FieldOutputPlanConsumer rescues package-private
    // inherited fields when subclass and declaring class share a package, so we mirror that
    // here by keeping public + protected + same-package PP and dropping only private.
    private static final List<String> INHERITED_ACCESSES = ['public', 'protected', 'package']
    private static final List<String> FIELD_KINDS = ['Plain', 'Sensitive', 'DoNotLog', 'DoLog']

    static String expectedChildToString(String childAnno, String parentAnno, String grandparentAnno) {
        String effC = childAnno
        String effP = parentAnno
        String effGp = grandparentAnno

        String childName = "Inh3C${childAnno}FromP${parentAnno}Gp${grandparentAnno}Subject"
        String parentName = "Inh3P${parentAnno}FromGp${grandparentAnno}Subject"
        String grandparentName = "Inh3Gp${grandparentAnno}Subject"

        List<Map> rendered = []
        rendered.addAll(layerFields(childName, effC, ROOT_ACCESSES))
        rendered.addAll(layerFields(parentName, effP, INHERITED_ACCESSES))
        rendered.addAll(layerFields(grandparentName, effGp, INHERITED_ACCESSES))

        Set<String> shadowed = findShadowedNames(rendered)

        String body = rendered.collect { Map f ->
            String label = shadowed.contains(f.name) ? "${f.name}(${f.owner})" : f.name
            "${label}=${f.display}"
        }.join(', ')

        "${childName}(${body})"
    }

    private static String firstNonPlain(List<String> annos) {
        annos.find { it != 'Plain' } ?: 'Plain'
    }

    /** Returns the ordered list of fields a layer contributes to the rendered toString. */
    private static List<Map> layerFields(String owner, String effectiveClassAnno, List<String> accessFilter) {
        List<Map> out = []
        for (String kind : FIELD_KINDS) {
            for (String access : ROOT_ACCESSES) {
                if (!accessFilter.contains(access)) continue
                String fieldPolicy = resolveFieldPolicy(kind, effectiveClassAnno)
                if (fieldPolicy == 'SUPPRESS') continue
                String name = access + kind
                String value = (kind == 'Plain' ? 'plain' : kind.toLowerCase()) + "-${access}-value"
                String display = fieldPolicy == 'MASK' ? '***' : value
                out << [name: name, owner: owner, display: display]
            }
        }
        out
    }

    /**
     * Field-level annotation beats class-level (field's own annotation, if any,
     * wins over the enclosing class's type-level annotation).
     *   - field @DoNotLog -> SUPPRESS
     *   - field @Sensitive -> MASK
     *   - field @DoLog -> PASS_THROUGH
     *   - plain field -> follow effective class anno
     */
    private static String resolveFieldPolicy(String fieldKind, String effectiveClassAnno) {
        switch (fieldKind) {
            case 'DoNotLog':
                return 'SUPPRESS'
            case 'Sensitive':
                return 'MASK'
            case 'DoLog':
                return 'PASS_THROUGH'
            case 'Plain':
                switch (effectiveClassAnno) {
                    case 'DoNotLog':
                        return 'SUPPRESS'
                    case 'Sensitive':
                        return 'MASK'
                    case 'DoLog':
                    case 'Plain':
                        return 'PASS_THROUGH'
                    default:
                        throw new IllegalArgumentException("unknown class anno: ${effectiveClassAnno}")
                }
            default:
                throw new IllegalArgumentException("unknown field kind: ${fieldKind}")
        }
    }

    private static Set<String> findShadowedNames(List<Map> rendered) {
        Set<String> seen = new HashSet<>()
        Set<String> dups = new LinkedHashSet<>()
        for (Map f : rendered) {
            if (!seen.add(f.name as String)) {
                dups << (f.name as String)
            }
        }
        dups
    }

    /**
     * Computes the expected callsite log lines for a 3-layer inheritance Subject child's
     * {@code invokeAll()} sequence. The lines mirror what CallsiteCapture captures from stdout
     * with the standard logback pattern {@code %-5level %logger{0} - %msg%n}.
     *
     * <p>invokeAll order: gpOwn, pOwn, cOwn, inheritedMethod (override chain via super).
     * Each method block:
     *   - BOUNDARY marker line (from contract.boundary logger).
     *   - ENTER + EXIT pair iff the declaring class's effective class policy is not SUPPRESS.
     *
     * <p>inheritedMethod emits in stack-nested order along the super-chain:
     *   C-ENTER, P-ENTER, GP-ENTER, GP-EXIT, P-EXIT, C-EXIT.
     * Any layer whose effective class policy is SUPPRESS contributes neither ENTER nor EXIT,
     * but the super-chain bytecode still runs (so remaining layers still emit normally).
     *
     * <p>Parameter rendering per ENTER:
     *   x: follows effective class policy (MASK -> "***", else "arg-x"); d dropped; s -> "***"; l -> "arg-l".
     * EXIT value follows the same rule as x.
     */
    static List<String> expectedCallsiteForChild(String childAnno, String parentAnno, String grandparentAnno) {
        String selfEffC = childAnno
        String selfEffP = parentAnno
        String selfEffGp = grandparentAnno
        String overrideEffC = firstNonPlain([childAnno, parentAnno, grandparentAnno])
        String overrideEffP = firstNonPlain([parentAnno, grandparentAnno])
        String overrideEffGp = grandparentAnno

        String childClass = "Inh3C${childAnno}FromP${parentAnno}Gp${grandparentAnno}Subject"
        String parentClass = "Inh3P${parentAnno}FromGp${grandparentAnno}Subject"
        String grandparentClass = "Inh3Gp${grandparentAnno}Subject"

        List<String> lines = []

        lines << "INFO  boundary - ===BOUNDARY gpOwn==="
        lines.addAll(simpleMethodLines(grandparentClass, 'gpOwn', selfEffGp))

        lines << "INFO  boundary - ===BOUNDARY pOwn==="
        lines.addAll(simpleMethodLines(parentClass, 'pOwn', selfEffP))

        lines << "INFO  boundary - ===BOUNDARY cOwn==="
        lines.addAll(simpleMethodLines(childClass, 'cOwn', selfEffC))

        lines << "INFO  boundary - ===BOUNDARY inheritedMethod==="
        lines << enterLine(childClass, 'inheritedMethod', overrideEffC)
        lines << enterLine(parentClass, 'inheritedMethod', overrideEffP)
        lines << enterLine(grandparentClass, 'inheritedMethod', overrideEffGp)
        lines << exitLine(grandparentClass, 'inheritedMethod', overrideEffGp)
        lines << exitLine(parentClass, 'inheritedMethod', overrideEffP)
        lines << exitLine(childClass, 'inheritedMethod', overrideEffC)

        lines << "INFO  boundary - ===BOUNDARY END==="
        lines
    }

    private static List<String> simpleMethodLines(String declaringClass, String methodName, String effectivePolicy) {
        [enterLine(declaringClass, methodName, effectivePolicy), exitLine(declaringClass, methodName, effectivePolicy)]
    }

    private static String enterLine(String declaringClass, String methodName, String effectivePolicy) {
        if (effectivePolicy == 'DoNotLog') {
            return "INFO  ${declaringClass} - |> [ENTER] ${declaringClass}.${methodName}(s=***, l=arg-l)"
        }
        String xRendered = effectivePolicy == 'Sensitive' ? '***' : 'arg-x'
        "INFO  ${declaringClass} - |> [ENTER] ${declaringClass}.${methodName}(x=${xRendered}, s=***, l=arg-l)"
    }

    private static String exitLine(String declaringClass, String methodName, String effectivePolicy) {
        if (effectivePolicy == 'DoNotLog') {
            return "INFO  ${declaringClass} - |< [EXIT] ${declaringClass}.${methodName}()"
        }
        String valueRendered = effectivePolicy == 'Sensitive' ? '***' : 'arg-x'
        "INFO  ${declaringClass} - |< [EXIT] ${declaringClass}.${methodName}(value=${valueRendered})"
    }
}
