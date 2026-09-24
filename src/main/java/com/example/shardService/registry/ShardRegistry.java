package com.example.shardService.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ShardRegistry {

    private static final long TIMEOUT_SECONDS = 10;

    private final Map<String, ShardNode> nodes = new ConcurrentHashMap<>();

    public void register(String nodeId) {
        nodes.put(nodeId, new ShardNode(nodeId));
    }

    public void deregister(String nodeId) {
        nodes.remove(nodeId);
    }

    public void heartbeat(String nodeId) {
        ShardNode node = nodes.get(nodeId);
        if (node != null) {
            node.heartBeat();
        }
    }

    public List<String> getActiveNodes() {
        return nodes.values().stream()
                .filter(n -> n.isAlive(TIMEOUT_SECONDS))
                .map(ShardNode::getId)
                .sorted()
                .toList();
    }

    public Map<String, Boolean> status() {
        var out = new LinkedHashMap<String, Boolean>();
        for (ShardNode n : nodes.values()) {
            out.put(n.getId(), n.isAlive(TIMEOUT_SECONDS));
        }
        return out;
    }
}
