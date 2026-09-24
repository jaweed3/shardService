package com.example.shardService.router;

import java.util.Map;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.example.shardService.entity.RecordEntity;
import com.example.shardService.hash.HashRing;
import com.example.shardService.repository.shard0.RecordRepositoryShard0;
import com.example.shardService.repository.shard1.RecordRepositoryShard1;

@Component
/**
 * ShardRouter
 */
public class ShardRouter {

  private final HashRing ring;
  private final Map<String, JpaRepository<RecordEntity, String>> repos;

  public ShardRouter(
      HashRing ring,
      RecordRepositoryShard0 shard0,
      RecordRepositoryShard1 shard1) {
    this.ring = ring;
    this.repos = Map.of(
        "shard0", shard0,
        "shard1", shard1);

    ring.addNode("shard0");
    ring.addNode("shard1");
  }

  public JpaRepository<RecordEntity, String> route(String key) {
    int idx = Math.abs(key.hashCode()) % 2;
    return idx == 0 ? shard0 : shard1;
  }

  public Long countShard0() {
    return shard0.count();
  }

  public Long countShard1() {
    return shard1.count();
  }
}
