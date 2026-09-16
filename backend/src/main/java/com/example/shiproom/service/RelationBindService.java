package com.example.shiproom.service;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RelationBindDTO;
import com.example.shiproom.dto.RelationChangeLogDTO;
import com.example.shiproom.dto.ShiftBlockedApplianceDTO;
import com.example.shiproom.dto.ShiftResultDTO;
import com.example.shiproom.entity.ElectricAppliance;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.RelationChangeLog;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.ShiftBlockedException;
import com.example.shiproom.repository.ElectricApplianceRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RelationChangeLogRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RelationBindService {

    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";

    private final RoomShipRelationRepository roomShipRelationRepository;
    private final ElectricApplianceRepository electricApplianceRepository;
    private final LoungeRoomRepository loungeRoomRepository;
    private final ShipRepository shipRepository;
    private final RelationChangeLogRepository relationChangeLogRepository;
    private final ShiftOperationLockService shiftOperationLockService;
    private final RoomPowerService roomPowerService;

    public RelationBindService(RoomShipRelationRepository roomShipRelationRepository,
                               ElectricApplianceRepository electricApplianceRepository,
                               LoungeRoomRepository loungeRoomRepository,
                               ShipRepository shipRepository,
                               RelationChangeLogRepository relationChangeLogRepository,
                               ShiftOperationLockService shiftOperationLockService,
                               RoomPowerService roomPowerService) {
        this.roomShipRelationRepository = roomShipRelationRepository;
        this.electricApplianceRepository = electricApplianceRepository;
        this.loungeRoomRepository = loungeRoomRepository;
        this.shipRepository = shipRepository;
        this.relationChangeLogRepository = relationChangeLogRepository;
        this.shiftOperationLockService = shiftOperationLockService;
        this.roomPowerService = roomPowerService;
    }

    @Transactional
    public void bindDevice(RelationBindDTO dto) {
        shiftOperationLockService.lock();

        ElectricAppliance appliance = findAppliance(dto);
        LoungeRoom room = findRoom(dto);
        Ship ship = findShip(dto);

        if (room == null || ship == null) {
            throw new RuntimeException("休息室和船舶不能为空");
        }
        if (appliance.getRoomId() != null && !Objects.equals(appliance.getRoomId(), room.getId())) {
            throw new RuntimeException("电器不能直接迁移休息室，请使用换班整房转移");
        }

        List<RoomShipRelation> roomRelations = roomShipRelationRepository.findByRoomIdForUpdate(room.getId());
        List<RoomShipRelation> activeRelations = roomRelations.stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .toList();
        RoomShipRelation currentRelation = activeRelations.stream().findFirst().orElse(null);
        if (currentRelation != null && !Objects.equals(currentRelation.getShipId(), ship.getId())) {
            throw new RuntimeException("电器所属船舶与房间当前停靠船舶不一致，请使用换班一次改齐");
        }
        if (appliance.getShipId() != null && !Objects.equals(appliance.getShipId(), ship.getId())) {
            throw new RuntimeException("电器不能通过绑定直接更换船舶，请使用换班整房转移");
        }
        if (isStopped(appliance)) {
            ShiftBlockedApplianceDTO blocked = convertToBlockedDTO(appliance, room, ship);
            throw new ShiftBlockedException(buildBlockedMessage(List.of(blocked)), List.of(blocked));
        }

        // 只有「把没挂房的电器挂进这间房」才算新挂入；已在该房的重绑在前面已被房间迁移检查挡住，
        // 所以到这里 oldRoomId 若有值必等于本房，不需要也不允许按挂入再校验承载。
        if (appliance.getRoomId() == null) {
            roomPowerService.assertCanAttach(room.getId(), appliance.getPower(),
                    appliance.getId(), appliance.getDeviceCode(), appliance.getDeviceName());
        }

        Long oldRoomId = appliance.getRoomId();
        Long oldShipId = appliance.getShipId();
        String batch = newBatch("BIND");
        appliance.setRoomId(room.getId());
        appliance.setShipId(ship.getId());
        appliance.setLastChangeBatch(batch);
        electricApplianceRepository.save(appliance);

        RoomShipRelation target = roomShipRelationRepository
                .findByRoomIdAndShipId(room.getId(), ship.getId())
                .orElseGet(() -> {
                    RoomShipRelation relation = new RoomShipRelation();
                    relation.setRoomId(room.getId());
                    relation.setShipId(ship.getId());
                    relation.setRelationType("ELECTRIC_BIND");
                    return relation;
                });
        deactivateOtherRelations(roomRelations, target.getId());
        target.setStatus(ACTIVE);
        target.setRelationType("ELECTRIC_BIND");
        target.setChangeBatch(batch);
        roomShipRelationRepository.save(target);

        saveShiftLog(batch, "BIND", appliance, room, ship, oldRoomId,
                oldShipId, dto.getOperator(), dto.getRemark());
    }

    @Transactional
    public ShiftResultDTO updateRelation(Long roomId, Long newShipId, String operator, String remark) {
        shiftOperationLockService.lock();

        LoungeRoom room = loungeRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RuntimeException("休息室不存在"));
        Ship newShip = shipRepository.findByIdForUpdate(newShipId)
                .orElseThrow(() -> new RuntimeException("新船舶不存在"));

        List<RoomShipRelation> relations = roomShipRelationRepository.findByRoomIdForUpdate(roomId);
        List<RoomShipRelation> activeRelations = relations.stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .toList();
        RoomShipRelation currentRelation = activeRelations.stream().findFirst().orElse(null);
        Long oldShipId = currentRelation != null ? currentRelation.getShipId() : null;
        Ship oldShip = oldShipId == null ? null : shipRepository.findById(oldShipId).orElse(null);

        List<ElectricAppliance> appliances = electricApplianceRepository.findByRoomIdForUpdate(roomId);
        if (currentRelation == null) {
            appliances.stream()
                    .filter(appliance -> appliance.getShipId() != null && !Objects.equals(appliance.getShipId(), newShipId))
                    .findFirst()
                    .ifPresent(appliance -> {
                        throw new RuntimeException("电器 " + appliance.getDeviceCode() + " 已属于其他船舶，不能直接改挂房间");
                    });
        }
        boolean targetReady = currentRelation != null
                && Objects.equals(currentRelation.getShipId(), newShipId)
                && appliances.stream().allMatch(appliance -> Objects.equals(appliance.getShipId(), newShipId));
        if (targetReady) {
            return noopResult(currentRelation.getChangeBatch());
        }

        List<ShiftBlockedApplianceDTO> blocked = appliances.stream()
                .filter(appliance -> !Objects.equals(appliance.getShipId(), newShipId))
                .filter(this::isStopped)
                .map(appliance -> convertToBlockedDTO(appliance, room,
                        appliance.getShipId() == null ? null : shipRepository.findById(appliance.getShipId()).orElse(null)))
                .toList();
        if (!blocked.isEmpty()) {
            throw new ShiftBlockedException(buildBlockedMessage(blocked), blocked);
        }

        String batch = newBatch("ROOM");
        RoomShipRelation target = roomShipRelationRepository.findByRoomIdAndShipId(roomId, newShipId)
                .orElseGet(() -> {
                    RoomShipRelation relation = new RoomShipRelation();
                    relation.setRoomId(roomId);
                    relation.setShipId(newShipId);
                    relation.setRelationType("ROOM_SHIP");
                    return relation;
                });
        deactivateOtherRelations(relations, target.getId());
        target.setStatus(ACTIVE);
        target.setRelationType("ROOM_SHIP");
        target.setChangeBatch(batch);
        roomShipRelationRepository.save(target);

        List<ElectricAppliance> changedAppliances = new ArrayList<>();
        for (ElectricAppliance appliance : appliances) {
            Long applianceOldShipId = appliance.getShipId();
            boolean needsChange = !Objects.equals(applianceOldShipId, newShipId);
            appliance.setShipId(newShipId);
            appliance.setLastChangeBatch(batch);
            electricApplianceRepository.save(appliance);
            if (needsChange) {
                changedAppliances.add(appliance);
                saveShiftLog(batch, "SHIP_CHANGE", appliance, room, newShip, roomId,
                        applianceOldShipId, operator, remark);
            }
        }

        saveRoomLog(batch, "ROOM_SHIP_CHANGE", room, newShip, oldShip, operator, remark,
                changedAppliances.size());

        ShiftResultDTO result = new ShiftResultDTO();
        result.setChangeBatch(batch);
        result.setRoomCount(1);
        result.setApplianceCount(changedAppliances.size());
        return result;
    }

    @Transactional
    public ShiftResultDTO shipChange(Long oldShipId, Long newShipId, String operator, String remark) {
        shiftOperationLockService.lock();

        Ship oldShip = shipRepository.findByIdForUpdate(oldShipId)
                .orElseThrow(() -> new RuntimeException("原船舶不存在"));
        Ship newShip = shipRepository.findByIdForUpdate(newShipId)
                .orElseThrow(() -> new RuntimeException("新船舶不存在"));
        if (Objects.equals(oldShipId, newShipId)) {
            throw new RuntimeException("原船舶和新船舶不能相同");
        }

        List<ElectricAppliance> oldShipAppliances = electricApplianceRepository.findByShipIdForUpdate(oldShipId);
        List<RoomShipRelation> oldShipRelations = roomShipRelationRepository.findByShipIdForUpdate(oldShipId);
        Set<Long> affectedRoomIds = new HashSet<>();
        oldShipRelations.stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .map(RoomShipRelation::getRoomId)
                .forEach(affectedRoomIds::add);
        oldShipAppliances.stream()
                .map(ElectricAppliance::getRoomId)
                .filter(Objects::nonNull)
                .forEach(affectedRoomIds::add);

        List<LoungeRoom> rooms = affectedRoomIds.stream()
                .map(id -> loungeRoomRepository.findByIdForUpdate(id).orElseThrow(() -> new RuntimeException("休息室不存在")))
                .sorted(Comparator.comparing(LoungeRoom::getId))
                .toList();

        Map<Long, List<ElectricAppliance>> appliancesByRoom = new LinkedHashMap<>();
        List<ElectricAppliance> orphanAppliances = new ArrayList<>();
        List<ElectricAppliance> appliancesToCheck = new ArrayList<>(orphanAppliances);
        for (LoungeRoom room : rooms) {
            appliancesByRoom.put(room.getId(), electricApplianceRepository.findByRoomIdForUpdate(room.getId()));
        }
        for (ElectricAppliance appliance : oldShipAppliances) {
            if (appliance.getRoomId() == null) {
                orphanAppliances.add(appliance);
                appliancesToCheck.add(appliance);
            }
        }

        Map<Long, RoomShipRelation> activeRelationByRoom = new LinkedHashMap<>();
        for (LoungeRoom room : rooms) {
            List<RoomShipRelation> roomRelations = roomShipRelationRepository.findByRoomIdForUpdate(room.getId());
            List<RoomShipRelation> activeRelations = roomRelations.stream()
                    .filter(relation -> ACTIVE.equals(relation.getStatus()))
                    .toList();
            RoomShipRelation activeRelation = activeRelations.stream().findFirst().orElse(null);
            activeRelationByRoom.put(room.getId(), activeRelation);
            List<ElectricAppliance> roomAppliances = appliancesByRoom.get(room.getId());
            boolean hasOldAppliance = roomAppliances.stream()
                    .anyMatch(appliance -> Objects.equals(appliance.getShipId(), oldShipId));
            boolean roomInShift = (activeRelation != null && Objects.equals(activeRelation.getShipId(), oldShipId))
                    || hasOldAppliance;

            if (activeRelation != null
                    && !Objects.equals(activeRelation.getShipId(), oldShipId)
                    && !Objects.equals(activeRelation.getShipId(), newShipId)) {
                throw new RuntimeException("房间 " + room.getRoomCode() + " 正停靠其他船舶，不能执行整船换班");
            }
            if (roomInShift) {
                roomAppliances.stream()
                        .filter(appliance -> !Objects.equals(appliance.getShipId(), oldShipId)
                                && !Objects.equals(appliance.getShipId(), newShipId))
                        .findFirst()
                        .ifPresent(appliance -> {
                            throw new RuntimeException("电器 " + appliance.getDeviceCode()
                                    + " 不属于本次换班船舶，不能随房转移");
                        });
            }
        }

        List<ElectricAppliance> appliancesToMove = new ArrayList<>(orphanAppliances);
        for (LoungeRoom room : rooms) {
            List<ElectricAppliance> roomAppliances = appliancesByRoom.get(room.getId());
            RoomShipRelation activeRelation = activeRelationByRoom.get(room.getId());
            if (activeRelation != null && Objects.equals(activeRelation.getShipId(), oldShipId)) {
                appliancesToMove.addAll(roomAppliances);
            } else {
                List<ElectricAppliance> halfChanged = roomAppliances.stream()
                        .filter(appliance -> Objects.equals(appliance.getShipId(), oldShipId))
                        .toList();
                appliancesToMove.addAll(halfChanged);
            }
        }
        appliancesToMove = appliancesToMove.stream()
                .filter(appliance -> !Objects.equals(appliance.getShipId(), newShipId))
                .collect(Collectors.toMap(ElectricAppliance::getId, appliance -> appliance,
                        (first, second) -> first, LinkedHashMap::new))
                .values()
                .stream()
                .toList();
        appliancesToCheck = appliancesToMove;

        if (appliancesToMove.isEmpty() && oldShipRelations.stream().noneMatch(r -> ACTIVE.equals(r.getStatus()))) {
            throw new RuntimeException("原船舶下没有需要换班的房间或电器");
        }

        List<ShiftBlockedApplianceDTO> blocked = appliancesToCheck.stream()
                .filter(this::isStopped)
                .map(appliance -> convertToBlockedDTO(appliance,
                        appliance.getRoomId() == null ? null : loungeRoomRepository.findById(appliance.getRoomId()).orElse(null),
                        oldShip))
                .toList();
        if (!blocked.isEmpty()) {
            throw new ShiftBlockedException(buildBlockedMessage(blocked), blocked);
        }

        String batch = newBatch("SHIP");
        int roomCount = 0;
        int applianceCount = 0;

        for (LoungeRoom room : rooms) {
            List<RoomShipRelation> roomRelations = roomShipRelationRepository.findByRoomIdForUpdate(room.getId());
            RoomShipRelation activeRelation = activeRelationByRoom.get(room.getId());
            Long activeShipId = activeRelation == null ? null : activeRelation.getShipId();
            List<ElectricAppliance> roomAppliances = appliancesByRoom.get(room.getId());
            boolean roomNeedsUpdate = Objects.equals(activeShipId, oldShipId)
                    || roomAppliances.stream().anyMatch(appliance -> Objects.equals(appliance.getShipId(), oldShipId));
            if (!roomNeedsUpdate) {
                continue;
            }

            RoomShipRelation target = roomShipRelationRepository.findByRoomIdAndShipId(room.getId(), newShipId)
                    .orElseGet(() -> {
                        RoomShipRelation relation = new RoomShipRelation();
                        relation.setRoomId(room.getId());
                        relation.setShipId(newShipId);
                        relation.setRelationType("SHIP_CHANGE");
                        return relation;
                    });
            deactivateOtherRelations(roomRelations, target.getId());
            target.setStatus(ACTIVE);
            target.setRelationType("SHIP_CHANGE");
            target.setChangeBatch(batch);
            roomShipRelationRepository.save(target);
            roomCount++;

            int changedInRoom = 0;
            for (ElectricAppliance appliance : roomAppliances) {
                Long applianceOldShipId = appliance.getShipId();
                boolean needsChange = !Objects.equals(applianceOldShipId, newShipId);
                appliance.setShipId(newShipId);
                appliance.setLastChangeBatch(batch);
                electricApplianceRepository.save(appliance);
                if (needsChange) {
                    changedInRoom++;
                    applianceCount++;
                    saveShiftLog(batch, "SHIP_SWITCH", appliance, room, newShip, room.getId(),
                            applianceOldShipId, operator, remark);
                }
            }
            saveRoomLog(batch, "ROOM_SHIP_CHANGE", room, newShip, oldShip, operator, remark, changedInRoom);
        }

        for (ElectricAppliance appliance : orphanAppliances) {
            if (Objects.equals(appliance.getShipId(), newShipId)) {
                continue;
            }
            appliance.setShipId(newShipId);
            appliance.setLastChangeBatch(batch);
            electricApplianceRepository.save(appliance);
            applianceCount++;
            saveShiftLog(batch, "SHIP_SWITCH", appliance, null, newShip, null, oldShipId, operator, remark);
        }

        ShiftResultDTO result = new ShiftResultDTO();
        result.setChangeBatch(batch);
        result.setRoomCount(roomCount);
        result.setApplianceCount(applianceCount);
        return result;
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
        return convertToRoomDTO(room);
    }

    public List<RelationChangeLogDTO> getChangeLogs(Long deviceId, Long roomId, Long shipId) {
        List<RelationChangeLog> logs;
        if (deviceId != null) {
            logs = relationChangeLogRepository.findByDeviceId(deviceId);
        } else if (roomId != null) {
            logs = relationChangeLogRepository.findByRoomId(roomId);
        } else if (shipId != null) {
            logs = relationChangeLogRepository.findByShipId(shipId);
        } else {
            logs = relationChangeLogRepository.findAll();
        }

        return logs.stream()
                .map(this::convertToLogDTO)
                .collect(Collectors.toList());
    }

    private ElectricAppliance findAppliance(RelationBindDTO dto) {
        if (dto.getDeviceId() != null) {
            return electricApplianceRepository.findById(dto.getDeviceId())
                    .orElseThrow(() -> new RuntimeException("设备不存在"));
        }
        if (dto.getDeviceCode() != null) {
            return electricApplianceRepository.findByDeviceCode(dto.getDeviceCode())
                    .orElseThrow(() -> new RuntimeException("设备不存在"));
        }
        throw new RuntimeException("设备ID或设备编号不能为空");
    }

    private LoungeRoom findRoom(RelationBindDTO dto) {
        if (dto.getRoomId() != null) {
            return loungeRoomRepository.findById(dto.getRoomId()).orElse(null);
        }
        if (dto.getRoomCode() != null) {
            return loungeRoomRepository.findByRoomCode(dto.getRoomCode()).orElse(null);
        }
        return null;
    }

    private Ship findShip(RelationBindDTO dto) {
        if (dto.getShipId() != null) {
            return shipRepository.findById(dto.getShipId()).orElse(null);
        }
        if (dto.getShipCode() != null) {
            return shipRepository.findByShipCode(dto.getShipCode()).orElse(null);
        }
        return null;
    }

    private boolean isStopped(ElectricAppliance appliance) {
        return INACTIVE.equalsIgnoreCase(appliance.getStatus())
                || "停用".equals(appliance.getStatus())
                || "STOPPED".equalsIgnoreCase(appliance.getStatus())
                || "DISABLED".equalsIgnoreCase(appliance.getStatus());
    }

    private void deactivateOtherRelations(List<RoomShipRelation> relations, Long activeRelationId) {
        for (RoomShipRelation relation : relations) {
            if (!Objects.equals(relation.getId(), activeRelationId) && ACTIVE.equals(relation.getStatus())) {
                relation.setStatus(INACTIVE);
                roomShipRelationRepository.save(relation);
            }
        }
    }

    private String newBatch(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private ShiftResultDTO noopResult(String batch) {
        ShiftResultDTO result = new ShiftResultDTO();
        result.setChangeBatch(batch);
        result.setRoomCount(0);
        result.setApplianceCount(0);
        return result;
    }

    private String buildBlockedMessage(List<ShiftBlockedApplianceDTO> blocked) {
        String devices = blocked.stream()
                .map(appliance -> appliance.getDeviceCode() + "（" + appliance.getDeviceName() + "）")
                .collect(Collectors.joining("、"));
        return "换班已整单退回：停用电器不能随船转移，请先处理 " + devices;
    }

    private void saveRoomLog(String batch, String changeType, LoungeRoom room, Ship newShip, Ship oldShip,
                             String operator, String remark, int applianceCount) {
        RelationChangeLog log = baseLog(batch, changeType, operator, remark);
        if (room != null) {
            log.setRoomId(room.getId());
            log.setRoomCode(room.getRoomCode());
        }
        if (newShip != null) {
            log.setShipId(newShip.getId());
            log.setShipCode(newShip.getShipCode());
        }
        if (oldShip != null) {
            log.setOldShipId(oldShip.getId());
            log.setOldShipCode(oldShip.getShipCode());
        }
        String countText = "本次换班电器数：" + applianceCount + "；换班批次：" + batch;
        log.setRemark(remark == null || remark.isBlank() ? countText : remark + "；" + countText);
        relationChangeLogRepository.save(log);
    }

    private void saveShiftLog(String batch, String changeType, ElectricAppliance appliance,
                              LoungeRoom newRoom, Ship newShip, Long oldRoomId, Long oldShipId,
                              String operator, String remark) {
        RelationChangeLog log = baseLog(batch, changeType, operator, remark);
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
                    .ifPresent(room -> log.setOldRoomCode(room.getRoomCode()));
        }
        if (oldShipId != null) {
            log.setOldShipId(oldShipId);
            shipRepository.findById(oldShipId)
                    .ifPresent(ship -> log.setOldShipCode(ship.getShipCode()));
        }

        String batchText = "换班批次：" + batch;
        log.setRemark(remark == null || remark.isBlank() ? batchText : remark + "；" + batchText);
        relationChangeLogRepository.save(log);
    }

    private RelationChangeLog baseLog(String batch, String changeType, String operator, String remark) {
        RelationChangeLog log = new RelationChangeLog();
        log.setChangeType(changeType);
        log.setChangeBatch(batch);
        log.setOperator(operator == null || operator.isBlank() ? "SYSTEM" : operator);
        log.setRemark(remark);
        return log;
    }

    private ShiftBlockedApplianceDTO convertToBlockedDTO(ElectricAppliance appliance, LoungeRoom room, Ship ship) {
        ShiftBlockedApplianceDTO dto = new ShiftBlockedApplianceDTO();
        dto.setId(appliance.getId());
        dto.setDeviceCode(appliance.getDeviceCode());
        dto.setDeviceName(appliance.getDeviceName());
        dto.setStatus(appliance.getStatus());
        if (room != null) {
            dto.setRoomId(room.getId());
            dto.setRoomCode(room.getRoomCode());
            dto.setRoomName(room.getRoomName());
        }
        if (ship != null) {
            dto.setShipId(ship.getId());
            dto.setShipCode(ship.getShipCode());
            dto.setShipName(ship.getShipName());
        }
        return dto;
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

    private LoungeRoomDTO convertToRoomDTO(LoungeRoom room) {
        LoungeRoomDTO dto = new LoungeRoomDTO();
        dto.setId(room.getId());
        dto.setRoomCode(room.getRoomCode());
        dto.setRoomName(room.getRoomName());
        dto.setFloor(room.getFloor());
        dto.setCapacity(room.getCapacity());
        dto.setStatus(room.getStatus());

        roomShipRelationRepository.findByRoomId(room.getId())
                .stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .findFirst()
                .ifPresent(relation -> {
                    dto.setShipId(relation.getShipId());
                    dto.setChangeBatch(relation.getChangeBatch());
                    shipRepository.findById(relation.getShipId())
                            .ifPresent(ship -> {
                                dto.setShipCode(ship.getShipCode());
                                dto.setShipName(ship.getShipName());
                            });
                });
        return dto;
    }

    private RelationChangeLogDTO convertToLogDTO(RelationChangeLog log) {
        RelationChangeLogDTO dto = new RelationChangeLogDTO();
        dto.setId(log.getId());
        dto.setChangeType(log.getChangeType());
        dto.setChangeBatch(log.getChangeBatch());
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
