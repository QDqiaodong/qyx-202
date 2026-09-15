package com.example.shiproom.config;

import com.example.shiproom.entity.ShiftOperationLock;
import com.example.shiproom.repository.ShiftOperationLockRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ShiftOperationLockConfig {

    @Bean
    public CommandLineRunner initShiftOperationLock(ShiftOperationLockRepository repository) {
        return args -> {
            if (repository.existsById(1L)) {
                return;
            }
            try {
                repository.saveAndFlush(new ShiftOperationLock());
            } catch (Exception ignored) {
                // 并发启动时另一个实例已经完成初始化；换班事务中的行锁会再确认这一行。
            }
        };
    }
}
