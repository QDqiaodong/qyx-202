package com.example.shiproom.controller;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.RelationBindDTO;
import com.example.shiproom.dto.RelationChangeLogDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.RoomOverCapacityException;
import com.example.shiproom.exception.ShiftBlockedException;
import com.example.shiproom.service.RelationBindService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/relation")
public class RelationBindController {

    @Autowired
    private RelationBindService relationBindService;

    @PostMapping("/bind")
    public ResponseEntity<ResponseDTO<?>> bindDevice(@RequestBody RelationBindDTO dto) {
        try {
            relationBindService.bindDevice(dto);
            return ResponseEntity.ok(ResponseDTO.success(null));
        } catch (RoomOverCapacityException e) {
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getDetail()));
        } catch (ShiftBlockedException e) {
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getBlockedAppliances()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().<ResponseDTO<?>>body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    @PutMapping("/room/{roomId}/ship/{shipId}")
    public ResponseEntity<ResponseDTO<?>> updateRelation(@PathVariable Long roomId, @PathVariable Long shipId,
                                            @RequestParam(required = false) String operator,
                                            @RequestParam(required = false) String remark) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(relationBindService.updateRelation(roomId, shipId, operator, remark)));
        } catch (ShiftBlockedException e) {
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getBlockedAppliances()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().<ResponseDTO<?>>body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    @PutMapping("/ship/change")
    public ResponseEntity<ResponseDTO<?>> shipChange(@RequestParam Long oldShipId, @RequestParam Long newShipId,
                                        @RequestParam(required = false) String operator,
                                        @RequestParam(required = false) String remark) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(relationBindService.shipChange(oldShipId, newShipId, operator, remark)));
        } catch (ShiftBlockedException e) {
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getBlockedAppliances()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().<ResponseDTO<?>>body(ResponseDTO.error(400, e.getMessage()));
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