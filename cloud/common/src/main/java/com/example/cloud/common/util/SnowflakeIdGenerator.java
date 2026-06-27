package com.example.cloud.common.util;

/**
 * 雪花算法 ID 生成器（与单体版逻辑保持一致）。
 * <p>
 * 注意：当前 MACHINE_ID 硬编码为 1，单实例可用；
 * 微服务多实例部署时需要按实例区分 machineId 以避免冲突，
 * 后续可以从环境变量 / Nacos 配置注入。
 */
public class SnowflakeIdGenerator {

    // 起始时间戳 (2024-01-01)
    private static final long START_TIMESTAMP = 1704067200000L;

    // 机器ID (0-31)
    private static final long MACHINE_ID = 1L;

    // 序列号
    private static long sequence = 0L;

    // 上次生成时间
    private static long lastTimestamp = -1L;

    /**
     * 生成下一个 ID
     */
    public static synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis();

        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("时钟回退，拒绝生成 ID");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & 4095L; // 12位序列号
            if (sequence == 0) {
                // 序列号溢出，等待下一毫秒
                currentTimestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = currentTimestamp;

        return ((currentTimestamp - START_TIMESTAMP) << 22)
                | (MACHINE_ID << 12)
                | sequence;
    }

    private static long waitNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}
