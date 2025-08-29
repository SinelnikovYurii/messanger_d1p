package com.messenger.gateway.repository;


import com.messenger.gateway.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId ORDER BY m.timestamp DESC")
    List<Message> findMessagesByChatId(Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.timestamp > :timestamp ORDER BY m.timestamp ASC")
    List<Message> findMessagesByChatIdAfterTimestamp(Long chatId, java.time.LocalDateTime timestamp);
}
