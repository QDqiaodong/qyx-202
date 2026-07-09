package com.example.shiproom.service;

import com.example.shiproom.dto.LoungeRoomDTO;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoungeRoomService {

    @Autowired
    private LoungeRoomRepository loungeRoomRepository;

    @Autowired
    private RoomShipRelationRepository roomShipRelationRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Transactional
    public LoungeRoomDTO create(LoungeRoomDTO dto) {
        if (loungeRoomRepository.existsByRoomCode(dto.getRoomCode())) {
            throw new RuntimeException("房间编号已存在");
        }
        LoungeRoom room = new LoungeRoom();
        room.setRoomCode(dto.getRoomCode());
        room.setRoomName(dto.getRoomName());
        room.setFloor(dto.getFloor());
        room.setCapacity(dto.getCapacity());
        room.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");

        return convertToDTO(loungeRoomRepository.save(room));
    }

    @Transactional
    public LoungeRoomDTO update(Long id, LoungeRoomDTO dto) {
        LoungeRoom room = loungeRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("房间不存在"));

        if (!room.getRoomCode().equals(dto.getRoomCode()) &&
                loungeRoomRepository.existsByRoomCode(dto.getRoomCode())) {
            throw new RuntimeException("房间编号已存在");
        }

        room.setRoomCode(dto.getRoomCode());
        room.setRoomName(dto.getRoomName());
        room.setFloor(dto.getFloor());
        room.setCapacity(dto.getCapacity());
        room.setStatus(dto.getStatus());

        return convertToDTO(loungeRoomRepository.save(room));
    }

    @Transactional
    public void delete(Long id) {
        if (!loungeRoomRepository.existsById(id)) {
            throw new RuntimeException("房间不存在");
        }
        loungeRoomRepository.deleteById(id);
    }

    public LoungeRoomDTO findById(Long id) {
        LoungeRoom room = loungeRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        return convertToDTO(room);
    }

    public List<LoungeRoomDTO> findAll() {
        return loungeRoomRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public LoungeRoomDTO findByRoomCode(String roomCode) {
        LoungeRoom room = loungeRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("房间不存在"));
        return convertToDTO(room);
    }

    private LoungeRoomDTO convertToDTO(LoungeRoom room) {
        LoungeRoomDTO dto = new LoungeRoomDTO();
        dto.setId(room.getId());
        dto.setRoomCode(room.getRoomCode());
        dto.setRoomName(room.getRoomName());
        dto.setFloor(room.getFloor());
        dto.setCapacity(room.getCapacity());
        dto.setStatus(room.getStatus());

        roomShipRelationRepository.findByRoomId(room.getId())
                .stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .findFirst()
                .ifPresent(relation -> {
                    shipRepository.findById(relation.getShipId())
                            .ifPresent(ship -> {
                                dto.setShipCode(ship.getShipCode());
                                dto.setShipName(ship.getShipName());
                            });
                });

        return dto;
    }
}