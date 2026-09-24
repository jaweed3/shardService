package com.example.shardService.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shardService.entity.RecordEntity;
import com.example.shardService.router.ShardRouter;

/**
 * RecordController
 */
@RestController
@RequestMapping("/records")
public class RecordController {

  private final ShardRouter router;

  RecordController(ShardRouter router) {
    this.router = router;
  }

  @GetMapping("/{key}")
  ResponseEntity<RecordEntity> get(@PathVariable String key) {
    return router.route(key).findById(key)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PutMapping("/{key}")
  ResponseEntity<RecordEntity> put(@PathVariable String key, @RequestBody String value) {
    RecordEntity saved = router.route(key).save(new RecordEntity(key, value));
    return ResponseEntity.ok(saved);
  }

  @DeleteMapping("/{key}")
  ResponseEntity<?> delete(@PathVariable String key) {
    var repo = router.route(key);
    if (!repo.existsById(key)) {
      return ResponseEntity.notFound().build();
    }
    repo.deleteById(key);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/debug/count")
  Map<String, Long> count() {
    return router.countPerShard();
  }
}
