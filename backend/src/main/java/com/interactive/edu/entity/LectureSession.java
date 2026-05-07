package com.interactive.edu.entity;

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
@Table(name = "lecture_session")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class LectureSession {

    @Id
    @Column(name = "id", length = 128, nullable = false)
    private String id;

    @Column(name = "courseware_id", length = 64, nullable = false)
    private String coursewareId;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "current_page_index")
    private Integer currentPageIndex = 1;

    @Column(name = "current_node_id", length = 64)
    private String currentNodeId;

    @Column(name = "resume_token", length = 255)
    private String resumeToken;

    // ACTIVE, PAUSED, FINISHED
    @Column(name = "status", length = 32)
    private String status = "ACTIVE";

    // POOR, NORMAL, GOOD
    @Column(name = "understanding_level", length = 32)
    private String understandingLevel = "NORMAL";

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}