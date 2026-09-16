package com.example.shiproom.service;

import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RoomOverCapacityDTO;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.exception.RoomOverCapacityException;
import com.example.shiproom.repository.ElectricApplianceRepository;
import com.example.shiproom.repository.LoungeRoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 休息室用电承载：已挂功率合计始终由 electric_appliance 按 room_id 实时 SUM，
 * 不存冗余合计，所以拆电器、改功率后三个数当场跟着变，且逐台相加必然对得上。
 */
@Service
public class RoomPowerService {

    /** 老房间没填承载时的默认值（千瓦） */
    public static final BigDecimal DEFAULT_POWER_CAPACITY = new BigDecimal("5.00");

    private final LoungeRoomRepository loungeRoomRepository;
    private final ElectricApplianceRepository electricApplianceRepository;

    public RoomPowerService(LoungeRoomRepository loungeRoomRepository,
                            ElectricApplianceRepository electricApplianceRepository) {
        this.loungeRoomRepository = loungeRoomRepository;
        this.electricApplianceRepository = electricApplianceRepository;
    }

    public BigDecimal effectiveCapacity(LoungeRoom room) {
        return normalize(room.getPowerCapacity() != null ? room.getPowerCapacity() : DEFAULT_POWER_CAPACITY);
    }

    /** 某间房当前已挂全部电器的功率合计（只读，用于房间页展示）。 */
    public BigDecimal usedPower(Long roomId) {
        return normalize(electricApplianceRepository.sumPowerByRoomId(roomId));
    }

    /** 把承载、已挂、剩余、是否超限三个数填进房间 DTO。 */
    public void fillPowerSummary(LoungeRoom room, LoungeRoomDTO dto) {
        BigDecimal capacity = effectiveCapacity(room);
        BigDecimal used = usedPower(room.getId());
        dto.setPowerCapacity(capacity);
        dto.setPowerUsed(used);
        dto.setPowerRemaining(capacity.subtract(used));
        dto.setOverCapacity(used.compareTo(capacity) > 0);
    }

    /**
     * 挂电器前的承载校验，必须在已经持有换班全局行锁的事务里调用。
     * 房间已超限（已挂 &gt; 承载）时拒绝再挂；否则按「已挂 + 本台」预测，压过承载也拒绝。
     * 校验通过不落任何数据；不通过抛 {@link RoomOverCapacityException}，整笔挂入由外层事务回滚。
     */
    @Transactional
    public void assertCanAttach(Long roomId, BigDecimal appliancePower,
                                Long applianceId, String applianceCode, String applianceName) {
        // 再取一次房间行悲观锁：与全局换班锁双保险，两个并发挂入同一间房时在这里强制排队
        LoungeRoom room = loungeRoomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RuntimeException("休息室不存在"));

        BigDecimal capacity = effectiveCapacity(room);
        BigDecimal used = usedPower(roomId);
        BigDecimal power = normalize(appliancePower);
        BigDecimal remaining = capacity.subtract(used);
        BigDecimal projected = used.add(power);

        if (used.compareTo(capacity) > 0) {
            throw blocked(room, capacity, used, remaining, power, projected,
                    applianceId, applianceCode, applianceName,
                    "房间 " + room.getRoomCode() + "（" + room.getRoomName() + "）当前已挂 "
                            + used + " kW，已超过承载 " + capacity + " kW；"
                            + "请先拆下电器或把电器功率调低，合计降回承载以内后才能再挂新电器。");
        }
        if (projected.compareTo(capacity) > 0) {
            throw blocked(room, capacity, used, remaining, power, projected,
                    applianceId, applianceCode, applianceName,
                    "挂入后会压过承载，本次不落账：房间 " + room.getRoomCode() + "（" + room.getRoomName()
                            + "）承载 " + capacity + " kW，当前已挂 " + used + " kW（剩余 "
                            + (remaining.signum() < 0 ? BigDecimal.ZERO.setScale(2) : remaining)
                            + " kW），这台 " + safeName(applianceCode, applianceName) + " 功率 " + power
                            + " kW，挂上后合计 " + projected + " kW。");
        }
    }

    private RoomOverCapacityException blocked(LoungeRoom room, BigDecimal capacity, BigDecimal used,
                                              BigDecimal remaining, BigDecimal power, BigDecimal projected,
                                              Long applianceId, String applianceCode, String applianceName,
                                              String message) {
        RoomOverCapacityDTO detail = new RoomOverCapacityDTO();
        detail.setRoomId(room.getId());
        detail.setRoomCode(room.getRoomCode());
        detail.setRoomName(room.getRoomName());
        detail.setPowerCapacity(capacity);
        detail.setPowerUsed(used);
        detail.setPowerRemaining(remaining);
        detail.setApplianceId(applianceId);
        detail.setApplianceCode(applianceCode);
        detail.setApplianceName(applianceName);
        detail.setAppliancePower(power);
        detail.setProjectedTotal(projected);
        detail.setMessage(message);
        return new RoomOverCapacityException(message, detail);
    }

    private String safeName(String code, String name) {
        if (code != null && name != null) {
            return name + "（" + code + "）";
        }
        return code != null ? code : (name != null ? name : "该电器");
    }

    private BigDecimal normalize(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
