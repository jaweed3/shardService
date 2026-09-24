package com.example.shardService.registry;

import java.time.Instant;

/**
 * ShardNode
 */
public class ShardNode {

    private final String id;
    private Instant lastHeartBeat;

    public ShardNode(String id) {
        this.id = id;
        this.lastHeartBeat = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Instant getLastHeartBeat() {
        return lastHeartBeat;
    }

    public void heartBeat() {
        this.lastHeartBeat = Instant.now();
    }

    public boolean isAlive(long timeoutSeconds) {
        return lastHeartBeat.plusSeconds(timeoutSeconds).isAfter(Instant.now());
    }
}
