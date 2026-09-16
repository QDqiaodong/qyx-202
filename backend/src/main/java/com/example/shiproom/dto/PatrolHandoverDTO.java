package com.example.shiproom.dto;

import lombok.Data;

import java.util.List;

/**
 * 夜班巡检交班提交。只有本班值班长（leaderId 指向 ACTIVE 的 LEADER）能提交。
 * entries 必须按楼层从低到高覆盖该船本窗口挂靠的全部房间。
 */
@Data
public class PatrolHandoverDTO {

    private Long windowId;
    private Long shipId;
    private Long leaderId;
    /** 兼容直接传值班长工号/姓名的场景 */
    private String leaderCode;
    private String remark;
    private List<PatrolCheckEntryDTO> entries;
}
