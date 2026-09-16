package com.example.shiproom.controller;

import com.example.shiproom.dto.PatrolHandoverDTO;
import com.example.shiproom.dto.PatrolHandoverRecordDTO;
import com.example.shiproom.dto.PatrolOfficerDTO;
import com.example.shiproom.dto.PatrolShipStateDTO;
import com.example.shiproom.dto.PatrolSubmitResultDTO;
import com.example.shiproom.dto.PatrolWindowDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.PatrolAlreadyHandedException;
import com.example.shiproom.exception.PatrolForbiddenException;
import com.example.shiproom.exception.PatrolWindowLockedException;
import com.example.shiproom.service.PatrolHandoverService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/patrol")
public class PatrolHandoverController {

    private final PatrolHandoverService patrolHandoverService;

    public PatrolHandoverController(PatrolHandoverService patrolHandoverService) {
        this.patrolHandoverService = patrolHandoverService;
    }

    /** 夜班巡检交班：只有本班值班长能交，整份不通过时后端事务只留 BLOCKED 退回说明 */
    @PostMapping("/handover")
    public ResponseEntity<ResponseDTO<?>> submitHandover(@RequestBody PatrolHandoverDTO dto) {
        try {
            PatrolSubmitResultDTO result = patrolHandoverService.submitHandover(dto);
            if (result.isSuccess()) {
                return ResponseEntity.ok(ResponseDTO.success(result.getMessage(), result));
            }
            // 409：漏房 / 跳层 / 房间已脱挂，整份退回，data 里带卡住的房间与窗口
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResponseDTO.error(409, result.getMessage(), result));
        } catch (PatrolWindowLockedException e) {
            // 423：接班窗口一过即锁，不能再补勾或改交班
            return ResponseEntity.status(HttpStatus.LOCKED)
                    .body(ResponseDTO.error(423, e.getMessage()));
        } catch (PatrolForbiddenException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ResponseDTO.error(403, e.getMessage()));
        } catch (PatrolAlreadyHandedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ResponseDTO.error(409, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    /** 关掉页面再打开：还原该船在该窗口是未交 / 卡在跳层漏房 / 窗口外已锁 / 已交 */
    @GetMapping("/state")
    public ResponseDTO<PatrolShipStateDTO> state(@RequestParam Long windowId, @RequestParam Long shipId) {
        try {
            return ResponseDTO.success(patrolHandoverService.getShipWindowState(windowId, shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    /** 交班流水：可按窗口、船舶过滤，逐条带值班长、窗口、走房顺序 */
    @GetMapping("/records")
    public ResponseDTO<List<PatrolHandoverRecordDTO>> records(@RequestParam(required = false) Long windowId,
                                                             @RequestParam(required = false) Long shipId) {
        try {
            return ResponseDTO.success(patrolHandoverService.listRecords(windowId, shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PostMapping("/window")
    public ResponseDTO<PatrolWindowDTO> createWindow(@RequestBody PatrolWindowDTO dto) {
        try {
            return ResponseDTO.success(patrolHandoverService.createWindow(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/window/{id}/close")
    public ResponseDTO<PatrolWindowDTO> closeWindow(@PathVariable Long id) {
        try {
            return ResponseDTO.success(patrolHandoverService.closeWindow(id));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/windows")
    public ResponseDTO<List<PatrolWindowDTO>> windows(@RequestParam(required = false) Long shipId) {
        try {
            return ResponseDTO.success(patrolHandoverService.listWindows(shipId));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PostMapping("/officer")
    public ResponseDTO<PatrolOfficerDTO> createOfficer(@RequestBody PatrolOfficerDTO dto) {
        try {
            return ResponseDTO.success(patrolHandoverService.createOfficer(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/officer/{id}")
    public ResponseDTO<PatrolOfficerDTO> updateOfficer(@PathVariable Long id, @RequestBody PatrolOfficerDTO dto) {
        try {
            return ResponseDTO.success(patrolHandoverService.updateOfficer(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/officers")
    public ResponseDTO<List<PatrolOfficerDTO>> officers() {
        try {
            return ResponseDTO.success(patrolHandoverService.listOfficers());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}
