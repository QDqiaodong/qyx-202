package com.example.shiproom.controller;

import com.example.shiproom.dto.KeyCheckoutDTO;
import com.example.shiproom.dto.KeyCheckoutRecordDTO;
import com.example.shiproom.dto.KeyOccupancyDTO;
import com.example.shiproom.dto.KeyReturnDTO;
import com.example.shiproom.dto.LoungeKeyDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.KeyCheckoutBlockedException;
import com.example.shiproom.exception.LinenOccupancyBlockedException;
import com.example.shiproom.service.KeyCheckoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/key")
public class KeyCheckoutController {

    @Autowired
    private KeyCheckoutService keyCheckoutService;

    @PostMapping("/checkout")
    public ResponseEntity<ResponseDTO<?>> checkout(@RequestBody KeyCheckoutDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(keyCheckoutService.checkout(dto)));
        } catch (KeyCheckoutBlockedException e) {
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getBlockedKey()));
        } catch (LinenOccupancyBlockedException e) {
            // 回收未齐：上一班脏床品还没登记齐，别的船的人不能占用这间房
            return ResponseEntity.status(409).<ResponseDTO<?>>body(ResponseDTO.error(409, e.getMessage(), e.getBlocked()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().<ResponseDTO<?>>body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    @PostMapping("/return")
    public ResponseEntity<ResponseDTO<?>> returnKey(@RequestBody KeyReturnDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(keyCheckoutService.returnKey(dto)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().<ResponseDTO<?>>body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    @GetMapping("/occupancy/ship/{shipId}")
    public ResponseDTO<List<KeyOccupancyDTO>> occupancyByShip(@PathVariable Long shipId) {
        try {
            return ResponseDTO.success(keyCheckoutService.listOccupancyByShip(shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/occupancy/room/{roomId}")
    public ResponseDTO<List<KeyOccupancyDTO>> occupancyByRoom(@PathVariable Long roomId) {
        try {
            return ResponseDTO.success(keyCheckoutService.listOccupancyByRoom(roomId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/records")
    public ResponseDTO<List<KeyCheckoutRecordDTO>> records(@RequestParam(required = false) Long keyId,
                                                           @RequestParam(required = false) Long roomId,
                                                           @RequestParam(required = false) Long shipId) {
        try {
            return ResponseDTO.success(keyCheckoutService.listRecords(keyId, roomId, shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PostMapping
    public ResponseDTO<LoungeKeyDTO> createKey(@RequestBody LoungeKeyDTO dto) {
        try {
            return ResponseDTO.success(keyCheckoutService.createKey(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseDTO<LoungeKeyDTO> updateKey(@PathVariable Long id, @RequestBody LoungeKeyDTO dto) {
        try {
            return ResponseDTO.success(keyCheckoutService.updateKey(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> deleteKey(@PathVariable Long id) {
        try {
            keyCheckoutService.deleteKey(id);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<LoungeKeyDTO>> listKeys() {
        try {
            return ResponseDTO.success(keyCheckoutService.listKeys());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}
