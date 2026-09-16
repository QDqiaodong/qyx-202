package com.example.shiproom.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新开一页布草回收：点出刚换过船的那间房，填套数 / 封袋公斤数 / 见证人。
 * 三栏允许先空着（草稿），但三栏还没齐之前房间可住灯不亮；
 * 三栏都填了则当场按每套约定公斤区间对账，对不上整单保存失败。
 */
@Data
public class LinenRecoverySaveDTO {

    /** 空=新开回收单；带 id=更新这一单 */
    private Long id;

    private Long roomId;

    /** 收走的脏床品套数（可先空） */
    private Integer setCount;

    /** 秤上的封袋公斤数（可先空） */
    private BigDecimal bagWeight;

    /** 见证人姓名（可先空） */
    private String witnessName;

    private String operator;

    private String remark;
}
