package com.messenger.core.dto.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SendMessageRequestTest {
    @Test
    void testEqualsAndHashCode_sameValues() {
        SendMessageRequest req1 = new SendMessageRequest();
        req1.setChatId(1L);
        req1.setContent("Hello");
        req1.setType("TEXT");

        SendMessageRequest req2 = new SendMessageRequest();
        req2.setChatId(1L);
        req2.setContent("Hello");
        req2.setType("TEXT");

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentValues() {
        SendMessageRequest req1 = new SendMessageRequest();
        req1.setChatId(1L);
        req1.setContent("Hello");
        req1.setType("TEXT");

        SendMessageRequest req2 = new SendMessageRequest();
        req2.setChatId(2L);
        req2.setContent("World");
        req2.setType("IMAGE");

        assertNotEquals(req1, req2);
        assertNotEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void testEquals_nullAndOtherType() {
        SendMessageRequest req = new SendMessageRequest();
        req.setChatId(1L);
        req.setContent("Hello");
        req.setType("TEXT");

        assertNotEquals(req, null);
        assertNotEquals(req, "string");
    }

    @Test
    void testEqualsAndHashCode_emptyFields() {
        SendMessageRequest req1 = new SendMessageRequest();
        SendMessageRequest req2 = new SendMessageRequest();
        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
    }
}

