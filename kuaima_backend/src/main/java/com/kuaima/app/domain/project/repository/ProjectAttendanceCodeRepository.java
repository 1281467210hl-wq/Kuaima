package com.kuaima.app.domain.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kuaima.app.domain.project.entity.ProjectAttendanceCode;

public interface ProjectAttendanceCodeRepository
        extends JpaRepository<ProjectAttendanceCode, Long> {

    List<ProjectAttendanceCode> findByProjectIdAndStatusOrderByIdDesc(Long projectId, String status);
}
