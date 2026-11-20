/*
 * Copyright (c) 2026 adesso SE. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.constructx.edc.policyhub.service;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyHubSyncSchedulerTest {

    private static final Duration INTERVAL = Duration.ofSeconds(3600);
    private static final Duration OUTAGE_INTERVAL = Duration.ofSeconds(300);
    private static final Instant NOW = Instant.parse("2026-11-05T10:15:00Z");

    private static final PolicyHubSyncService.SyncSummary COMPLETED =
            new PolicyHubSyncService.SyncSummary(2, 0, 2, 0, null);
    private static final PolicyHubSyncService.SyncSummary ABORTED =
            new PolicyHubSyncService.SyncSummary(0, 0, 0, 0, new PolicyHubSyncService.Abort(PolicyHubSyncService.AbortCause.UNREACHABLE, "Connection refused"));

    private final PolicyHubSyncService syncService = mock();
    private final ScheduledExecutorService executor = mock();
    private final Monitor monitor = mock();

    private final PolicyHubSyncScheduler scheduler = new PolicyHubSyncScheduler(syncService, executor, INTERVAL,
            OUTAGE_INTERVAL, Clock.fixed(NOW, ZoneOffset.UTC), monitor);

    @Test
    void start_shouldScheduleFirstRunAfterInitialDelay() {
        scheduler.start(Duration.ofSeconds(42));

        verify(executor).schedule(any(Runnable.class), eq(42L), eq(TimeUnit.SECONDS));
    }

    @Test
    void run_whenRunCompleted_shouldScheduleNextRunAfterInterval() {
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenReturn(COMPLETED);

        scheduler.run();

        verify(executor).schedule(any(Runnable.class), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void run_whenRunAborted_shouldScheduleNextRunAfterOutageInterval() {
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenReturn(ABORTED);

        scheduler.run();
        scheduler.run();

        verify(executor, times(2)).schedule(any(Runnable.class), eq(300L), eq(TimeUnit.SECONDS));
        verify(monitor).warning("Next Policy Hub sync in 300 s (1 aborted run in a row since 2026-11-05T10:15:00Z, last cause: unreachable (Connection refused))");
        verify(monitor).warning("Next Policy Hub sync in 300 s (2 aborted runs in a row since 2026-11-05T10:15:00Z, last cause: unreachable (Connection refused))");
    }

    @Test
    void run_whenHubRejectsCredentials_shouldScheduleNextRunAfterOutageIntervalAndNameTheCause() {
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenReturn(new PolicyHubSyncService.SyncSummary(0, 0, 0, 0,
                new PolicyHubSyncService.Abort(PolicyHubSyncService.AbortCause.ACCESS_DENIED, "status 401")));

        scheduler.run();

        verify(executor).schedule(any(Runnable.class), eq(300L), eq(TimeUnit.SECONDS));
        verify(monitor).warning("Next Policy Hub sync in 300 s (1 aborted run in a row since 2026-11-05T10:15:00Z, last cause: access denied (status 401))");
    }

    @Test
    void run_whenHubReachableAgain_shouldReturnToRegularInterval() {
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenReturn(ABORTED, ABORTED, COMPLETED);

        scheduler.run();
        scheduler.run();
        scheduler.run();

        verify(monitor).info("Policy Hub sync completed again after 2 aborted runs, back to the regular interval of 3600 s");
        verify(executor).schedule(any(Runnable.class), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void run_whenSyncThrows_shouldStillScheduleNextRun() {
        var failure = new IllegalStateException("database down");
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenThrow(failure);

        scheduler.run();

        verify(monitor).severe("Policy Hub sync failed", failure);
        verify(executor).schedule(any(Runnable.class), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    void run_whenExecutorShutDown_shouldNotFail() {
        when(syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED)).thenReturn(COMPLETED);
        when(executor.schedule(any(Runnable.class), anyLong(), any())).thenThrow(new RejectedExecutionException());

        assertThatNoException().isThrownBy(scheduler::run);
    }
}
