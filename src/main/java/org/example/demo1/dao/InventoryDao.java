package org.example.demo1.dao;

import org.example.demo1.model.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryDao extends JpaRepository<InventoryEntity, Long> {

    Optional<InventoryEntity> findBySku(String sku);
}
