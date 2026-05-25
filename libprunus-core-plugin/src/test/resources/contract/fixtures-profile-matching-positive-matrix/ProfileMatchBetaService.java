package com.beta;

import com.example.runtime.CallsiteCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileMatchBetaService {

    public String run(String x) {
        return x;
    }

    public static String invokeAll() {
        return CallsiteCapture.capture(() -> {
            Logger boundary = LoggerFactory.getLogger("contract.boundary");
            ProfileMatchBetaService inst = new ProfileMatchBetaService();
            CallsiteCapture.step(boundary, "run", () -> inst.run("arg-run"));
            boundary.info("===BOUNDARY END===");
        });
    }
}
