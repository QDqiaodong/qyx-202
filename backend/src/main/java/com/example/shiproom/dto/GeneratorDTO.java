package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应急发电机档案。
 */
@Data
public class GeneratorDTO {

    private Long id;

    private String genCode;

    private String genName;

    private String location;

    private String status;

    /** 库存升数（只随复核通过变动） */
    private BigDecimal fuelStockLiters;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
