package com.example.shardService.router;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.shardService.common.HashRing;
import com.example.shardService.entity.RecordEntity;
import com.example.shardService.rebalance.Rebalancer;
import com.example.shardService.registry.ShardRegistry;
import com.example.shardService.repository.BaseRecordRepository;
import com.example.shardService.repository.shard0.RecordRepositoryShard0;
import com.example.shardService.repository.shard1.RecordRepositoryShard1;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
/**
 * ShardRouter
 */
public class ShardRouter {

    private static final int VNODES = 150;

    private final ShardRegistry registry;
    private final Rebalancer rebalancer;
    private final Map<String, BaseRecordRepository> repos;

    private volatile HashRing ring = new HashRing();
    private volatile List<String> lastKnownNodes = List.of();

    public ShardRouter(
            ShardRegistry registry,
            Rebalancer rebalancer,
            RecordRepositoryShard0 shard0,
            RecordRepositoryShard1 shard1) {
        this.registry = registry;
        this.rebalancer = rebalancer;
        this.repos = Map.of(
                "shard0", shard0,
                "shard1", shard1);

        registry.register("shard0");
        registry.register("shard1");

        syncRing();
    }

    @Scheduled(fixedDelay = 5000)
    public synchronized void syncRing() {
        registry.register("shard0");
        registry.register("shard1");

        List<String> current = registry.getActiveNodes();

        if (current.equals(lastKnownNodes)) {
            return;
        }

        System.out.println("ring changing : " + lastKnownNodes + " -> " + current);

        HashRing newRing = new HashRing();
        for (String node : current) {
            newRing.addNode(node, VNODES);
        }

        Map<String, List<String>> keysByNode = new HashMap<>();
        for (String node : lastKnownNodes) {
            if (!current.contains(node)) {
                var repo = repos.get(node);

                if (repo == null)
                    continue;

                List<String> keysToMove = new ArrayList<>();
                for (String key : repo.findAllKeys()) {
                    String newOwner = newRing.getNode(key);
                    if (!newOwner.equals(node)) {
                        keysToMove.add(key);
                    }
                }
                if (!keysToMove.isEmpty()) {
                    keysByNode.put(node, keysToMove);
                }
            }
        }

        rebalancer.rebalance(ring, newRing, repos, keysByNode);
        this.ring = newRing;
        this.lastKnownNodes = current;

        System.out.println("ring rebuilt : " + current);
    }

    public JpaRepository<RecordEntity, String> route(String key) {
        String node = ring.getNode(key);
        return repos.get(node);
    }

    public Map<String, Long> countPerShard() {
        Map<String, Long> out = new HashMap<>();
        for (var e : repos.entrySet()) {
            out.put(e.getKey(), e.getValue().count());
        }
        return out;
    }

    public String routeNode(String key) {
        return ring.getNode(key);
    }

    public List<String> allKeys() {
        List<String> all = new ArrayList<>();
        all.addAll(repos.get("shard0").findAllKeys());
        all.addAll(repos.get("shard1").findAllKeys());
        return all;
    }
}
