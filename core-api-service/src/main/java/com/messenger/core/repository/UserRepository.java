package com.messenger.core.repository;

import com.messenger.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);

    // Поиск пользователей по имени пользователя или имени/фамилии
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<User> searchUsers(@Param("query") String query);

    // Найти всех друзей пользователя
    @Query("SELECT CASE WHEN f.requester.id = :userId THEN f.receiver ELSE f.requester END " +
           "FROM Friendship f WHERE " +
           "(f.requester.id = :userId OR f.receiver.id = :userId) AND f.status = 'ACCEPTED'")
    List<User> findFriendsByUserId(@Param("userId") Long userId);

    // Проверить, существует ли пользователь с данным username или email
    boolean existsByUsernameOrEmail(String username, String email);
}
