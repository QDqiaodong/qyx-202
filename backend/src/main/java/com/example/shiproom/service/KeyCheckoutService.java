package com.example.shiproom.service;

import com.example.shiproom.dto.KeyBlockedDTO;
import com.example.shiproom.dto.KeyCheckoutDTO;
import com.example.shiproom.dto.KeyCheckoutRecordDTO;
import com.example.shiproom.dto.KeyOccupancyDTO;
import com.example.shiproom.dto.KeyReturnDTO;
import com.example.shiproom.dto.LoungeKeyDTO;
import com.example.shiproom.entity.KeyCheckoutRecord;
import com.example.shiproom.entity.KeyOccupancy;
import com.example.shiproom.entity.LoungeKey;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.KeyCheckoutBlockedException;
import com.example.shiproom.repository.KeyCheckoutRecordRepository;
import com.example.shiproom.repository.KeyOccupancyRepository;
import com.example.shiproom.repository.LoungeKeyRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KeyCheckoutService {

    private static final String ACTIVE = "ACTIVE";
    private static final String OUT = "OUT";
    private static final String RETURNED = "RETURNED";

    private final LoungeKeyRepository loungeKeyRepository;
    private final KeyOccupancyRepository keyOccupancyRepository;
    private final KeyCheckoutRecordRepository keyCheckoutRecordRepository;
    private final LoungeRoomRepository loungeRoomRepository;
    private final RoomShipRelationRepository roomShipRelationRepository;
    private final ShipRepository shipRepository;
    private final ShiftOperationLockService shiftOperationLockService;

    public KeyCheckoutService(LoungeKeyRepository loungeKeyRepository,
                              KeyOccupancyRepository keyOccupancyRepository,
                              KeyCheckoutRecordRepository keyCheckoutRecordRepository,
                              LoungeRoomRepository loungeRoomRepository,
                              RoomShipRelationRepository roomShipRelationRepository,
                              ShipRepository shipRepository,
                              ShiftOperationLockService shiftOperationLockService) {
        this.loungeKeyRepository = loungeKeyRepository;
        this.keyOccupancyRepository = keyOccupancyRepository;
        this.keyCheckoutRecordRepository = keyCheckoutRecordRepository;
        this.loungeRoomRepository = loungeRoomRepository;
        this.roomShipRelationRepository = roomShipRelationRepository;
        this.shipRepository = shipRepository;
        this.shiftOperationLockService = shiftOperationLockService;
    }

    @Transactional
    public KeyCheckoutRecordDTO checkout(KeyCheckoutDTO dto) {
        shiftOperationLockService.lock();

        if (dto.getKeyId() == null || dto.getShipId() == null) {
            throw new RuntimeException("钥匙和船舶不能为空");
        }
        if (dto.getHolderName() == null || dto.getHolderName().isBlank()) {
            throw new RuntimeException("持匙人不能为空");
        }

        LoungeKey key = loungeKeyRepository.findByIdForUpdate(dto.getKeyId())
                .orElseThrow(() -> new RuntimeException("钥匙不存在"));
        LoungeRoom room = loungeRoomRepository.findByIdForUpdate(key.getRoomId())
                .orElseThrow(() -> new RuntimeException("钥匙所属休息室不存在"));
        Ship requestedShip = shipRepository.findById(dto.getShipId())
                .orElseThrow(() -> new RuntimeException("船舶不存在"));

        keyOccupancyRepository.findByKeyId(key.getId()).ifPresent(occupancy -> {
            throw new KeyCheckoutBlockedException(
                    "钥匙 " + key.getKeyCode() + "（房间 " + room.getRoomCode()
                            + "）已被 " + occupancy.getHolderName() + " 领走，尚未归还",
                    buildBlockedDTO(key, room, requestedShip, null, occupancy.getHolderName()));
        });

        List<RoomShipRelation> relations = roomShipRelationRepository.findByRoomIdForUpdate(room.getId());
        RoomShipRelation activeRelation = relations.stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .findFirst()
                .orElse(null);
        Ship currentShip = activeRelation == null ? null
                : shipRepository.findById(activeRelation.getShipId()).orElse(null);

        if (activeRelation == null || !Objects.equals(activeRelation.getShipId(), requestedShip.getId())) {
            String currentText = currentShip == null
                    ? "房间当前没有停靠船舶"
                    : "房间当前停靠船舶 " + currentShip.getShipCode() + "（" + currentShip.getShipName() + "）";
            throw new KeyCheckoutBlockedException(
                    "钥匙 " + key.getKeyCode() + "（房间 " + room.getRoomCode() + "）领取整单退回：房间已不停靠船舶 "
                            + requestedShip.getShipCode() + "（" + requestedShip.getShipName() + "），" + currentText,
                    buildBlockedDTO(key, room, requestedShip, currentShip, null));
        }

        String batch = newBatch();
        LocalDateTime now = LocalDateTime.now();

        KeyOccupancy occupancy = new KeyOccupancy();
        occupancy.setKeyId(key.getId());
        occupancy.setKeyCode(key.getKeyCode());
        occupancy.setRoomId(room.getId());
        occupancy.setRoomCode(room.getRoomCode());
        occupancy.setShipId(currentShip.getId());
        occupancy.setShipCode(currentShip.getShipCode());
        occupancy.setHolderName(dto.getHolderName());
        occupancy.setCheckoutBatch(batch);
        occupancy.setCheckoutTime(now);
        keyOccupancyRepository.save(occupancy);

        KeyCheckoutRecord record = new KeyCheckoutRecord();
        record.setCheckoutBatch(batch);
        record.setKeyId(key.getId());
        record.setKeyCode(key.getKeyCode());
        record.setRoomId(room.getId());
        record.setRoomCode(room.getRoomCode());
        record.setShipId(currentShip.getId());
        record.setShipCode(currentShip.getShipCode());
        record.setHolderName(dto.getHolderName());
        record.setOperator(dto.getOperator());
        record.setStatus(OUT);
        record.setCheckoutTime(now);
        record.setRemark(dto.getRemark());
        keyCheckoutRecordRepository.save(record);

        return convertToRecordDTO(record);
    }

    @Transactional
    public KeyCheckoutRecordDTO returnKey(KeyReturnDTO dto) {
        shiftOperationLockService.lock();

        if (dto.getKeyId() == null) {
            throw new RuntimeException("钥匙不能为空");
        }
        LoungeKey key = loungeKeyRepository.findByIdForUpdate(dto.getKeyId())
                .orElseThrow(() -> new RuntimeException("钥匙不存在"));
        KeyOccupancy occupancy = keyOccupancyRepository.findByKeyId(key.getId())
                .orElseThrow(() -> new RuntimeException("钥匙 " + key.getKeyCode() + " 当前未借出，不能归还"));
        KeyCheckoutRecord record = keyCheckoutRecordRepository
                .findByCheckoutBatchAndStatus(occupancy.getCheckoutBatch(), OUT)
                .orElseThrow(() -> new RuntimeException(
                        "钥匙 " + key.getKeyCode() + " 的领取流水缺失，批次 " + occupancy.getCheckoutBatch()));

        keyOccupancyRepository.delete(occupancy);

        record.setStatus(RETURNED);
        record.setReturnTime(LocalDateTime.now());
        record.setReturnOperator(dto.getOperator());
        if (dto.getRemark() != null && !dto.getRemark().isBlank()) {
            record.setRemark(record.getRemark() == null || record.getRemark().isBlank()
                    ? dto.getRemark()
                    : record.getRemark() + "；归还备注：" + dto.getRemark());
        }
        keyCheckoutRecordRepository.save(record);

        return convertToRecordDTO(record);
    }

    @Transactional
    public LoungeKeyDTO createKey(LoungeKeyDTO dto) {
        if (dto.getKeyCode() == null || dto.getKeyCode().isBlank()) {
            throw new RuntimeException("钥匙编号不能为空");
        }
        if (dto.getKeyName() == null || dto.getKeyName().isBlank()) {
            throw new RuntimeException("钥匙名称不能为空");
        }
        if (loungeKeyRepository.existsByKeyCode(dto.getKeyCode())) {
            throw new RuntimeException("钥匙编号已存在");
        }
        LoungeRoom room = loungeRoomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("休息室不存在"));

        LoungeKey key = new LoungeKey();
        key.setKeyCode(dto.getKeyCode());
        key.setKeyName(dto.getKeyName());
        key.setRoomId(room.getId());
        key.setStatus("ACTIVE");
        return convertToKeyDTO(loungeKeyRepository.save(key));
    }

    @Transactional
    public LoungeKeyDTO updateKey(Long id, LoungeKeyDTO dto) {
        LoungeKey key = loungeKeyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("钥匙不存在"));
        if (!key.getKeyCode().equals(dto.getKeyCode()) && loungeKeyRepository.existsByKeyCode(dto.getKeyCode())) {
            throw new RuntimeException("钥匙编号已存在");
        }
        if (!Objects.equals(key.getRoomId(), dto.getRoomId())
                && keyOccupancyRepository.existsByKeyId(key.getId())) {
            throw new RuntimeException("钥匙 " + key.getKeyCode() + " 已借出，不能改挂休息室");
        }
        LoungeRoom room = loungeRoomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("休息室不存在"));

        key.setKeyCode(dto.getKeyCode());
        key.setKeyName(dto.getKeyName());
        key.setRoomId(room.getId());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            key.setStatus(dto.getStatus());
        }
        return convertToKeyDTO(loungeKeyRepository.save(key));
    }

    @Transactional
    public void deleteKey(Long id) {
        LoungeKey key = loungeKeyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("钥匙不存在"));
        if (keyOccupancyRepository.existsByKeyId(id)) {
            throw new RuntimeException("钥匙 " + key.getKeyCode() + " 已借出，不能删除");
        }
        loungeKeyRepository.delete(key);
    }

    public List<LoungeKeyDTO> listKeys() {
        return loungeKeyRepository.findAll().stream()
                .sorted(Comparator.comparing(LoungeKey::getId))
                .map(key -> {
                    LoungeKeyDTO dto = convertToKeyDTO(key);
                    keyOccupancyRepository.findByKeyId(key.getId()).ifPresent(occupancy -> {
                        dto.setHolderName(occupancy.getHolderName());
                        dto.setShipId(occupancy.getShipId());
                        dto.setShipCode(occupancy.getShipCode());
                        shipRepository.findById(occupancy.getShipId())
                                .ifPresent(ship -> dto.setShipName(ship.getShipName()));
                        dto.setCheckoutBatch(occupancy.getCheckoutBatch());
                        dto.setCheckoutTime(occupancy.getCheckoutTime());
                    });
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public List<KeyOccupancyDTO> listOccupancyByShip(Long shipId) {
        return keyOccupancyRepository.findByShipId(shipId).stream()
                .sorted(Comparator.comparing(KeyOccupancy::getId))
                .map(this::convertToOccupancyDTO)
                .collect(Collectors.toList());
    }

    public List<KeyOccupancyDTO> listOccupancyByRoom(Long roomId) {
        return keyOccupancyRepository.findByRoomId(roomId).stream()
                .sorted(Comparator.comparing(KeyOccupancy::getId))
                .map(this::convertToOccupancyDTO)
                .collect(Collectors.toList());
    }

    public List<KeyCheckoutRecordDTO> listRecords(Long keyId, Long roomId, Long shipId) {
        List<KeyCheckoutRecord> records;
        if (keyId != null) {
            records = keyCheckoutRecordRepository.findByKeyId(keyId);
        } else if (roomId != null) {
            records = keyCheckoutRecordRepository.findByRoomId(roomId);
        } else if (shipId != null) {
            records = keyCheckoutRecordRepository.findByShipId(shipId);
        } else {
            records = keyCheckoutRecordRepository.findAll();
        }
        return records.stream()
                .sorted(Comparator.comparing(KeyCheckoutRecord::getId).reversed())
                .map(this::convertToRecordDTO)
                .collect(Collectors.toList());
    }

    private KeyBlockedDTO buildBlockedDTO(LoungeKey key, LoungeRoom room, Ship requestedShip,
                                          Ship currentShip, String holderName) {
        KeyBlockedDTO blocked = new KeyBlockedDTO();
        blocked.setKeyId(key.getId());
        blocked.setKeyCode(key.getKeyCode());
        blocked.setKeyName(key.getKeyName());
        blocked.setRoomId(room.getId());
        blocked.setRoomCode(room.getRoomCode());
        blocked.setRoomName(room.getRoomName());
        if (requestedShip != null) {
            blocked.setRequestedShipId(requestedShip.getId());
            blocked.setRequestedShipCode(requestedShip.getShipCode());
            blocked.setRequestedShipName(requestedShip.getShipName());
        }
        if (currentShip != null) {
            blocked.setCurrentShipId(currentShip.getId());
            blocked.setCurrentShipCode(currentShip.getShipCode());
            blocked.setCurrentShipName(currentShip.getShipName());
        }
        blocked.setHolderName(holderName);
        return blocked;
    }

    private LoungeKeyDTO convertToKeyDTO(LoungeKey key) {
        LoungeKeyDTO dto = new LoungeKeyDTO();
        dto.setId(key.getId());
        dto.setKeyCode(key.getKeyCode());
        dto.setKeyName(key.getKeyName());
        dto.setRoomId(key.getRoomId());
        dto.setStatus(key.getStatus());
        loungeRoomRepository.findById(key.getRoomId()).ifPresent(room -> {
            dto.setRoomCode(room.getRoomCode());
            dto.setRoomName(room.getRoomName());
        });
        return dto;
    }

    private KeyOccupancyDTO convertToOccupancyDTO(KeyOccupancy occupancy) {
        KeyOccupancyDTO dto = new KeyOccupancyDTO();
        dto.setId(occupancy.getId());
        dto.setKeyId(occupancy.getKeyId());
        dto.setKeyCode(occupancy.getKeyCode());
        dto.setRoomId(occupancy.getRoomId());
        dto.setRoomCode(occupancy.getRoomCode());
        dto.setShipId(occupancy.getShipId());
        dto.setShipCode(occupancy.getShipCode());
        dto.setHolderName(occupancy.getHolderName());
        dto.setCheckoutBatch(occupancy.getCheckoutBatch());
        dto.setCheckoutTime(occupancy.getCheckoutTime());
        loungeKeyRepository.findById(occupancy.getKeyId())
                .ifPresent(key -> dto.setKeyName(key.getKeyName()));
        loungeRoomRepository.findById(occupancy.getRoomId())
                .ifPresent(room -> dto.setRoomName(room.getRoomName()));
        shipRepository.findById(occupancy.getShipId())
                .ifPresent(ship -> dto.setShipName(ship.getShipName()));
        return dto;
    }

    private KeyCheckoutRecordDTO convertToRecordDTO(KeyCheckoutRecord record) {
        KeyCheckoutRecordDTO dto = new KeyCheckoutRecordDTO();
        dto.setId(record.getId());
        dto.setCheckoutBatch(record.getCheckoutBatch());
        dto.setKeyId(record.getKeyId());
        dto.setKeyCode(record.getKeyCode());
        dto.setRoomId(record.getRoomId());
        dto.setRoomCode(record.getRoomCode());
        dto.setShipId(record.getShipId());
        dto.setShipCode(record.getShipCode());
        dto.setHolderName(record.getHolderName());
        dto.setOperator(record.getOperator());
        dto.setStatus(record.getStatus());
        dto.setCheckoutTime(record.getCheckoutTime());
        dto.setReturnTime(record.getReturnTime());
        dto.setReturnOperator(record.getReturnOperator());
        dto.setRemark(record.getRemark());
        loungeKeyRepository.findById(record.getKeyId())
                .ifPresent(key -> dto.setKeyName(key.getKeyName()));
        loungeRoomRepository.findById(record.getRoomId())
                .ifPresent(room -> dto.setRoomName(room.getRoomName()));
        shipRepository.findById(record.getShipId())
                .ifPresent(ship -> dto.setShipName(ship.getShipName()));
        return dto;
    }

    private String newBatch() {
        return "KEY-" + UUID.randomUUID().toString().replace("-", "");
    }
}
