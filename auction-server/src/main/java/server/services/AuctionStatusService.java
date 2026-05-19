package server.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import server.dao.interfaces.AuctionRoomDAO;
import server.models.auction.AuctionRoom;
import server.models.auction.AuctionStatus;

/**
 * Chỉ phụ trách cập nhật trạng thái phiên theo thời gian.
 * Logic OPEN -> RUNNING, RUNNING -> settlement và remove khỏi RAM giữ nguyên code cũ.
 */
public class AuctionStatusService {

    private static final int ROOM_REMOVE_DELAY_MS = 30_000;

    private final ConcurrentHashMap<Long, AuctionRoom> activeRooms;
    private final AuctionRoomDAO roomDAO;
    private final AuctionSettlementService settlementService;
    private final AuctionNotificationService notificationService;

    public AuctionStatusService(
            ConcurrentHashMap<Long, AuctionRoom> activeRooms,
            AuctionRoomDAO roomDAO,
            AuctionSettlementService settlementService,
            AuctionNotificationService notificationService) {
        this.activeRooms = activeRooms;
        this.roomDAO = roomDAO;
        this.settlementService = settlementService;
        this.notificationService = notificationService;
    }

    public void autoUpdateStatuses() {
        LocalDateTime now = LocalDateTime.now();
        for (AuctionRoom room : activeRooms.values()) {
            synchronized (room) {
                try {
                    processRoomStatusTick(room, now);
                } catch (Exception e) {
                    System.err.println(">>> [Auction Status Error] " + e.getMessage());
                }
            }
        }
    }

    private void processRoomStatusTick(AuctionRoom room, LocalDateTime now) {
        if (room.getStatus() == AuctionStatus.FINISHED
                || room.getStatus() == AuctionStatus.PAID
                || room.getStatus() == AuctionStatus.CANCELED) {
            return;
        }

        if (room.getStatus() == AuctionStatus.OPEN && !now.isBefore(room.getStarttime())) {
            room.setStatus(AuctionStatus.RUNNING);
            updateRoomInDb(room);
            notificationService.broadcast("AUCTION_STARTED", String.valueOf(room.getId()));
            System.out.println(">>> [Auction] Room " + room.getId() + " START.");
        }

        if (room.getStatus() == AuctionStatus.RUNNING && room.isExpired()) {
            settlementService.processAuctionSettlement(room);
            updateRoomInDb(room);
            notificationService.broadcastToRoom(room.getId(), "AUCTION_FINISHED", String.valueOf(room.getId()));
            scheduleRemoveRoom(room.getId());
        }
    }

    private void updateRoomInDb(AuctionRoom room) {
        BigDecimal oldPrice = room.getCurrentPrice();
        try {
            roomDAO.updateWithOptimisticLock(room, oldPrice);
        } catch (Exception e) {
            try {
                roomDAO.update(room);
            } catch (Exception ignored) {
                // bỏ qua — không thể update bằng cả 2 cách
            }
            System.err.println(">>> [DB Update Room Error] " + e.getMessage());
        }
    }

    private void scheduleRemoveRoom(long roomId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(ROOM_REMOVE_DELAY_MS);
                activeRooms.remove(roomId);
                System.out.println(">>> [Auction] Đã xóa Room " + roomId + " khỏi RAM.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}