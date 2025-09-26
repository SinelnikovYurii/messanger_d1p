package com.messenger.core.repository;

import com.messenger.core.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByChatIdOrderBySentAtDesc(Long chatId, Pageable pageable);

    List<Message> findByChatIdOrderBySentAtAsc(Long chatId);

    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Message> searchInChat(@Param("chatId") Long chatId, @Param("query") String query);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chat.id = :chatId AND m.sender.id != :userId AND m.status != 'READ'")
    Long countUnreadMessagesInChat(@Param("chatId") Long chatId, @Param("userId") Long userId);
}
