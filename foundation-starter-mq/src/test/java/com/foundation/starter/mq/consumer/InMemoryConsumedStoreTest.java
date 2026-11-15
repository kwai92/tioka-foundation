package com.foundation.starter.mq.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link InMemoryConsumedStore} 行为（F-02 BAS-24 §foundation-starter-mq）。 */
class InMemoryConsumedStoreTest {

    @Test
    void 首次为真重复为假() {
        InMemoryConsumedStore store = new InMemoryConsumedStore(60_000L, 1000);

        assertTrue(store.markConsumed("c1", "m1"));
        assertFalse(store.markConsumed("c1", "m1"));
        assertTrue(store.markConsumed("c1", "m2"));
        assertTrue(store.markConsumed("c2", "m1"));
    }

    @Test
    void 超过保留窗口后可再次登记() throws InterruptedException {
        InMemoryConsumedStore store = new InMemoryConsumedStore(50L, 1000);

        assertTrue(store.markConsumed("c1", "m1"));
        assertFalse(store.markConsumed("c1", "m1"));

        Thread.sleep(120L);

        assertTrue(store.markConsumed("c1", "m1"));
    }
}
