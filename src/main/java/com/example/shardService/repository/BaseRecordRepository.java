package com.example.shardService.repository;

import java.util.List;

import com.example.shardService.entity.RecordEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BaseRecordRepository extends JpaRepository<RecordEntity, String> {

    @Query("SELECT r.key FROM RecordEntity r")
    List<String> findAllKeys();
}
