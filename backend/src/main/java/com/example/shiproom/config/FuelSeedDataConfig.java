package com.example.shiproom.config;

import com.example.shiproom.entity.EmergencyGenerator;
import com.example.shiproom.repository.EmergencyGeneratorRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 应急发电机演示档案：码头楼顶那台应急发电机 + 泵房备用机。
 * 已有同机号档案时跳过，不影响正式数据。
 */
@Configuration
public class FuelSeedDataConfig {

    @Bean
    CommandLineRunner initFuelSeedData(EmergencyGeneratorRepository generatorRepository) {
        return args -> {
            seedGenerator(generatorRepository, "GEN-ROOF-01", "码头楼顶应急发电机", "码头楼顶",
                    new BigDecimal("120.00"));
            seedGenerator(generatorRepository, "GEN-PUMP-02", "泵房备用发电机", "码头泵房",
                    new BigDecimal("60.00"));
        };
    }

    private void seedGenerator(EmergencyGeneratorRepository repository, String code, String name,
                               String location, BigDecimal stock) {
        if (repository.existsByGenCode(code)) {
            return;
        }
        EmergencyGenerator generator = new EmergencyGenerator();
        generator.setGenCode(code);
        generator.setGenName(name);
        generator.setLocation(location);
        generator.setStatus("ACTIVE");
        generator.setFuelStockLiters(stock);
        generator.setRemark("系统初始化演示发电机");
        try {
            repository.save(generator);
        } catch (Exception ignored) {
            // 并发启动时由另一个实例写入
        }
    }
}
