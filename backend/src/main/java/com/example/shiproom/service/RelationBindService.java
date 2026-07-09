package com.example.shiproom.service;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RelationBindDTO;
import com.example.shiproom.dto.RelationChangeLogDTO;
import com.example.shiproom.entity.*;
import com.example.shiproom.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RelationBindService {

    @Autowired
    private RoomShipRelationRepository roomShipRelationRepository;

    @Autowired
    private ElectricApplianceRepository electricApplianceRepository;

    @Autowired
    private LoungeRoomRepository loungeRoomRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private RelationChangeLogRepository relationChangeLogRepository;

    @Transactional
    public void bindDevice(RelationBindDTO dto) {
        ElectricAppliance appliance = null;

        if (dto.getDeviceId() != null) {
            appliance = electricApplianceRepository.findById(dto.getDeviceId())
                    .orElseThrow(() -> new RuntimeException("设备不存在"));
        } else if (dto.getDeviceCode() != null) {
            appliance = electricApplianceRepository.findByDeviceCode(dto.getDeviceCode())
                    .orElseThrow(() -> new RuntimeException("设备不存在"));
        } else {
            throw new RuntimeException("设备ID或设备编号不能为空");
        }

        LoungeRoom room = null;
        if (dto.getRoomId() != null) {
            room = loungeRoomRepository.findById(dto.getRoomId())
                    .orElseThrow(() -> new RuntimeException("休息室不存在"));
        } else if (dto.getRoomCode() != null) {
            room = loungeRoomRepository.findByRoomCode(dto.getRoomCode())
                    .orElseThrow(() -> new RuntimeException("休息室不存在"));
        }

        Ship ship = null;
        if (dto.getShipId() != null) {
            ship = shipRepository.findById(dto.getShipId())
                    .orElseThrow(() -> new RuntimeException("船舶不存在"));
        } else if (dto.getShipCode() != null) {
            ship = shipRepository.findByShipCode(dto.getShipCode())
                    .orElseThrow(() -> new RuntimeException("船舶不存在"));
        }

        Long oldRoomId = appliance.getRoomId();
        Long oldShipId = appliance.getShipId();

        appliance.setRoomId(room != null ? room.getId() : null);
        appliance.setShipId(ship != null ? ship.getId() : null);
        electricApplianceRepository.save(appliance);

        if (room != null && ship != null) {
            RoomShipRelation existingRelation = roomShipRelationRepository
                    .findByRoomIdAndShipId(room.getId(), ship.getId()).orElse(null);

            if (existingRelation == null) {
                RoomShipRelation relation = new RoomShipRelation();
                relation.setRoomId(room.getId());
                relation.setShipId(ship.getId());
                relation.setRelationType("ELECTRIC_BIND");
                relation.setStatus("ACTIVE");
                roomShipRelationRepository.save(relation);
            } else {
                existingRelation.setStatus("ACTIVE");
                roomShipRelationRepository.save(existingRelation);
            }
        }

        saveChangeLog(appliance, oldRoomId, oldShipId, room, ship, "BIND", dto.getOperator(), dto.getRemark());
    }

    @Transactional
    public void updateRelation(Long roomId, Long newShipId, String operator, String remark) {
        LoungeRoom room = loungeRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("休息室不存在"));

        Ship newShip = shipRepository.findById(newShipId)
                .orElseThrow(() -> new RuntimeException("船舶不存在"));

        List<ElectricAppliance> appliances = electricApplianceRepository.findByRoomId(roomId);

        List<RoomShipRelation> oldRelations = roomShipRelationRepository.findByRoomId(roomId);
        for (RoomShipRelation relation : oldRelations) {
            relation.setStatus("INACTIVE");
            roomShipRelationRepository.save(relation);
        }

        RoomShipRelation newRelation = roomShipRelationRepository
                .findByRoomIdAndShipId(roomId, newShipId).orElse(null);

        if (newRelation == null) {
            newRelation = new RoomShipRelation();
            newRelation.setRoomId(roomId);
            newRelation.setShipId(newShipId);
            newRelation.setRelationType("ROOM_SHIP");
            newRelation.setStatus("ACTIVE");
        } else {
            newRelation.setStatus("ACTIVE");
        }
        roomShipRelationRepository.save(newRelation);

        Ship oldShip = null;
        if (!oldRelations.isEmpty()) {
            oldShip = shipRepository.findById(oldRelations.get(0).getShipId()).orElse(null);
        }

        for (ElectricAppliance appliance : appliances) {
            Long oldShipId = appliance.getShipId();
            appliance.setShipId(newShipId);
            electricApplianceRepository.save(appliance);

            saveChangeLog(appliance, roomId, oldShipId, room, newShip, "SHIP_CHANGE", operator, remark);
        }
    }

    @Transactional
    public void shipChange(Long oldShipId, Long newShipId, String operator, String remark) {
        Ship oldShip = shipRepository.findById(oldShipId)
                .orElseThrow(() -> new RuntimeException("原船舶不存在"));

        Ship newShip = shipRepository.findById(newShipId)
                .orElseThrow(() -> new RuntimeException("新船舶不存在"));

        List<ElectricAppliance> appliances = electricApplianceRepository.findByShipId(oldShipId);

        for (ElectricAppliance appliance : appliances) {
            Long roomId = appliance.getRoomId();
            appliance.setShipId(newShipId);
            electricApplianceRepository.save(appliance);

            RoomShipRelation oldRelation = roomShipRelationRepository
                    .findByRoomIdAndShipId(roomId, oldShipId).orElse(null);
            if (oldRelation != null) {
                oldRelation.setStatus("INACTIVE");
                roomShipRelationRepository.save(oldRelation);
            }

            RoomShipRelation newRelation = roomShipRelationRepository
                    .findByRoomIdAndShipId(roomId, newShipId).orElse(null);
            if (newRelation == null) {
                newRelation = new RoomShipRelation();
                newRelation.setRoomId(roomId);
                newRelation.setShipId(newShipId);
                newRelation.setRelationType("SHIP_CHANGE");
                newRelation.setStatus("ACTIVE");
            } else {
                newRelation.setStatus("ACTIVE");
            }
            roomShipRelationRepository.save(newRelation);

            LoungeRoom room = loungeRoomRepository.findById(roomId).orElse(null);
            saveChangeLog(appliance, roomId, oldShipId, room, newShip, "SHIP_SWITCH", operator, remark);
        }
    }

    public List<ElectricApplianceDTO> getAppliancesByShip(Long shipId) {
        return electricApplianceRepository.findByShipId(shipId).stream()
                .map(this::convertToApplianceDTO)
                .collect(Collectors.toList());
    }

    public List<ElectricApplianceDTO> getAppliancesByRoom(Long roomId) {
        return electricApplianceRepository.findByRoomId(roomId).stream()
                .map(this::convertToApplianceDTO)
                .collect(Collectors.toList());
    }

    public LoungeRoomDTO getRoomWithShip(Long roomId) {
        LoungeRoom room = loungeRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("休息室不存在"));

        LoungeRoomDTO dto = new LoungeRoomDTO();
        dto.setId(room.getId());
        dto.setRoomCode(room.getRoomCode());
        dto.setRoomName(room.getRoomName());
        dto.setFloor(room.getFloor());
        dto.setCapacity(room.getCapacity());
        dto.setStatus(room.getStatus());

        roomShipRelationRepository.findByRoomId(roomId)
                .stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .findFirst()
                .ifPresent(relation -> {
                    shipRepository.findById(relation.getShipId())
                            .ifPresent(ship -> {
                                dto.setShipCode(ship.getShipCode());
                                dto.setShipName(ship.getShipName());
                            });
                });

        return dto;
    }

    public List<RelationChangeLogDTO> getChangeLogs(Long deviceId, Long roomId, Long shipId) {
        List<RelationChangeLog> logs = new ArrayList<>();

        if (deviceId != null) {
            logs.addAll(relationChangeLogRepository.findByDeviceId(deviceId));
        } else if (roomId != null) {
            logs.addAll(relationChangeLogRepository.findByRoomId(roomId));
        } else if (shipId != null) {
            logs.addAll(relationChangeLogRepository.findByShipId(shipId));
        } else {
            logs = relationChangeLogRepository.findAll();
        }

        return logs.stream()
                .map(this::convertToLogDTO)
                .collect(Collectors.toList());
    }

    private void saveChangeLog(ElectricAppliance appliance, Long oldRoomId, Long oldShipId,
                               LoungeRoom newRoom, Ship newShip, String changeType,
                               String operator, String remark) {
        RelationChangeLog log = new RelationChangeLog();
        log.setChangeType(changeType);
        log.setDeviceId(appliance.getId());
        log.setDeviceCode(appliance.getDeviceCode());

        if (newRoom != null) {
            log.setRoomId(newRoom.getId());
            log.setRoomCode(newRoom.getRoomCode());
        }
        if (newShip != null) {
            log.setShipId(newShip.getId());
            log.setShipCode(newShip.getShipCode());
        }

        if (oldRoomId != null) {
            log.setOldRoomId(oldRoomId);
            loungeRoomRepository.findById(oldRoomId)
                    .ifPresent(r -> log.setOldRoomCode(r.getRoomCode()));
        }
        if (oldShipId != null) {
            log.setOldShipId(oldShipId);
            shipRepository.findById(oldShipId)
                    .ifPresent(s -> log.setOldShipCode(s.getShipCode()));
        }

        log.setOperator(operator != null ? operator : "SYSTEM");
        log.setRemark(remark);

        relationChangeLogRepository.save(log);
    }

    private ElectricApplianceDTO convertToApplianceDTO(ElectricAppliance appliance) {
        ElectricApplianceDTO dto = new ElectricApplianceDTO();
        dto.setId(appliance.getId());
        dto.setDeviceCode(appliance.getDeviceCode());
        dto.setDeviceName(appliance.getDeviceName());
        dto.setPower(appliance.getPower());
        dto.setApplianceType(appliance.getApplianceType());
        dto.setStatus(appliance.getStatus());
        dto.setRoomId(appliance.getRoomId());
        dto.setShipId(appliance.getShipId());

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

    private RelationChangeLogDTO convertToLogDTO(RelationChangeLog log) {
        RelationChangeLogDTO dto = new RelationChangeLogDTO();
        dto.setId(log.getId());
        dto.setChangeType(log.getChangeType());
        dto.setDeviceId(log.getDeviceId());
        dto.setDeviceCode(log.getDeviceCode());
        dto.setRoomId(log.getRoomId());
        dto.setRoomCode(log.getRoomCode());
        dto.setShipId(log.getShipId());
        dto.setShipCode(log.getShipCode());
        dto.setOldRoomId(log.getOldRoomId());
        dto.setOldRoomCode(log.getOldRoomCode());
        dto.setOldShipId(log.getOldShipId());
        dto.setOldShipCode(log.getOldShipCode());
        dto.setOperator(log.getOperator());
        dto.setRemark(log.getRemark());
        dto.setChangeTime(log.getChangeTime());
        return dto;
    }
}