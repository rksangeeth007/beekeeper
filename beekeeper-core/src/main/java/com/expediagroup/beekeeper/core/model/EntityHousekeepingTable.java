package com.expediagroup.beekeeper.core.model;

import java.time.Duration;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.hibernate.annotations.UpdateTimestamp;

import com.expediagroup.beekeeper.core.error.BeekeeperException;
import com.expediagroup.beekeeper.core.monitoring.MetricTag;

@Entity
@Table(name = "table")
public class EntityHousekeepingTable implements Housekeeping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "database_name", nullable = false)
  private String databaseName;

  @Column(name = "table_name", nullable = false)
  private String tableName;

  @Column(name = "path_status", nullable = false)
  @Enumerated(EnumType.STRING)
  private HousekeepingStatus housekeepingStatus;

  @Column(name = "creation_timestamp", nullable = false, updatable = false)
  private LocalDateTime creationTimestamp;

  @Column(name = "modified_timestamp")
  @UpdateTimestamp
  private LocalDateTime modifiedTimestamp;

  @Column(name = "cleanup_timestamp", nullable = false)
  private LocalDateTime cleanupTimestamp;

  @Column(name = "cleanup_delay", nullable = false)
  @Convert(converter = DurationConverter.class)
  private Duration cleanupDelay;

  @Column(name = "cleanup_attempts", nullable = false)
  private int cleanupAttempts;

  @Column(name = "client_id")
  private String clientId;
  @Column(name = "lifecycle_type", nullable = false)
  private String lifecycleType;

  private EntityHousekeepingTable(Long id, String databaseName, String tableName, HousekeepingStatus housekeepingStatus,
      LocalDateTime creationTimestamp, LocalDateTime modifiedTimestamp,
      LocalDateTime cleanupTimestamp, Duration cleanupDelay, int cleanupAttempts, String lifecycleType,
      String clientId) {
    this.id = id;
    this.databaseName = databaseName;
    this.tableName = tableName;
    this.housekeepingStatus = housekeepingStatus;
    this.creationTimestamp = creationTimestamp;
    this.modifiedTimestamp = modifiedTimestamp;
    this.cleanupTimestamp = cleanupTimestamp;
    this.cleanupDelay = cleanupDelay;
    this.cleanupAttempts = cleanupAttempts;
    this.lifecycleType = lifecycleType;
    this.clientId = clientId;
  }

  @Override
  public String getLifecycleType() {
    return lifecycleType;
  }

  @Override
  public void setLifecycleType(String lifecycleType) {
    this.lifecycleType = lifecycleType;
  }

  @Override
  public Long getId() {
    return id;
  }

  @Override
  public String getDatabaseName() {
    return databaseName;
  }

  @Override
  public void setDatabaseName(String databaseName) {
    this.databaseName = databaseName;
  }

  @Override
  public String getTableName() {
    return tableName;
  }

  @Override
  public void setTableName(String tableName) {
    this.tableName = tableName;
  }

  @Override
  public HousekeepingStatus getHousekeepingStatus() {
    return housekeepingStatus;
  }

  @Override
  public void setHousekeepingStatus(HousekeepingStatus housekeepingStatus) {
    this.housekeepingStatus = housekeepingStatus;
  }

  @Override
  public LocalDateTime getCreationTimestamp() {
    return creationTimestamp;
  }

  @Override
  public void setCreationTimestamp(LocalDateTime creationTimestamp) {
    this.creationTimestamp = creationTimestamp;
  }

  @Override
  public LocalDateTime getModifiedTimestamp() {
    return modifiedTimestamp;
  }

  @Override
  public void setModifiedTimestamp(LocalDateTime modifiedTimestamp) {
    this.modifiedTimestamp = modifiedTimestamp;
  }

  @Override
  public LocalDateTime getCleanupTimestamp() {
    return cleanupTimestamp;
  }

  @Override
  public void setCleanupTimestamp(LocalDateTime cleanupTimestamp) {
    this.cleanupTimestamp = cleanupTimestamp;
  }

  @Override
  public int getCleanupAttempts() {
    return cleanupAttempts;
  }

  @Override
  public void setCleanupAttempts(int cleanupAttempts) {
    this.cleanupAttempts = cleanupAttempts;
  }

  @Override
  public String getClientId() {
    return clientId;
  }

  @Override
  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  @Override
  public Duration getCleanupDelay() {
    return cleanupDelay;
  }

  @Override
  public void setCleanupDelay(Duration cleanupDelay) {
    this.cleanupDelay = cleanupDelay;
    cleanupTimestamp = creationTimestamp.plus(cleanupDelay);
  }

  @Override
  public MetricTag getMetricTag() {
    return new MetricTag("table", String.join(".", databaseName, tableName));
  }

  public static final class Builder {

    private Long id;
    private String databaseName;
    private String tableName;
    private HousekeepingStatus housekeepingStatus;
    private LocalDateTime creationTimestamp;
    private LocalDateTime modifiedTimestamp;
    private Duration cleanupDelay;
    private int cleanupAttempts;
    private String lifecycleType;
    private String clientId;

    public Builder() { }

    public EntityHousekeepingTable.Builder id(Long id) {
      this.id = id;
      return this;
    }

    public EntityHousekeepingTable.Builder databaseName(String databaseName) {
      this.databaseName = databaseName;
      return this;
    }

    public EntityHousekeepingTable.Builder tableName(String tableName) {
      this.tableName = tableName;
      return this;
    }

    public EntityHousekeepingTable.Builder housekeepingStatus(HousekeepingStatus housekeepingStatus) {
      this.housekeepingStatus = housekeepingStatus;
      return this;
    }

    public EntityHousekeepingTable.Builder creationTimestamp(LocalDateTime creationTimestamp) {
      this.creationTimestamp = creationTimestamp;
      return this;
    }

    public EntityHousekeepingTable.Builder modifiedTimestamp(LocalDateTime modifiedTimestamp) {
      this.modifiedTimestamp = modifiedTimestamp;
      return this;
    }

    public EntityHousekeepingTable.Builder cleanupDelay(Duration cleanupDelay) {
      this.cleanupDelay = cleanupDelay;
      return this;
    }

    public EntityHousekeepingTable.Builder cleanupAttempts(int cleanupAttempts) {
      this.cleanupAttempts = cleanupAttempts;
      return this;
    }

    public EntityHousekeepingTable.Builder lifecycleType(String lifecycleType) {
      this.lifecycleType = lifecycleType;
      return this;
    }

    public EntityHousekeepingTable.Builder clientId(String clientId) {
      this.clientId = clientId;
      return this;
    }

    public EntityHousekeepingTable build() {
      LocalDateTime cleanupTimestamp = configureCleanupTimestamp();

      return new EntityHousekeepingTable(id, databaseName, tableName, housekeepingStatus, creationTimestamp,
          modifiedTimestamp, cleanupTimestamp, cleanupDelay, cleanupAttempts, lifecycleType, clientId);
    }

    private LocalDateTime configureCleanupTimestamp() {
      if (creationTimestamp == null) {
        throw new BeekeeperException("Table requires a creation timestamp");
      }
      if (cleanupDelay == null) {
        throw new BeekeeperException("Table requires a cleanup delay");
      }
      return creationTimestamp.plus(cleanupDelay);
    }
  }
}