package com.interactive.edu.repository;

import com.interactive.edu.entity.Courseware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursewareRepository extends JpaRepository<Courseware, String> {
}
