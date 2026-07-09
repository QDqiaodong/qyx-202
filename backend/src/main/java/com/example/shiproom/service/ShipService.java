package com.example.shiproom.service;

import com.example.shiproom.dto.ShipDTO;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShipService {

    @Autowired
    private ShipRepository shipRepository;

    @Transactional
    public ShipDTO create(ShipDTO dto) {
        if (shipRepository.existsByShipCode(dto.getShipCode())) {
            throw new RuntimeException("船舶编号已存在");
        }
        Ship ship = new Ship();
        ship.setShipCode(dto.getShipCode());
        ship.setShipName(dto.getShipName());
        ship.setShipType(dto.getShipType());
        ship.setDockCode(dto.getDockCode());
        ship.setStatus(dto.getStatus() != null ? dto.getStatus() : "DOCKED");

        return convertToDTO(shipRepository.save(ship));
    }

    @Transactional
    public ShipDTO update(Long id, ShipDTO dto) {
        Ship ship = shipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("船舶不存在"));

        if (!ship.getShipCode().equals(dto.getShipCode()) &&
                shipRepository.existsByShipCode(dto.getShipCode())) {
            throw new RuntimeException("船舶编号已存在");
        }

        ship.setShipCode(dto.getShipCode());
        ship.setShipName(dto.getShipName());
        ship.setShipType(dto.getShipType());
        ship.setDockCode(dto.getDockCode());
        ship.setStatus(dto.getStatus());

        return convertToDTO(shipRepository.save(ship));
    }

    @Transactional
    public void delete(Long id) {
        if (!shipRepository.existsById(id)) {
            throw new RuntimeException("船舶不存在");
        }
        shipRepository.deleteById(id);
    }

    public ShipDTO findById(Long id) {
        Ship ship = shipRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("船舶不存在"));
        return convertToDTO(ship);
    }

    public List<ShipDTO> findAll() {
        return shipRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ShipDTO findByShipCode(String shipCode) {
        Ship ship = shipRepository.findByShipCode(shipCode)
                .orElseThrow(() -> new RuntimeException("船舶不存在"));
        return convertToDTO(ship);
    }

    private ShipDTO convertToDTO(Ship ship) {
        ShipDTO dto = new ShipDTO();
        dto.setId(ship.getId());
        dto.setShipCode(ship.getShipCode());
        dto.setShipName(ship.getShipName());
        dto.setShipType(ship.getShipType());
        dto.setDockCode(ship.getDockCode());
        dto.setStatus(ship.getStatus());
        return dto;
    }
}