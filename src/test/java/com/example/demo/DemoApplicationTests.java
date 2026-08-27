package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DemoApplicationTests {

    @Test
    void contextLoads() {
        // Verify that the main class exists and is instantiable
        assertDoesNotThrow(() -> DemoApplication.class.getDeclaredConstructor().newInstance());
    }
}
