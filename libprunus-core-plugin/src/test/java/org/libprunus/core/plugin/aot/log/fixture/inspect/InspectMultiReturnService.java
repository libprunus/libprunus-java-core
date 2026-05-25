package org.libprunus.core.plugin.aot.log.fixture.inspect;

import java.util.List;
import org.libprunus.core.log.annotation.DoNotLog;
import org.libprunus.core.log.annotation.Sensitive;

public class InspectMultiReturnService {

    /** Four exit paths (null, empty, short, long). Input is masked. */
    public String classify(@Sensitive String input) {
        if (input == null) return "null";
        if (input.isEmpty()) return "empty";
        if (input.length() < 4) return "short";
        return "long";
    }

    /** In-loop return and end-of-loop return. Target is ignored in logs. */
    public int firstIndex(List<String> items, @DoNotLog String target) {
        if (items == null || target == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (target.equals(items.get(i))) return i;
        }
        return -1;
    }

    /** Two early returns on zero via short-circuit; one normal return. */
    public int multiply(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return a * b;
    }

    /** Void method; all params masked at method level. */
    @Sensitive
    public void record(String key, String value) {
        // no-op
    }

    /**
     * long parameter occupies 2 LVT slots, pushing firstLocal to 4 for this instance method.
     * Instrumentation injects a long returnValueSlot (2 slots) and a logger slot (1 slot),
     * yielding shiftAmount=3. Local variables result (long, slots 4-5) and i (int, slot 6)
     * are both shifted to slots 7-8 and 9 respectively. IINC on i and LSTORE/LLOAD on result
     * must all use the shifted indices, or the loop produces incorrect output.
     *
     * <p>Odd iterations (i%2 != 0) subtract 1 from result; even iterations add i to result.
     * Starting the loop at i=1 ensures the very first iteration exercises the odd path,
     * allowing each branch to be tested in isolation across the where-table.
     */
    public long accumulate(long base, int steps) {
        long result = base;
        for (int i = 1; i <= steps; i++) {
            if (i % 2 == 0) {
                result += i;
            } else {
                result -= 1;
            }
        }
        return result;
    }

    /**
     * int parameters keep firstLocal=3. Local double acc (2 slots, 3-4) and int i (1 slot, 5)
     * are shifted by shiftAmount=3 (double returnValueSlot + loggerSlot) to slots 6-7 and 8.
     * DSTORE/DLOAD on acc and IINC on i must use the shifted indices.
     * Odd iterations subtract base; even iterations add base * i; both paths change acc.
     */
    public double weighted(int base, int steps) {
        double acc = 0.0;
        for (int i = 1; i <= steps; i++) {
            if (i % 2 == 0) {
                acc += (double) base * i;
            } else {
                acc -= base;
            }
        }
        return acc;
    }

    /**
     * int parameters keep firstLocal=3. Local float acc (1 slot, 3) and int i (1 slot, 4)
     * are shifted by shiftAmount=2 (float returnValueSlot + loggerSlot) to slots 5 and 6.
     * FSTORE/FLOAD on acc and IINC on i must use the shifted indices.
     * Even iterations add 0.5f; odd iterations subtract 1.5f; both paths change acc.
     */
    public float average(int total, int count) {
        float acc = total;
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                acc += 0.5f;
            } else {
                acc -= 1.5f;
            }
        }
        return acc;
    }

    /**
     * firstLocal=3 (this + sep ref + count int). Local StringBuilder sb (ref, slot 3) and
     * int i (slot 4) are shifted by shiftAmount=2 (ref returnValueSlot + loggerSlot) to
     * slots 5 and 6. ASTORE/ALOAD on sb and IINC on i must use the shifted indices.
     * If sb is accessed at the wrong slot the string is built incorrectly or a NullPointerException
     * is thrown.
     */
    public String join(String sep, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(sep);
            sb.append(i);
        }
        return sb.toString();
    }

    /**
     * Deliberately mixes every LVT-shifted instruction category in one method body.
     *
     * <p>Parameter layout (instance method): slot 0=this, 1-2=limit(long), 3-4=scale(double),
     * 5=items(ref); firstLocal=6. Instrumentation allocates returnValueSlot=6(ref, 1 slot) and
     * loggerSlot=7(ref, 1 slot), yielding shiftAmount=2. Every local variable is shifted by 2:
     *
     * <ul>
     *   <li>count (int, slot 6->8): IINC +1 / IINC -1 / IINC +100
     *   <li>total (long, slots 7-8->9-10): LSTORE / LLOAD
     *   <li>dsum (double, slots 9-10->11-12): DSTORE / DLOAD
     *   <li>fsum (float, slot 11->13): FSTORE / FLOAD
     *   <li>buf (StringBuilder ref, slot 12->14): ASTORE / ALOAD
     *   <li>i (int, slot 13->15): IINC +1 / ILOAD / ISTORE
     *   <li>s (String ref, slot 14->16): ASTORE / ALOAD
     * </ul>
     *
     * <p>All paths through the loop contribute to at least one accumulator, so a wrong shift on any
     * slot will corrupt the return value and be caught by the assertions.
     */
    public String complex(long limit, double scale, List<String> items) {
        int count = 0;
        long total = 0L;
        double dsum = 0.0;
        float fsum = 0.0f;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String s = items.get(i);
            if (s == null || s.isEmpty()) {
                count--;
                continue;
            }
            count++;
            total += s.length();
            dsum += scale;
            fsum += 1.0f;
            buf.append(s.charAt(0));
            if (total >= limit) {
                count += 100;
                break;
            }
        }
        return count + ":" + total + ":" + (long) dsum + ":" + (int) fsum + ":" + buf;
    }
}
