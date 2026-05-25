package org.libprunus.core.plugin.aot.log.fixture.inspect;

import java.util.List;

public class InspectOverloadService {

    public String compute() {
        return "empty";
    }

    public String compute(int a) {
        return "int:" + a;
    }

    public String compute(long a) {
        return "long:" + a;
    }

    public String compute(double a) {
        return "double:" + (int) a;
    }

    public String compute(String s) {
        if (s == null) return "str:null";
        return "str:" + s.length();
    }

    public String compute(int a, int b) {
        return "ii:" + (a + b);
    }

    public String compute(int a, String s) {
        return "is:" + (a + (s == null ? 0 : s.length()));
    }

    public String compute(String s, int n) {
        if (s == null || n <= 0 || n > s.length()) return "si:oob";
        return "si:" + s.substring(0, n);
    }

    public String compute(String a, String b) {
        return "ss:" + (a == null ? "" : a) + (b == null ? "" : b);
    }

    public String compute(int a, long b) {
        return "il:" + (a + b);
    }

    public String compute(List<String> items) {
        return "list:" + (items == null ? 0 : items.size());
    }

    public String compute(int a, int b, int c) {
        return "iii:" + (a + b + c);
    }
}
