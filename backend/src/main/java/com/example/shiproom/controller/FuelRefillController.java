package com.example.shiproom.controller;

import com.example.shiproom.dto.FuelRefillDTO;
import com.example.shiproom.dto.FuelRefillSaveDTO;
import com.example.shiproom.dto.FuelReviewDTO;
import com.example.shiproom.dto.GeneratorDTO;
import com.example.shiproom.dto.GeneratorFuelStateDTO;
import com.example.shiproom.dto.ResponseDTO;
import com.example.shiproom.exception.FuelAlreadyReviewedException;
import com.example.shiproom.exception.FuelRefillBlockedException;
import com.example.shiproom.exception.FuelReviewForbiddenException;
import com.example.shiproom.service.FuelRefillService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 发电机加油：登记（哪台机、本罐编号、实加升数、经办值班）+ 另一个班复核。
 * 同机同日只挂一条未复核；复核通过与库存升数同事务；复核撞车只留先写完的结论。
 */
@RestController
@RequestMapping("/api/fuel")
public class FuelRefillController {

    private final FuelRefillService fuelRefillService;

    public FuelRefillController(FuelRefillService fuelRefillService) {
        this.fuelRefillService = fuelRefillService;
    }

    /** 登记一次加油；同机同日已有未复核单 → 409，data 带已在库那条的单号/罐号/升数/经办 */
    @PostMapping("/records")
    public ResponseEntity<ResponseDTO<?>> create(@RequestBody FuelRefillSaveDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(fuelRefillService.create(dto)));
        } catch (FuelRefillBlockedException e) {
            return ResponseEntity.status(409)
                    .body(ResponseDTO.error(409, e.getMessage(), e.getBlocked()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    /**
     * 复核通过一次加油。自己加的不能自己核、复核人必须是另一个班 → 403；
     * 两人抢着复核，后到的人 → 409，data 带先写完的那次复核结论。
     */
    @PostMapping("/records/{id}/review")
    public ResponseEntity<ResponseDTO<?>> review(@PathVariable Long id, @RequestBody FuelReviewDTO dto) {
        try {
            return ResponseEntity.ok(ResponseDTO.success(fuelRefillService.review(id, dto)));
        } catch (FuelAlreadyReviewedException e) {
            return ResponseEntity.status(409)
                    .body(ResponseDTO.error(409, e.getMessage(), e.getBlocked()));
        } catch (FuelReviewForbiddenException e) {
            return ResponseEntity.status(403)
                    .body(ResponseDTO.error(403, e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(ResponseDTO.error(400, e.getMessage()));
        }
    }

    /** 加油流水：可按发电机、自然日过滤；升数、罐号、经办、复核人都可查 */
    @GetMapping("/records")
    public ResponseDTO<List<FuelRefillDTO>> records(
            @RequestParam(required = false) Long generatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            return ResponseDTO.success(fuelRefillService.listRecords(generatorId, date));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    /** 发电机列表 + 每台机今天的加油状态（已加油列带得出升数和复核人） */
    @GetMapping("/generators/state")
    public ResponseDTO<List<GeneratorFuelStateDTO>> generatorStates() {
        try {
            return ResponseDTO.success(fuelRefillService.listGeneratorStates());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @GetMapping("/generators")
    public ResponseDTO<List<GeneratorDTO>> generators() {
        try {
            return ResponseDTO.success(fuelRefillService.listGenerators());
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PostMapping("/generators")
    public ResponseDTO<GeneratorDTO> createGenerator(@RequestBody GeneratorDTO dto) {
        try {
            return ResponseDTO.success(fuelRefillService.createGenerator(dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }

    @PutMapping("/generators/{id}")
    public ResponseDTO<GeneratorDTO> updateGenerator(@PathVariable Long id, @RequestBody GeneratorDTO dto) {
        try {
            return ResponseDTO.success(fuelRefillService.updateGenerator(id, dto));
        } catch (Exception e) {
            return ResponseDTO.error(e.getMessage());
        }
    }
}
