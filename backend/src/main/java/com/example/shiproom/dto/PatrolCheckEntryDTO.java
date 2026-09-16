package com.example.shiproom.dto;

import lombok.Data;

/**
 * 交班提交时的一个走房条目（一间已巡房间）。
 * seq 为值班长实际走房顺序，从 1 开始，必须严格按楼层从低到高。
 */
@Data
public class PatrolCheckEntryDTO {

    private Long roomId;

    /** 走房顺序，从 1 开始连续编号 */
    private Integer seq;

    /** 可选：巡检备注 */
    private String note;
}
