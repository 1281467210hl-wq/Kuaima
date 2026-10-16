package com.kuaima.app.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "admin_agreement")
@Getter
@Setter
public class Agreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 60)
    private String type;

    @Column(nullable = false, length = 10)
    private String contentMode = "INPUT";

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_object_key", length = 500)
    private String fileObjectKey;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "upload_admin_id")
    private Long uploadAdminId;

    @Column(name = "upload_account", length = 100)
    private String uploadAccount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
