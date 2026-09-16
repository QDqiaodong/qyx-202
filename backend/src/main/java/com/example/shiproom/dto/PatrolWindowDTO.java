package com.example.shiproom.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatrolWindowDTO {

    private Long id;
    private String windowCode;
    private String windowName;
    private Long shipId;
    private String shipCode;
    private String shipName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String operator;
    private String remark;
    private LocalDateTime createTime;

    /** 服务端按当前时间算出的窗口状态：NOT_STARTED / OPEN / LOCKED / CLOSED */
    private String windowState;
}
