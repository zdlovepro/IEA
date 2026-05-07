package com.interactive.edu.repository;

import com.interactive.edu.entity.CoursewarePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoursewarePageRepository extends JpaRepository<CoursewarePage, Long> {
    List<CoursewarePage> findByCoursewareIdOrderByPageIndexAsc(String coursewareId);
    void deleteByCoursewareId(String coursewareId);
}
