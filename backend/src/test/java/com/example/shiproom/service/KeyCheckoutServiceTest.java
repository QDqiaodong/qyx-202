package com.example.shiproom.service;

import com.example.shiproom.dto.KeyCheckoutDTO;
import com.example.shiproom.dto.KeyCheckoutRecordDTO;
import com.example.shiproom.dto.KeyReturnDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeyCheckoutServiceTest {

    @Mock
    private LoungeKeyRepository loungeKeyRepository;
    @Mock
    private KeyOccupancyRepository keyOccupancyRepository;
    @Mock
    private KeyCheckoutRecordRepository keyCheckoutRecordRepository;
    @Mock
    private LoungeRoomRepository loungeRoomRepository;
    @Mock
    private RoomShipRelationRepository roomShipRelationRepository;
    @Mock
    private ShipRepository shipRepository;
    @Mock
    private ShiftOperationLockService shiftOperationLockService;

    private KeyCheckoutService service;

    @BeforeEach
    void setUp() {
        service = new KeyCheckoutService(
                loungeKeyRepository,
                keyOccupancyRepository,
                keyCheckoutRecordRepository,
                loungeRoomRepository,
                roomShipRelationRepository,
                shipRepository,
                shiftOperationLockService
        );
    }

    @Test
    void checkoutWritesOccupancyAndFlowRecordWithSameBatchAndShipSnapshot() {
        LoungeKey key = key(1L, "K-01", 10L);
        LoungeRoom room = room(10L, "R-101");
        Ship ship = ship(20L, "SHIP-A");
        RoomShipRelation relation = relation(10L, 20L, "ACTIVE");

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(shipRepository.findById(20L)).thenReturn(Optional.of(ship));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.empty());
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L)).thenReturn(List.of(relation));

        KeyCheckoutDTO dto = new KeyCheckoutDTO();
        dto.setKeyId(1L);
        dto.setShipId(20L);
        dto.setHolderName("张三");
        dto.setOperator("值班员甲");

        KeyCheckoutRecordDTO result = service.checkout(dto);

        ArgumentCaptor<KeyOccupancy> occupancyCaptor = ArgumentCaptor.forClass(KeyOccupancy.class);
        verify(keyOccupancyRepository).save(occupancyCaptor.capture());
        ArgumentCaptor<KeyCheckoutRecord> recordCaptor = ArgumentCaptor.forClass(KeyCheckoutRecord.class);
        verify(keyCheckoutRecordRepository).save(recordCaptor.capture());

        KeyOccupancy occupancy = occupancyCaptor.getValue();
        KeyCheckoutRecord record = recordCaptor.getValue();

        assertThat(occupancy.getCheckoutBatch()).isNotBlank();
        assertThat(record.getCheckoutBatch()).isEqualTo(occupancy.getCheckoutBatch());
        assertThat(result.getCheckoutBatch()).isEqualTo(occupancy.getCheckoutBatch());

        assertThat(occupancy.getKeyId()).isEqualTo(1L);
        assertThat(occupancy.getRoomId()).isEqualTo(10L);
        assertThat(occupancy.getShipId()).isEqualTo(20L);
        assertThat(occupancy.getShipCode()).isEqualTo("SHIP-A");
        assertThat(occupancy.getHolderName()).isEqualTo("张三");

        assertThat(record.getKeyId()).isEqualTo(1L);
        assertThat(record.getRoomId()).isEqualTo(10L);
        assertThat(record.getShipId()).isEqualTo(20L);
        assertThat(record.getShipCode()).isEqualTo("SHIP-A");
        assertThat(record.getHolderName()).isEqualTo("张三");
        assertThat(record.getStatus()).isEqualTo("OUT");
        assertThat(record.getCheckoutTime()).isEqualTo(occupancy.getCheckoutTime());
    }

    @Test
    void checkoutBlockedWhenRoomNoLongerDockedAtRequestedShipLeavesNothing() {
        LoungeKey key = key(1L, "K-01", 10L);
        LoungeRoom room = room(10L, "R-101");
        Ship requestedShip = ship(20L, "SHIP-A");
        Ship currentShip = ship(30L, "SHIP-B");
        RoomShipRelation relation = relation(10L, 30L, "ACTIVE");

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(shipRepository.findById(20L)).thenReturn(Optional.of(requestedShip));
        when(shipRepository.findById(30L)).thenReturn(Optional.of(currentShip));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.empty());
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L)).thenReturn(List.of(relation));

        KeyCheckoutDTO dto = new KeyCheckoutDTO();
        dto.setKeyId(1L);
        dto.setShipId(20L);
        dto.setHolderName("张三");

        assertThatThrownBy(() -> service.checkout(dto))
                .isInstanceOf(KeyCheckoutBlockedException.class)
                .hasMessageContaining("K-01")
                .hasMessageContaining("R-101")
                .hasMessageContaining("SHIP-A")
                .hasMessageContaining("SHIP-B");

        verify(keyOccupancyRepository, never()).save(any());
        verify(keyCheckoutRecordRepository, never()).save(any());
    }

    @Test
    void checkoutBlockedWhenRoomHasNoActiveDockingLeavesNothing() {
        LoungeKey key = key(1L, "K-01", 10L);
        LoungeRoom room = room(10L, "R-101");
        Ship requestedShip = ship(20L, "SHIP-A");

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(shipRepository.findById(20L)).thenReturn(Optional.of(requestedShip));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.empty());
        when(roomShipRelationRepository.findByRoomIdForUpdate(10L)).thenReturn(List.of());

        KeyCheckoutDTO dto = new KeyCheckoutDTO();
        dto.setKeyId(1L);
        dto.setShipId(20L);
        dto.setHolderName("张三");

        assertThatThrownBy(() -> service.checkout(dto))
                .isInstanceOf(KeyCheckoutBlockedException.class)
                .hasMessageContaining("K-01")
                .hasMessageContaining("R-101");

        verify(keyOccupancyRepository, never()).save(any());
        verify(keyCheckoutRecordRepository, never()).save(any());
    }

    @Test
    void checkoutBlockedWhenKeyAlreadyOutLeavesNothing() {
        LoungeKey key = key(1L, "K-01", 10L);
        LoungeRoom room = room(10L, "R-101");
        Ship ship = ship(20L, "SHIP-A");
        KeyOccupancy existing = occupancy(1L, "K-01", 10L, "R-101", 20L, "SHIP-A", "李四", "KEY-old");

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(loungeRoomRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(room));
        when(shipRepository.findById(20L)).thenReturn(Optional.of(ship));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.of(existing));

        KeyCheckoutDTO dto = new KeyCheckoutDTO();
        dto.setKeyId(1L);
        dto.setShipId(20L);
        dto.setHolderName("张三");

        assertThatThrownBy(() -> service.checkout(dto))
                .isInstanceOf(KeyCheckoutBlockedException.class)
                .hasMessageContaining("K-01")
                .hasMessageContaining("李四");

        verify(keyOccupancyRepository, never()).save(any());
        verify(keyCheckoutRecordRepository, never()).save(any());
    }

    @Test
    void returnDeletesOccupancyAndMarksSameBatchRecordReturned() {
        LoungeKey key = key(1L, "K-01", 10L);
        KeyOccupancy existing = occupancy(1L, "K-01", 10L, "R-101", 20L, "SHIP-A", "张三", "KEY-batch-1");
        KeyCheckoutRecord record = record("KEY-batch-1", 1L, "K-01", 10L, "R-101", 20L, "SHIP-A", "张三", "OUT");

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.of(existing));
        when(keyCheckoutRecordRepository.findByCheckoutBatchAndStatus("KEY-batch-1", "OUT"))
                .thenReturn(Optional.of(record));

        KeyReturnDTO dto = new KeyReturnDTO();
        dto.setKeyId(1L);
        dto.setOperator("值班员乙");

        KeyCheckoutRecordDTO result = service.returnKey(dto);

        verify(keyOccupancyRepository).delete(existing);
        verify(keyCheckoutRecordRepository).save(record);
        assertThat(record.getStatus()).isEqualTo("RETURNED");
        assertThat(record.getReturnTime()).isNotNull();
        assertThat(record.getReturnOperator()).isEqualTo("值班员乙");
        assertThat(result.getCheckoutBatch()).isEqualTo("KEY-batch-1");
    }

    @Test
    void returnFailsWhenKeyIsNotOut() {
        LoungeKey key = key(1L, "K-01", 10L);

        when(loungeKeyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(key));
        when(keyOccupancyRepository.findByKeyId(1L)).thenReturn(Optional.empty());

        KeyReturnDTO dto = new KeyReturnDTO();
        dto.setKeyId(1L);

        assertThatThrownBy(() -> service.returnKey(dto))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("K-01");

        verify(keyOccupancyRepository, never()).delete(any());
        verify(keyCheckoutRecordRepository, never()).save(any());
    }

    private LoungeKey key(Long id, String keyCode, Long roomId) {
        LoungeKey key = new LoungeKey();
        key.setId(id);
        key.setKeyCode(keyCode);
        key.setKeyName("钥匙" + keyCode);
        key.setRoomId(roomId);
        key.setStatus("ACTIVE");
        return key;
    }

    private LoungeRoom room(Long id, String roomCode) {
        LoungeRoom room = new LoungeRoom();
        room.setId(id);
        room.setRoomCode(roomCode);
        room.setRoomName("休息室" + roomCode);
        room.setStatus("ACTIVE");
        return room;
    }

    private Ship ship(Long id, String shipCode) {
        Ship ship = new Ship();
        ship.setId(id);
        ship.setShipCode(shipCode);
        ship.setShipName("船舶" + shipCode);
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

    private KeyOccupancy occupancy(Long keyId, String keyCode, Long roomId, String roomCode,
                                   Long shipId, String shipCode, String holderName, String batch) {
        KeyOccupancy occupancy = new KeyOccupancy();
        occupancy.setKeyId(keyId);
        occupancy.setKeyCode(keyCode);
        occupancy.setRoomId(roomId);
        occupancy.setRoomCode(roomCode);
        occupancy.setShipId(shipId);
        occupancy.setShipCode(shipCode);
        occupancy.setHolderName(holderName);
        occupancy.setCheckoutBatch(batch);
        return occupancy;
    }

    private KeyCheckoutRecord record(String batch, Long keyId, String keyCode, Long roomId, String roomCode,
                                     Long shipId, String shipCode, String holderName, String status) {
        KeyCheckoutRecord record = new KeyCheckoutRecord();
        record.setCheckoutBatch(batch);
        record.setKeyId(keyId);
        record.setKeyCode(keyCode);
        record.setRoomId(roomId);
        record.setRoomCode(roomCode);
        record.setShipId(shipId);
        record.setShipCode(shipCode);
        record.setHolderName(holderName);
        record.setStatus(status);
        return record;
    }
}
