package com.messenger.core.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"sender", "chat"})
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    private MessageType type = MessageType.TEXT;

    @CreationTimestamp
    private LocalDateTime sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    private Boolean isEdited = false;

    private LocalDateTime editedAt;

    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.SENT;

    public enum MessageType {
        TEXT, IMAGE, FILE, AUDIO, VIDEO
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }
}
