package com.messenger.core.dto.request;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateChatRequestTest {
    @Test
    void testEqualsAndHashCode_sameValues() {
        List<Long> ids = Arrays.asList(1L, 2L);
        CreateChatRequest req1 = new CreateChatRequest();
        req1.setName("chat");
        req1.setDescription("desc");
        req1.setType("GROUP");
        req1.setParticipantIds(ids);

        CreateChatRequest req2 = new CreateChatRequest();
        req2.setName("chat");
        req2.setDescription("desc");
        req2.setType("GROUP");
        req2.setParticipantIds(ids);

        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void testEqualsAndHashCode_differentValues() {
        CreateChatRequest req1 = new CreateChatRequest();
        req1.setName("chat1");
        req1.setDescription("desc1");
        req1.setType("GROUP");
        req1.setParticipantIds(Collections.singletonList(1L));

        CreateChatRequest req2 = new CreateChatRequest();
        req2.setName("chat2");
        req2.setDescription("desc2");
        req2.setType("PRIVATE");
        req2.setParticipantIds(Collections.singletonList(2L));

        assertNotEquals(req1, req2);
        assertNotEquals(req1.hashCode(), req2.hashCode());
    }

    @Test
    void testEquals_nullAndOtherType() {
        CreateChatRequest req = new CreateChatRequest();
        req.setName("chat");
        req.setDescription("desc");
        req.setType("GROUP");
        req.setParticipantIds(Arrays.asList(1L, 2L));

        assertNotEquals(null, req);
        assertNotEquals("string", req);
    }

    @Test
    void testEqualsAndHashCode_emptyFields() {
        CreateChatRequest req1 = new CreateChatRequest();
        CreateChatRequest req2 = new CreateChatRequest();
        assertEquals(req1, req2);
        assertEquals(req1.hashCode(), req2.hashCode());
    }
}
