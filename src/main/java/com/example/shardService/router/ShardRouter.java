package com.example.shardService.router;

import java.util.Map;

import com.example.shardService.entity.RecordEntity;
import com.example.shardService.hash.HashRing;
import com.example.shardService.registry.ShardRegistry;
import com.example.shardService.repository.shard0.RecordRepositoryShard0;
import com.example.shardService.repository.shard1.RecordRepositoryShard1;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

@Component
/**
 * ShardRouter
 */
public class ShardRouter {

    private static final int VNODES = 150;

    private final ShardRegistry registry;
    private final HashRing ring;
    private final Map<String, JpaRepository<RecordEntity, String>> repos;

    public ShardRouter(
            ShardRegistry registry,
            HashRing ring,
            RecordRepositoryShard0 shard0,
            RecordRepositoryShard1 shard1) {
        this.registry = registry;
        this.ring = ring;
        this.repos = Map.of(
                "shard0", shard0,
                "shard1", shard1);

        registry.register("shard0");
        registry.register("shard1");

        rebuildRing();
    }

    public synchronized void rebuildRing() {
        ring.clear();
        for (String node : registry.getActiveNodes()) {
            ring.addNode(node, VNODES);
        }
    }

    public JpaRepository<RecordEntity, String> route(String key) {
        String node = ring.getNode(key);
        return repos.get(node);
    }

    public Map<String, Long> countPerShard() {
        return Map.of(
                "shard0", repos.get("shard0").count(),
                "shard1", repos.get("shard1").count());
    }
}
