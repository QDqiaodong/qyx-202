package com.example.shiproom.controller;

import com.example.shiproom.dto.ElectricApplianceDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.RoomOverCapacityException;
import com.example.shiproom.service.ElectricApplianceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/appliance")
public class ElectricApplianceController {

    @Autowired
    private ElectricApplianceService electricApplianceService;

    @PostMapping
    public ResponseEntity<ResponseDTO<?>> create(@RequestBody ElectricApplianceDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(electricApplianceService.create(dto)));
        } catch (RoomOverCapacityException e) {
            return ResponseEntity.status(409)
                    .body(ResponseDTO.error(409, e.getMessage(), e.getDetail()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseDTO<ElectricApplianceDTO> update(@PathVariable Long id, @RequestBody ElectricApplianceDTO dto) {
        try {
            return ResponseDTO.success(electricApplianceService.update(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        try {
            electricApplianceService.delete(id);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseDTO<ElectricApplianceDTO> findById(@PathVariable Long id) {
        try {
            return ResponseDTO.success(electricApplianceService.findById(id));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<ElectricApplianceDTO>> findAll() {
        try {
            return ResponseDTO.success(electricApplianceService.findAll());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/room/{roomId}")
    public ResponseDTO<List<ElectricApplianceDTO>> findByRoomId(@PathVariable Long roomId) {
        try {
            return ResponseDTO.success(electricApplianceService.findByRoomId(roomId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/ship/{shipId}")
    public ResponseDTO<List<ElectricApplianceDTO>> findByShipId(@PathVariable Long shipId) {
        try {
            return ResponseDTO.success(electricApplianceService.findByShipId(shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}
