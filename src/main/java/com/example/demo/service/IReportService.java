package com.example.demo.service;

import com.example.demo.entity.Report;
import org.springframework.data.domain.Page;

public interface IReportService {

    /**
     * 提交举报
     */
    String submitReport(String targetType, Long targetId, String reasonType, String reasonDetail);

    /**
     * 获取举报列表（管理员）
     */
    Page<Report> getReportList(String status, int pageNum, int pageSize);

    /**
     * 处理举报（管理员）
     */
    String handleReport(Long reportId, String status, String handleResult);

    /**
     * 获取我提交的举报列表
     */
    Page<Report> getMyReports(int pageNum, int pageSize);

    /**
     * 统计举报数量（支持条件筛选）
     */
    Long countByStatus(String status);

    Report getById(Long id);

    Report save(Report report);
}