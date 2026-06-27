package org.example.demo1.dao;

import org.example.demo1.model.entity.InventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * DAO 层：负责访问 inventory 表。
 *
 * 继承 JpaRepository 后，Spring Data JPA 会在运行时自动生成实现类。
 */
@Repository
public interface InventoryDao extends JpaRepository<InventoryEntity, Long> {

    // 按 SKU 查询库存，方法名会被 Spring Data JPA 自动解析成 SQL。
    Optional<InventoryEntity> findBySku(String sku);
}
