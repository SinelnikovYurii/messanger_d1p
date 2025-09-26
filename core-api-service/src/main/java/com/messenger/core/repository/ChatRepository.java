package com.messenger.core.repository;

import com.messenger.core.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("SELECT c FROM Chat c JOIN c.participants p WHERE p.id = :userId")
    List<Chat> findByParticipantId(@Param("userId") Long userId);

    @Query("SELECT c FROM Chat c WHERE c.type = 'PRIVATE' AND SIZE(c.participants) = 2 AND EXISTS (SELECT p1 FROM c.participants p1 WHERE p1.id = :user1) AND EXISTS (SELECT p2 FROM c.participants p2 WHERE p2.id = :user2)")
    Chat findPrivateChatBetweenUsers(@Param("user1") Long user1Id, @Param("user2") Long user2Id);
}
