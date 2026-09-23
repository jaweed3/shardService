package com.example.shardService.router;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.example.shardService.entity.RecordEntity;
import com.example.shardService.repository.shard0.RecordRepositoryShard0;
import com.example.shardService.repository.shard1.RecordRepositoryShard1;

@Component
/**
 * ShardRouter
 */
public class ShardRouter {

  private final RecordRepositoryShard0 shard0;
  private final RecordRepositoryShard1 shard1;

  public ShardRouter(RecordRepositoryShard0 shard0, RecordRepositoryShard1 shard1) {
    this.shard0 = shard0;
    this.shard1 = shard1;
  }

  public JpaRepository<RecordEntity, String> route(String key) {
    int idx = Math.abs(key.hashCode()) % 2;
    return idx == 0 ? shard0 : shard1;
  }
}
