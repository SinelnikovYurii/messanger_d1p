package com.messenger.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sentMessages", "chats"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    private String displayName;

    private String avatarUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.OFFLINE;

    private LocalDateTime lastSeen;

    // Дополнительные поля для статуса пользователя
    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> sentMessages;

    @ManyToMany(mappedBy = "participants", fetch = FetchType.LAZY)
    private List<Chat> chats;

    public enum UserStatus {
        ONLINE, OFFLINE, AWAY, BUSY
    }
}
