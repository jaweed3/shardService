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

  private Long hash(String s) {
    try {
      var md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(s.getBytes());
      long h = 0;
      for (int i = 0; i < 8; i++) {
        h = (h << 8) | (digest[i] & 0xFF);
      }
      return h;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
