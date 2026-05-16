package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.service.IOssService;
import io.swagger.annotations.Api;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Api(tags = "文件上传", description = "提供文件上传功能")
@RestController
@RequestMapping("/oss")
public class OssController {

    @Resource
    private IOssService ossService;

    @PostMapping("/upload")
    public SaResult upload(@RequestPart("file") MultipartFile file) {
        return SaResult.ok().setData(ossService.uploadImage(file));
    }
}
