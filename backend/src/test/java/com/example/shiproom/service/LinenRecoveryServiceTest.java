package com.example.shiproom.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinenRecoveryServiceTest {

    @Mock
    private LinenRecoveryRepository linenRecoveryRepository;
    @Mock
    private LoungeRoomRepository loungeRoomRepository;
    @Mock
    private RoomShipRelationRepository roomShipRelationRepository;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private ShiftOperationLockService shiftOperationLockService;

    private LinenRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new LinenRecoveryService(
                linenRecoveryRepository,
                loungeRoomRepository,
                roomShipRelationRepository,
                shipRepository,
                shiftOperationLockService
        );
    }

    // 三栏只填了一部分：保存为草稿，可住灯不亮（状态仍 DRAFT）
    @Test
    void saveWithEmptyColumnsStaysDraftAndKeepsRoomUnavailable() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L))
                .thenReturn(List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
        when(shipRepository.findById(30L)).thenReturn(Optional.of(ship(30L, "SHIP-A")));
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of());

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setRoomId(10L);
        dto.setSetCount(4);
        dto.setOperator("保洁甲");

        LinenRecoveryDTO result = service.save(dto);

        ArgumentCaptor<LinenRecovery> captor = ArgumentCaptor.forClass(LinenRecovery.class);
        verify(linenRecoveryRepository).saveAndFlush(captor.capture());
        LinenRecovery saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("DRAFT");
        assertThat(saved.getSetCount()).isEqualTo(4);
        assertThat(saved.getBagWeight()).isNull();
        assertThat(saved.getWitnessName()).isNull();
        assertThat(saved.getConfirmedTime()).isNull();
        assertThat(saved.getActiveUniqueKey()).isEqualTo("10#BATCH-2");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    // 三栏齐且公斤折套数对得上：确认，可住灯亮
    @Test
    void saveWithAllColumnsMatchingConfirmsAndLightsRoom() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L))
                .thenReturn(List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
        when(shipRepository.findById(30L)).thenReturn(Optional.of(ship(30L, "SHIP-A")));
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of());

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setRoomId(10L);
        dto.setSetCount(4);
        // 4 套，每套 1.5~2.5kg => 6.0~10.0kg，8.2kg 对得上
        dto.setBagWeight(new BigDecimal("8.20"));
        dto.setWitnessName("见证人李");
        dto.setOperator("保洁甲");

        LinenRecoveryDTO result = service.save(dto);

        ArgumentCaptor<LinenRecovery> captor = ArgumentCaptor.forClass(LinenRecovery.class);
        verify(linenRecoveryRepository).saveAndFlush(captor.capture());
        LinenRecovery saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
        assertThat(saved.getConfirmedTime()).isNotNull();
        assertThat(saved.getConfirmedBy()).isEqualTo("保洁甲");
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    // 三栏齐但公斤折套数对不上：整单退回，带约定区间、本次秤重、折出来的套数
    @Test
    void saveWithWeightMismatchRejectsAndCarriesIntervalAndInferredSets() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L))
                .thenReturn(List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
        when(shipRepository.findById(30L)).thenReturn(Optional.of(ship(30L, "SHIP-A")));
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of());

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setRoomId(10L);
        dto.setSetCount(4);
        // 20kg 折成套数是 ceil(20/2.5)=8 ~ floor(20/1.5)=13 套，4 套对不上
        dto.setBagWeight(new BigDecimal("20.00"));
        dto.setWitnessName("见证人李");

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(LinenRecoveryBlockedException.class)
                .hasMessageContaining("4")
                .hasMessageContaining("20.00")
                .hasMessageContaining("1.50")
                .hasMessageContaining("2.50")
                .hasMessageContaining("8")
                .hasMessageContaining("13");

        verify(linenRecoveryRepository, never()).saveAndFlush(any());
    }

    // 可住灯亮过后再把套数/公斤改歪：保存失败，带区间、秤重、折套数
    @Test
    void tamperAfterConfirmedRejects() {
        LinenRecovery confirmed = confirmedSheet(10L, "R-101", "BATCH-2", 4, new BigDecimal("8.20"), "见证人李");
        when(linenRecoveryRepository.findById(5L)).thenReturn(Optional.of(confirmed));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room(10L, "R-101")));

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setId(5L);
        dto.setSetCount(9); // 改歪
        dto.setBagWeight(new BigDecimal("8.20"));
        dto.setWitnessName("见证人李");

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(LinenRecoveryBlockedException.class)
                .hasMessageContaining("LINEN")
                .hasMessageContaining("锁");

        verify(linenRecoveryRepository, never()).saveAndFlush(any());
    }

    // 同一间房同一时刻已有未作废回收单：后保存的失败，并带已在库单号
    @Test
    void duplicateSheetForSameRoomCycleRejectsWithExistingNo() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L))
                .thenReturn(List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
        LinenRecovery existing = draftSheet(10L, "R-101", "BATCH-2");
        existing.setRecoveryNo("LINEN-EXISTING");
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of(existing));

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setRoomId(10L);
        dto.setSetCount(4);

        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(LinenRecoveryBlockedException.class)
                .hasMessageContaining("LINEN-EXISTING");

        verify(linenRecoveryRepository, never()).saveAndFlush(any());
    }

    // 离泊以后回收单还缺着：补登入口仍能用（按 id 补草稿），且缺着时占用失败
    @Test
    void occupancyBlockedWhileDraftMissingColumnsEvenAfterDeparture() {
        LoungeRoom room = room(10L, "R-101");
        LinenRecovery draft = draftSheet(10L, "R-101", "BATCH-2");
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of(draft));

        // 缺着：占用被挡
        assertThatThrownBy(() -> service.assertAvailableForOccupancy(
                room, List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2"))))
                .isInstanceOf(LinenOccupancyBlockedException.class)
                .hasMessageContaining("R-101");

        // 补登：同一张草稿补齐三栏并对齐公斤 -> 确认，灯亮
        when(linenRecoveryRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setId(draft.getId());
        dto.setSetCount(4);
        dto.setBagWeight(new BigDecimal("8.20"));
        dto.setWitnessName("见证人李");
        service.save(dto);
        assertThat(draft.getStatus()).isEqualTo("CONFIRMED");

        // 已回收：补登后的确认单放行占用
        service.assertAvailableForOccupancy(
                room, List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
    }

    // 首班船（没换过船）：不挡占用，也不允许开回收单
    @Test
    void firstShiftRoomNeedsNoRecoveryAndOccupancyPasses() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L))
                .thenReturn(List.of(active(20L, "SHIP-B", "BATCH-2")));

        LinenRecoverySaveDTO dto = new LinenRecoverySaveDTO();
        dto.setRoomId(10L);
        assertThatThrownBy(() -> service.save(dto))
                .isInstanceOf(IllegalArgumentException.class);

        service.assertAvailableForOccupancy(room, List.of(active(20L, "SHIP-B", "BATCH-2")));
    }

    // 房间状态：草稿缺栏时亮「回收未齐」、确认后只亮「可住灯」，两灯互斥
    @Test
    void roomStateLightsAreMutuallyExclusive() {
        LoungeRoom room = room(10L, "R-101");
        when(loungeRoomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(roomShipRelationRepository.findByRoomId(10L))
                .thenReturn(List.of(inactive(30L, "SHIP-A", "BATCH-1"), active(20L, "SHIP-B", "BATCH-2")));
        LinenRecovery draft = draftSheet(10L, "R-101", "BATCH-2");
        when(linenRecoveryRepository
                .findCycleSheets(10L, "BATCH-2"))
                .thenReturn(List.of(draft));

        LinenRoomStateDTO draftState = service.getRoomState(10L);
        assertThat(draftState.getLinenState()).isEqualTo("DRAFT");
        assertThat(draftState.isRecoveryPendingLight()).isTrue();
        assertThat(draftState.isAvailableLight()).isFalse();

        LinenRecovery confirmed = confirmedSheet(10L, "R-101", "BATCH-2", 4, new BigDecimal("8.20"), "见证人李");
        when(linenRecoveryRepository
                .findCycleSheets(eq(10L), eq("BATCH-2")))
                .thenReturn(List.of(confirmed));

        LinenRoomStateDTO recoveredState = service.getRoomState(10L);
        assertThat(recoveredState.getLinenState()).isEqualTo("RECOVERED");
        assertThat(recoveredState.isAvailableLight()).isTrue();
        assertThat(recoveredState.isRecoveryPendingLight()).isFalse();
    }

    private LoungeRoom room(Long id, String code) {
        LoungeRoom room = new LoungeRoom();
        room.setId(id);
        room.setRoomCode(code);
        room.setRoomName("休息室" + code);
        room.setStatus("ACTIVE");
        return room;
    }

    private Ship ship(Long id, String code) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipCode(code);
        ship.setShipName("船舶" + code);
        return ship;
    }

    private RoomShipRelation active(Long shipId, String shipCode, String batch) {
        return relation(shipId, "ACTIVE", batch, LocalDateTime.now());
    }

    private RoomShipRelation inactive(Long shipId, String shipCode, String batch) {
        return relation(shipId, "INACTIVE", batch, LocalDateTime.now().minusHours(2));
    }

    private RoomShipRelation relation(Long shipId, String status, String batch, LocalDateTime updateTime) {
        RoomShipRelation relation = new RoomShipRelation();
        relation.setRoomId(10L);
        relation.setShipId(shipId);
        relation.setStatus(status);
        relation.setChangeBatch(batch);
        relation.setUpdateTime(updateTime);
        return relation;
    }

    private LinenRecovery draftSheet(Long roomId, String roomCode, String batch) {
        LinenRecovery sheet = baseSheet(roomId, roomCode, batch);
        sheet.setId(7L);
        sheet.setRecoveryNo("LINEN-DRAFT");
        sheet.setStatus("DRAFT");
        sheet.setSetCount(4);
        return sheet;
    }

    private LinenRecovery confirmedSheet(Long roomId, String roomCode, String batch,
                                         int sets, BigDecimal weight, String witness) {
        LinenRecovery sheet = baseSheet(roomId, roomCode, batch);
        sheet.setId(5L);
        sheet.setRecoveryNo("LINEN-CONFIRMED");
        sheet.setStatus("CONFIRMED");
        sheet.setSetCount(sets);
        sheet.setBagWeight(weight);
        sheet.setWitnessName(witness);
        sheet.setConfirmedTime(LocalDateTime.now());
        return sheet;
    }

    private LinenRecovery baseSheet(Long roomId, String roomCode, String batch) {
        LinenRecovery sheet = new LinenRecovery();
        sheet.setRoomId(roomId);
        sheet.setRoomCode(roomCode);
        sheet.setRoomName("休息室" + roomCode);
        sheet.setChangeBatch(batch);
        sheet.setDepartedShipId(30L);
        sheet.setDepartedShipCode("SHIP-A");
        sheet.setKgPerSetMin(LinenRecoveryService.DEFAULT_KG_PER_SET_MIN);
        sheet.setKgPerSetMax(LinenRecoveryService.DEFAULT_KG_PER_SET_MAX);
        return sheet;
    }
}
