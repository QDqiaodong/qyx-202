package com.example.shiproom.dto;

import lombok.Data;

@Data
public class PatrolOfficerDTO {

    private Long id;
    private String officerCode;
    private String officerName;
    /** LEADER=值班长，MEMBER=巡检员 */
    private String role;
    private String status;
    private String remark;
}
