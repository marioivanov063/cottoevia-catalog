package org.cottoevia;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Moved from org.bklvsc.shoppingcart to org.cottoevia during the rename.
 *
 * The previous location was the root cause of the build failure: this
 * class declared package org.bklvsc.shoppingcart, but no
 * @SpringBootApplication class ever existed in or above that package —
 * the real application class lived in the flat org.bklvsc package, one
 * level outside the scan path @SpringBootTest uses by default (it scans
 * upward from the test class's own package when no explicit `classes =`
 * is given). Spring Boot could not find a configuration to bootstrap the
 * test context, which is what broke compilation/test execution.
 *
 * Placing this test directly in org.cottoevia — the same package as
 * CottoeviaCatalogApplication — fixes that: the scan now finds it
 * immediately.
 */
@SpringBootTest
class CottoeviaCatalogApplicationTests {

    @Test
    void contextLoads() {
        System.out.println("hi");
    }
}
