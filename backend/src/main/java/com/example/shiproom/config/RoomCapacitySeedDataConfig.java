package com.example.shiproom.config;

import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.service.RoomPowerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 老档案在用电承载字段上线前就已经存在，新增列对它们是 NULL。
 * 启动时把仍是 NULL 的房间承载回填为默认值，保证房间页三个数都能正常算。
 */
@Configuration
public class RoomCapacitySeedDataConfig {

    private static final Logger logger = LoggerFactory.getLogger(RoomCapacitySeedDataConfig.class);

    @Bean
    CommandLineRunner initRoomCapacity(LoungeRoomRepository loungeRoomRepository) {
        return args -> {
            List<LoungeRoom> rooms = loungeRoomRepository.findAll();
            int updated = 0;
            for (LoungeRoom room : rooms) {
                if (room.getPowerCapacity() == null) {
                    room.setPowerCapacity(RoomPowerService.DEFAULT_POWER_CAPACITY);
                    loungeRoomRepository.save(room);
                    updated++;
                }
            }
            if (updated > 0) {
                logger.info("已为 {} 间老休息室回填默认用电承载 {} kW",
                        updated, RoomPowerService.DEFAULT_POWER_CAPACITY);
            }
        };
    }
}
