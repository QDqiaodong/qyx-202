package com.example.shiproom.controller;

import com.example.shiproom.dto.LinenRecoveryDTO;
import com.example.shiproom.dto.LinenRecoverySaveDTO;
import com.example.shiproom.dto.LinenRoomStateDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.LinenRecoveryBlockedException;
import com.example.shiproom.service.LinenRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 布草回收：换船后脏床品套数 / 封袋公斤数 / 见证人三栏登记。
 * 三栏没齐前房间可住灯不亮；可住灯亮过后套数公斤锁死；回收未齐别的船占用被挡。
 */
@RestController
@RequestMapping("/api/linen")
public class LinenRecoveryController {

    private final LinenRecoveryService linenRecoveryService;

    public LinenRecoveryController(LinenRecoveryService linenRecoveryService) {
        this.linenRecoveryService = linenRecoveryService;
    }

    /** 新开一页 / 补登 / 修改回收单；对不上、重复、锁死后改歪一律 409，data 带退回明细 */
    @PostMapping("/records")
    public ResponseEntity<ResponseDTO<?>> save(@RequestBody LinenRecoverySaveDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(linenRecoveryService.save(dto)));
        } catch (LinenRecoveryBlockedException e) {
            return ResponseEntity.status(409)
                    .body(ResponseDTO.error(409, e.getMessage(), e.getBlocked()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    /** 作废回收单（填错纠正），作废后本周期可重新开单 */
    @PutMapping("/records/{id}/void")
    public ResponseEntity<ResponseDTO<?>> voidRecord(@PathVariable Long id,
                                                     @RequestParam(required = false) String operator,
                                                     @RequestParam(required = false) String reason) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(linenRecoveryService.voidRecovery(id, operator, reason)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    /** 回收单流水，可按房间、离泊船过滤 */
    @GetMapping("/records")
    public ResponseDTO<List<LinenRecoveryDTO>> records(@RequestParam(required = false) Long roomId,
                                                       @RequestParam(required = false) Long shipId) {
        try {
            return ResponseDTO.success(linenRecoveryService.listRecords(roomId, shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    /** 全部房间的布草/可住灯状态（房间卡片据此画一个互斥的灯） */
    @GetMapping("/rooms")
    public ResponseDTO<List<LinenRoomStateDTO>> roomStates() {
        try {
            return ResponseDTO.success(linenRecoveryService.listRoomStates());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    /** 单间房的布草/可住灯状态（关掉页面重开仍一致） */
    @GetMapping("/rooms/{roomId}")
    public ResponseDTO<LinenRoomStateDTO> roomState(@PathVariable Long roomId) {
        try {
            return ResponseDTO.success(linenRecoveryService.getRoomState(roomId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}
