package com.messenger.core.repository;

import com.messenger.core.model.Chat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ChatRepositoryTest {
    @Autowired
    private ChatRepository chatRepository;

    @Test
    void testSaveAndFind() {
        Chat chat = new Chat();
        chat.setChatName("RepoTest");
        chat.setChatType(Chat.ChatType.GROUP);
        Chat saved = chatRepository.save(chat);
        assertNotNull(saved.getId());
        List<Chat> all = chatRepository.findAll();
        assertTrue(all.stream().anyMatch(c -> "RepoTest".equals(c.getChatName())));
    }
}

