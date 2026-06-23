package com.example.demo.task;

import com.example.demo.service.UserVectorBufferService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserVectorFlushTask {

    @Resource
    private UserVectorBufferService userVectorBufferService;

    @Scheduled(fixedDelayString = "${user-vector.flush.delay-ms:1000}")
    public void flushBufferedVectors() {
        UserVectorBufferService.FlushStats stats = userVectorBufferService.flushOnce(200, 128);
        if (stats.events() > 0) {
            log.info("User interest model flush done: users={}, events={}", stats.users(), stats.events());
        }
    }
}
