package com.messenger.core.repository;

import com.messenger.core.model.Friendship;
import com.messenger.core.model.Friendship.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByRequesterId_Id(Long requesterId);
    List<Friendship> findByReceiverId_Id(Long receiverId);

    List<Friendship> findByRequesterId_IdAndStatus(Long requesterId, FriendshipStatus status);
    List<Friendship> findByReceiverId_IdAndStatus(Long receiverId, FriendshipStatus status);

    // Поиск запроса дружбы по ID отправителя и получателя
    Optional<Friendship> findByRequesterId_IdAndReceiverId_IdAndStatus(
            Long requesterId, Long receiverId, FriendshipStatus status);

    // Добавляем метод для поиска любых отношений между пользователями
    List<Friendship> findByRequesterIdOrReceiverId(Long userId, Long sameUserId);

    // Найти дружескую связь между двумя пользователями
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :user1Id AND f.receiver.id = :user2Id) OR " +
           "(f.requester.id = :user2Id AND f.receiver.id = :user1Id)")
    Optional<Friendship> findFriendshipBetweenUsers(@Param("user1Id") Long user1Id,
                                                   @Param("user2Id") Long user2Id);

    // Найти все входящие запросы дружбы пользователя
    @Query("SELECT f FROM Friendship f WHERE f.receiver.id = :userId AND f.status = :status")
    List<Friendship> findIncomingFriendRequests(@Param("userId") Long userId,
                                               @Param("status") FriendshipStatus status);

    // Найти все исходящие запросы дружбы пользователя
    @Query("SELECT f FROM Friendship f WHERE f.requester.id = :userId AND f.status = :status")
    List<Friendship> findOutgoingFriendRequests(@Param("userId") Long userId,
                                               @Param("status") FriendshipStatus status);

    // Найти всех друзей пользователя
    @Query("SELECT f FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.receiver.id = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendships(@Param("userId") Long userId);

    // Проверить статус дружбы между пользователями
    @Query("SELECT f.status FROM Friendship f WHERE " +
           "(f.requester.id = :user1Id AND f.receiver.id = :user2Id) OR " +
           "(f.requester.id = :user2Id AND f.receiver.id = :user1Id)")
    Optional<FriendshipStatus> getFriendshipStatus(@Param("user1Id") Long user1Id,
                                                  @Param("user2Id") Long user2Id);

    // Проверить, заблокирован ли пользователь (существует связь со статусом BLOCKED)
    @Query("SELECT COUNT(f) > 0 FROM Friendship f WHERE " +
           "((f.requester.id = :requesterId AND f.receiver.id = :receiverId) OR " +
           "(f.requester.id = :receiverId AND f.receiver.id = :requesterId)) AND f.status = 'BLOCKED'")
    boolean isBlocked(@Param("requesterId") Long requesterId, @Param("receiverId") Long receiverId);
}
