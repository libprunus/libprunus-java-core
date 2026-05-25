package com.example.runtime;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CallsiteCapture {

    private CallsiteCapture() {}

    public static synchronized String capture(Runnable body) {
        LoggerFactory.getLogger("contract.bootstrap").info("bootstrap");
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream capture = new PrintStream(buf, true, StandardCharsets.UTF_8);
        System.setOut(capture);
        System.setErr(capture);
        try {
            body.run();
        } finally {
            System.setOut(origOut);
            System.setErr(origErr);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    public static <T> T step(Logger boundary, String name, Supplier<T> action) {
        boundary.info("===BOUNDARY " + name + "===");
        return action.get();
    }
}
