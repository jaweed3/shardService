package com.example.shardService.hash;

import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

@Component
/**
 * HashRing
 */
public class HashRing {

  private final TreeMap<Long, String> ring = new TreeMap<>();

  public void addNode(String node) {
    long hash = hash(node);
    ring.put(hash, node);
  }

  public void removeNode(String node) {
    ring.remove(hash(node));
  }

  public String getNode(String key) {
    if (ring.isEmpty()) {
      throw new IllegalStateException("ring is empty!");
    }

    long hash = hash(key);

    Map.Entry<Long, String> entry = ring.ceilingEntry(hash);

    if (entry == null) {
      entry = ring.firstEntry();
    }

    return entry.getValue();
  }

