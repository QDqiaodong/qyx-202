package com.example.shiproom.service;

import com.example.shiproom.dto.FuelBlockedDTO;
import com.example.shiproom.dto.FuelRefillDTO;
import com.example.shiproom.dto.FuelRefillSaveDTO;
import com.example.shiproom.dto.FuelReviewDTO;
import com.example.shiproom.dto.GeneratorDTO;
import com.example.shiproom.dto.GeneratorFuelStateDTO;
import com.example.shiproom.entity.EmergencyGenerator;
import com.example.shiproom.entity.FuelRefill;
import com.example.shiproom.exception.FuelAlreadyReviewedException;
import com.example.shiproom.exception.FuelRefillBlockedException;
import com.example.shiproom.exception.FuelReviewForbiddenException;
import com.example.shiproom.repository.EmergencyGeneratorRepository;
import com.example.shiproom.repository.FuelRefillRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 发电机加油登记 + 复核。
 *
 * 登记：选中哪一台机，写下本罐编号、实加升数、经办值班（姓名+班次）。
 *       同一台机同一自然日只允许挂着一条未复核（active_unique_key = generatorId#refillDate
 *       + 唯一约束兜底，两人同时登记后到者 409 并看见已在库那条）。
 *
 * 复核：复核人必须是另一个班的人，自己加的不能自己核（403）。
 *       复核通过与库存升数同一事务：先把实加升数加进发电机库存，再落复核结论，
 *       任何一步失败整体回滚——升数和复核结论不留半截，库存仍是复核前的数。
 *       两人抢着复核同一条：换班全局行锁 + 记录行悲观锁串行，先写完的那次结论留下，
 *       后到的人 409 并看得见这条已经核过。
 *       复核通过之后，这台机当天才能再开下一条。
 */
@Service
public class FuelRefillService {

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String REVIEWED = "REVIEWED";

    public static final String CONCLUSION_PASS = "PASS";

    public static final String STATE_NONE = "NONE";
    public static final String STATE_PENDING = "PENDING_REVIEW";
    public static final String STATE_REVIEWED = "REVIEWED";

    private static final String ACTIVE = "ACTIVE";

    /** 班次：早班 / 中班 / 夜班；复核人班次必须与经办班次不同 */
    public static final Set<String> SHIFTS = Set.of("DAY", "MIDDLE", "NIGHT");

    private final FuelRefillRepository fuelRefillRepository;
    private final EmergencyGeneratorRepository generatorRepository;
    private final ShiftOperationLockService shiftOperationLockService;

    /** 可替换时钟，便于测试「同一自然日」 */
    private Clock clock = Clock.systemDefaultZone();

    public FuelRefillService(FuelRefillRepository fuelRefillRepository,
                             EmergencyGeneratorRepository generatorRepository,
                             ShiftOperationLockService shiftOperationLockService) {
        this.fuelRefillRepository = fuelRefillRepository;
        this.generatorRepository = generatorRepository;
        this.shiftOperationLockService = shiftOperationLockService;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ============================ 登记加油 ============================

    /**
     * 登记一次加油：写下哪台机、本罐编号、实加升数、经办值班。
     * 登记后状态为未复核，库存升数不动；同机同日已有未复核单则整单退回（409）。
     */
    @Transactional
    public FuelRefillDTO create(FuelRefillSaveDTO dto) {
        shiftOperationLockService.lock();

        if (dto.getGeneratorId() == null) {
            throw new IllegalArgumentException("要加油的发电机不能为空");
        }
        String canNo = dto.getCanNo() == null ? null : dto.getCanNo().trim();
        if (canNo == null || canNo.isEmpty()) {
            throw new IllegalArgumentException("本罐编号不能为空，光写「加过了」对不上库存");
        }
        BigDecimal liters = normalizeLiters(dto.getLiters());
        if (liters == null) {
            throw new IllegalArgumentException("实加升数必须大于 0，光写「加过了」对不上库存");
        }
        String dutyOfficer = dto.getDutyOfficer() == null ? null : dto.getDutyOfficer().trim();
        if (dutyOfficer == null || dutyOfficer.isEmpty()) {
            throw new IllegalArgumentException("经办值班不能为空");
        }
        String dutyShift = normalizeShift(dto.getDutyShift());
        if (dutyShift == null) {
            throw new IllegalArgumentException("经办班次必须是早班(DAY)/中班(MIDDLE)/夜班(NIGHT)之一");
        }

        EmergencyGenerator generator = generatorRepository.findByIdForUpdate(dto.getGeneratorId())
                .orElseThrow(() -> new IllegalArgumentException("发电机不存在"));
        if (!ACTIVE.equals(generator.getStatus())) {
            throw new IllegalArgumentException("发电机 " + generator.getGenCode() + " 已停用，不能登记加油");
        }

        LocalDate refillDate = today();
        FuelRefill existing = findPending(generator.getId(), refillDate);
        if (existing != null) {
            throw new FuelRefillBlockedException(
                    "发电机 " + generator.getGenCode() + " 今天已挂着一条未复核的加油 "
                            + existing.getRefillNo() + "（经办 " + existing.getDutyOfficer()
                            + "），同一台机同一天只允许一条未复核；请先由另一个班的人复核通过，再开下一条",
                    duplicateBlocked(generator, existing));
        }

        FuelRefill refill = new FuelRefill();
        refill.setRefillNo(newRefillNo());
        refill.setGeneratorId(generator.getId());
        refill.setGenCode(generator.getGenCode());
        refill.setGenName(generator.getGenName());
        refill.setRefillDate(refillDate);
        refill.setCanNo(canNo);
        refill.setLiters(liters);
        refill.setDutyOfficer(dutyOfficer);
        refill.setDutyShift(dutyShift);
        refill.setStatus(PENDING_REVIEW);
        refill.setActiveUniqueKey(activeKey(generator.getId(), refillDate));
        refill.setRemark(dto.getRemark());

        try {
            refill = fuelRefillRepository.saveAndFlush(refill);
        } catch (DataIntegrityViolationException e) {
            // 两人同时给同一台机登记：唯一约束兜底，后到者看见已在库那条
            FuelRefill current = findPending(generator.getId(), refillDate);
            throw new FuelRefillBlockedException(
                    "发电机 " + generator.getGenCode() + " 今天已挂着一条未复核的加油"
                            + (current != null ? " " + current.getRefillNo() : "")
                            + "，后登记的这一条失败",
                    duplicateBlocked(generator, current));
        }
        return toDTO(refill);
    }

    // ============================ 复核 ============================

    /**
     * 复核通过一次加油。复核人必须是另一个班的人，自己加的不能自己核。
     * 复核结论、库存升数同一事务落库；两人抢着复核，先写完的那次留下，
     * 后到的人 409 并看见这条已经核过。
     */
    @Transactional
    public FuelRefillDTO review(Long id, FuelReviewDTO dto) {
        shiftOperationLockService.lock();

        FuelRefill refill = fuelRefillRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("加油登记不存在"));

        if (!PENDING_REVIEW.equals(refill.getStatus())) {
            // 复核撞车：后到的人看见先写完的那次结论
            throw new FuelAlreadyReviewedException(
                    "加油单 " + refill.getRefillNo() + " 已由 " + refill.getReviewerName()
                            + "（" + shiftText(refill.getReviewerShift()) + "）在 " + refill.getReviewTime()
                            + " 复核通过，不能重复复核",
                    alreadyReviewedBlocked(refill));
        }

        String reviewerName = dto.getReviewerName() == null ? null : dto.getReviewerName().trim();
        if (reviewerName == null || reviewerName.isEmpty()) {
            throw new IllegalArgumentException("复核人不能为空");
        }
        String reviewerShift = normalizeShift(dto.getReviewerShift());
        if (reviewerShift == null) {
            throw new IllegalArgumentException("复核人班次必须是早班(DAY)/中班(MIDDLE)/夜班(NIGHT)之一");
        }
        if (reviewerName.equalsIgnoreCase(refill.getDutyOfficer())) {
            throw new FuelReviewForbiddenException(
                    "自己加的不能自己核：加油单 " + refill.getRefillNo() + " 是 " + refill.getDutyOfficer()
                            + " 加的，复核人必须换一个人");
        }
        if (reviewerShift.equals(refill.getDutyShift())) {
            throw new FuelReviewForbiddenException(
                    "复核人必须是另一个班的人：加油单 " + refill.getRefillNo() + " 由 "
                            + shiftText(refill.getDutyShift()) + " 的 " + refill.getDutyOfficer()
                            + " 登记，复核人不能再是 " + shiftText(refill.getDutyShift()));
        }

        // 复核通过与库存升数同一事务：先加库存，再落复核结论；
        // 任一步失败整体回滚，升数和复核结论不留半截，库存仍是复核前的数
        EmergencyGenerator generator = generatorRepository.findByIdForUpdate(refill.getGeneratorId())
                .orElseThrow(() -> new IllegalStateException("发电机档案缺失，复核整单回滚"));
        BigDecimal stockBefore = generator.getFuelStockLiters() == null
                ? BigDecimal.ZERO : generator.getFuelStockLiters();
        BigDecimal stockAfter = stockBefore.add(refill.getLiters());
        generator.setFuelStockLiters(stockAfter);
        generatorRepository.saveAndFlush(generator);

        refill.setStatus(REVIEWED);
        refill.setReviewerName(reviewerName);
        refill.setReviewerShift(reviewerShift);
        refill.setReviewTime(now());
        refill.setReviewConclusion(CONCLUSION_PASS);
        refill.setReviewComment(dto.getReviewComment() == null || dto.getReviewComment().isBlank()
                ? null : dto.getReviewComment().trim());
        refill.setStockBeforeLiters(stockBefore);
        refill.setStockAfterLiters(stockAfter);
        // 复核通过，腾出「同机同日一条未复核」的名额，这台机当天才能再开下一条
        refill.setActiveUniqueKey(null);
        refill = fuelRefillRepository.saveAndFlush(refill);
        return toDTO(refill);
    }

    // ============================ 查询 ============================

    /** 发电机列表 + 每台机今天的加油状态（已加油列背后带得出升数和复核人） */
    @Transactional(readOnly = true)
    public List<GeneratorFuelStateDTO> listGeneratorStates() {
        LocalDate today = today();
        return generatorRepository.findAll().stream()
                .sorted(Comparator.comparing(EmergencyGenerator::getId))
                .map(generator -> buildState(generator, today))
                .collect(Collectors.toList());
    }

    /** 加油流水：可按发电机、自然日过滤；升数、罐号、经办、复核人都可查 */
    @Transactional(readOnly = true)
    public List<FuelRefillDTO> listRecords(Long generatorId, LocalDate refillDate) {
        List<FuelRefill> records;
        if (generatorId != null && refillDate != null) {
            records = fuelRefillRepository.findByGeneratorIdAndRefillDateOrderByIdDesc(generatorId, refillDate);
        } else if (generatorId != null) {
            records = fuelRefillRepository.findByGeneratorIdOrderByIdDesc(generatorId);
        } else if (refillDate != null) {
            records = fuelRefillRepository.findByRefillDateOrderByIdDesc(refillDate);
        } else {
            records = fuelRefillRepository.findAll();
        }
        return records.stream()
                .sorted(Comparator.comparing(FuelRefill::getId).reversed())
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ============================ 发电机档案 ============================

    @Transactional
    public GeneratorDTO createGenerator(GeneratorDTO dto) {
        if (dto.getGenCode() == null || dto.getGenCode().isBlank()) {
            throw new IllegalArgumentException("机号不能为空");
        }
        if (dto.getGenName() == null || dto.getGenName().isBlank()) {
            throw new IllegalArgumentException("发电机名称不能为空");
        }
        if (generatorRepository.existsByGenCode(dto.getGenCode().trim())) {
            throw new IllegalArgumentException("机号 " + dto.getGenCode() + " 已存在");
        }
        EmergencyGenerator generator = new EmergencyGenerator();
        generator.setGenCode(dto.getGenCode().trim());
        generator.setGenName(dto.getGenName().trim());
        generator.setLocation(dto.getLocation());
        generator.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? ACTIVE : dto.getStatus());
        generator.setFuelStockLiters(dto.getFuelStockLiters() != null && dto.getFuelStockLiters().signum() > 0
                ? dto.getFuelStockLiters().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        generator.setRemark(dto.getRemark());
        return toGeneratorDTO(generatorRepository.save(generator));
    }

    @Transactional
    public GeneratorDTO updateGenerator(Long id, GeneratorDTO dto) {
        EmergencyGenerator generator = generatorRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("发电机不存在"));
        if (dto.getGenCode() != null && !dto.getGenCode().isBlank()
                && !dto.getGenCode().trim().equals(generator.getGenCode())) {
            if (generatorRepository.existsByGenCode(dto.getGenCode().trim())) {
                throw new IllegalArgumentException("机号 " + dto.getGenCode() + " 已存在");
            }
            generator.setGenCode(dto.getGenCode().trim());
        }
        if (dto.getGenName() != null && !dto.getGenName().isBlank()) {
            generator.setGenName(dto.getGenName().trim());
        }
        if (dto.getLocation() != null) {
            generator.setLocation(dto.getLocation());
        }
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            generator.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            generator.setRemark(dto.getRemark());
        }
        return toGeneratorDTO(generatorRepository.save(generator));
    }

    @Transactional(readOnly = true)
    public List<GeneratorDTO> listGenerators() {
        return generatorRepository.findAll().stream()
                .sorted(Comparator.comparing(EmergencyGenerator::getId))
                .map(this::toGeneratorDTO)
                .collect(Collectors.toList());
    }

    // ============================ 私有辅助 ============================

    private FuelRefill findPending(Long generatorId, LocalDate refillDate) {
        return fuelRefillRepository.findPending(generatorId, refillDate).stream()
                .findFirst().orElse(null);
    }

    private GeneratorFuelStateDTO buildState(EmergencyGenerator generator, LocalDate today) {
        GeneratorFuelStateDTO dto = new GeneratorFuelStateDTO();
        dto.setGeneratorId(generator.getId());
        dto.setGenCode(generator.getGenCode());
        dto.setGenName(generator.getGenName());
        dto.setLocation(generator.getLocation());
        dto.setStatus(generator.getStatus());
        dto.setFuelStockLiters(generator.getFuelStockLiters());
        dto.setToday(today);

        List<FuelRefill> todayRecords = fuelRefillRepository
                .findByGeneratorIdAndRefillDateOrderByIdDesc(generator.getId(), today);

        FuelRefill pending = todayRecords.stream()
                .filter(r -> PENDING_REVIEW.equals(r.getStatus()))
                .findFirst().orElse(null);
        List<FuelRefill> reviewed = todayRecords.stream()
                .filter(r -> REVIEWED.equals(r.getStatus()))
                .toList();

        if (pending != null) {
            dto.setTodayState(STATE_PENDING);
            dto.setPendingRefillId(pending.getId());
            dto.setPendingRefillNo(pending.getRefillNo());
            dto.setPendingCanNo(pending.getCanNo());
            dto.setPendingLiters(pending.getLiters());
            dto.setPendingDutyOfficer(pending.getDutyOfficer());
            dto.setPendingDutyShift(pending.getDutyShift());
        } else if (!reviewed.isEmpty()) {
            dto.setTodayState(STATE_REVIEWED);
        } else {
            dto.setTodayState(STATE_NONE);
        }

        BigDecimal reviewedLiters = reviewed.stream()
                .map(FuelRefill::getLiters)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTodayReviewedLiters(reviewedLiters);

        reviewed.stream().findFirst().ifPresent(latest -> {
            dto.setTodayReviewerName(latest.getReviewerName());
            dto.setTodayReviewerShift(latest.getReviewerShift());
            dto.setTodayReviewTime(latest.getReviewTime());
        });
        return dto;
    }

    private FuelBlockedDTO duplicateBlocked(EmergencyGenerator generator, FuelRefill existing) {
        FuelBlockedDTO blocked = new FuelBlockedDTO();
        blocked.setReason("DUPLICATE_PENDING");
        blocked.setGeneratorId(generator.getId());
        blocked.setGenCode(generator.getGenCode());
        blocked.setGenName(generator.getGenName());
        if (existing != null) {
            blocked.setExistingRefillId(existing.getId());
            blocked.setExistingRefillNo(existing.getRefillNo());
            blocked.setRefillId(existing.getId());
            blocked.setRefillNo(existing.getRefillNo());
            blocked.setCanNo(existing.getCanNo());
            blocked.setLiters(existing.getLiters());
            blocked.setDutyOfficer(existing.getDutyOfficer());
            blocked.setDutyShift(existing.getDutyShift());
        }
        blocked.setMessage("同一台机同一自然日只允许挂着一条未复核的加油，复核通过后才能再开下一条");
        return blocked;
    }

    private FuelBlockedDTO alreadyReviewedBlocked(FuelRefill refill) {
        FuelBlockedDTO blocked = new FuelBlockedDTO();
        blocked.setReason("ALREADY_REVIEWED");
        blocked.setGeneratorId(refill.getGeneratorId());
        blocked.setGenCode(refill.getGenCode());
        blocked.setGenName(refill.getGenName());
        blocked.setRefillId(refill.getId());
        blocked.setRefillNo(refill.getRefillNo());
        blocked.setCanNo(refill.getCanNo());
        blocked.setLiters(refill.getLiters());
        blocked.setDutyOfficer(refill.getDutyOfficer());
        blocked.setDutyShift(refill.getDutyShift());
        blocked.setReviewerName(refill.getReviewerName());
        blocked.setReviewerShift(refill.getReviewerShift());
        blocked.setReviewTime(refill.getReviewTime());
        blocked.setReviewConclusion(refill.getReviewConclusion());
        blocked.setMessage("这条已经核过：先写完的那次结论留下，后到的复核不再写入");
        return blocked;
    }

    private FuelRefillDTO toDTO(FuelRefill refill) {
        FuelRefillDTO dto = new FuelRefillDTO();
        dto.setId(refill.getId());
        dto.setRefillNo(refill.getRefillNo());
        dto.setGeneratorId(refill.getGeneratorId());
        dto.setGenCode(refill.getGenCode());
        dto.setGenName(refill.getGenName());
        dto.setRefillDate(refill.getRefillDate());
        dto.setCanNo(refill.getCanNo());
        dto.setLiters(refill.getLiters());
        dto.setDutyOfficer(refill.getDutyOfficer());
        dto.setDutyShift(refill.getDutyShift());
        dto.setStatus(refill.getStatus());
        dto.setReviewerName(refill.getReviewerName());
        dto.setReviewerShift(refill.getReviewerShift());
        dto.setReviewTime(refill.getReviewTime());
        dto.setReviewConclusion(refill.getReviewConclusion());
        dto.setReviewComment(refill.getReviewComment());
        dto.setStockBeforeLiters(refill.getStockBeforeLiters());
        dto.setStockAfterLiters(refill.getStockAfterLiters());
        dto.setRemark(refill.getRemark());
        dto.setCreateTime(refill.getCreateTime());
        dto.setUpdateTime(refill.getUpdateTime());
        return dto;
    }

    private GeneratorDTO toGeneratorDTO(EmergencyGenerator generator) {
        GeneratorDTO dto = new GeneratorDTO();
        dto.setId(generator.getId());
        dto.setGenCode(generator.getGenCode());
        dto.setGenName(generator.getGenName());
        dto.setLocation(generator.getLocation());
        dto.setStatus(generator.getStatus());
        dto.setFuelStockLiters(generator.getFuelStockLiters());
        dto.setRemark(generator.getRemark());
        dto.setCreateTime(generator.getCreateTime());
        dto.setUpdateTime(generator.getUpdateTime());
        return dto;
    }

    private BigDecimal normalizeLiters(BigDecimal liters) {
        return liters != null && liters.signum() > 0
                ? liters.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private String normalizeShift(String shift) {
        if (shift == null || shift.isBlank()) {
            return null;
        }
        String upper = shift.trim().toUpperCase();
        return SHIFTS.contains(upper) ? upper : null;
    }

    private String activeKey(Long generatorId, LocalDate refillDate) {
        return generatorId + "#" + refillDate;
    }

    private String newRefillNo() {
        return "FUEL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    static String shiftText(String shift) {
        if (shift == null) {
            return "未知班次";
        }
        return switch (shift) {
            case "DAY" -> "早班";
            case "MIDDLE" -> "中班";
            case "NIGHT" -> "夜班";
            default -> shift;
        };
    }
}
