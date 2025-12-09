package com.messenger.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = {"chats", "sentMessages", "sentFriendRequests", "receivedFriendRequests"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "is_online")
    private Boolean isOnline = false;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "identity_key", columnDefinition = "TEXT")
    private String identityKey;

    @Column(name = "signed_prekey", columnDefinition = "TEXT")
    private String signedPreKey;

    @Column(name = "signed_prekey_signature", columnDefinition = "TEXT")
    private String signedPreKeySignature;

    @Column(name = "one_time_prekeys", columnDefinition = "TEXT") // JSON-массив
    private String oneTimePreKeys;

    // Чаты, в которых участвует пользователь
    @ManyToMany(mappedBy = "participants", fetch = FetchType.LAZY)
    @BatchSize(size = 16)
    private Set<Chat> chats;

    // Отправленные сообщения
    @OneToMany(mappedBy = "sender", fetch = FetchType.LAZY)
    @BatchSize(size = 20)
    private List<Message> sentMessages;

    // Исходящие запросы дружбы
    @OneToMany(mappedBy = "requester", fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private Set<Friendship> sentFriendRequests;

    // Входящие запросы дружбы
    @OneToMany(mappedBy = "receiver", fetch = FetchType.LAZY)
    @BatchSize(size = 10)
    private Set<Friendship> receivedFriendRequests;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
