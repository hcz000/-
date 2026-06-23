package com.example.demo.controller;

import cn.dev33.satoken.util.SaResult;
import com.example.demo.service.ISensitiveWordService;
import com.example.demo.service.Impl.SensitiveWordServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
/**
 * 违禁词管理控制器
 */

@RestController
@RequestMapping("/sensitive")
public class SensitiveWordController {

    @Resource
    private ISensitiveWordService sensitiveWordService;

    @Resource
    private SensitiveWordServiceImpl sensitiveWordServiceImpl;

    /**
     * 检查文本是否包含违禁词
     */
    @PostMapping("/check")
    public SaResult check(@RequestBody String text) {
        boolean contains = sensitiveWordService.containsSensitiveWord(text);
        return SaResult.ok().setData(contains);
    }

    /**
     * 查找文本中的违禁词
     */
    @PostMapping("/find")
    public SaResult find(@RequestBody String text) {
        Set<String> words = sensitiveWordService.findSensitiveWords(text);
        return SaResult.ok().setData(words);
    }

    /**
     * 过滤文本中的违禁词
     */
    @PostMapping("/filter")
    public SaResult filter(@RequestBody String text, @RequestParam(defaultValue = "*") char replacement) {
        String filtered = sensitiveWordService.filterSensitiveWords(text, replacement);
        return SaResult.ok().setData(filtered);
    }

    /**
     * 高亮显示违禁词
     */
    @PostMapping("/highlight")
    public SaResult highlight(@RequestBody String text) {
        String highlighted = sensitiveWordService.highlightSensitiveWords(text);
        return SaResult.ok().setData(highlighted);
    }

    /**
     * 获取所有违禁词（管理员）
     */
    @GetMapping("/list")
    public SaResult list() {
        List<String> words = sensitiveWordService.getAllSensitiveWords();
        return SaResult.ok().setData(words);
    }

    /**
     * 添加违禁词（管理员）
     */
    @PostMapping("/add")
    public SaResult add(@RequestParam String word) {
        sensitiveWordService.addSensitiveWord(word);
        return SaResult.ok("添加成功");
    }

    /**
     * 删除违禁词（管理员）
     */
    @PostMapping("/remove")
    public SaResult remove(@RequestParam String word) {
        sensitiveWordService.removeSensitiveWord(word);
        return SaResult.ok("删除成功");
    }

    /**
     * 从文件加载违禁词（管理员）
     */
    @PostMapping("/load")
    public SaResult load(@RequestParam String filePath) {
        sensitiveWordService.loadSensitiveWords(filePath);
        return SaResult.ok("加载成功");
    }

    /**
     * 从 Redis 同步到内存（管理员）
     * 多实例部署时，某个实例修改后可调用此接口同步
     */
    @PostMapping("/sync")
    public SaResult sync() {
        sensitiveWordServiceImpl.syncFromRedis();
        return SaResult.ok("同步成功");
    }
}