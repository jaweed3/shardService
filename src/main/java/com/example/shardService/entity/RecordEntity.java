package com.example.shardService.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Record
 */
@Entity
@Table(name = "records")
public class RecordEntity {

  @Id
  @Column(name = "record_key")
  private String key;

  @Column(name = "record_value")
  private String value;

  public RecordEntity() {
  }

  public RecordEntity(String key, String value) {
    this.key = key;
    this.value = value;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
