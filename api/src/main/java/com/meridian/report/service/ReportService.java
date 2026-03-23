package com.meridian.report.service;

import com.meridian.report.entity.Report;
import com.meridian.report.repository.ReportRepository;
import com.meridian.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Report Service - Manages research report retrieval and storage
 * Task 20-21 will implement full storage and RAG chat functionality
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportService {

    private final ReportRepository reportRepository;

    /**
     * Get report by ID
     */
    public Optional<Report> getReportById(UUID reportId) {
        return reportRepository.findById(reportId);
    }

    /**
     * Get reports for a project
     */
    public Page<Report> getReportsByProject(UUID projectId, Pageable pageable) {
        return reportRepository.findByProjectId(projectId, pageable);
    }

    /**
     * Update report visibility
     */
    public Report updateReportVisibility(UUID reportId, boolean isPublic) {
        Report report = getReportById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        report.setIsPublic(isPublic);
        return reportRepository.save(report);
    }
}
