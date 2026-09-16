package com.example.shiproom.service;

import com.example.shiproom.dto.LinenBlockedDTO;
import com.example.shiproom.dto.LinenRecoveryDTO;
import com.example.shiproom.dto.LinenRecoverySaveDTO;
import com.example.shiproom.dto.LinenRoomStateDTO;
import com.example.shiproom.entity.LinenRecovery;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.LinenOccupancyBlockedException;
import com.example.shiproom.exception.LinenRecoveryBlockedException;
import com.example.shiproom.repository.LinenRecoveryRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 布草回收。
 *
 * 一间房每“换过一次船”开一页回收单，单子锁定在本次换班批次（change_batch）上：
 * - 草稿（DRAFT）：套数 / 封袋公斤数 / 见证人三栏还没齐，可随时补登、可改；
 * - 三栏齐且按每套约定公斤区间对得上，保存即确认（CONFIRMED），可住灯亮起，套数与公斤数随之锁死；
 * - 作废（VOID）：填错时纠正用，作废后同周期可再开一页。
 *
 * 同一间房同一换班批次最多一份未作废回收单（active_unique_key = roomId#changeBatch + 唯一约束兜底）。
 */
@Service
public class LinenRecoveryService {

    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String VOID = "VOID";

    public static final String NOT_NEEDED = "NOT_NEEDED";
    public static final String PENDING = "PENDING";
    public static final String DRAFT_STATE = "DRAFT";
    public static final String RECOVERED = "RECOVERED";

    /** 每套脏床品约定公斤区间（系统默认约定，随单留底，避免事后改口径） */
    public static final BigDecimal DEFAULT_KG_PER_SET_MIN = new BigDecimal("1.50");
    public static final BigDecimal DEFAULT_KG_PER_SET_MAX = new BigDecimal("2.50");

    private static final String ACTIVE = "ACTIVE";

    private final LinenRecoveryRepository linenRecoveryRepository;
    private final LoungeRoomRepository loungeRoomRepository;
    private final RoomShipRelationRepository roomShipRelationRepository;
    private final ShipRepository shipRepository;
    private final ShiftOperationLockService shiftOperationLockService;

    public LinenRecoveryService(LinenRecoveryRepository linenRecoveryRepository,
                                LoungeRoomRepository loungeRoomRepository,
                                RoomShipRelationRepository roomShipRelationRepository,
                                ShipRepository shipRepository,
                                ShiftOperationLockService shiftOperationLockService) {
        this.linenRecoveryRepository = linenRecoveryRepository;
        this.loungeRoomRepository = loungeRoomRepository;
        this.roomShipRelationRepository = roomShipRelationRepository;
        this.shipRepository = shipRepository;
        this.shiftOperationLockService = shiftOperationLockService;
    }

    /**
     * 新开一页或补登/修改回收单。三栏允许先空（草稿）；三栏齐时当场按约定公斤区间对账，
     * 对不上整单退回（409），带约定区间、本次秤重、按公斤折出来的套数。
     */
    @Transactional
    public LinenRecoveryDTO save(LinenRecoverySaveDTO dto) {
        shiftOperationLockService.lock();

        if (dto.getRoomId() == null && dto.getId() == null) {
            throw new IllegalArgumentException("休息室不能为空");
        }

        LinenRecovery recovery;
        if (dto.getId() != null) {
            recovery = linenRecoveryRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("回收单不存在"));
            if (VOID.equals(recovery.getStatus())) {
                throw new IllegalArgumentException("回收单 " + recovery.getRecoveryNo() + " 已作废，不能再改，请新开一页");
            }
            // 重新锁一下房间，跟新建路径保持同一把锁顺序
            loungeRoomRepository.findByIdForUpdate(recovery.getRoomId());
        } else {
            LoungeRoom room = loungeRoomRepository.findByIdForUpdate(dto.getRoomId())
                    .orElseThrow(() -> new IllegalArgumentException("休息室不存在"));
            CycleContext cycle = loadCycle(room.getId());
            if (cycle.latestInactive == null) {
                throw new IllegalArgumentException(
                        "房间 " + room.getRoomCode() + " 还没换过船，没有上一班留下的脏床品，无需开回收单");
            }
            LinenRecovery existing = findCycleSheet(room.getId(), cycle.cycleBatch).orElse(null);
            if (existing != null) {
                throw new LinenRecoveryBlockedException(
                        "房间 " + room.getRoomCode() + " 本次换船已挂着未作废回收单 "
                                + existing.getRecoveryNo() + "，不能重复开单；请补登这一单或先作废",
                        duplicateBlocked(room, existing, dto));
            }

            recovery = new LinenRecovery();
            recovery.setRecoveryNo(newRecoveryNo());
            recovery.setRoomId(room.getId());
            recovery.setRoomCode(room.getRoomCode());
            recovery.setRoomName(room.getRoomName());
            recovery.setChangeBatch(cycle.cycleBatch);
            if (cycle.latestInactive != null) {
                recovery.setDepartedShipId(cycle.latestInactive.getShipId());
                shipRepository.findById(cycle.latestInactive.getShipId()).ifPresent(ship -> {
                    recovery.setDepartedShipCode(ship.getShipCode());
                    recovery.setDepartedShipName(ship.getShipName());
                });
            }
            recovery.setKgPerSetMin(DEFAULT_KG_PER_SET_MIN);
            recovery.setKgPerSetMax(DEFAULT_KG_PER_SET_MAX);
            recovery.setStatus(DRAFT);
            recovery.setCreateTime(LocalDateTime.now());
        }

        Integer setCount = dto.getSetCount() != null && dto.getSetCount() > 0 ? dto.getSetCount() : null;
        BigDecimal bagWeight = dto.getBagWeight() != null && dto.getBagWeight().signum() > 0
                ? dto.getBagWeight().setScale(2, RoundingMode.HALF_UP) : null;
        String witness = dto.getWitnessName() == null || dto.getWitnessName().isBlank()
                ? null : dto.getWitnessName().trim();

        // 可住灯已亮（已确认）后，套数、公斤数锁死，谁改歪保存都失败
        if (CONFIRMED.equals(recovery.getStatus())
                && (!Objects.equals(recovery.getSetCount(), normalizeSets(dto.getSetCount()))
                || !Objects.equals(recovery.getBagWeight(), normalizeWeight(dto.getBagWeight())))) {
            throw new LinenRecoveryBlockedException(
                    buildTamperMessage(recovery, setCount, bagWeight),
                    mismatchBlocked(recovery, setCount, bagWeight, witness, "TAMPER_AFTER_CONFIRMED"));
        }

        recovery.setSetCount(setCount);
        recovery.setBagWeight(bagWeight);
        recovery.setWitnessName(witness);
        if (dto.getOperator() != null && !dto.getOperator().isBlank()) {
            recovery.setOperator(dto.getOperator().trim());
        }
        if (dto.getRemark() != null) {
            recovery.setRemark(dto.getRemark());
        }

        boolean complete = recovery.getSetCount() != null
                && recovery.getBagWeight() != null
                && recovery.getWitnessName() != null;
        if (complete) {
            validateWeight(recovery);
            if (!CONFIRMED.equals(recovery.getStatus())) {
                recovery.setStatus(CONFIRMED);
                recovery.setConfirmedTime(LocalDateTime.now());
                recovery.setConfirmedBy(recovery.getOperator());
            }
        }
        refreshActiveKey(recovery);

        try {
            linenRecoveryRepository.saveAndFlush(recovery);
        } catch (DataIntegrityViolationException e) {
            // 两人同时给同一间房本次换船开单：唯一约束兜底，后到者看到已在库单号
            LinenRecovery existing = findCycleSheet(recovery.getRoomId(), recovery.getChangeBatch())
                    .orElse(null);
            LoungeRoom room = loungeRoomRepository.findById(recovery.getRoomId()).orElse(null);
            throw new LinenRecoveryBlockedException(
                    "房间本次换船已有未作废回收单"
                            + (existing != null ? " " + existing.getRecoveryNo() : "")
                            + "，后保存的这一单失败",
                    duplicateBlocked(room, existing, dto));
        }
        return convertToDTO(recovery);
    }

    /** 作废回收单（填错纠正）；作废后本周期可重新开单，可住灯随之熄灭。 */
    @Transactional
    public LinenRecoveryDTO voidRecovery(Long id, String operator, String reason) {
        shiftOperationLockService.lock();
        LinenRecovery recovery = linenRecoveryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("回收单不存在"));
        if (VOID.equals(recovery.getStatus())) {
            throw new IllegalArgumentException("回收单 " + recovery.getRecoveryNo() + " 已作废");
        }
        loungeRoomRepository.findByIdForUpdate(recovery.getRoomId());
        recovery.setStatus(VOID);
        recovery.setActiveUniqueKey(null);
        recovery.setVoidTime(LocalDateTime.now());
        recovery.setVoidBy(operator == null || operator.isBlank() ? "SYSTEM" : operator.trim());
        recovery.setVoidReason(reason);
        linenRecoveryRepository.saveAndFlush(recovery);
        return convertToDTO(recovery);
    }

    /**
     * 占用闸门：别的船的人来占用（领钥匙进门）前，上一班留下的脏床品必须已回收齐。
     * 没经历过换船（首班船）放行；缺单 / 草稿三栏未齐一律 409 挡回。
     * 入参 relations 为调用方已悲观锁好的该房靠泊关联，不再重复加锁。
     */
    public void assertAvailableForOccupancy(LoungeRoom room, List<RoomShipRelation> relations) {
        CycleContext cycle = cycleFromRelations(relations);
        if (cycle.latestInactive == null) {
            // 首班船，房间还没换过船，没有上一班脏床品
            return;
        }
        LinenRecovery sheet = findCycleSheet(room.getId(), cycle.cycleBatch).orElse(null);
        boolean recovered = sheet != null && CONFIRMED.equals(sheet.getStatus())
                && sheet.getSetCount() != null && sheet.getBagWeight() != null
                && sheet.getWitnessName() != null;
        if (recovered) {
            return;
        }

        Ship departed = shipRepository.findById(cycle.latestInactive.getShipId()).orElse(null);
        String stateText = sheet == null
                ? "连回收单都还没开"
                : "回收单 " + sheet.getRecoveryNo() + " 还停在草稿，套数/公斤数/见证人没齐";
        String message = "房间 " + room.getRoomCode() + " 布草回收未齐（" + stateText + "），上一班船 "
                + (departed != null ? departed.getShipCode() + "（" + departed.getShipName() + "）" : "")
                + "留下的脏床品还没回收，别的船的人暂不能占用这间房";

        LinenBlockedDTO blocked = new LinenBlockedDTO();
        blocked.setReason("LINEN_PENDING");
        blocked.setRoomId(room.getId());
        blocked.setRoomCode(room.getRoomCode());
        blocked.setRoomName(room.getRoomName());
        if (departed != null) {
            blocked.setDepartedShipId(departed.getId());
            blocked.setDepartedShipCode(departed.getShipCode());
            blocked.setDepartedShipName(departed.getShipName());
        }
        if (sheet != null) {
            blocked.setRecoveryId(sheet.getId());
            blocked.setRecoveryNo(sheet.getRecoveryNo());
            blocked.setExistingRecoveryId(sheet.getId());
            blocked.setExistingRecoveryNo(sheet.getRecoveryNo());
            blocked.setSubmittedSetCount(sheet.getSetCount());
            blocked.setSubmittedBagWeight(sheet.getBagWeight());
            blocked.setKgPerSetMin(sheet.getKgPerSetMin());
            blocked.setKgPerSetMax(sheet.getKgPerSetMax());
        } else {
            blocked.setKgPerSetMin(DEFAULT_KG_PER_SET_MIN);
            blocked.setKgPerSetMax(DEFAULT_KG_PER_SET_MAX);
        }
        blocked.setMessage(message);
        throw new LinenOccupancyBlockedException(message, blocked);
    }

    public LinenRoomStateDTO getRoomState(Long roomId) {
        LoungeRoom room = loungeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("休息室不存在"));
        return buildState(room, roomShipRelationRepository.findByRoomId(roomId));
    }

    public List<LinenRoomStateDTO> listRoomStates() {
        List<LoungeRoom> rooms = loungeRoomRepository.findAll().stream()
                .sorted(Comparator.comparing(LoungeRoom::getId))
                .collect(Collectors.toList());
        return rooms.stream()
                .map(room -> buildState(room, roomShipRelationRepository.findByRoomId(room.getId())))
                .collect(Collectors.toList());
    }

    public List<LinenRecoveryDTO> listRecords(Long roomId, Long shipId) {
        List<LinenRecovery> records;
        if (roomId != null) {
            records = linenRecoveryRepository.findByRoomIdOrderByIdDesc(roomId);
        } else if (shipId != null) {
            records = linenRecoveryRepository.findByDepartedShipIdOrderByIdDesc(shipId);
        } else {
            records = linenRecoveryRepository.findAll();
        }
        return records.stream()
                .sorted(Comparator.comparing(LinenRecovery::getId).reversed())
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ---- 内部 ----

    private void validateWeight(LinenRecovery recovery) {
        int sets = recovery.getSetCount();
        BigDecimal weight = recovery.getBagWeight();
        BigDecimal min = recovery.getKgPerSetMin();
        BigDecimal max = recovery.getKgPerSetMax();

        BigDecimal lower = min.multiply(BigDecimal.valueOf(sets));
        BigDecimal upper = max.multiply(BigDecimal.valueOf(sets));
        boolean within = weight.compareTo(lower) >= 0 && weight.compareTo(upper) <= 0;
        if (within) {
            return;
        }
        LinenBlockedDTO blocked = mismatchBlocked(recovery, sets, weight, recovery.getWitnessName(),
                "WEIGHT_MISMATCH");
        throw new LinenRecoveryBlockedException(buildMismatchMessage(recovery, sets, weight), blocked);
    }

    private LinenBlockedDTO mismatchBlocked(LinenRecovery recovery, Integer sets, BigDecimal weight,
                                            String witness, String reason) {
        LinenBlockedDTO blocked = new LinenBlockedDTO();
        blocked.setReason(reason);
        blocked.setRoomId(recovery.getRoomId());
        blocked.setRoomCode(recovery.getRoomCode());
        blocked.setRoomName(recovery.getRoomName());
        blocked.setRecoveryId(recovery.getId());
        blocked.setRecoveryNo(recovery.getRecoveryNo());
        blocked.setSubmittedSetCount(sets);
        blocked.setSubmittedBagWeight(weight);
        blocked.setSubmittedWitnessName(witness);
        blocked.setKgPerSetMin(recovery.getKgPerSetMin());
        blocked.setKgPerSetMax(recovery.getKgPerSetMax());
        if (weight != null && weight.signum() > 0) {
            blocked.setInferredSetCountMin(inferredMinSets(weight, recovery.getKgPerSetMax()));
            blocked.setInferredSetCountMax(inferredMaxSets(weight, recovery.getKgPerSetMin()));
        }
        blocked.setMessage(buildMismatchMessage(recovery, sets, weight));
        return blocked;
    }

    private LinenBlockedDTO duplicateBlocked(LoungeRoom room, LinenRecovery existing, LinenRecoverySaveDTO dto) {
        LinenBlockedDTO blocked = new LinenBlockedDTO();
        blocked.setReason("DUPLICATE_ACTIVE_RECOVERY");
        if (room != null) {
            blocked.setRoomId(room.getId());
            blocked.setRoomCode(room.getRoomCode());
            blocked.setRoomName(room.getRoomName());
        }
        if (existing != null) {
            blocked.setRoomId(existing.getRoomId());
            blocked.setRoomCode(existing.getRoomCode());
            blocked.setRoomName(existing.getRoomName());
            blocked.setRecoveryId(existing.getId());
            blocked.setRecoveryNo(existing.getRecoveryNo());
            blocked.setExistingRecoveryId(existing.getId());
            blocked.setExistingRecoveryNo(existing.getRecoveryNo());
            blocked.setSubmittedSetCount(existing.getSetCount());
            blocked.setSubmittedBagWeight(existing.getBagWeight());
            blocked.setSubmittedWitnessName(existing.getWitnessName());
            blocked.setKgPerSetMin(existing.getKgPerSetMin());
            blocked.setKgPerSetMax(existing.getKgPerSetMax());
        } else {
            blocked.setSubmittedSetCount(dto.getSetCount());
            blocked.setSubmittedBagWeight(dto.getBagWeight());
            blocked.setSubmittedWitnessName(dto.getWitnessName());
            blocked.setKgPerSetMin(DEFAULT_KG_PER_SET_MIN);
            blocked.setKgPerSetMax(DEFAULT_KG_PER_SET_MAX);
        }
        blocked.setMessage("同一间房同一时刻只允许挂一份未作废回收单，后保存的这一单失败");
        return blocked;
    }

    private String buildMismatchMessage(LinenRecovery recovery, Integer sets, BigDecimal weight) {
        String inferred = "—";
        if (weight != null && weight.signum() > 0) {
            inferred = inferredMinSets(weight, recovery.getKgPerSetMax()) + "~"
                    + inferredMaxSets(weight, recovery.getKgPerSetMin());
        }
        return "房间 " + recovery.getRoomCode() + " 布草回收保存失败：登记 " + sets + " 套、本次秤重 "
                + weight.toPlainString() + "kg，对不上每套约定 "
                + recovery.getKgPerSetMin().toPlainString() + "~" + recovery.getKgPerSetMax().toPlainString()
                + "kg/套；按这次秤重折出来应是 " + inferred + " 套";
    }

    private String buildTamperMessage(LinenRecovery recovery, Integer sets, BigDecimal weight) {
        String inferred = "—";
        if (weight != null && weight.signum() > 0) {
            inferred = inferredMinSets(weight, recovery.getKgPerSetMax()) + "~"
                    + inferredMaxSets(weight, recovery.getKgPerSetMin());
        }
        return "回收单 " + recovery.getRecoveryNo() + " 的可住灯已亮过，套数、公斤数已锁定，"
                + "不能改成 " + sets + " 套 / " + (weight == null ? "空" : weight.toPlainString())
                + "kg；每套约定 " + recovery.getKgPerSetMin().toPlainString() + "~"
                + recovery.getKgPerSetMax().toPlainString() + "kg，按这次秤重 "
                + (weight == null ? "空" : weight.toPlainString()) + "kg 折出来应是 " + inferred + " 套";
    }

    /** ceil(weight / 每套上界)：每套最重时，至少得有多少套 */
    private Integer inferredMinSets(BigDecimal weight, BigDecimal kgPerSetMax) {
        return weight.divide(kgPerSetMax, 0, RoundingMode.CEILING).intValue();
    }

    /** floor(weight / 每套下界)：每套最轻时，最多能有多少套 */
    private Integer inferredMaxSets(BigDecimal weight, BigDecimal kgPerSetMin) {
        return weight.divide(kgPerSetMin, 0, RoundingMode.FLOOR).intValue();
    }

    private LinenRoomStateDTO buildState(LoungeRoom room, List<RoomShipRelation> relations) {
        LinenRoomStateDTO dto = new LinenRoomStateDTO();
        dto.setRoomId(room.getId());
        dto.setRoomCode(room.getRoomCode());
        dto.setRoomName(room.getRoomName());
        dto.setFloor(room.getFloor());

        relations.stream()
                .filter(r -> ACTIVE.equals(r.getStatus()))
                .findFirst()
                .ifPresent(active -> shipRepository.findById(active.getShipId()).ifPresent(ship -> {
                    dto.setCurrentShipId(ship.getId());
                    dto.setCurrentShipCode(ship.getShipCode());
                    dto.setCurrentShipName(ship.getShipName());
                }));

        CycleContext cycle = cycleFromRelations(relations);
        if (cycle.latestInactive != null) {
            shipRepository.findById(cycle.latestInactive.getShipId()).ifPresent(ship -> {
                dto.setDepartedShipId(ship.getId());
                dto.setDepartedShipCode(ship.getShipCode());
                dto.setDepartedShipName(ship.getShipName());
            });
        }

        dto.setKgPerSetMin(DEFAULT_KG_PER_SET_MIN);
        dto.setKgPerSetMax(DEFAULT_KG_PER_SET_MAX);

        if (cycle.latestInactive == null) {
            dto.setLinenState(NOT_NEEDED);
            dto.setAvailableLight(false);
            dto.setRecoveryPendingLight(false);
            return dto;
        }

        LinenRecovery sheet = findCycleSheet(room.getId(), cycle.cycleBatch).orElse(null);
        if (sheet == null) {
            dto.setLinenState(PENDING);
            dto.setAvailableLight(false);
            dto.setRecoveryPendingLight(true);
            return dto;
        }

        dto.setRecoveryId(sheet.getId());
        dto.setRecoveryNo(sheet.getRecoveryNo());
        dto.setStatus(sheet.getStatus());
        dto.setSetCount(sheet.getSetCount());
        dto.setBagWeight(sheet.getBagWeight());
        dto.setWitnessName(sheet.getWitnessName());
        dto.setKgPerSetMin(sheet.getKgPerSetMin());
        dto.setKgPerSetMax(sheet.getKgPerSetMax());

        boolean complete = CONFIRMED.equals(sheet.getStatus())
                && sheet.getSetCount() != null && sheet.getBagWeight() != null
                && sheet.getWitnessName() != null;
        if (complete) {
            dto.setLinenState(RECOVERED);
            dto.setAvailableLight(true);
            dto.setRecoveryPendingLight(false);
        } else {
            dto.setLinenState(DRAFT_STATE);
            dto.setAvailableLight(false);
            dto.setRecoveryPendingLight(true);
        }
        return dto;
    }

    private CycleContext loadCycle(Long roomId) {
        return cycleFromRelations(roomShipRelationRepository.findByRoomIdForUpdate(roomId));
    }

    private CycleContext cycleFromRelations(List<RoomShipRelation> relations) {
        RoomShipRelation active = relations.stream()
                .filter(r -> ACTIVE.equals(r.getStatus()))
                .findFirst().orElse(null);
        RoomShipRelation latestInactive = relations.stream()
                .filter(r -> !ACTIVE.equals(r.getStatus()))
                .max(Comparator
                        .comparing(RoomShipRelation::getUpdateTime,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(RoomShipRelation::getId))
                .orElse(null);
        String cycleBatch = active != null ? active.getChangeBatch()
                : latestInactive != null ? latestInactive.getChangeBatch() : null;
        return new CycleContext(active, latestInactive, cycleBatch);
    }

    private Optional<LinenRecovery> findCycleSheet(Long roomId, String cycleBatch) {
        return linenRecoveryRepository.findCycleSheets(roomId, cycleBatch).stream().findFirst();
    }

    private void refreshActiveKey(LinenRecovery recovery) {
        if (VOID.equals(recovery.getStatus())) {
            recovery.setActiveUniqueKey(null);
        } else {
            recovery.setActiveUniqueKey(recovery.getRoomId() + "#" + Objects.toString(recovery.getChangeBatch(), ""));
        }
    }

    private Integer normalizeSets(Integer sets) {
        return sets != null && sets > 0 ? sets : null;
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        return weight != null && weight.signum() > 0 ? weight.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private LinenRecoveryDTO convertToDTO(LinenRecovery recovery) {
        LinenRecoveryDTO dto = new LinenRecoveryDTO();
        dto.setId(recovery.getId());
        dto.setRecoveryNo(recovery.getRecoveryNo());
        dto.setRoomId(recovery.getRoomId());
        dto.setRoomCode(recovery.getRoomCode());
        dto.setRoomName(recovery.getRoomName());
        dto.setDepartedShipId(recovery.getDepartedShipId());
        dto.setDepartedShipCode(recovery.getDepartedShipCode());
        dto.setDepartedShipName(recovery.getDepartedShipName());
        dto.setChangeBatch(recovery.getChangeBatch());
        dto.setSetCount(recovery.getSetCount());
        dto.setBagWeight(recovery.getBagWeight());
        dto.setKgPerSetMin(recovery.getKgPerSetMin());
        dto.setKgPerSetMax(recovery.getKgPerSetMax());
        dto.setWitnessName(recovery.getWitnessName());
        dto.setOperator(recovery.getOperator());
        dto.setStatus(recovery.getStatus());
        dto.setRemark(recovery.getRemark());
        dto.setConfirmedTime(recovery.getConfirmedTime());
        dto.setConfirmedBy(recovery.getConfirmedBy());
        dto.setVoidTime(recovery.getVoidTime());
        dto.setVoidBy(recovery.getVoidBy());
        dto.setVoidReason(recovery.getVoidReason());
        dto.setCreateTime(recovery.getCreateTime());
        dto.setUpdateTime(recovery.getUpdateTime());
        return dto;
    }

    private String newRecoveryNo() {
        return "LINEN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private record CycleContext(RoomShipRelation active, RoomShipRelation latestInactive, String cycleBatch) {
    }
}
