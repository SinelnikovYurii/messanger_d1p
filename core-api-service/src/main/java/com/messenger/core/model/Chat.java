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
@Table(name = "chats")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"messages", "participants"})
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ChatType type = ChatType.PRIVATE;

    private String description;

    private String avatarUrl;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime lastActivity;

    // Поле для хранения ID создателя чата
    @Column(name = "created_by")
    private Long createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "chat_participants",
        joinColumns = @JoinColumn(name = "chat_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> participants;

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Message> messages;

    public enum ChatType {
        PRIVATE, GROUP
    }
}
