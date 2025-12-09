package com.messenger.core.repository;

import com.messenger.core.model.MessageReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {

    /**
     * Найти статус прочтения конкретного сообщения конкретным пользователем
     */
    @Query("SELECT mrs FROM MessageReadStatus mrs WHERE mrs.message.id = :messageId AND mrs.user.id = :userId")
    Optional<MessageReadStatus> findByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * Получить все статусы прочтения для сообщения
     */
    @Query("SELECT mrs FROM MessageReadStatus mrs " +
           "LEFT JOIN FETCH mrs.user " +
           "WHERE mrs.message.id = :messageId")
    List<MessageReadStatus> findByMessageId(@Param("messageId") Long messageId);

    /**
     * Получить статусы прочтения для списка сообщений
     */
    @Query("SELECT mrs FROM MessageReadStatus mrs " +
           "LEFT JOIN FETCH mrs.user " +
           "WHERE mrs.message.id IN :messageIds")
    List<MessageReadStatus> findByMessageIdIn(@Param("messageIds") List<Long> messageIds);

    /**
     * Проверить, прочитано ли сообщение пользователем
     */
    @Query("SELECT COUNT(mrs) > 0 FROM MessageReadStatus mrs " +
           "WHERE mrs.message.id = :messageId AND mrs.user.id = :userId")
    boolean existsByMessageIdAndUserId(@Param("messageId") Long messageId, @Param("userId") Long userId);

    /**
     * Получить количество пользователей, прочитавших сообщение
     */
    @Query("SELECT COUNT(mrs) FROM MessageReadStatus mrs WHERE mrs.message.id = :messageId")
    long countByMessageId(@Param("messageId") Long messageId);

    /**
     * Получить ID непрочитанных сообщений в чате для пользователя
     */
    @Query("SELECT m.id FROM Message m " +
           "WHERE m.chat.id = :chatId " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND NOT EXISTS (SELECT 1 FROM MessageReadStatus mrs WHERE mrs.message.id = m.id AND mrs.user.id = :userId)")
    List<Long> findUnreadMessageIdsInChat(@Param("chatId") Long chatId, @Param("userId") Long userId);

    /**
     * Подсчитать количество непрочитанных сообщений в чате для пользователя
     */
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE m.chat.id = :chatId " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND NOT EXISTS (SELECT 1 FROM MessageReadStatus mrs WHERE mrs.message.id = m.id AND mrs.user.id = :userId)")
    long countUnreadMessagesInChat(@Param("chatId") Long chatId, @Param("userId") Long userId);

    /**
     * Получить статусы прочтения для сообщений в чате
     */
    @Query("SELECT mrs FROM MessageReadStatus mrs " +
           "LEFT JOIN FETCH mrs.user " +
           "WHERE mrs.message.chat.id = :chatId " +
           "AND mrs.message.id IN :messageIds")
    List<MessageReadStatus> findByChatIdAndMessageIdIn(@Param("chatId") Long chatId, @Param("messageIds") List<Long> messageIds);

    /**
     * Возвращает список массивов вида Object[]{chatId, unreadCount} для чатов, где есть непрочитанные сообщения.
     * <p>Каждый элемент: [Long chatId, Long unreadCount]</p>
     *
     * @param chatIds список ID чатов
     * @param userId ID пользователя
     * @return список массивов Object[]{chatId, unreadCount}
     */
    @Query("SELECT m.chat.id as chatId, COUNT(m) as unreadCount " +
           "FROM Message m " +
           "WHERE m.chat.id IN :chatIds " +
           "AND m.sender.id != :userId " +
           "AND m.isDeleted = false " +
           "AND NOT EXISTS (SELECT 1 FROM MessageReadStatus mrs WHERE mrs.message.id = m.id AND mrs.user.id = :userId) " +
           "GROUP BY m.chat.id")
    List<Object[]> countUnreadMessagesForChats(@Param("chatIds") List<Long> chatIds, @Param("userId") Long userId);
}
