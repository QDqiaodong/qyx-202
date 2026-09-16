package com.example.shiproom.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 应急发电机档案（码头楼顶、泵房等）。
 *
 * fuel_stock_liters 是这台机的库存升数：加油登记时不动的，
 * 只有复核通过才在同一事务里把实加升数加进来；
 * 复核写到一半失败，库存升数必须仍是复核前的数。
 */
@Data
@Entity
@Table(name = "emergency_generator")
public class EmergencyGenerator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 机号，如 GEN-ROOF-01 */
    @Column(name = "gen_code", nullable = false, unique = true, length = 50)
    private String genCode;

    @Column(name = "gen_name", nullable = false, length = 100)
    private String genName;

    /** 安放位置，如 码头楼顶 */
    @Column(name = "location", length = 100)
    private String location;

    /** ACTIVE / INACTIVE，停用后不能再登记加油 */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 库存升数：只随复核通过变动，变动与复核结论同一事务 */
    @Column(name = "fuel_stock_liters", nullable = false, precision = 10, scale = 2)
    private BigDecimal fuelStockLiters = BigDecimal.ZERO;

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
