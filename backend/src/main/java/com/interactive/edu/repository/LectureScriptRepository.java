package com.interactive.edu.repository;

import com.interactive.edu.entity.LectureScript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LectureScriptRepository extends JpaRepository<LectureScript, String> {
    List<LectureScript> findByCoursewareIdOrderByPageIndexAsc(String coursewareId);
    void deleteByCoursewareId(String coursewareId);
}
