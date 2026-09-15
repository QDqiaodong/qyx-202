package com.example.shiproom.service;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.entity.ElectricAppliance;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.repository.ElectricApplianceRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ElectricApplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ElectricApplianceService.class);
    private static final String REDIS_POWER_KEY = "appliance:power";

    @Autowired
    private ElectricApplianceRepository electricApplianceRepository;

    @Autowired
    private LoungeRoomRepository loungeRoomRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private RoomShipRelationRepository roomShipRelationRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ShiftOperationLockService shiftOperationLockService;

    @Transactional
    public ElectricApplianceDTO create(ElectricApplianceDTO dto) {
        shiftOperationLockService.lock();
        validateRoomShipBinding(dto.getRoomId(), dto.getShipId());
        if (electricApplianceRepository.existsByDeviceCode(dto.getDeviceCode())) {
            throw new RuntimeException("设备编号已存在");
        }
        ElectricAppliance appliance = new ElectricAppliance();
        appliance.setDeviceCode(dto.getDeviceCode());
        appliance.setDeviceName(dto.getDeviceName());
        appliance.setPower(dto.getPower());
        appliance.setApplianceType(dto.getApplianceType());
        appliance.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        appliance.setRoomId(dto.getRoomId());
        appliance.setShipId(dto.getShipId());

        ElectricAppliance saved = electricApplianceRepository.save(appliance);
        updateRedisPower(saved);
        return convertToDTO(saved);
    }

    @Transactional
    public ElectricApplianceDTO update(Long id, ElectricApplianceDTO dto) {
        shiftOperationLockService.lock();
        ElectricAppliance appliance = electricApplianceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        validateRoomShipBinding(dto.getRoomId(), dto.getShipId());
        if (!java.util.Objects.equals(appliance.getRoomId(), dto.getRoomId())
                || !java.util.Objects.equals(appliance.getShipId(), dto.getShipId())) {
            throw new RuntimeException("不能通过电器编辑直接改挂休息室或船舶，请使用绑定或换班功能一次改齐");
        }

        if (!appliance.getDeviceCode().equals(dto.getDeviceCode()) &&
                electricApplianceRepository.existsByDeviceCode(dto.getDeviceCode())) {
            throw new RuntimeException("设备编号已存在");
        }

        appliance.setDeviceCode(dto.getDeviceCode());
        appliance.setDeviceName(dto.getDeviceName());
        appliance.setPower(dto.getPower());
        appliance.setApplianceType(dto.getApplianceType());
        appliance.setStatus(dto.getStatus());
        appliance.setRoomId(dto.getRoomId());
        appliance.setShipId(dto.getShipId());

        ElectricAppliance saved = electricApplianceRepository.save(appliance);
        updateRedisPower(saved);
        return convertToDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        shiftOperationLockService.lock();
        ElectricAppliance appliance = electricApplianceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForZSet().remove(REDIS_POWER_KEY, appliance.getDeviceCode());
            } catch (Exception e) {
                logger.warn("Failed to remove from Redis: {}", e.getMessage());
            }
        }
        electricApplianceRepository.delete(appliance);
    }

    public ElectricApplianceDTO findById(Long id) {
        ElectricAppliance appliance = electricApplianceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("设备不存在"));
        return convertToDTO(appliance);
    }

    public List<ElectricApplianceDTO> findAll() {
        return electricApplianceRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ElectricApplianceDTO> findByRoomId(Long roomId) {
        return electricApplianceRepository.findByRoomId(roomId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ElectricApplianceDTO> findByShipId(Long shipId) {
        return electricApplianceRepository.findByShipId(shipId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private void validateRoomShipBinding(Long roomId, Long shipId) {
        if (roomId == null && shipId == null) {
            return;
        }
        if (roomId == null || shipId == null) {
            throw new RuntimeException("房间占用和电器所属船舶必须同时登记，不能只改一层");
        }
        loungeRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("休息室不存在"));
        shipRepository.findById(shipId)
                .orElseThrow(() -> new RuntimeException("船舶不存在"));
        boolean active = roomShipRelationRepository.findByRoomId(roomId).stream()
                .anyMatch(relation -> shipId.equals(relation.getShipId()) && "ACTIVE".equals(relation.getStatus()));
        if (!active) {
            throw new RuntimeException("房间当前未停靠该船舶，不能登记电器归属");
        }
    }

    private void updateRedisPower(ElectricAppliance appliance) {
        if (redisTemplate == null) {
            logger.warn("RedisTemplate not available");
            return;
        }
        try {
            redisTemplate.opsForZSet().add(REDIS_POWER_KEY, appliance.getDeviceCode(),
                    appliance.getPower().doubleValue());
            logger.info("Updated Redis power for device: {}", appliance.getDeviceCode());
        } catch (Exception e) {
            logger.error("Failed to update Redis power: {}", e.getMessage(), e);
        }
    }

    private ElectricApplianceDTO convertToDTO(ElectricAppliance appliance) {
        ElectricApplianceDTO dto = new ElectricApplianceDTO();
        dto.setId(appliance.getId());
        dto.setDeviceCode(appliance.getDeviceCode());
        dto.setDeviceName(appliance.getDeviceName());
        dto.setPower(appliance.getPower());
        dto.setApplianceType(appliance.getApplianceType());
        dto.setStatus(appliance.getStatus());
        dto.setRoomId(appliance.getRoomId());
        dto.setShipId(appliance.getShipId());
        dto.setLastChangeBatch(appliance.getLastChangeBatch());

        if (appliance.getRoomId() != null) {
            loungeRoomRepository.findById(appliance.getRoomId())
                    .ifPresent(room -> {
                        dto.setRoomCode(room.getRoomCode());
                        dto.setRoomName(room.getRoomName());
                    });
        }

        if (appliance.getShipId() != null) {
            shipRepository.findById(appliance.getShipId())
                    .ifPresent(ship -> {
                        dto.setShipCode(ship.getShipCode());
                        dto.setShipName(ship.getShipName());
                    });
        }

        return dto;
    }
}