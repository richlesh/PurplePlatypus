package com.markdownpro;

import org.junit.Test;
import static org.junit.Assert.*;

public class MainTest {
    @Test
    public void testMain() {
        assertNotNull("Main class should exist", new Main());
    }
}
