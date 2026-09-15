package com.example.shiproom.dto;

import lombok.Data;

@Data
public class KeyCheckoutDTO {

    private Long keyId;

    private Long shipId;

    private String holderName;

    private String operator;

    private String remark;
}
