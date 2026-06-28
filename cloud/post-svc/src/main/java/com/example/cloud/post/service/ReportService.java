package com.example.cloud.post.service;

import com.example.cloud.common.exception.BusinessException;
import com.example.cloud.post.entity.Report;
import com.example.cloud.post.repository.ReportRepository;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 举报服务（简化版：去掉 @Cacheable、admin 权限检查改为简单 header 判断）。
 */
@Slf4j
@Service
public class ReportService {

    private static final Set<String> VALID_TARGET_TYPES = Set.of("POST", "COMMENT", "USER");
    private static final Set<String> VALID_REASON_TYPES = Set.of("SPAM", "ABUSE", "ILLEGAL", "OTHER");
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "PROCESSING", "RESOLVED", "REJECTED");

    @Resource
    private ReportRepository reportRepository;

    @Transactional(rollbackFor = Exception.class)
    public String submitReport(Long reporterId, String targetType, Long targetId,
                               String reasonType, String reasonDetail) {
        if (reporterId == null) throw new BusinessException("请先登录");
        if (!StringUtils.hasText(targetType) || targetId == null || !StringUtils.hasText(reasonType)) {
            throw new BusinessException("举报参数不完整");
        }
        if (!VALID_TARGET_TYPES.contains(targetType)) throw new BusinessException("无效的举报对象类型");
        if (!VALID_REASON_TYPES.contains(reasonType)) throw new BusinessException("无效的举报原因类型");

        // 重复举报检查
        Specification<Report> spec = (root, query, cb) -> {
            Predicate p = cb.equal(root.get("reporterId"), reporterId);
            p = cb.and(p, cb.equal(root.get("targetType"), targetType));
            p = cb.and(p, cb.equal(root.get("targetId"), targetId));
            p = cb.and(p, cb.equal(root.get("status"), "PENDING"));
            return p;
        };
        if (reportRepository.count(spec) > 0) throw new BusinessException("您已举报过该内容，请等待处理");

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReasonType(reasonType);
        report.setReasonDetail(reasonDetail);
        report.setStatus("PENDING");
        report.setCreateTime(LocalDateTime.now());
        reportRepository.save(report);
        return "举报提交成功，我们会尽快处理";
    }

    public Page<Report> getReportList(String status, int pageNum, int pageSize) {
        Specification<Report> spec = (root, query, cb) -> {
            if (StringUtils.hasText(status)) return cb.equal(root.get("status"), status);
            return cb.conjunction();
        };
        return reportRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
    }

    @Transactional(rollbackFor = Exception.class)
    public String handleReport(Long handlerId, Long reportId, String status, String handleResult) {
        if (!VALID_STATUSES.contains(status)) throw new BusinessException("无效的状态值");
        Report report = reportRepository.findById(reportId).orElseThrow(() -> new BusinessException("举报不存在"));
        report.setStatus(status);
        report.setHandlerId(handlerId);
        report.setHandleResult(handleResult);
        report.setHandleTime(LocalDateTime.now());
        reportRepository.save(report);
        return "处理成功";
    }

    public Page<Report> getMyReports(Long reporterId, int pageNum, int pageSize) {
        return reportRepository.findByReporterIdOrderByCreateTimeDesc(reporterId,
                PageRequest.of(pageNum - 1, pageSize));
    }

    public Long countByStatus(String status) {
        if (!StringUtils.hasText(status)) return reportRepository.count();
        Specification<Report> spec = (root, query, cb) -> cb.equal(root.get("status"), status);
        return reportRepository.count(spec);
    }
}
