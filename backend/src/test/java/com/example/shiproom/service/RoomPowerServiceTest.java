package com.example.shiproom.service;

import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RoomOverCapacityDTO;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.exception.RoomOverCapacityException;
import com.example.shiproom.repository.ElectricApplianceRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomPowerServiceTest {

    @Mock
    private LoungeRoomRepository loungeRoomRepository;
    @Mock
    private ElectricApplianceRepository electricApplianceRepository;

    private RoomPowerService service;

    @BeforeEach
    void setUp() {
        service = new RoomPowerService(loungeRoomRepository, electricApplianceRepository);
    }

    @Test
    void fillPowerSummarySumsUsedAndComputesRemaining() {
        LoungeRoom room = room(1L, new BigDecimal("3.00"));
        when(electricApplianceRepository.sumPowerByRoomId(1L)).thenReturn(new BigDecimal("2.30"));

        LoungeRoomDTO dto = new LoungeRoomDTO();
        service.fillPowerSummary(room, dto);

        assertThat(dto.getPowerCapacity()).isEqualByComparingTo("3.00");
        assertThat(dto.getPowerUsed()).isEqualByComparingTo("2.30");
        assertThat(dto.getPowerRemaining()).isEqualByComparingTo("0.70");
        assertThat(dto.getOverCapacity()).isFalse();
    }

    @Test
    void nullCapacityFallsBackToDefault() {
        LoungeRoom room = room(1L, null);
        when(electricApplianceRepository.sumPowerByRoomId(1L)).thenReturn(null);

        LoungeRoomDTO dto = new LoungeRoomDTO();
        service.fillPowerSummary(room, dto);

        assertThat(dto.getPowerCapacity()).isEqualByComparingTo(RoomPowerService.DEFAULT_POWER_CAPACITY);
        assertThat(dto.getPowerUsed()).isEqualByComparingTo("0.00");
        assertThat(dto.getPowerRemaining()).isEqualByComparingTo(RoomPowerService.DEFAULT_POWER_CAPACITY);
        assertThat(dto.getOverCapacity()).isFalse();
    }

    @Test
    void attachIsRejectedWhenRoomAlreadyOverCapacityEvenIfNewDeviceFits() {
        LoungeRoom room = room(1L, new BigDecimal("3.00"));
        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(electricApplianceRepository.sumPowerByRoomId(1L)).thenReturn(new BigDecimal("3.50"));

        assertThatThrownBy(() -> service.assertCanAttach(1L, new BigDecimal("0.10"),
                9L, "D9", "小台灯"))
                .isInstanceOf(RoomOverCapacityException.class)
                .satisfies(ex -> {
                    RoomOverCapacityDTO detail = ((RoomOverCapacityException) ex).getDetail();
                    assertThat(detail.getPowerCapacity()).isEqualByComparingTo("3.00");
                    assertThat(detail.getPowerUsed()).isEqualByComparingTo("3.50");
                    assertThat(detail.getAppliancePower()).isEqualByComparingTo("0.10");
                    assertThat(detail.getRoomId()).isEqualTo(1L);
                });
    }

    @Test
    void attachIsRejectedWhenProjectedTotalExceedsCapacity() {
        LoungeRoom room = room(1L, new BigDecimal("3.00"));
        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(electricApplianceRepository.sumPowerByRoomId(1L)).thenReturn(new BigDecimal("2.50"));

        assertThatThrownBy(() -> service.assertCanAttach(1L, new BigDecimal("1.00"),
                9L, "D9", "热水器"))
                .isInstanceOf(RoomOverCapacityException.class)
                .hasMessageContaining("3.00")
                .hasMessageContaining("2.50")
                .hasMessageContaining("1.00")
                .hasMessageContaining("3.50");
    }

    @Test
    void attachAtExactlyCapacityIsAllowedAndDevicePowerZeroIsAllowed() {
        LoungeRoom room = room(1L, new BigDecimal("3.00"));
        when(loungeRoomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(electricApplianceRepository.sumPowerByRoomId(1L)).thenReturn(new BigDecimal("2.00"));

        assertThatCode(() -> service.assertCanAttach(1L, new BigDecimal("1.00"),
                9L, "D9", "正好顶满")).doesNotThrowAnyException();
    }

    private LoungeRoom room(Long id, BigDecimal capacity) {
        LoungeRoom room = new LoungeRoom();
        room.setId(id);
        room.setRoomCode("R" + id);
        room.setRoomName("休息室" + id);
        room.setStatus("ACTIVE");
        room.setPowerCapacity(capacity);
        return room;
    }
}
