package com.example.cloud.post.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.cloud.post.service.ISensitiveWordService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/sensitive")
public class SensitiveWordController {

    @Resource
    private ISensitiveWordService sensitiveWordService;

    @PostMapping("/check")
    public SaResult check(@RequestBody String text) {
        return SaResult.ok().setData(sensitiveWordService.containsSensitiveWord(text));
    }

    @PostMapping("/find")
    public SaResult find(@RequestBody String text) {
        Set<String> words = sensitiveWordService.findSensitiveWords(text);
        return SaResult.ok().setData(words);
    }

    @PostMapping("/filter")
    public SaResult filter(@RequestBody String text, @RequestParam(defaultValue = "*") char replacement) {
        return SaResult.ok().setData(sensitiveWordService.filterSensitiveWords(text, replacement));
    }

    @PostMapping("/highlight")
    public SaResult highlight(@RequestBody String text) {
        return SaResult.ok().setData(sensitiveWordService.highlightSensitiveWords(text));
    }

    @GetMapping("/list")
    public SaResult list() {
        List<String> words = sensitiveWordService.getAllSensitiveWords();
        return SaResult.ok().setData(words);
    }

    @PostMapping("/add")
    public SaResult add(@RequestParam String word) {
        sensitiveWordService.addSensitiveWord(word);
        return SaResult.ok("添加成功");
    }

    @PostMapping("/remove")
    public SaResult remove(@RequestParam String word) {
        sensitiveWordService.removeSensitiveWord(word);
        return SaResult.ok("删除成功");
    }
}
