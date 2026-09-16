package com.example.shiproom.service;

import com.example.shiproom.dto.PatrolCheckEntryDTO;
import com.example.shiproom.dto.PatrolHandoverDTO;
import com.example.shiproom.dto.PatrolShipStateDTO;
import com.example.shiproom.dto.PatrolSubmitResultDTO;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.PatrolHandover;
import com.example.shiproom.entity.PatrolOfficer;
import com.example.shiproom.entity.PatrolWindow;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.PatrolAlreadyHandedException;
import com.example.shiproom.exception.PatrolForbiddenException;
import com.example.shiproom.exception.PatrolWindowLockedException;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.PatrolCheckRepository;
import com.example.shiproom.repository.PatrolHandoverRepository;
import com.example.shiproom.repository.PatrolOfficerRepository;
import com.example.shiproom.repository.PatrolWindowRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
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
class PatrolHandoverServiceTest {

    @Mock private PatrolWindowRepository windowRepository;
    @Mock private PatrolOfficerRepository officerRepository;
    @Mock private PatrolHandoverRepository handoverRepository;
    @Mock private PatrolCheckRepository checkRepository;
    @Mock private LoungeRoomRepository loungeRoomRepository;
    @Mock private RoomShipRelationRepository roomShipRelationRepository;
    @Mock private ShipRepository shipRepository;
    @Mock private ShiftOperationLockService shiftOperationLockService;

    private PatrolHandoverService service;

    private static final LocalDateTime WINDOW_START = LocalDateTime.of(2026, 9, 15, 20, 0);
    private static final LocalDateTime WINDOW_END = LocalDateTime.of(2026, 9, 16, 8, 0);
    private static final LocalDateTime INSIDE = LocalDateTime.of(2026, 9, 15, 23, 0);
    private static final LocalDateTime OUTSIDE = LocalDateTime.of(2026, 9, 16, 9, 0);
    private static final String ACTIVE = "ACTIVE";

    @BeforeEach
    void setUp() {
        service = new PatrolHandoverService(
                windowRepository, officerRepository, handoverRepository, checkRepository,
                loungeRoomRepository, roomShipRelationRepository, shipRepository, shiftOperationLockService);
        fixClock(INSIDE);
    }

    private void fixClock(LocalDateTime time) {
        Clock clock = Clock.fixed(time.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service.setClock(clock);
    }

    @Test
    void leaderHandsOverAllRoomsFloorAscendingWritesFlowAndChecksSameBatch() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer(7L, "L-01", "值班长赵", "LEADER", "ACTIVE")));
        when(handoverRepository.findByHandedUniqueKeyForUpdate("20#1")).thenReturn(Optional.empty());
        when(handoverRepository.saveAndFlush(any(PatrolHandover.class))).thenAnswer(inv -> {
            PatrolHandover h = inv.getArgument(0);
            h.setId(100L);
            return h;
        });
        when(checkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PatrolSubmitResultDTO result = service.submitHandover(dto(entries(
                entry(10L, 1), entry(11L, 2), entry(12L, 3))));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo("HANDED");

        ArgumentCaptor<PatrolHandover> handoverCaptor = ArgumentCaptor.forClass(PatrolHandover.class);
        verify(handoverRepository).saveAndFlush(handoverCaptor.capture());
        PatrolHandover handover = handoverCaptor.getValue();
        assertThat(handover.getLeaderName()).isEqualTo("值班长赵");
        assertThat(handover.getWindowCode()).isEqualTo("W-NIGHT");
        assertThat(handover.getHandedUniqueKey()).isEqualTo("20#1");
        assertThat(handover.getRoomCount()).isEqualTo(3);

        // 三间房各落一勾，顺序 1/2/3，且与流水同批次
        ArgumentCaptor<com.example.shiproom.entity.PatrolCheck> checkCaptor =
                ArgumentCaptor.forClass(com.example.shiproom.entity.PatrolCheck.class);
        verify(checkRepository, org.mockito.Mockito.times(3)).save(checkCaptor.capture());
        List<com.example.shiproom.entity.PatrolCheck> checks = checkCaptor.getAllValues();
        assertThat(checks).extracting(c -> c.getSeq() + ":" + c.getRoomCode())
                .containsExactly("1:R-101", "2:R-102", "3:R-201");
        assertThat(checks).allSatisfy(c -> {
            assertThat(c.getHandoverId()).isEqualTo(100L);
            assertThat(c.getHandoverBatch()).isEqualTo(handover.getHandoverBatch());
            assertThat(c.getLeaderName()).isEqualTo("值班长赵");
            assertThat(c.getWindowCode()).isEqualTo("W-NIGHT");
        });
        assertThat(handover.getWalkOrder()).isEqualTo("1:R-101 > 1:R-102 > 2:R-201");
    }

    @Test
    void nonLeaderCannotHandOver() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(8L))
                .thenReturn(Optional.of(officer(8L, "M-01", "巡检员钱", "MEMBER", "ACTIVE")));

        assertThatThrownBy(() -> service.submitHandover(dto(8L, entries(entry(10L, 1), entry(11L, 2), entry(12L, 3)))))
                .isInstanceOf(PatrolForbiddenException.class)
                .hasMessageContaining("值班长");

        verify(handoverRepository, never()).save(any());
        verify(checkRepository, never()).save(any());
    }

    @Test
    void inactiveLeaderCannotHandOver() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(7L))
                .thenReturn(Optional.of(officer(7L, "L-01", "前值班长", "LEADER", "INACTIVE")));

        assertThatThrownBy(() -> service.submitHandover(dto(7L, entries(entry(10L, 1), entry(11L, 2), entry(12L, 3)))))
                .isInstanceOf(PatrolForbiddenException.class);

        verify(handoverRepository, never()).save(any());
    }

    @Test
    void skippingAFloorRejectsWholeHandoverAndOnlyKeepsBlockedRecord() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer(7L, "L-01", "值班长赵", "LEADER", "ACTIVE")));
        when(handoverRepository.findByHandedUniqueKeyForUpdate("20#1")).thenReturn(Optional.empty());
        when(handoverRepository.saveAndFlush(any(PatrolHandover.class))).thenAnswer(inv -> inv.getArgument(0));

        // 先 2 楼再 1 楼 = 跳层
        PatrolSubmitResultDTO result = service.submitHandover(dto(entries(
                entry(12L, 1), entry(10L, 2), entry(11L, 3))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo("BLOCKED");
        assertThat(result.getBlocked().getReason()).isEqualTo("FLOOR_SKIP");
        assertThat(result.getBlocked().getRoomCode()).isEqualTo("R-101");
        assertThat(result.getBlocked().getPreviousFloor()).isEqualTo("2");
        assertThat(result.getBlocked().getWindowCode()).isEqualTo("W-NIGHT");
        assertThat(result.getMessage()).contains("跳层");

        ArgumentCaptor<PatrolHandover> captor = ArgumentCaptor.forClass(PatrolHandover.class);
        verify(handoverRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("BLOCKED");
        // 一间房的勾都不能留下
        verify(checkRepository, never()).save(any());
    }

    @Test
    void missingOneRoomRejectsWholeHandover() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer(7L, "L-01", "值班长赵", "LEADER", "ACTIVE")));
        when(handoverRepository.findByHandedUniqueKeyForUpdate("20#1")).thenReturn(Optional.empty());
        when(handoverRepository.saveAndFlush(any(PatrolHandover.class))).thenAnswer(inv -> inv.getArgument(0));

        // 漏走 1 楼的 R-102
        PatrolSubmitResultDTO result = service.submitHandover(dto(entries(entry(10L, 1), entry(12L, 2))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBlocked().getReason()).isEqualTo("MISSING_ROOM");
        assertThat(result.getMessage()).contains("漏走").contains("R-102");
        verify(checkRepository, never()).save(any());
    }

    @Test
    void roomNoLongerDockedAtShipRejectsWholeHandoverAndNamesRoomAndCurrentShip() {
        stubWindowShipAndRooms(ACTIVE);
        // 额外交了一间已经改挂别的船的房间
        LoungeRoom foreign = room(30L, "R-301", "3");
        when(loungeRoomRepository.findById(30L)).thenReturn(Optional.of(foreign));
        when(roomShipRelationRepository.findByRoomId(30L)).thenReturn(List.of(relation(30L, 40L, "ACTIVE")));
        Ship otherShip = ship(40L, "SHIP-Z");
        when(shipRepository.findById(40L)).thenReturn(Optional.of(otherShip));
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer(7L, "L-01", "值班长赵", "LEADER", "ACTIVE")));
        when(handoverRepository.findByHandedUniqueKeyForUpdate("20#1")).thenReturn(Optional.empty());
        when(handoverRepository.saveAndFlush(any(PatrolHandover.class))).thenAnswer(inv -> inv.getArgument(0));

        PatrolSubmitResultDTO result = service.submitHandover(dto(entries(
                entry(10L, 1), entry(11L, 2), entry(12L, 3), entry(30L, 4))));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getBlocked().getReason()).isEqualTo("ROOM_DETACHED");
        assertThat(result.getBlocked().getRoomCode()).isEqualTo("R-301");
        assertThat(result.getBlocked().getWindowCode()).isEqualTo("W-NIGHT");
        assertThat(result.getBlocked().getCurrentShipCode()).isEqualTo("SHIP-Z");
        assertThat(result.getMessage()).contains("R-301").contains("SHIP-Z").contains("退回");
        verify(checkRepository, never()).save(any());
    }

    @Test
    void cannotSubmitAfterWindowClosed() {
        fixClock(OUTSIDE);
        stubWindowShipAndRooms(ACTIVE);

        assertThatThrownBy(() -> service.submitHandover(dto(entries(entry(10L, 1), entry(11L, 2), entry(12L, 3)))))
                .isInstanceOf(PatrolWindowLockedException.class)
                .hasMessageContaining("窗口外已锁");

        verify(handoverRepository, never()).save(any());
        verify(checkRepository, never()).save(any());
    }

    @Test
    void secondConcurrentHandoverForSameShipAndWindowIsRejected() {
        stubWindowShipAndRooms(ACTIVE);
        when(officerRepository.findById(7L)).thenReturn(Optional.of(officer(7L, "L-01", "值班长赵", "LEADER", "ACTIVE")));
        PatrolHandover existing = new PatrolHandover();
        existing.setId(99L);
        existing.setLeaderName("值班长孙");
        existing.setHandoverBatch("PATROL-first");
        when(handoverRepository.findByHandedUniqueKeyForUpdate("20#1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.submitHandover(dto(entries(entry(10L, 1), entry(11L, 2), entry(12L, 3)))))
                .isInstanceOf(PatrolAlreadyHandedException.class)
                .hasMessageContaining("值班长孙");

        verify(checkRepository, never()).save(any());
    }

    @Test
    void stateReadsBackAsHandedAfterReopen() {
        stubWindowShipAndRooms(ACTIVE);
        PatrolHandover handed = new PatrolHandover();
        handed.setId(100L);
        handed.setHandoverBatch("PATROL-ok");
        handed.setLeaderName("值班长赵");
        handed.setRoomCount(3);
        handed.setSubmitTime(INSIDE);
        handed.setWalkOrder("1:R-101 > 1:R-102 > 2:R-201");
        when(handoverRepository.findByHandedUniqueKey("20#1")).thenReturn(Optional.of(handed));
        when(checkRepository.findByHandoverIdOrderBySeqAsc(100L)).thenReturn(List.of());

        PatrolShipStateDTO state = service.getShipWindowState(1L, 20L);

        assertThat(state.getHandoverState()).isEqualTo("HANDED");
        assertThat(state.getWindowState()).isEqualTo("OPEN");
        assertThat(state.getHandoverBatch()).isEqualTo("PATROL-ok");
        assertThat(state.getExpectedRooms()).hasSize(3);
    }

    @Test
    void stateReadsBackAsLockedAfterWindowPassedWithoutHandover() {
        fixClock(OUTSIDE);
        stubWindowShipAndRooms(ACTIVE);
        when(handoverRepository.findByHandedUniqueKey("20#1")).thenReturn(Optional.empty());
        when(handoverRepository.findByWindowIdAndShipIdOrderByIdAsc(1L, 20L)).thenReturn(List.of());

        PatrolShipStateDTO state = service.getShipWindowState(1L, 20L);

        assertThat(state.getWindowState()).isEqualTo("LOCKED");
        assertThat(state.getHandoverState()).isEqualTo("LOCKED");
    }

    // ---------------- helpers ----------------

    private void stubWindowShipAndRooms(String windowStatus) {
        org.mockito.Mockito.lenient().when(windowRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(window(1L, windowStatus)));
        org.mockito.Mockito.lenient().when(windowRepository.findById(1L))
                .thenReturn(Optional.of(window(1L, windowStatus)));

        Ship ship = ship(20L, "SHIP-A");
        org.mockito.Mockito.lenient().when(shipRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(ship));
        org.mockito.Mockito.lenient().when(shipRepository.findById(20L)).thenReturn(Optional.of(ship));

        LoungeRoom r1 = room(10L, "R-101", "1");
        LoungeRoom r2 = room(11L, "R-102", "1");
        LoungeRoom r3 = room(12L, "R-201", "2");
        List<RoomShipRelation> relations = List.of(
                relation(10L, 20L, "ACTIVE"), relation(11L, 20L, "ACTIVE"), relation(12L, 20L, "ACTIVE"));
        org.mockito.Mockito.lenient().when(roomShipRelationRepository.findByShipIdForUpdate(20L)).thenReturn(relations);
        org.mockito.Mockito.lenient().when(roomShipRelationRepository.findByShipId(20L)).thenReturn(relations);
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(r1));
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(r2));
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(r3));
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findById(10L)).thenReturn(Optional.of(r1));
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findById(11L)).thenReturn(Optional.of(r2));
        org.mockito.Mockito.lenient().when(loungeRoomRepository.findById(12L)).thenReturn(Optional.of(r3));
    }

    private PatrolWindow window(Long id, String status) {
        PatrolWindow window = new PatrolWindow();
        window.setId(id);
        window.setWindowCode("W-NIGHT");
        window.setWindowName("夜班接班窗口");
        window.setShipId(20L);
        window.setShipCode("SHIP-A");
        window.setStartTime(WINDOW_START);
        window.setEndTime(WINDOW_END);
        window.setStatus(status);
        return window;
    }

    private PatrolHandoverDTO dto(List<PatrolCheckEntryDTO> entries) {
        return dto(7L, entries);
    }

    private PatrolHandoverDTO dto(Long leaderId, List<PatrolCheckEntryDTO> entries) {
        PatrolHandoverDTO dto = new PatrolHandoverDTO();
        dto.setWindowId(1L);
        dto.setShipId(20L);
        dto.setLeaderId(leaderId);
        dto.setEntries(entries);
        return dto;
    }

    private PatrolCheckEntryDTO entry(Long roomId, int seq) {
        PatrolCheckEntryDTO entry = new PatrolCheckEntryDTO();
        entry.setRoomId(roomId);
        entry.setSeq(seq);
        return entry;
    }

    private List<PatrolCheckEntryDTO> entries(PatrolCheckEntryDTO... items) {
        return List.of(items);
    }

    private PatrolOfficer officer(Long id, String code, String name, String role, String status) {
        PatrolOfficer officer = new PatrolOfficer();
        officer.setId(id);
        officer.setOfficerCode(code);
        officer.setOfficerName(name);
        officer.setRole(role);
        officer.setStatus(status);
        return officer;
    }

    private LoungeRoom room(Long id, String code, String floor) {
        LoungeRoom room = new LoungeRoom();
        room.setId(id);
        room.setRoomCode(code);
        room.setRoomName("房间" + code);
        room.setFloor(floor);
        room.setStatus("ACTIVE");
        return room;
    }

    private Ship ship(Long id, String code) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipCode(code);
        ship.setShipName("船舶" + code);
        ship.setStatus("DOCKED");
        return ship;
    }

    private RoomShipRelation relation(Long roomId, Long shipId, String status) {
        RoomShipRelation relation = new RoomShipRelation();
        relation.setRoomId(roomId);
        relation.setShipId(shipId);
        relation.setStatus(status);
        return relation;
    }
}
