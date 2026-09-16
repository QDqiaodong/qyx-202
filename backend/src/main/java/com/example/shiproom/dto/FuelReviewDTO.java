package com.example.shiproom.dto;

import lombok.Data;

/**
 * 复核一次加油：复核人必须是另一个班的人，自己加的不能自己核。
 * 复核通过后，实加升数与复核结论、库存升数同一事务落库。
 */
@Data
public class FuelReviewDTO {

    /** 复核人姓名（不能是经办值班本人） */
    private String reviewerName;

    /** 复核人班次（必须与经办班次不同） */
    private String reviewerShift;

    /** 复核意见（选填） */
    private String reviewComment;
}
