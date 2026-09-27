package com.example.shardService.rebalance;

import java.util.List;
import java.util.Map;

import com.example.shardService.common.HashRing;
import com.example.shardService.repository.BaseRecordRepository;

import org.springframework.stereotype.Component;

@Component
public class Rebalancer {

    public void rebalance(
            HashRing oldRing,
            HashRing newRing,
            Map<String, BaseRecordRepository> repos,
            Map<String, List<String>> keysByNode) {
        for (var entry : keysByNode.entrySet()) {
            String oldNode = entry.getKey();
            for (String key : entry.getValue()) {
                String newNode = newRing.getNode(key);
                if (!newNode.equals(oldNode)) {
                    move(key, oldNode, newNode, repos);
                }
            }
        }
    }

    /**
     * move key from shard `from` ke shart `to`.
     * trade off: at-most-once, idempotent via existsById check.
     */
    private void move(
            String key,
            String from,
            String to,
            Map<String, BaseRecordRepository> repos) {
        var fromRepo = repos.get(from);
        var toRepo = repos.get(to);

        fromRepo.findById(key).ifPresent(entity -> {
            if (!toRepo.existsById(key)) {
                toRepo.save(entity);
            }
            fromRepo.deleteById(key);
        });
    }
}
