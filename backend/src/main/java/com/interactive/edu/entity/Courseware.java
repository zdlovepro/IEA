package com.interactive.edu.entity;

import com.interactive.edu.enums.CoursewareStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.EntityListeners;

import java.time.LocalDateTime;

@Entity
@Table(name = "courseware")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Courseware {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "file_url", length = 512, nullable = false)
    private String fileUrl;

    @Column(name = "file_type", length = 32, nullable = false)
    private String fileType;

    @Column(name = "status", length = 64, nullable = false)
    private String status = CoursewareStatus.UPLOADED.name();

    @Column(name = "uploader_id", length = 64)
    private String uploaderId;

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
