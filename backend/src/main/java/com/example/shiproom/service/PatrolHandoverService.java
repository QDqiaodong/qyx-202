package com.example.shiproom.service;

import com.example.shiproom.dto.PatrolBlockedRoomDTO;
import com.example.shiproom.dto.PatrolCheckDTO;
import com.example.shiproom.dto.PatrolCheckEntryDTO;
import com.example.shiproom.dto.PatrolHandoverDTO;
import com.example.shiproom.dto.PatrolHandoverRecordDTO;
import com.example.shiproom.dto.PatrolOfficerDTO;
import com.example.shiproom.dto.PatrolShipStateDTO;
import com.example.shiproom.dto.PatrolSubmitResultDTO;
import com.example.shiproom.dto.PatrolWindowDTO;
import com.example.shiproom.entity.LoungeRoom;
import com.example.shiproom.entity.PatrolCheck;
import com.example.shiproom.entity.PatrolHandover;
import com.example.shiproom.entity.PatrolOfficer;
import com.example.shiproom.entity.PatrolWindow;
import com.example.shiproom.entity.RoomShipRelation;
import com.example.shiproom.entity.Ship;
import com.example.shiproom.exception.PatrolAlreadyHandedException;
import com.example.shiproom.exception.PatrolForbiddenException;
import com.example.shiproom.exception.PatrolWindowLockedException;
import com.example.shiproom.repository.LoungeRoomRepository;
import com.example.shiproom.repository.PatrolCheckRepository;
import com.example.shiproom.repository.PatrolHandoverRepository;
import com.example.shiproom.repository.PatrolOfficerRepository;
import com.example.shiproom.repository.PatrolWindowRepository;
import com.example.shiproom.repository.RoomShipRelationRepository;
import com.example.shiproom.repository.ShipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 夜班巡检交班。
 *
 * 第一件事：在现有靠泊关系（room_ship_relation 的 ACTIVE 关联）上补交班，
 *           只有本班值班长能交；一次交班必须按楼层从低到高走完该船当时挂靠的全部房间，
 *           漏一间或跳层都不能交；交班流水写下值班长、窗口、走房顺序。
 *
 * 第二件事：接班窗口一过就不能再补勾或改交班；窗口内若有房间已不再停靠本船，
 *           整份交班退回，已走过的勾和交班记录都不留半截（只留一条 BLOCKED 退回说明，
 *           明确是哪间房、哪个窗口卡住）。
 *
 * 所有判定与写入都在同一个事务、同一把换班全局行锁 + 船舶行锁内完成，
 * 因此两个人同时给同一条船交班，最多只有一份 HANDED。
 */
@Service
public class PatrolHandoverService {

    static final String ACTIVE = "ACTIVE";
    static final String HANDED = "HANDED";
    static final String BLOCKED = "BLOCKED";

    private static final String ROLE_LEADER = "LEADER";

    /** 退回原因码 */
    static final String REASON_ROOM_DETACHED = "ROOM_DETACHED";
    static final String REASON_MISSING_ROOM = "MISSING_ROOM";
    static final String REASON_FLOOR_SKIP = "FLOOR_SKIP";
    static final String REASON_BAD_ENTRY = "BAD_ENTRY";

    private final PatrolWindowRepository windowRepository;
    private final PatrolOfficerRepository officerRepository;
    private final PatrolHandoverRepository handoverRepository;
    private final PatrolCheckRepository checkRepository;
    private final LoungeRoomRepository loungeRoomRepository;
    private final RoomShipRelationRepository roomShipRelationRepository;
    private final ShipRepository shipRepository;
    private final ShiftOperationLockService shiftOperationLockService;

    /** 可替换时钟，便于测试“窗口外已锁” */
    private Clock clock = Clock.systemDefaultZone();

    public PatrolHandoverService(PatrolWindowRepository windowRepository,
                                 PatrolOfficerRepository officerRepository,
                                 PatrolHandoverRepository handoverRepository,
                                 PatrolCheckRepository checkRepository,
                                 LoungeRoomRepository loungeRoomRepository,
                                 RoomShipRelationRepository roomShipRelationRepository,
                                 ShipRepository shipRepository,
                                 ShiftOperationLockService shiftOperationLockService) {
        this.windowRepository = windowRepository;
        this.officerRepository = officerRepository;
        this.handoverRepository = handoverRepository;
        this.checkRepository = checkRepository;
        this.loungeRoomRepository = loungeRoomRepository;
        this.roomShipRelationRepository = roomShipRelationRepository;
        this.shipRepository = shipRepository;
        this.shiftOperationLockService = shiftOperationLockService;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ============================ 交班提交 ============================

    @Transactional
    public PatrolSubmitResultDTO submitHandover(PatrolHandoverDTO dto) {
        // 取换班全局行锁：与换班改挂、钥匙领还互斥，保证提交期间靠泊关系不会变
        shiftOperationLockService.lock();

        if (dto.getWindowId() == null || dto.getShipId() == null) {
            throw new IllegalArgumentException("接班窗口和船舶不能为空");
        }
        if (dto.getEntries() == null || dto.getEntries().isEmpty()) {
            throw new IllegalArgumentException("走房记录为空，不能交班");
        }

        PatrolWindow window = windowRepository.findByIdForUpdate(dto.getWindowId())
                .orElseThrow(() -> new IllegalArgumentException("接班窗口不存在"));
        Ship ship = shipRepository.findByIdForUpdate(dto.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在"));

        if (!Objects.equals(window.getShipId(), ship.getId())) {
            throw new IllegalArgumentException("接班窗口不属于船舶 " + ship.getShipCode());
        }

        // —— 第二件事：窗口一过就锁，既不能补勾也不能改交班 ——
        assertWindowOpen(window);

        // —— 第一件事：只有本班值班长能交 ——
        PatrolOfficer leader = resolveLeader(dto);
        if (!leader.isActiveLeader()) {
            throw new PatrolForbiddenException(
                    "只有本班值班长才能交班：" + leader.getOfficerName()
                            + "（" + leader.getOfficerCode() + "）不是在岗值班长");
        }

        // —— 两人同时给同一条船交班：唯一成功键 + 全局锁下二次确认 ——
        String uniqueKey = handedUniqueKey(ship.getId(), window.getId());
        handoverRepository.findByHandedUniqueKeyForUpdate(uniqueKey).ifPresent(existing -> {
            throw new PatrolAlreadyHandedException(
                    "船舶 " + ship.getShipCode() + " 在窗口 " + window.getWindowCode()
                            + " 已由值班长 " + existing.getLeaderName() + " 交班（批次 "
                            + existing.getHandoverBatch() + "），不能重复交班");
        });

        // 该船此刻（锁定后）挂靠的全部房间，按楼层从低到高
        List<LoungeRoom> expectedRooms = loadExpectedRooms(ship.getId(), true);

        // 逐条校验走房：房间必须仍停靠本船、不重不漏、楼层单调不降
        List<LoungeRoom> walkedRooms = new ArrayList<>();
        PatrolBlockedRoomDTO blocked = validateWalk(window, ship, dto.getEntries(), expectedRooms, walkedRooms);
        if (blocked != null) {
            // 整份退回：只落一条 BLOCKED 说明，不写任何 patrol_check，不留半截
            PatrolHandover blockedRecord = saveBlockedHandover(window, ship, leader, dto, blocked);
            return blockedResult(blocked, blockedRecord);
        }

        // —— 校验全过：同一事务落下交班流水 + 全部走房勾 ——
        String batch = newBatch();
        LocalDateTime submitTime = now();

        PatrolHandover handover = new PatrolHandover();
        handover.setHandoverBatch(batch);
        handover.setWindowId(window.getId());
        handover.setWindowCode(window.getWindowCode());
        handover.setShipId(ship.getId());
        handover.setShipCode(ship.getShipCode());
        handover.setShipName(ship.getShipName());
        handover.setLeaderId(leader.getId());
        handover.setLeaderCode(leader.getOfficerCode());
        handover.setLeaderName(leader.getOfficerName());
        handover.setStatus(HANDED);
        handover.setRoomCount(walkedRooms.size());
        handover.setHandedUniqueKey(uniqueKey);
        handover.setRemark(dto.getRemark());
        handover.setSubmitTime(submitTime);
        handover = handoverRepository.saveAndFlush(handover);

        List<PatrolCheck> checks = new ArrayList<>();
        for (int i = 0; i < walkedRooms.size(); i++) {
            LoungeRoom room = walkedRooms.get(i);
            PatrolCheck check = new PatrolCheck();
            check.setHandoverId(handover.getId());
            check.setHandoverBatch(batch);
            check.setWindowId(window.getId());
            check.setWindowCode(window.getWindowCode());
            check.setShipId(ship.getId());
            check.setShipCode(ship.getShipCode());
            check.setRoomId(room.getId());
            check.setRoomCode(room.getRoomCode());
            check.setRoomName(room.getRoomName());
            check.setFloor(room.getFloor());
            check.setSeq(i + 1);
            check.setLeaderName(leader.getOfficerName());
            check.setCheckTime(submitTime);
            checks.add(checkRepository.save(check));
        }
        handover.setWalkOrder(buildWalkOrder(checks));
        handoverRepository.save(handover);

        PatrolSubmitResultDTO result = new PatrolSubmitResultDTO();
        result.setSuccess(true);
        result.setHandoverBatch(batch);
        result.setStatus(HANDED);
        result.setMessage("交班成功：值班长 " + leader.getOfficerName() + " 在窗口 "
                + window.getWindowCode() + " 按楼层从低到高走完 " + walkedRooms.size() + " 间房");
        result.setRecord(toRecordDTO(handover, checks));
        return result;
    }

    /**
     * 校验走房顺序。返回 null 表示通过；否则返回卡住明细（含房间、窗口、原因）。
     * 通过时把按 seq 排好的房间写入 walkedRooms。
     */
    private PatrolBlockedRoomDTO validateWalk(PatrolWindow window, Ship ship,
                                              List<PatrolCheckEntryDTO> entries,
                                              List<LoungeRoom> expectedRooms,
                                              List<LoungeRoom> walkedRooms) {
        // seq 必须从 1 连续，且按 seq 排序
        List<PatrolCheckEntryDTO> ordered = new ArrayList<>(entries);
        for (int i = 0; i < ordered.size(); i++) {
            PatrolCheckEntryDTO entry = ordered.get(i);
            if (entry == null || entry.getRoomId() == null) {
                return blockedBase(window, ship, REASON_BAD_ENTRY, null)
                        .message("第 " + (i + 1) + " 条走房记录缺少房间，整份交班退回")
                        .build();
            }
        }
        ordered.sort(Comparator.comparing(e -> e.getSeq() == null ? Integer.MAX_VALUE : e.getSeq()));
        for (int i = 0; i < ordered.size(); i++) {
            int expectedSeq = i + 1;
            Integer seq = ordered.get(i).getSeq();
            if (seq == null || seq != expectedSeq) {
                return blockedBase(window, ship, REASON_BAD_ENTRY, null)
                        .message("走房顺序必须从 1 连续编号，第 " + expectedSeq + " 间缺失/乱序，整份交班退回")
                        .build();
            }
        }

        Map<Long, LoungeRoom> expectedById = expectedRooms.stream()
                .collect(Collectors.toMap(LoungeRoom::getId, room -> room, (a, b) -> a, LinkedHashMap::new));

        LoungeRoom previousRoom = null;
        String previousFloor = null;
        Map<Long, PatrolCheckEntryDTO> seenRoom = new LinkedHashMap<>();

        for (PatrolCheckEntryDTO entry : ordered) {
            LoungeRoom room = expectedById.get(entry.getRoomId());
            if (room == null) {
                // 提交的房间不存在于本船当前挂靠集合：可能已换挂别的船，或根本不是本船房间
                LoungeRoom raw = loungeRoomRepository.findById(entry.getRoomId()).orElse(null);
                Ship currentShip = findCurrentDockingShip(entry.getRoomId());
                String roomText = raw == null ? ("房间#" + entry.getRoomId())
                        : raw.getRoomCode() + "（" + raw.getRoomName() + "）";
                String currentText = currentShip == null
                        ? "（当前无停靠船舶）"
                        : "（当前停靠 " + currentShip.getShipCode() + "（" + currentShip.getShipName() + "））";
                return blockedBase(window, ship, REASON_ROOM_DETACHED, raw)
                        .currentShipId(currentShip == null ? null : currentShip.getId())
                        .currentShipCode(currentShip == null ? null : currentShip.getShipCode())
                        .currentShipName(currentShip == null ? null : currentShip.getShipName())
                        .message("房间 " + roomText + " 在窗口 " + window.getWindowCode()
                                + " 已不再停靠本船 " + ship.getShipCode() + currentText
                                + "，整份交班退回，已走的勾全部作废")
                        .build();
            }

            if (seenRoom.containsKey(room.getId())) {
                return blockedBase(window, ship, REASON_BAD_ENTRY, room)
                        .message("房间 " + room.getRoomCode() + " 在走房顺序里重复出现，整份交班退回")
                        .build();
            }

            // 楼层必须从低到高（同层可连走）：当前层低于上一层即为跳层
            if (previousFloor != null && compareFloor(room.getFloor(), previousFloor) < 0) {
                return blockedBase(window, ship, REASON_FLOOR_SKIP, room)
                        .previousFloor(previousFloor)
                        .previousRoomCode(previousRoom == null ? null : previousRoom.getRoomCode())
                        .message("走房跳层：从 " + previousFloor + " 层（"
                                + (previousRoom == null ? "" : previousRoom.getRoomCode()) + "）跳到更低的 "
                                + floorText(room.getFloor()) + " 层（" + room.getRoomCode()
                                + "），必须按楼层从低到高，整份交班退回")
                        .build();
            }

            seenRoom.put(room.getId(), entry);
            walkedRooms.add(room);
            previousRoom = room;
            previousFloor = room.getFloor();
        }

        // 必须覆盖该船当时挂靠的全部房间：漏一间都不能交
        List<LoungeRoom> missing = expectedRooms.stream()
                .filter(room -> !seenRoom.containsKey(room.getId()))
                .toList();
        if (!missing.isEmpty()) {
            LoungeRoom first = missing.get(0);
            String codes = missing.stream().map(r -> floorText(r.getFloor()) + ":" + r.getRoomCode())
                    .collect(Collectors.joining("、"));
            return blockedBase(window, ship, REASON_MISSING_ROOM, first)
                    .message("漏走 " + missing.size() + " 间房（" + codes + "），该船挂靠房间必须一间不漏走完，整份交班退回")
                    .build();
        }
        return null;
    }

    /** 读取该船此刻 ACTIVE 挂靠的全部房间，按楼层从低到高、同房按房间编号排序；提交路径加行锁 */
    private List<LoungeRoom> loadExpectedRooms(Long shipId, boolean lock) {
        List<RoomShipRelation> relations = lock
                ? roomShipRelationRepository.findByShipIdForUpdate(shipId)
                : roomShipRelationRepository.findByShipId(shipId);
        Map<Long, LoungeRoom> rooms = new LinkedHashMap<>();
        for (RoomShipRelation relation : relations) {
            if (!ACTIVE.equals(relation.getStatus())) {
                continue;
            }
            if (rooms.containsKey(relation.getRoomId())) {
                continue;
            }
            LoungeRoom room = lock
                    ? loungeRoomRepository.findByIdForUpdate(relation.getRoomId()).orElse(null)
                    : loungeRoomRepository.findById(relation.getRoomId()).orElse(null);
            if (room != null) {
                rooms.put(room.getId(), room);
            }
        }
        return rooms.values().stream()
                .sorted(roomFloorComparator())
                .collect(Collectors.toList());
    }

    private Ship findCurrentDockingShip(Long roomId) {
        return roomShipRelationRepository.findByRoomId(roomId).stream()
                .filter(relation -> ACTIVE.equals(relation.getStatus()))
                .findFirst()
                .map(relation -> shipRepository.findById(relation.getShipId()).orElse(null))
                .orElse(null);
    }

    // ============================ 状态 / 流水查询（关掉页面重开仍对得上） ============================

    @Transactional(readOnly = true)
    public PatrolShipStateDTO getShipWindowState(Long windowId, Long shipId) {
        PatrolWindow window = windowRepository.findById(windowId)
                .orElseThrow(() -> new IllegalArgumentException("接班窗口不存在"));
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在"));

        PatrolShipStateDTO dto = new PatrolShipStateDTO();
        dto.setShipId(ship.getId());
        dto.setShipCode(ship.getShipCode());
        dto.setShipName(ship.getShipName());
        dto.setWindowId(window.getId());
        dto.setWindowCode(window.getWindowCode());
        dto.setWindowName(window.getWindowName());
        dto.setStartTime(window.getStartTime());
        dto.setEndTime(window.getEndTime());

        String windowState = windowState(window, now());
        dto.setWindowState(windowState);

        List<LoungeRoom> expectedRooms = loadExpectedRooms(ship.getId(), false);
        List<PatrolShipStateDTO.PatrolExpectedRoomDTO> expectedDTO = expectedRooms.stream()
                .map(room -> {
                    PatrolShipStateDTO.PatrolExpectedRoomDTO item = new PatrolShipStateDTO.PatrolExpectedRoomDTO();
                    item.setRoomId(room.getId());
                    item.setRoomCode(room.getRoomCode());
                    item.setRoomName(room.getRoomName());
                    item.setFloor(room.getFloor());
                    item.setChecked(false);
                    return item;
                })
                .collect(Collectors.toList());
        dto.setExpectedRooms(expectedDTO);

        Optional<PatrolHandover> handed = handoverRepository
                .findByHandedUniqueKey(handedUniqueKey(ship.getId(), window.getId()));
        if (handed.isPresent()) {
            PatrolHandover h = handed.get();
            dto.setHandoverState(HANDED);
            dto.setHandoverBatch(h.getHandoverBatch());
            dto.setLeaderName(h.getLeaderName());
            dto.setRoomCount(h.getRoomCount());
            dto.setSubmitTime(h.getSubmitTime());
            List<PatrolCheck> checks = checkRepository.findByHandoverIdOrderBySeqAsc(h.getId());
            dto.setWalkOrder(buildWalkOrder(checks));
            Map<Long, PatrolCheck> checkedRoom = checks.stream()
                    .collect(Collectors.toMap(PatrolCheck::getRoomId, c -> c, (a, b) -> a));
            expectedDTO.forEach(item -> item.setChecked(checkedRoom.containsKey(item.getRoomId())));
            return dto;
        }

        List<PatrolHandover> history = handoverRepository.findByWindowIdAndShipIdOrderByIdAsc(window.getId(), ship.getId());
        PatrolHandover lastBlocked = null;
        for (PatrolHandover h : history) {
            if (BLOCKED.equals(h.getStatus())) {
                lastBlocked = h;
            }
        }

        // 没有 HANDED：窗口外即锁死（不能再补勾/改交班）；窗口内仍可纠正后重交
        boolean outsideWindow = "LOCKED".equals(windowState) || "CLOSED".equals(windowState);
        if (outsideWindow || "NOT_STARTED".equals(windowState)) {
            dto.setHandoverState(outsideWindow ? "LOCKED" : "NOT_HANDED");
        } else {
            dto.setHandoverState(lastBlocked != null ? BLOCKED : "NOT_HANDED");
        }
        if (lastBlocked != null) {
            dto.setHandoverBatch(lastBlocked.getHandoverBatch());
            dto.setLeaderName(lastBlocked.getLeaderName());
            // 即使窗口已锁，也保留上一次退回时卡住的房间与窗口，方便对账
            dto.setBlocked(toBlockedDTO(lastBlocked, window, ship));
        }
        return dto;
    }

    @Transactional(readOnly = true)
    public List<PatrolHandoverRecordDTO> listRecords(Long windowId, Long shipId) {
        List<PatrolHandover> records;
        if (windowId != null && shipId != null) {
            records = handoverRepository.findByWindowIdAndShipIdOrderByIdAsc(windowId, shipId);
        } else if (windowId != null) {
            records = handoverRepository.findByWindowIdOrderByIdAsc(windowId);
        } else if (shipId != null) {
            records = handoverRepository.findByShipIdOrderByIdDesc(shipId);
        } else {
            records = handoverRepository.findAll();
        }
        return records.stream()
                .sorted(Comparator.comparing(PatrolHandover::getId).reversed())
                .map(h -> toRecordDTO(h, HANDED.equals(h.getStatus())
                        ? checkRepository.findByHandoverIdOrderBySeqAsc(h.getId())
                        : List.of()))
                .collect(Collectors.toList());
    }

    // ============================ 窗口 / 值班长维护 ============================

    @Transactional
    public PatrolWindowDTO createWindow(PatrolWindowDTO dto) {
        if (dto.getWindowCode() == null || dto.getWindowCode().isBlank()) {
            throw new IllegalArgumentException("窗口编号不能为空");
        }
        if (dto.getShipId() == null) {
            throw new IllegalArgumentException("窗口必须挂靠一条船");
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null) {
            throw new IllegalArgumentException("接班窗口起止时间不能为空");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new IllegalArgumentException("接班窗口截止时间必须晚于开始时间");
        }
        if (windowRepository.existsByWindowCode(dto.getWindowCode())) {
            throw new IllegalArgumentException("窗口编号已存在");
        }
        Ship ship = shipRepository.findById(dto.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在"));

        PatrolWindow window = new PatrolWindow();
        window.setWindowCode(dto.getWindowCode());
        window.setWindowName(dto.getWindowName());
        window.setShipId(ship.getId());
        window.setShipCode(ship.getShipCode());
        window.setStartTime(dto.getStartTime());
        window.setEndTime(dto.getEndTime());
        window.setStatus(ACTIVE);
        window.setOperator(dto.getOperator());
        window.setRemark(dto.getRemark());
        return toWindowDTO(windowRepository.save(window));
    }

    /** 仅允许人工提前关闭窗口；起止时间不提供任何修改入口，防止先勾完再改时间窗 */
    @Transactional
    public PatrolWindowDTO closeWindow(Long id) {
        PatrolWindow window = windowRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("接班窗口不存在"));
        window.setStatus("CLOSED");
        return toWindowDTO(windowRepository.save(window));
    }

    @Transactional(readOnly = true)
    public List<PatrolWindowDTO> listWindows(Long shipId) {
        List<PatrolWindow> windows = shipId == null
                ? windowRepository.findAll()
                : windowRepository.findByShipIdOrderByStartTimeAsc(shipId);
        return windows.stream()
                .sorted(Comparator.comparing(PatrolWindow::getStartTime).reversed())
                .map(this::toWindowDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PatrolOfficerDTO createOfficer(PatrolOfficerDTO dto) {
        if (dto.getOfficerCode() == null || dto.getOfficerCode().isBlank()) {
            throw new IllegalArgumentException("值班人员工号不能为空");
        }
        if (dto.getOfficerName() == null || dto.getOfficerName().isBlank()) {
            throw new IllegalArgumentException("值班人员姓名不能为空");
        }
        if (officerRepository.existsByOfficerCode(dto.getOfficerCode())) {
            throw new IllegalArgumentException("值班人员工号已存在");
        }
        PatrolOfficer officer = new PatrolOfficer();
        officer.setOfficerCode(dto.getOfficerCode());
        officer.setOfficerName(dto.getOfficerName());
        officer.setRole(ROLE_LEADER.equalsIgnoreCase(dto.getRole()) ? ROLE_LEADER : "MEMBER");
        officer.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? ACTIVE : dto.getStatus());
        officer.setRemark(dto.getRemark());
        return toOfficerDTO(officerRepository.save(officer));
    }

    @Transactional
    public PatrolOfficerDTO updateOfficer(Long id, PatrolOfficerDTO dto) {
        PatrolOfficer officer = officerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("值班人员不存在"));
        if (!officer.getOfficerCode().equals(dto.getOfficerCode())
                && officerRepository.existsByOfficerCode(dto.getOfficerCode())) {
            throw new IllegalArgumentException("值班人员工号已存在");
        }
        officer.setOfficerCode(dto.getOfficerCode());
        officer.setOfficerName(dto.getOfficerName());
        officer.setRole(ROLE_LEADER.equalsIgnoreCase(dto.getRole()) ? ROLE_LEADER : "MEMBER");
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            officer.setStatus(dto.getStatus());
        }
        officer.setRemark(dto.getRemark());
        return toOfficerDTO(officerRepository.save(officer));
    }

    @Transactional(readOnly = true)
    public List<PatrolOfficerDTO> listOfficers() {
        return officerRepository.findAll().stream()
                .sorted(Comparator.comparing(PatrolOfficer::getId))
                .map(this::toOfficerDTO)
                .collect(Collectors.toList());
    }

    // ============================ 私有辅助 ============================

    private void assertWindowOpen(PatrolWindow window) {
        LocalDateTime current = now();
        if ("CLOSED".equalsIgnoreCase(window.getStatus())) {
            throw new PatrolWindowLockedException(
                    "窗口 " + window.getWindowCode() + " 已人工关闭，不能再补勾或交班");
        }
        if (current.isBefore(window.getStartTime())) {
            throw new PatrolWindowLockedException(
                    "窗口 " + window.getWindowCode() + " 尚未开始（" + window.getStartTime() + "），不能提前交班");
        }
        if (!current.isBefore(window.getEndTime())) {
            throw new PatrolWindowLockedException(
                    "接班窗口 " + window.getWindowCode() + " 已在 " + window.getEndTime()
                            + " 截止，窗口外已锁，不能再补勾或改交班");
        }
    }

    private PatrolOfficer resolveLeader(PatrolHandoverDTO dto) {
        if (dto.getLeaderId() != null) {
            return officerRepository.findById(dto.getLeaderId())
                    .orElseThrow(() -> new PatrolForbiddenException("提交人不在值班名册中，无权交班"));
        }
        if (dto.getLeaderCode() != null && !dto.getLeaderCode().isBlank()) {
            return officerRepository.findByOfficerCode(dto.getLeaderCode())
                    .orElseThrow(() -> new PatrolForbiddenException("提交人 " + dto.getLeaderCode() + " 不在值班名册中，无权交班"));
        }
        throw new PatrolForbiddenException("缺少交班值班长，只有本班值班长才能交班");
    }

    private PatrolHandover saveBlockedHandover(PatrolWindow window, Ship ship, PatrolOfficer leader,
                                               PatrolHandoverDTO dto, PatrolBlockedRoomDTO blocked) {
        PatrolHandover handover = new PatrolHandover();
        handover.setHandoverBatch(newBatch());
        handover.setWindowId(window.getId());
        handover.setWindowCode(window.getWindowCode());
        handover.setShipId(ship.getId());
        handover.setShipCode(ship.getShipCode());
        handover.setShipName(ship.getShipName());
        handover.setLeaderId(leader.getId());
        handover.setLeaderCode(leader.getOfficerCode());
        handover.setLeaderName(leader.getOfficerName());
        handover.setStatus(BLOCKED);
        handover.setBlockedReason(blocked.getReason());
        handover.setRemark(dto.getRemark());
        handover.setSubmitTime(now());
        if (blocked.getRoomId() != null) {
            handover.setBlockedRoomId(blocked.getRoomId());
            handover.setBlockedRoomCode(blocked.getRoomCode());
            handover.setBlockedRoomName(blocked.getRoomName());
            handover.setBlockedFloor(blocked.getFloor());
        }
        if (blocked.getCurrentShipId() != null) {
            handover.setCurrentShipId(blocked.getCurrentShipId());
            handover.setCurrentShipCode(blocked.getCurrentShipCode());
            handover.setCurrentShipName(blocked.getCurrentShipName());
        }
        return handoverRepository.saveAndFlush(handover);
    }

    private PatrolSubmitResultDTO blockedResult(PatrolBlockedRoomDTO blocked, PatrolHandover record) {
        PatrolSubmitResultDTO result = new PatrolSubmitResultDTO();
        result.setSuccess(false);
        result.setHandoverBatch(record.getHandoverBatch());
        result.setStatus(BLOCKED);
        result.setMessage(blocked.getMessage());
        result.setBlocked(blocked);
        result.setRecord(toRecordDTO(record, List.of()));
        return result;
    }

    private PatrolBlockedRoomDTO.PatrolBlockedRoomDTOBuilder blockedBase(PatrolWindow window, Ship ship,
                                                                         String reason, LoungeRoom room) {
        return PatrolBlockedRoomDTO.builder()
                .reason(reason)
                .windowId(window.getId())
                .windowCode(window.getWindowCode())
                .shipId(ship.getId())
                .shipCode(ship.getShipCode())
                .shipName(ship.getShipName())
                .roomId(room == null ? null : room.getId())
                .roomCode(room == null ? null : room.getRoomCode())
                .roomName(room == null ? null : room.getRoomName())
                .floor(room == null ? null : room.getFloor());
    }

    private PatrolBlockedRoomDTO toBlockedDTO(PatrolHandover h, PatrolWindow window, Ship ship) {
        return PatrolBlockedRoomDTO.builder()
                .reason(h.getBlockedReason())
                .windowId(window.getId())
                .windowCode(window.getWindowCode())
                .shipId(ship.getId())
                .shipCode(ship.getShipCode())
                .shipName(ship.getShipName())
                .roomId(h.getBlockedRoomId())
                .roomCode(h.getBlockedRoomCode())
                .roomName(h.getBlockedRoomName())
                .floor(h.getBlockedFloor())
                .currentShipId(h.getCurrentShipId())
                .currentShipCode(h.getCurrentShipCode())
                .currentShipName(h.getCurrentShipName())
                .message(buildBlockedMessageFromRecord(h))
                .build();
    }

    private String buildBlockedMessageFromRecord(PatrolHandover h) {
        String where = "窗口 " + h.getWindowCode();
        String room = h.getBlockedRoomCode() == null ? ""
                : "，房间 " + floorText(h.getBlockedFloor()) + ":" + h.getBlockedRoomCode();
        String reason = switch (h.getBlockedReason() == null ? "" : h.getBlockedReason()) {
            case REASON_ROOM_DETACHED -> "该房间已不再停靠本船"
                    + (h.getCurrentShipCode() == null ? "（当前无停靠船舶）" : "（当前停靠 " + h.getCurrentShipCode() + "）");
            case REASON_MISSING_ROOM -> "该船挂靠房间未走完，存在漏房";
            case REASON_FLOOR_SKIP -> "走房未按楼层从低到高，发生跳层";
            default -> "走房顺序不合规";
        };
        return "交班整单退回：" + where + room + "，" + reason;
    }

    private PatrolHandoverRecordDTO toRecordDTO(PatrolHandover h, List<PatrolCheck> checks) {
        PatrolHandoverRecordDTO dto = new PatrolHandoverRecordDTO();
        dto.setId(h.getId());
        dto.setHandoverBatch(h.getHandoverBatch());
        dto.setWindowId(h.getWindowId());
        dto.setWindowCode(h.getWindowCode());
        dto.setShipId(h.getShipId());
        dto.setShipCode(h.getShipCode());
        dto.setShipName(h.getShipName());
        dto.setLeaderId(h.getLeaderId());
        dto.setLeaderCode(h.getLeaderCode());
        dto.setLeaderName(h.getLeaderName());
        dto.setStatus(h.getStatus());
        dto.setBlockedReason(h.getBlockedReason());
        dto.setWalkOrder(h.getWalkOrder());
        dto.setRoomCount(h.getRoomCount());
        dto.setRemark(h.getRemark());
        dto.setSubmitTime(h.getSubmitTime());
        if (BLOCKED.equals(h.getStatus())) {
            dto.setBlockedRoom(PatrolBlockedRoomDTO.builder()
                    .reason(h.getBlockedReason())
                    .windowId(h.getWindowId())
                    .windowCode(h.getWindowCode())
                    .shipId(h.getShipId())
                    .shipCode(h.getShipCode())
                    .shipName(h.getShipName())
                    .roomId(h.getBlockedRoomId())
                    .roomCode(h.getBlockedRoomCode())
                    .roomName(h.getBlockedRoomName())
                    .floor(h.getBlockedFloor())
                    .currentShipId(h.getCurrentShipId())
                    .currentShipCode(h.getCurrentShipCode())
                    .currentShipName(h.getCurrentShipName())
                    .message(buildBlockedMessageFromRecord(h))
                    .build());
        }
        dto.setChecks(checks.stream().map(this::toCheckDTO).collect(Collectors.toList()));
        return dto;
    }

    private PatrolCheckDTO toCheckDTO(PatrolCheck check) {
        PatrolCheckDTO dto = new PatrolCheckDTO();
        dto.setId(check.getId());
        dto.setHandoverId(check.getHandoverId());
        dto.setHandoverBatch(check.getHandoverBatch());
        dto.setWindowId(check.getWindowId());
        dto.setWindowCode(check.getWindowCode());
        dto.setShipId(check.getShipId());
        dto.setShipCode(check.getShipCode());
        dto.setRoomId(check.getRoomId());
        dto.setRoomCode(check.getRoomCode());
        dto.setRoomName(check.getRoomName());
        dto.setFloor(check.getFloor());
        dto.setSeq(check.getSeq());
        dto.setLeaderName(check.getLeaderName());
        dto.setCheckTime(check.getCheckTime());
        return dto;
    }

    private PatrolWindowDTO toWindowDTO(PatrolWindow window) {
        PatrolWindowDTO dto = new PatrolWindowDTO();
        dto.setId(window.getId());
        dto.setWindowCode(window.getWindowCode());
        dto.setWindowName(window.getWindowName());
        dto.setShipId(window.getShipId());
        dto.setShipCode(window.getShipCode());
        dto.setStartTime(window.getStartTime());
        dto.setEndTime(window.getEndTime());
        dto.setStatus(window.getStatus());
        dto.setOperator(window.getOperator());
        dto.setRemark(window.getRemark());
        dto.setCreateTime(window.getCreateTime());
        dto.setWindowState(windowState(window, now()));
        shipRepository.findById(window.getShipId())
                .ifPresent(ship -> dto.setShipName(ship.getShipName()));
        return dto;
    }

    private PatrolOfficerDTO toOfficerDTO(PatrolOfficer officer) {
        PatrolOfficerDTO dto = new PatrolOfficerDTO();
        dto.setId(officer.getId());
        dto.setOfficerCode(officer.getOfficerCode());
        dto.setOfficerName(officer.getOfficerName());
        dto.setRole(officer.getRole());
        dto.setStatus(officer.getStatus());
        dto.setRemark(officer.getRemark());
        return dto;
    }

    private String windowState(PatrolWindow window, LocalDateTime current) {
        if ("CLOSED".equalsIgnoreCase(window.getStatus())) {
            return "CLOSED";
        }
        if (current.isBefore(window.getStartTime())) {
            return "NOT_STARTED";
        }
        if (!current.isBefore(window.getEndTime())) {
            return "LOCKED";
        }
        return "OPEN";
    }

    private String handedUniqueKey(Long shipId, Long windowId) {
        return shipId + "#" + windowId;
    }

    private String newBatch() {
        return "PATROL-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String buildWalkOrder(List<PatrolCheck> checks) {
        return checks.stream()
                .sorted(Comparator.comparing(PatrolCheck::getSeq))
                .map(c -> floorText(c.getFloor()) + ":" + c.getRoomCode())
                .collect(Collectors.joining(" > "));
    }

    private static String floorText(String floor) {
        return floor == null || floor.isBlank() ? "未设楼层" : floor;
    }

    /** 楼层从低到高：可解析为数字的按数值比，否则按中文/字符串排；空楼层排最后 */
    static Comparator<LoungeRoom> roomFloorComparator() {
        Collator collator = Collator.getInstance(Locale.CHINA);
        Comparator<String> floorCmp = (a, b) -> {
            boolean an = a == null || a.isBlank();
            boolean bn = b == null || b.isBlank();
            if (an && bn) {
                return 0;
            }
            if (an) {
                return 1;
            }
            if (bn) {
                return -1;
            }
            Integer ai = parseLeadingInt(a);
            Integer bi = parseLeadingInt(b);
            if (ai != null && bi != null) {
                int cmp = Integer.compare(ai, bi);
                return cmp != 0 ? cmp : collator.compare(a, b);
            }
            if (ai != null) {
                return -1;
            }
            if (bi != null) {
                return 1;
            }
            return collator.compare(a, b);
        };
        return Comparator.comparing(LoungeRoom::getFloor, floorCmp)
                .thenComparing(LoungeRoom::getRoomCode, collator);
    }

    private static int compareFloor(String a, String b) {
        return roomFloorComparator()
                .compare(roomWithFloor(a), roomWithFloor(b));
    }

    private static LoungeRoom roomWithFloor(String floor) {
        LoungeRoom room = new LoungeRoom();
        room.setFloor(floor);
        room.setRoomCode("");
        return room;
    }

    private static Integer parseLeadingInt(String text) {
        int i = 0;
        while (i < text.length() && Character.isDigit(text.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return null;
        }
        try {
            return Integer.parseInt(text.substring(0, i));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
