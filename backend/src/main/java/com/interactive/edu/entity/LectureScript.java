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
@Table(name = "lecture_script")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class LectureScript {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "courseware_id", length = 64, nullable = false)
    private String coursewareId;

    @Column(name = "page_index", nullable = false)
    private Integer pageIndex;

    @Column(name = "node_id", length = 64, nullable = false, unique = true)
    private String nodeId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "audio_url", length = 512)
    private String audioUrl;

    // AUTO, EDITED
    @Column(name = "edit_status", length = 32)
    private String editStatus = "AUTO";

    @CreatedDate
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
