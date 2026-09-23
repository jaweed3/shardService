package com.example.shardService.repository.shard1;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.shardService.entity.RecordEntity;

/**
 * RecordRepositoryShard1
 */
public interface RecordRepositoryShard1 extends JpaRepository<RecordEntity, String> {

}
