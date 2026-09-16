package com.example.shiproom.service;

import com.example.shiproom.entity.ElectricAppliance;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.ShiftBlockedException;
import com.example.shiproom.repository.ElectricApplianceRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RelationChangeLogRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationBindServiceTest {

    @Mock
    private RoomShipRelationRepository roomShipRelationRepository;
    @Mock
    private ElectricApplianceRepository electricApplianceRepository;
    @Mock
    private LoungeRoomRepository loungeRoomRepository;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private RelationChangeLogRepository relationChangeLogRepository;
    @Mock
    private ShiftOperationLockService shiftOperationLockService;
    @Mock
    private RoomPowerService roomPowerService;

    private RelationBindService service;

    @BeforeEach
    void setUp() {
        service = new RelationBindService(
                roomShipRelationRepository,
                electricApplianceRepository,
                loungeRoomRepository,
                shipRepository,
                relationChangeLogRepository,
                shiftOperationLockService,
                roomPowerService
        );
    }

    @Test
    void roomShiftBlockedByInactiveApplianceDoesNotWriteRelationsOrAppliances() {
        LoungeRoom room = room(1L, "R1", "休息室1");
        Ship oldShip = ship(10L, "A", "甲船");
        Ship newShip = ship(20L, "B", "乙船");
        RoomShipRelation relation = relation(100L, 1L, 10L, "ACTIVE");
        ElectricAppliance appliance = appliance(1000L, "D1", "冰箱", 1L, 10L, "INACTIVE");

        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(shipRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(newShip));
        when(roomShipRelationRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(relation));
        when(electricApplianceRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(appliance));
        when(shipRepository.findById(10L)).thenReturn(Optional.of(oldShip));

        assertThatThrownBy(() -> service.updateRelation(1L, 20L, "值班员", "换班"))
                .isInstanceOf(ShiftBlockedException.class)
                .hasMessageContaining("D1");

        verify(roomShipRelationRepository, never()).save(any());
        verify(electricApplianceRepository, never()).save(any());
        verify(relationChangeLogRepository, never()).save(any());
        assertThat(appliance.getShipId()).isEqualTo(10L);
        assertThat(relation.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void roomShiftUpdatesRoomRelationAllAppliancesAndLogsWithSameBatch() {
        LoungeRoom room = room(1L, "R1", "休息室1");
        Ship oldShip = ship(10L, "A", "甲船");
        Ship newShip = ship(20L, "B", "乙船");
        RoomShipRelation oldRelation = relation(100L, 1L, 10L, "ACTIVE");
        oldRelation.setChangeBatch("old-batch");
        ElectricAppliance appliance = appliance(1000L, "D1", "冰箱", 1L, 10L, "ACTIVE");
        ElectricAppliance readyAppliance = appliance(1001L, "D3", "空调", 1L, 20L, "ACTIVE");

        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(shipRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(newShip));
        when(roomShipRelationRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(oldRelation));
        when(electricApplianceRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(appliance, readyAppliance));
        when(shipRepository.findById(10L)).thenReturn(Optional.of(oldShip));
        when(roomShipRelationRepository.findByRoomIdAndShipId(1L, 20L)).thenReturn(Optional.empty());

        var result = service.updateRelation(1L, 20L, "值班员", "换班");

        assertThat(result.getApplianceCount()).isEqualTo(1);
        assertThat(result.getRoomCount()).isEqualTo(1);
        assertThat(result.getChangeBatch()).isNotBlank();
        assertThat(appliance.getShipId()).isEqualTo(20L);
        assertThat(appliance.getLastChangeBatch()).isEqualTo(result.getChangeBatch());
        assertThat(readyAppliance.getShipId()).isEqualTo(20L);
        assertThat(readyAppliance.getLastChangeBatch()).isEqualTo(result.getChangeBatch());
        assertThat(oldRelation.getStatus()).isEqualTo("INACTIVE");
        verify(roomShipRelationRepository).save(org.mockito.ArgumentMatchers.argThat(relation ->
                relation.getRoomId().equals(1L)
                        && relation.getShipId().equals(20L)
                        && "ACTIVE".equals(relation.getStatus())
                        && result.getChangeBatch().equals(relation.getChangeBatch())));
        verify(relationChangeLogRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shipShiftBlockedByInactiveApplianceDoesNotWriteRelationsOrAppliances() {
        LoungeRoom room = room(1L, "R1", "休息室1");
        Ship oldShip = ship(10L, "A", "甲船");
        Ship newShip = ship(20L, "B", "乙船");
        RoomShipRelation relation = relation(100L, 1L, 10L, "ACTIVE");
        ElectricAppliance appliance = appliance(1000L, "D2", "热水器", 1L, 10L, "INACTIVE");

        when(shipRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(oldShip));
        when(shipRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(newShip));
        when(electricApplianceRepository.findByShipIdForUpdate(10L)).thenReturn(List.of(appliance));
        when(roomShipRelationRepository.findByShipIdForUpdate(10L)).thenReturn(List.of(relation));
        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(electricApplianceRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(appliance));
        when(roomShipRelationRepository.findByRoomIdForUpdate(1L)).thenReturn(List.of(relation));
        when(loungeRoomRepository.findById(1L)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> service.shipChange(10L, 20L, "值班员", "换班"))
                .isInstanceOf(ShiftBlockedException.class)
                .hasMessageContaining("D2");

        verify(roomShipRelationRepository, never()).save(any());
        verify(electricApplianceRepository, never()).save(any());
        verify(relationChangeLogRepository, never()).save(any());
        assertThat(appliance.getShipId()).isEqualTo(10L);
        assertThat(relation.getStatus()).isEqualTo("ACTIVE");
    }

    private LoungeRoom room(Long id, String code, String name) {
        LoungeRoom room = new LoungeRoom();
        room.setId(id);
        room.setRoomCode(code);
        room.setRoomName(name);
        room.setStatus("ACTIVE");
        return room;
    }

    private Ship ship(Long id, String code, String name) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipCode(code);
        ship.setShipName(name);
        ship.setStatus("DOCKED");
        return ship;
    }

    private RoomShipRelation relation(Long id, Long roomId, Long shipId, String status) {
        RoomShipRelation relation = new RoomShipRelation();
        relation.setId(id);
        relation.setRoomId(roomId);
        relation.setShipId(shipId);
        relation.setStatus(status);
        return relation;
    }

    private ElectricAppliance appliance(Long id, String code, String name, Long roomId, Long shipId, String status) {
        ElectricAppliance appliance = new ElectricAppliance();
        appliance.setId(id);
        appliance.setDeviceCode(code);
        appliance.setDeviceName(name);
        appliance.setPower(BigDecimal.ONE);
        appliance.setApplianceType("冰箱");
        appliance.setRoomId(roomId);
        appliance.setShipId(shipId);
        appliance.setStatus(status);
        return appliance;
    }
}
