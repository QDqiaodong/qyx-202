package com.example.shiproom.config;

import com.example.shiproom.entity.PatrolOfficer;
import com.example.shiproom.entity.PatrolWindow;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.repository.PatrolOfficerRepository;
import com.example.shiproom.repository.PatrolWindowRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 夜班巡检交班演示数据：
 * - 两名值班长（在岗/停用各一）与一名巡检员，用来证明“只有本班值班长能交”；
 * - 给第一条在泊船建一个“昨夜 20:00 至今晨 08:00”的接班窗口。
 * 已有同名窗口或无船舶时跳过，不影响正式数据。
 */
@Configuration
public class PatrolSeedDataConfig {

    @Bean
    CommandLineRunner initPatrolSeedData(PatrolOfficerRepository officerRepository,
                                         PatrolWindowRepository windowRepository,
                                         ShipRepository shipRepository) {
        return args -> {
            seedOfficer(officerRepository, "LEADER-ZHAO", "赵值班长", "LEADER", "ACTIVE");
            seedOfficer(officerRepository, "LEADER-SUN", "孙前值班长", "LEADER", "INACTIVE");
            seedOfficer(officerRepository, "MEMBER-QIAN", "钱巡检员", "MEMBER", "ACTIVE");

            if (windowRepository.existsByWindowCode("W-NIGHT-SEED")) {
                return;
            }
            Ship ship = shipRepository.findAll().stream().findFirst().orElse(null);
            if (ship == null) {
                return;
            }
            LocalDateTime start = LocalDateTime.of(LocalDate.now(), LocalTime.of(20, 0));
            PatrolWindow window = new PatrolWindow();
            window.setWindowCode("W-NIGHT-SEED");
            window.setWindowName("夜班接班窗口（当日20:00-次日08:00）");
            window.setShipId(ship.getId());
            window.setShipCode(ship.getShipCode());
            window.setStartTime(start);
            window.setEndTime(start.plusHours(12));
            window.setStatus("ACTIVE");
            window.setOperator("SYSTEM");
            window.setRemark("系统初始化演示窗口");
            try {
                windowRepository.save(window);
            } catch (Exception ignored) {
                // 并发启动时由另一个实例写入
            }
        };
    }

    private void seedOfficer(PatrolOfficerRepository repository, String code, String name,
                             String role, String status) {
        if (repository.existsByOfficerCode(code)) {
            return;
        }
        PatrolOfficer officer = new PatrolOfficer();
        officer.setOfficerCode(code);
        officer.setOfficerName(name);
        officer.setRole(role);
        officer.setStatus(status);
        try {
            repository.save(officer);
        } catch (Exception ignored) {
            // 并发启动时由另一个实例写入
        }
    }
}
