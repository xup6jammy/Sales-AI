package com.example.salesai;

public final class SmokeTest {
    public static void main(String[] args) {
        new SmokeTest().run();
    }

    void run() {
        testAssertionsAreEnabled();
        System.out.println("SmokeTest: 1 passed");
    }

    void testAssertionsAreEnabled() {
        boolean enabled = false;
        assert enabled = true;
        if (!enabled) {
            throw new AssertionError(
                "Assertions are not enabled — run with `java -ea`");
        }
    }
}
