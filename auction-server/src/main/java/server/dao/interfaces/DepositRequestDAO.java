package server.dao.interfaces;

import server.models.finance.DepositRequest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

public interface DepositRequestDAO {
    DepositRequest create(int userId, BigDecimal amount, String note) throws Exception;

    DepositRequest findById(int id) throws Exception;

    List<DepositRequest> findAll() throws Exception;

    List<DepositRequest> findPending() throws Exception;

    List<DepositRequest> findByUserId(int userId) throws Exception;

    boolean markReviewed(Connection conn, int requestId, int adminId, String status, String adminNote) throws Exception;

    int countPending() throws Exception;

    BigDecimal sumByStatus(String status) throws Exception;
}