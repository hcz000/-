package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.entity.Report;
import com.example.demo.service.IReportService;
import com.example.demo.util.PageParamUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Api(tags = "举报管理", description = "提供用户举报相关接口")
@RestController
@RequestMapping("/report")
public class ReportController {

    @Resource
    private IReportService reportService;

    @ApiOperation(value = "提交举报")
    @PostMapping("/submit")
    public SaResult submit(@RequestParam String targetType,
                           @RequestParam Long targetId,
                           @RequestParam String reasonType,
                           @RequestParam(required = false) String reasonDetail) {
        return SaResult.ok(reportService.submitReport(targetType, targetId, reasonType, reasonDetail));
    }

    @ApiOperation(value = "获取举报列表（管理员）")
    @GetMapping("/list")
    public SaResult list(@RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer page,
                         @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Report> pageData = reportService.getReportList(status, p, s);
        return SaResult.ok().setData(pageData);
    }

    @ApiOperation(value = "处理举报（管理员）")
    @PostMapping("/handle")
    public SaResult handle(@RequestParam Long reportId,
                           @RequestParam String status,
                           @RequestParam(required = false) String handleResult) {
        return SaResult.ok(reportService.handleReport(reportId, status, handleResult));
    }

    @ApiOperation(value = "获取我提交的举报列表")
    @GetMapping("/my")
    public SaResult myReports(@RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        int p = PageParamUtil.resolvePage(page, null);
        int s = PageParamUtil.resolveSize(size, null, 10, 100);
        Page<Report> pageData = reportService.getMyReports(p, s);
        return SaResult.ok().setData(pageData);
    }
}
