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

    // Найти сообщения чата с пагинацией и загруженными связями
    @Query("SELECT m FROM Message m " +
           "LEFT JOIN FETCH m.sender " +
           "LEFT JOIN FETCH m.chat " +
           "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Message> findByChatIdOrderByCreatedAtDescWithSender(@Param("chatId") Long chatId, Pageable pageable);

    // Найти сообщения чата с пагинацией (без дополнительных загрузок)
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findByChatIdOrderByCreatedAtDesc(@Param("chatId") Long chatId, Pageable pageable);

    // Найти последнее сообщение в чате с загруженным отправителем
    @Query("SELECT m FROM Message m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.chat.id = :chatId AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Message> findLastMessageByChatIdWithSender(@Param("chatId") Long chatId, Pageable pageable);

    // Найти последнее сообщение в чате
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false ORDER BY m.createdAt DESC")
    List<Message> findLastMessageByChatId(@Param("chatId") Long chatId, Pageable pageable);

    // Поиск сообщений в чате по содержимому с загруженными связями
    @Query("SELECT m FROM Message m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.chat.id = :chatId AND " +
           "LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) AND m.isDeleted = false " +
           "ORDER BY m.createdAt DESC")
    List<Message> searchMessagesInChatWithSender(@Param("chatId") Long chatId,
                                                 @Param("query") String query,
                                                 Pageable pageable);

    // Поиск сообщений в чате по содержимому
    @Query("SELECT m FROM Message m WHERE m.chat.id = :chatId AND " +
            "LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    List<Message> searchMessagesInChat(@Param("chatId") Long chatId,
                                       @Param("query") String query,
                                       Pageable pageable);

    // Подсчет непрочитанных сообщений в чате
    @Query("SELECT COUNT(m) FROM Message m WHERE m.chat.id = :chatId AND m.isDeleted = false")
    Long countMessagesByChatId(@Param("chatId") Long chatId);

    // Batch loading для сообщений с отправителями
    @Query("SELECT m FROM Message m " +
           "LEFT JOIN FETCH m.sender " +
           "WHERE m.id IN :messageIds")
    List<Message> findMessagesByIdsWithSender(@Param("messageIds") List<Long> messageIds);

    // Найти последние сообщения для списка чатов
    @Query("SELECT DISTINCT m1 FROM Message m1 " +
           "LEFT JOIN FETCH m1.sender " +
           "WHERE m1.id IN (" +
           "  SELECT MAX(m2.id) FROM Message m2 " +
           "  WHERE m2.chat.id IN :chatIds AND m2.isDeleted = false " +
           "  GROUP BY m2.chat.id" +
           ")")
    List<Message> findLastMessagesByChatIds(@Param("chatIds") List<Long> chatIds);
}
