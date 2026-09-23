package com.example.shardService.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.shardService.repository.RecordRepository;

import com.example.shardService.entity.RecordEntity;

/**
 * RecordController
 */
@RestController
public class RecordController {

  private RecordRepository repository;

  RecordController(RecordRepository repository, Record record) {
    this.repository = repository;
  }

  @GetMapping("/records/{key}")
  RecordEntity getByID(@PathVariable String key) {
    return repository.findById(key).orElse(null);
  }

  @PutMapping("/records/{key}")
  RecordEntity update(@PathVariable String key, @RequestBody String value) {
    return repository.save(new RecordEntity(key, value));
  }

  @DeleteMapping("/records/{key}")
  ResponseEntity<?> deleteById(@PathVariable String key) {
    if (!repository.existsById(key)) {
      return ResponseEntity.notFound().build();
    }
    repository.deleteById(key);
    return ResponseEntity.noContent().build();
  }

}
