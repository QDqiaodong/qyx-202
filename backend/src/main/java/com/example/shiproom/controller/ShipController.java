package com.example.shiproom.controller;

import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.dto.ShipDTO;
import com.example.shiproom.service.ShipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ship")
public class ShipController {

    @Autowired
    private ShipService shipService;

    @PostMapping
    public ResponseDTO<ShipDTO> create(@RequestBody ShipDTO dto) {
        try {
            return ResponseDTO.success(shipService.create(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseDTO<ShipDTO> update(@PathVariable Long id, @RequestBody ShipDTO dto) {
        try {
            return ResponseDTO.success(shipService.update(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        try {
            shipService.delete(id);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseDTO<ShipDTO> findById(@PathVariable Long id) {
        try {
            return ResponseDTO.success(shipService.findById(id));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<ShipDTO>> findAll() {
        try {
            return ResponseDTO.success(shipService.findAll());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/code/{shipCode}")
    public ResponseDTO<ShipDTO> findByShipCode(@PathVariable String shipCode) {
        try {
            return ResponseDTO.success(shipService.findByShipCode(shipCode));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}