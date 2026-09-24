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
