package com.example.shiproom.controller;

import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.service.LoungeRoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/room")
public class LoungeRoomController {

    @Autowired
    private LoungeRoomService loungeRoomService;

    @PostMapping
    public ResponseDTO<LoungeRoomDTO> create(@RequestBody LoungeRoomDTO dto) {
        try {
            return ResponseDTO.success(loungeRoomService.create(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseDTO<LoungeRoomDTO> update(@PathVariable Long id, @RequestBody LoungeRoomDTO dto) {
        try {
            return ResponseDTO.success(loungeRoomService.update(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseDTO<Void> delete(@PathVariable Long id) {
        try {
            loungeRoomService.delete(id);
            return ResponseDTO.success(null);
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseDTO<LoungeRoomDTO> findById(@PathVariable Long id) {
        try {
            return ResponseDTO.success(loungeRoomService.findById(id));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping
    public ResponseDTO<List<LoungeRoomDTO>> findAll() {
        try {
            return ResponseDTO.success(loungeRoomService.findAll());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/code/{roomCode}")
    public ResponseDTO<LoungeRoomDTO> findByRoomCode(@PathVariable String roomCode) {
        try {
            return ResponseDTO.success(loungeRoomService.findByRoomCode(roomCode));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}