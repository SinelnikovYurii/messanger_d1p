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

    // Найти сообщения чата с пагинацией
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findByChatIdOrderByCreatedAtDesc(@Param("chatId") Long chatId, Pageable pageable);

    // Найти последнее сообщение в чате
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findLastMessageByChatId(@Param("chatId") Long chatId, Pageable pageable);

    // Поиск сообщений в чате по содержимому
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    List<Message> searchMessagesInChat(@Param("chatId") Long chatId,
                                       @Param("query") String query,
                                       Pageable pageable);

    // Подсчет непрочитанных сообщений в чате (можно расширить позже)
    @Query("SELECT COUNT(m) FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false")
    Long countMessagesByChatId(@Param("chatId") Long chatId);
}
