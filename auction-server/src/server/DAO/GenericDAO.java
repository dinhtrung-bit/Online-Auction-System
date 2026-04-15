package src.server.DAO;

import java.util.List;

public interface GenericDAO<T> {
    void insert(T obj) throws Exception;

    void update(T obj) throws Exception;

    void delete(int id) throws Exception;

    List<T> findAll() throws Exception;
}