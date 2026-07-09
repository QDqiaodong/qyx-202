package com.example.shiproom.controller;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RelationBindDTO;
import com.example.shiproom.dto.RelationChangeLogDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.service.RelationBindService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relation")
public class RelationBindController {

    @Autowired
    private RelationBindService relationBindService;

    @PostMapping("/bind")
    public ResponseDTO<Void> bindDevice(@RequestBody RelationBindDTO dto) {
        try {
            relationBindService.bindDevice(dto);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/room/{roomId}/ship/{shipId}")
    public ResponseDTO<Void> updateRelation(@PathVariable Long roomId, @PathVariable Long shipId,
                                            @RequestParam(required = false) String operator,
                                            @RequestParam(required = false) String remark) {
        try {
            relationBindService.updateRelation(roomId, shipId, operator, remark);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/ship/change")
    public ResponseDTO<Void> shipChange(@RequestParam Long oldShipId, @RequestParam Long newShipId,
                                        @RequestParam(required = false) String operator,
                                        @RequestParam(required = false) String remark) {
        try {
            relationBindService.shipChange(oldShipId, newShipId, operator, remark);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/ship/{shipId}/appliances")
    public ResponseDTO<List<ElectricApplianceDTO>> getAppliancesByShip(@PathVariable Long shipId) {
        try {
            return ResponseDTO.success(relationBindService.getAppliancesByShip(shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/room/{roomId}/appliances")
    public ResponseDTO<List<ElectricApplianceDTO>> getAppliancesByRoom(@PathVariable Long roomId) {
        try {
            return ResponseDTO.success(relationBindService.getAppliancesByRoom(roomId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/room/{roomId}/with-ship")
    public ResponseDTO<LoungeRoomDTO> getRoomWithShip(@PathVariable Long roomId) {
        try {
            return ResponseDTO.success(relationBindService.getRoomWithShip(roomId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/logs")
    public ResponseDTO<List<RelationChangeLogDTO>> getChangeLogs(
            @RequestParam(required = false) Long deviceId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long shipId) {
        try {
            return ResponseDTO.success(relationBindService.getChangeLogs(deviceId, roomId, shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}