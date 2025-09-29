package com.messenger.core.repository;

import com.messenger.core.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    // Найти все чаты пользователя с оптимизированной загрузкой
    @Query("SELECT DISTINCT c FROM Chat c " +
           "LEFT JOIN FETCH c.participants p " +
           "LEFT JOIN FETCH c.createdBy " +
           "WHERE p.id = :userId " +
           "ORDER BY c.lastMessageAt DESC")
    List<Chat> findChatsByUserIdWithParticipants(@Param("userId") Long userId);

    // Найти все чаты пользователя (простой запрос)
    @Query("SELECT c FROM Chat c JOIN c.participants p WHERE p.id = :userId ORDER BY c.lastMessageAt DESC")
    List<Chat> findChatsByUserId(@Param("userId") Long userId);

    // Найти приватный чат между двумя пользователями
    @Query("SELECT c FROM Chat c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE c.chatType = 'PRIVATE' AND p1.id = :user1Id AND p2.id = :user2Id " +
           "AND (SELECT COUNT(p) FROM c.participants p) = 2")
    Optional<Chat> findPrivateChatBetweenUsers(@Param("user1Id") Long user1Id,
                                              @Param("user2Id") Long user2Id);

    // Найти групповые чаты пользователя
    @Query("SELECT c FROM Chat c JOIN c.participants p WHERE p.id = :userId AND c.chatType = 'GROUP'")
    List<Chat> findGroupChatsByUserId(@Param("userId") Long userId);

    // Найти чаты по названию (для поиска)
    @Query("SELECT c FROM Chat c WHERE c.chatName LIKE %:name% AND c.chatType = 'GROUP'")
    List<Chat> findChatsByNameContaining(@Param("name") String name);

    // Проверить, является ли пользователь участником чата
    @Query("SELECT COUNT(p) > 0 FROM Chat c JOIN c.participants p WHERE c.id = :chatId AND p.id = :userId")
    boolean isUserParticipant(@Param("chatId") Long chatId, @Param("userId") Long userId);

    // Найти чат с загруженными участниками
    @Query("SELECT c FROM Chat c LEFT JOIN FETCH c.participants WHERE c.id = :chatId")
    Optional<Chat> findByIdWithParticipants(@Param("chatId") Long chatId);

    // Найти чат с загруженными участниками и создателем
    @Query("SELECT c FROM Chat c " +
           "LEFT JOIN FETCH c.participants " +
           "LEFT JOIN FETCH c.createdBy " +
           "WHERE c.id = :chatId")
    Optional<Chat> findByIdWithParticipantsAndCreatedBy(@Param("chatId") Long chatId);

    // Найти чаты пользователя с участниками (batch loading)
    @Query("SELECT DISTINCT c FROM Chat c " +
           "LEFT JOIN FETCH c.participants " +
           "WHERE c.id IN :chatIds")
    List<Chat> findChatsByIdsWithParticipants(@Param("chatIds") List<Long> chatIds);
}
