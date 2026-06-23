package com.example.demo.service.Impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.demo.config.CacheConfig;
import com.example.demo.entity.Report;
import com.example.demo.entity.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.ReportRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.IReportService;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ReportServiceImpl implements IReportService {

    @Resource
    private UserRepository userRepository;
    @Resource
    private ReportRepository reportRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_MY_REPORTS, allEntries = true)
    public String submitReport(String targetType, Long targetId, String reasonType, String reasonDetail) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }

        if (!StringUtils.hasText(targetType) || targetId == null || !StringUtils.hasText(reasonType)) {
            throw new BusinessException("举报参数不完整");
        }

        if (!isValidTargetType(targetType)) {
            throw new BusinessException("无效的举报对象类型");
        }

        if (!isValidReasonType(reasonType)) {
            throw new BusinessException("无效的举报原因类型");
        }

        Long reporterId = StpUtil.getLoginIdAsLong();
        Specification<Report> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("reporterId"), reporterId));
            predicate = cb.and(predicate, cb.equal(root.get("targetType"), targetType));
            predicate = cb.and(predicate, cb.equal(root.get("targetId"), targetId));
            predicate = cb.and(predicate, cb.equal(root.get("status"), "PENDING"));
            return predicate;
        };
        if (reportRepository.count(spec) > 0) {
            throw new BusinessException("您已举报过该内容，请等待处理");
        }

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

    @Override
    public Page<Report> getReportList(String status, int pageNum, int pageSize) {
        checkAdminPermission();

        Specification<Report> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (StringUtils.hasText(status)) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return reportRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = CacheConfig.CACHE_MY_REPORTS, allEntries = true)
    public String handleReport(Long reportId, String status, String handleResult) {
        checkAdminPermission();

        if (reportId == null || !StringUtils.hasText(status)) {
            throw new BusinessException("参数不完整");
        }

        if (!isValidHandleStatus(status)) {
            throw new BusinessException("无效的处理状态");
        }

        Report report = reportRepository.findById(reportId).orElse(null);
        if (report == null) {
            throw new BusinessException("举报记录不存在");
        }

        if ("RESOLVED".equals(report.getStatus()) || "REJECTED".equals(report.getStatus())) {
            throw new BusinessException("该举报已处理完成");
        }

        report.setStatus(status);
        report.setHandlerId(StpUtil.getLoginIdAsLong());
        report.setHandleResult(handleResult);
        report.setHandleTime(LocalDateTime.now());

        reportRepository.save(report);

        if ("RESOLVED".equals(status)) {
            handleTarget(report);
        }

        return "处理成功";
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_MY_REPORTS, key = "T(cn.dev33.satoken.stp.StpUtil).getLoginIdAsLong() + ':' + #pageNum + ':' + #pageSize")
    public Page<Report> getMyReports(int pageNum, int pageSize) {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("请先登录");
        }

        Long reporterId = StpUtil.getLoginIdAsLong();
        Pageable pageable = PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        return reportRepository.findByReporterIdOrderByCreateTimeDesc(reporterId, pageable);
    }

    @Override
    public Long countByStatus(String status) {
        Specification<Report> spec = (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            if (status != null && !status.isEmpty()) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
        return reportRepository.count(spec);
    }

    private void handleTarget(Report report) {
        // reserved
    }

    private void checkAdminPermission() {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        User user = userRepository.findById(currentUserId).orElse(null);

        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        if (!isAdmin(user)) {
            throw new BusinessException("无管理员权限");
        }
    }

    private boolean isAdmin(User user) {
        return "admin".equals(user.getRole());
    }

    private boolean isValidTargetType(String targetType) {
        return "POST".equals(targetType) || "COMMENT".equals(targetType) || "USER".equals(targetType);
    }

    private boolean isValidReasonType(String reasonType) {
        return "SPAM".equals(reasonType) || "ABUSE".equals(reasonType)
                || "ILLEGAL".equals(reasonType) || "OTHER".equals(reasonType);
    }

    private boolean isValidHandleStatus(String status) {
        return "PROCESSING".equals(status) || "RESOLVED".equals(status) || "REJECTED".equals(status);
    }

    @Override
    public Report getById(Long id) {
        return reportRepository.findById(id).orElse(null);
    }

    @Override
    public Report save(Report report) {
        return reportRepository.save(report);
    }
}
