package com.example.shardService.rebalance;

import java.util.List;
import java.util.Map;

import com.example.shardService.hash.HashRing;
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

    private void move(
            String key,
            String from,
            String to,
            Map<String, BaseRecordRepository> repos) {
        var fromRepo = repos.get(from);
        var toRepo = repos.get(to);

        fromRepo.findById(key).ifPresent(entity -> {
            toRepo.save(entity);
            fromRepo.deleteById(key);
        });
    }
}
