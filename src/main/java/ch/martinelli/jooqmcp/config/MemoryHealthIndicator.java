package ch.martinelli.jooqmcp.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@Component
public class MemoryHealthIndicator implements HealthIndicator {

    private static final double MEMORY_THRESHOLD = 0.85; // 85% threshold

    @Override
    public Health health() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        
        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        double memoryUsageRatio = (double) usedMemory / maxMemory;
        
        Health.Builder status = memoryUsageRatio < MEMORY_THRESHOLD ? Health.up() : Health.down();
        
        return status
                .withDetail("heap.used", formatBytes(usedMemory))
                .withDetail("heap.max", formatBytes(maxMemory))
                .withDetail("heap.usage", String.format("%.2f%%", memoryUsageRatio * 100))
                .withDetail("heap.free", formatBytes(maxMemory - usedMemory))
                .withDetail("nonHeap.used", formatBytes(memoryBean.getNonHeapMemoryUsage().getUsed()))
                .build();
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
}