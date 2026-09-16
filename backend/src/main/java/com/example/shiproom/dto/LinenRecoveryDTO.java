package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LinenRecoveryDTO {

    private Long id;

    private String recoveryNo;

    private Long roomId;

    private String roomCode;

    private String roomName;

    /** 换走的上一班船（离泊船） */
    private Long departedShipId;

    private String departedShipCode;

    private String departedShipName;

    private String changeBatch;

    /** 收走的脏床品套数 */
    private Integer setCount;

    /** 秤上的封袋公斤数 */
    private BigDecimal bagWeight;

    /** 每套约定公斤区间下界（随单留底） */
    private BigDecimal kgPerSetMin;

    /** 每套约定公斤区间上界（随单留底） */
    private BigDecimal kgPerSetMax;

    /** 见证人姓名 */
    private String witnessName;

    private String operator;

    /** DRAFT / CONFIRMED / VOID */
    private String status;

    private String remark;

    private LocalDateTime confirmedTime;

    private String confirmedBy;

    private LocalDateTime voidTime;

    private String voidBy;

    private String voidReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
