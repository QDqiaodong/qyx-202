package com.example.shiproom.service;

import com.example.shiproom.dto.FuelRefillDTO;
import com.example.shiproom.dto.FuelRefillSaveDTO;
import com.example.shiproom.dto.FuelReviewDTO;
import com.example.shiproom.dto.GeneratorFuelStateDTO;
import com.example.shiproom.entity.EmergencyGenerator;
import com.example.shiproom.entity.FuelRefill;
import com.example.shiproom.exception.FuelAlreadyReviewedException;
import com.example.shiproom.exception.FuelRefillBlockedException;
import com.example.shiproom.exception.FuelReviewForbiddenException;
import com.example.shiproom.repository.EmergencyGeneratorRepository;
import com.example.shiproom.repository.FuelRefillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelRefillServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);

    @Mock
    private FuelRefillRepository fuelRefillRepository;
    @Mock
    private EmergencyGeneratorRepository generatorRepository;
    @Mock
    private ShiftOperationLockService shiftOperationLockService;

    private FuelRefillService service;

    @BeforeEach
    void setUp() {
        service = new FuelRefillService(fuelRefillRepository, generatorRepository, shiftOperationLockService);
        service.setClock(Clock.fixed(Instant.parse("2026-09-16T10:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    // 登记：写下哪台机、本罐编号、实加升数、经办值班；登记后未复核，库存不动
    @Test
    void createWritesCanLitersAndDutyOfficerAndLeavesStockUntouched() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "100.00");
        when(generatorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generator));
        when(fuelRefillRepository.findPending(1L, TODAY)).thenReturn(List.of());
        when(fuelRefillRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FuelRefillSaveDTO dto = new FuelRefillSaveDTO();
        dto.setGeneratorId(1L);
        dto.setCanNo("CAN-2026-091");
        dto.setLiters(new BigDecimal("30.5"));
        dto.setDutyOfficer("张值班");
        dto.setDutyShift("DAY");

        FuelRefillDTO result = service.create(dto);

        ArgumentCaptor<FuelRefill> captor = ArgumentCaptor.forClass(FuelRefill.class);
        verify(fuelRefillRepository).saveAndFlush(captor.capture());
        FuelRefill saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(saved.getCanNo()).isEqualTo("CAN-2026-091");
        assertThat(saved.getLiters()).isEqualByComparingTo("30.50");
        assertThat(saved.getDutyOfficer()).isEqualTo("张值班");
        assertThat(saved.getDutyShift()).isEqualTo("DAY");
        assertThat(saved.getRefillDate()).isEqualTo(TODAY);
        assertThat(saved.getActiveUniqueKey()).isEqualTo("1#" + TODAY);
        assertThat(result.getRefillNo()).startsWith("FUEL-");
        // 登记阶段库存升数不动，盘库存只看复核通过的
        verify(generatorRepository, never()).saveAndFlush(any());
        assertThat(generator.getFuelStockLiters()).isEqualByComparingTo("100.00");
    }

    // 缺罐号 / 缺升数 / 缺经办：不许只写「加过了」
    @Test
    void createWithoutCanLitersOrOfficerRejected() {
        FuelRefillSaveDTO noCan = new FuelRefillSaveDTO();
        noCan.setGeneratorId(1L);
        noCan.setLiters(new BigDecimal("10"));
        noCan.setDutyOfficer("张值班");
        noCan.setDutyShift("DAY");
        assertThatThrownBy(() -> service.create(noCan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("本罐编号");

        FuelRefillSaveDTO noLiters = new FuelRefillSaveDTO();
        noLiters.setGeneratorId(1L);
        noLiters.setCanNo("CAN-1");
        noLiters.setDutyOfficer("张值班");
        noLiters.setDutyShift("DAY");
        assertThatThrownBy(() -> service.create(noLiters))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("实加升数");

        FuelRefillSaveDTO noOfficer = new FuelRefillSaveDTO();
        noOfficer.setGeneratorId(1L);
        noOfficer.setCanNo("CAN-1");
        noOfficer.setLiters(new BigDecimal("10"));
        noOfficer.setDutyShift("DAY");
        assertThatThrownBy(() -> service.create(noOfficer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("经办值班");
    }

    // 同一台机同一自然日已挂着未复核：后登记的整单退回，并看见已在库那条
    @Test
    void createWithPendingSameDayRejectsWithExistingRefillNo() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "100.00");
        when(generatorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generator));
        FuelRefill existing = pending(7L, 1L, "CAN-OLD", "20.00", "李值班", "NIGHT");
        when(fuelRefillRepository.findPending(1L, TODAY)).thenReturn(List.of(existing));

        FuelRefillSaveDTO dto = new FuelRefillSaveDTO();
        dto.setGeneratorId(1L);
        dto.setCanNo("CAN-NEW");
        dto.setLiters(new BigDecimal("30"));
        dto.setDutyOfficer("张值班");
        dto.setDutyShift("DAY");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(FuelRefillBlockedException.class)
                .hasMessageContaining(existing.getRefillNo())
                .hasMessageContaining("李值班");

        verify(fuelRefillRepository, never()).saveAndFlush(any());
    }

    // 复核通过之后，这台机当天才能再开下一条
    @Test
    void createAfterReviewPassedSameDayAllowed() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "130.00");
        when(generatorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generator));
        // 当天只有已复核的单，没有未复核的 -> 允许再开
        when(fuelRefillRepository.findPending(1L, TODAY)).thenReturn(List.of());
        when(fuelRefillRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FuelRefillSaveDTO dto = new FuelRefillSaveDTO();
        dto.setGeneratorId(1L);
        dto.setCanNo("CAN-2026-092");
        dto.setLiters(new BigDecimal("25"));
        dto.setDutyOfficer("王值班");
        dto.setDutyShift("MIDDLE");

        FuelRefillDTO result = service.create(dto);
        assertThat(result.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(result.getCanNo()).isEqualTo("CAN-2026-092");
    }

    // 复核通过：另一个班的人核，升数与复核结论一起落库，库存从复核前的数加上实加升数
    @Test
    void reviewPassesWithDifferentShiftAndAddsLitersToStock() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "100.00");
        FuelRefill refill = pending(7L, 1L, "CAN-2026-091", "30.50", "张值班", "DAY");
        when(fuelRefillRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refill));
        when(generatorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generator));
        when(fuelRefillRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        FuelReviewDTO dto = new FuelReviewDTO();
        dto.setReviewerName("陈复核");
        dto.setReviewerShift("NIGHT");
        dto.setReviewComment("罐号与加油枪读数一致");

        FuelRefillDTO result = service.review(7L, dto);

        assertThat(result.getStatus()).isEqualTo("REVIEWED");
        assertThat(result.getReviewerName()).isEqualTo("陈复核");
        assertThat(result.getReviewerShift()).isEqualTo("NIGHT");
        assertThat(result.getReviewConclusion()).isEqualTo("PASS");
        assertThat(result.getReviewTime()).isNotNull();
        // 库存：复核前 100.00 + 实加 30.50 = 130.50，随单留底
        assertThat(result.getStockBeforeLiters()).isEqualByComparingTo("100.00");
        assertThat(result.getStockAfterLiters()).isEqualByComparingTo("130.50");
        assertThat(generator.getFuelStockLiters()).isEqualByComparingTo("130.50");
        // 复核通过腾出名额，当天才能再开下一条
        assertThat(refill.getActiveUniqueKey()).isNull();
    }

    // 自己加的不能自己核
    @Test
    void reviewByDutyOfficerSelfForbidden() {
        FuelRefill refill = pending(7L, 1L, "CAN-1", "30", "张值班", "DAY");
        when(fuelRefillRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refill));

        FuelReviewDTO dto = new FuelReviewDTO();
        dto.setReviewerName("张值班");
        dto.setReviewerShift("NIGHT"); // 即使换个班填，人是同一个也不行

        assertThatThrownBy(() -> service.review(7L, dto))
                .isInstanceOf(FuelReviewForbiddenException.class)
                .hasMessageContaining("自己加的不能自己核");

        verify(generatorRepository, never()).saveAndFlush(any());
        verify(fuelRefillRepository, never()).saveAndFlush(any());
    }

    // 复核人必须是另一个班的人
    @Test
    void reviewBySameShiftForbidden() {
        FuelRefill refill = pending(7L, 1L, "CAN-1", "30", "张值班", "DAY");
        when(fuelRefillRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refill));

        FuelReviewDTO dto = new FuelReviewDTO();
        dto.setReviewerName("同班李");
        dto.setReviewerShift("DAY");

        assertThatThrownBy(() -> service.review(7L, dto))
                .isInstanceOf(FuelReviewForbiddenException.class)
                .hasMessageContaining("另一个班");

        verify(generatorRepository, never()).saveAndFlush(any());
        verify(fuelRefillRepository, never()).saveAndFlush(any());
    }

    // 两人抢着复核同一条：先写完的结论留下，后到的人看见这条已经核过，库存不再变
    @Test
    void reviewRaceKeepsFirstConclusionAndLateComerSeesReviewed() {
        FuelRefill refill = pending(7L, 1L, "CAN-1", "30", "张值班", "DAY");
        // 先写完的那次结论
        refill.setStatus("REVIEWED");
        refill.setReviewerName("陈复核");
        refill.setReviewerShift("NIGHT");
        refill.setReviewTime(LocalDateTime.of(2026, 9, 16, 9, 30));
        refill.setReviewConclusion("PASS");
        refill.setActiveUniqueKey(null);
        when(fuelRefillRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refill));

        FuelReviewDTO dto = new FuelReviewDTO();
        dto.setReviewerName("迟到复核");
        dto.setReviewerShift("MIDDLE");

        assertThatThrownBy(() -> service.review(7L, dto))
                .isInstanceOf(FuelAlreadyReviewedException.class)
                .hasMessageContaining("陈复核")
                .hasMessageContaining("复核通过");

        // 后到者的结论和升数都不写库
        verify(generatorRepository, never()).saveAndFlush(any());
        verify(fuelRefillRepository, never()).saveAndFlush(any());
        assertThat(refill.getReviewerName()).isEqualTo("陈复核");
    }

    // 复核写到一半失败：复核结论留不下半截，库存升数仍是复核前的数
    @Test
    void reviewFailureLeavesNoHalfWrittenState() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "100.00");
        FuelRefill refill = pending(7L, 1L, "CAN-1", "30.50", "张值班", "DAY");
        when(fuelRefillRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(refill));
        when(generatorRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(generator));
        // 库存写库失败 -> 整个复核事务回滚
        when(generatorRepository.saveAndFlush(any())).thenThrow(new RuntimeException("DB 写入失败"));

        FuelReviewDTO dto = new FuelReviewDTO();
        dto.setReviewerName("陈复核");
        dto.setReviewerShift("NIGHT");

        assertThatThrownBy(() -> service.review(7L, dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 写入失败");

        // 复核结论没机会落库（先写库存、后写结论，库存失败结论不写）
        verify(fuelRefillRepository, never()).saveAndFlush(any());
        assertThat(refill.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(refill.getReviewerName()).isNull();
        assertThat(refill.getReviewConclusion()).isNull();
        assertThat(refill.getStockAfterLiters()).isNull();
    }

    // 发电机列表的「已加油」列：带得出今天已核升数和复核人
    @Test
    void generatorStateShowsReviewedLitersAndReviewer() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "130.50");
        when(generatorRepository.findAll()).thenReturn(List.of(generator));
        FuelRefill reviewed = reviewed(7L, 1L, "CAN-1", "30.50", "张值班", "DAY", "陈复核", "NIGHT");
        when(fuelRefillRepository.findByGeneratorIdAndRefillDateOrderByIdDesc(1L, TODAY))
                .thenReturn(List.of(reviewed));

        List<GeneratorFuelStateDTO> states = service.listGeneratorStates();

        assertThat(states).hasSize(1);
        GeneratorFuelStateDTO state = states.get(0);
        assertThat(state.getTodayState()).isEqualTo("REVIEWED");
        assertThat(state.getTodayReviewedLiters()).isEqualByComparingTo("30.50");
        assertThat(state.getTodayReviewerName()).isEqualTo("陈复核");
        assertThat(state.getTodayReviewerShift()).isEqualTo("NIGHT");
        assertThat(state.getFuelStockLiters()).isEqualByComparingTo("130.50");
    }

    // 挂着未复核单时：状态为待复核，且带得出未复核单的罐号升数经办
    @Test
    void generatorStateShowsPendingRefill() {
        EmergencyGenerator generator = generator(1L, "GEN-ROOF-01", "100.00");
        when(generatorRepository.findAll()).thenReturn(List.of(generator));
        FuelRefill pending = pending(7L, 1L, "CAN-9", "18.00", "张值班", "DAY");
        when(fuelRefillRepository.findByGeneratorIdAndRefillDateOrderByIdDesc(1L, TODAY))
                .thenReturn(List.of(pending));

        GeneratorFuelStateDTO state = service.listGeneratorStates().get(0);
        assertThat(state.getTodayState()).isEqualTo("PENDING_REVIEW");
        assertThat(state.getPendingRefillNo()).isEqualTo(pending.getRefillNo());
        assertThat(state.getPendingCanNo()).isEqualTo("CAN-9");
        assertThat(state.getPendingLiters()).isEqualByComparingTo("18.00");
        assertThat(state.getPendingDutyOfficer()).isEqualTo("张值班");
    }

    private EmergencyGenerator generator(Long id, String code, String stock) {
        EmergencyGenerator generator = new EmergencyGenerator();
        generator.setId(id);
        generator.setGenCode(code);
        generator.setGenName("发电机" + code);
        generator.setLocation("码头楼顶");
        generator.setStatus("ACTIVE");
        generator.setFuelStockLiters(new BigDecimal(stock));
        return generator;
    }

    private FuelRefill pending(Long id, Long generatorId, String canNo, String liters,
                               String dutyOfficer, String dutyShift) {
        FuelRefill refill = new FuelRefill();
        refill.setId(id);
        refill.setRefillNo("FUEL-" + id);
        refill.setGeneratorId(generatorId);
        refill.setGenCode("GEN-ROOF-01");
        refill.setGenName("码头楼顶应急发电机");
        refill.setRefillDate(TODAY);
        refill.setCanNo(canNo);
        refill.setLiters(new BigDecimal(liters));
        refill.setDutyOfficer(dutyOfficer);
        refill.setDutyShift(dutyShift);
        refill.setStatus("PENDING_REVIEW");
        refill.setActiveUniqueKey(generatorId + "#" + TODAY);
        return refill;
    }

    private FuelRefill reviewed(Long id, Long generatorId, String canNo, String liters,
                                String dutyOfficer, String dutyShift,
                                String reviewer, String reviewerShift) {
        FuelRefill refill = pending(id, generatorId, canNo, liters, dutyOfficer, dutyShift);
        refill.setStatus("REVIEWED");
        refill.setReviewerName(reviewer);
        refill.setReviewerShift(reviewerShift);
        refill.setReviewTime(LocalDateTime.of(2026, 9, 16, 9, 30));
        refill.setReviewConclusion("PASS");
        refill.setStockBeforeLiters(new BigDecimal("100.00"));
        refill.setStockAfterLiters(new BigDecimal("100.00").add(new BigDecimal(liters)));
        refill.setActiveUniqueKey(null);
        return refill;
    }
}
