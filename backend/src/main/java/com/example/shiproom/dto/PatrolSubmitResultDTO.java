package com.example.shiproom.dto;

import lombok.Data;

/**
 * 交班提交结果。success=false 表示整份交班被退回（status=BLOCKED），
 * 此时数据库只多出一条 BLOCKED 退回说明，没有留下任何走房勾。
 */
@Data
public class PatrolSubmitResultDTO {

    private boolean success;
    private String handoverBatch;
    private String status;
    private String message;
    private PatrolBlockedRoomDTO blocked;
    private PatrolHandoverRecordDTO record;
}
