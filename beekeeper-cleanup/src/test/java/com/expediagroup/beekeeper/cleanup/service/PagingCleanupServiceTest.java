/**
 * Copyright (C) 2019-2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.expediagroup.beekeeper.cleanup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.expediagroup.beekeeper.cleanup.TestApplication;
import com.expediagroup.beekeeper.cleanup.handler.UnreferencedHandler;
import com.expediagroup.beekeeper.cleanup.path.PathCleaner;
import com.expediagroup.beekeeper.core.model.EntityHousekeepingPath;
import com.expediagroup.beekeeper.core.model.LifecycleEventType;
import com.expediagroup.beekeeper.core.model.PathStatus;
import com.expediagroup.beekeeper.core.repository.HousekeepingPathRepository;

@ExtendWith(SpringExtension.class)
@ExtendWith(MockitoExtension.class)
@TestPropertySource(properties = {
    "hibernate.data-source.driver-class-name=org.h2.Driver",
    "hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "hibernate.hbm2ddl.auto=create",
    "spring.datasource.url=jdbc:h2:mem:beekeeper;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL" })
@ContextConfiguration(classes = { TestApplication.class },
    loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class PagingCleanupServiceTest {

  private final LocalDateTime localNow = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
  private PagingCleanupService pagingCleanupService;
  private @Captor ArgumentCaptor<EntityHousekeepingPath> pathCaptor;
  private @Autowired HousekeepingPathRepository housekeepingPathRepository;
  private @MockBean PathCleaner pathCleaner;

  @Test
  public void typicalWithPaging() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 2, false);

    List<String> paths = List.of("s3://some_foo", "s3://some_bar", "s3://some_foobar");
    paths.forEach(path -> housekeepingPathRepository.save(createEntityHousekeepingPath(path, PathStatus.SCHEDULED)));
    pagingCleanupService.cleanUp(Instant.now());

    verify(pathCleaner, times(3)).cleanupPath(pathCaptor.capture());
    assertThat(pathCaptor.getAllValues())
        .extracting("path")
        .containsExactly(paths.get(0), paths.get(1), paths.get(2));

    housekeepingPathRepository.findAll().forEach(housekeepingPath -> {
      assertThat(housekeepingPath.getCleanupAttempts()).isEqualTo(1);
      assertThat(housekeepingPath.getPathStatus()).isEqualTo(PathStatus.DELETED);
    });

    pagingCleanupService.cleanUp(Instant.now());
    verifyNoMoreInteractions(pathCleaner);
  }

  @Test
  public void mixOfScheduledAndFailedPaths() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 2, false);
    List<EntityHousekeepingPath> paths = List.of(
        createEntityHousekeepingPath("s3://some_foo", PathStatus.SCHEDULED),
        createEntityHousekeepingPath("s3://some_bar", PathStatus.FAILED)
    );
    paths.forEach(path -> housekeepingPathRepository.save(path));
    pagingCleanupService.cleanUp(Instant.now());

    verify(pathCleaner, times(2)).cleanupPath(pathCaptor.capture());
    assertThat(pathCaptor.getAllValues())
        .extracting("path")
        .containsExactly(paths.get(0).getPath(), paths.get(1).getPath());
  }

  @Test
  public void mixOfAllPaths() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 2, false);
    List<EntityHousekeepingPath> paths = List.of(
        createEntityHousekeepingPath("s3://some_foo", PathStatus.SCHEDULED),
        createEntityHousekeepingPath("s3://some_bar", PathStatus.FAILED),
        createEntityHousekeepingPath("s3://some_foobar", PathStatus.DELETED)
    );
    paths.forEach(path -> housekeepingPathRepository.save(path));
    pagingCleanupService.cleanUp(Instant.now());

    verify(pathCleaner, times(2)).cleanupPath(pathCaptor.capture());
    assertThat(pathCaptor.getAllValues())
        .extracting("path")
        .containsExactly(paths.get(0).getPath(), paths.get(1).getPath());
  }

  @Test
  void pathCleanerException() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 2, false);

    doThrow(new RuntimeException("Error"))
        .doNothing()
        .when(pathCleaner)
        .cleanupPath(any(EntityHousekeepingPath.class));

    List<String> paths = List.of("s3://some_foo", "s3://some_bar");
    paths.forEach(path -> housekeepingPathRepository.save(createEntityHousekeepingPath(path, PathStatus.SCHEDULED)));
    pagingCleanupService.cleanUp(Instant.now());

    verify(pathCleaner, times(2)).cleanupPath(pathCaptor.capture());
    assertThat(pathCaptor.getAllValues())
        .extracting("path")
        .containsExactly(paths.get(0), paths.get(1));

    List<EntityHousekeepingPath> result = housekeepingPathRepository.findAll();
    assertThat(result.size()).isEqualTo(2);
    EntityHousekeepingPath housekeepingPath1 = result.get(0);
    EntityHousekeepingPath housekeepingPath2 = result.get(1);

    assertThat(housekeepingPath1.getPath()).isEqualTo(paths.get(0));
    assertThat(housekeepingPath1.getPathStatus()).isEqualTo(PathStatus.FAILED);
    assertThat(housekeepingPath1.getCleanupAttempts()).isEqualTo(1);
    assertThat(housekeepingPath2.getPath()).isEqualTo(paths.get(1));
    assertThat(housekeepingPath2.getPathStatus()).isEqualTo(PathStatus.DELETED);
    assertThat(housekeepingPath2.getCleanupAttempts()).isEqualTo(1);
  }

  @Test
  @Timeout(value = 10)
  void doNotInfiniteLoopOnRepeatedFailures() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 1, false);
    List<EntityHousekeepingPath> paths = List.of(
        createEntityHousekeepingPath("s3://some_foo", PathStatus.FAILED),
        createEntityHousekeepingPath("s3://some_bar", PathStatus.FAILED),
        createEntityHousekeepingPath("s3://some_foobar", PathStatus.FAILED)
    );

    for (int i = 0; i < 5; i++) {
      int finalI = i;
      paths.forEach(path -> {
        if (finalI == 0) {
          housekeepingPathRepository.save(path);
        }

        doThrow(new RuntimeException("Error"))
            .when(pathCleaner)
            .cleanupPath(any());
      });

      pagingCleanupService.cleanUp(Instant.now());
      housekeepingPathRepository.findAll().forEach(path -> {
        assertThat(path.getCleanupAttempts()).isEqualTo(finalI + 1);
        assertThat(path.getPathStatus()).isEqualTo(PathStatus.FAILED);
      });
    }
  }

  @Test
  @Timeout(value = 10)
  void doNotInfiniteLoopOnDryRunCleanup() {
    UnreferencedHandler handler = new UnreferencedHandler(housekeepingPathRepository, pathCleaner);
    pagingCleanupService = new PagingCleanupService(List.of(handler), 1, true);
    List<EntityHousekeepingPath> paths = List.of(
        createEntityHousekeepingPath("s3://some_foo", PathStatus.SCHEDULED),
        createEntityHousekeepingPath("s3://some_bar", PathStatus.SCHEDULED),
        createEntityHousekeepingPath("s3://some_foobar", PathStatus.SCHEDULED)
    );
    housekeepingPathRepository.saveAll(paths);

    pagingCleanupService.cleanUp(Instant.now());

    housekeepingPathRepository.findAll().forEach(path -> {
      assertThat(path.getCleanupAttempts()).isEqualTo(0);
      assertThat(path.getPathStatus()).isEqualTo(PathStatus.SCHEDULED);
    });
  }

  private EntityHousekeepingPath createEntityHousekeepingPath(String path, PathStatus pathStatus) {
    EntityHousekeepingPath housekeepingPath = new EntityHousekeepingPath.Builder()
        .path(path)
        .databaseName("database")
        .tableName("table")
        .pathStatus(pathStatus)
        .creationTimestamp(localNow)
        .modifiedTimestamp(localNow)
        .modifiedTimestamp(localNow)
        .cleanupDelay(Duration.parse("P3D"))
        .cleanupAttempts(0)
        .lifecycleType(LifecycleEventType.UNREFERENCED.toString())
        .build();
    housekeepingPath.setCleanupTimestamp(localNow);
    return housekeepingPath;
  }
}
