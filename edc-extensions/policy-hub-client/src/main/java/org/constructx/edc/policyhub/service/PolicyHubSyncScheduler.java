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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the periodic Policy Hub sync. After every run it schedules the next one itself: after the
 * regular {@code interval} normally, or after the shorter {@code outageInterval} if the run was
 * aborted because the Policy Hub was unreachable or rejected the credentials. Since the next run is
 * only scheduled once the previous one has ended, runs never overlap.
 */
public class PolicyHubSyncScheduler {

    private final PolicyHubSyncService syncService;
    private final ScheduledExecutorService executor;
    private final Duration interval;
    private final Duration outageInterval;
    private final Clock clock;
    private final Monitor monitor;

    // only touched by the executor's single thread
    private int consecutiveAbortedRuns;
    private Instant abortedSince;

    public PolicyHubSyncScheduler(PolicyHubSyncService syncService, ScheduledExecutorService executor,
                                  Duration interval, Duration outageInterval, Clock clock, Monitor monitor) {
        this.syncService = syncService;
        this.executor = executor;
        this.interval = interval;
        this.outageInterval = outageInterval;
        this.clock = clock;
        this.monitor = monitor;
    }

    /**
     * Schedules the first run.
     *
     * @param initialDelay time until the first run
     */
    public void start(Duration initialDelay) {
        schedule(initialDelay);
    }

    void run() {
        var startedAt = clock.instant();
        Duration next;
        // an exception must not break the chain of runs, so it is caught and the next run scheduled
        try {
            var summary = syncService.sync(PolicyHubSyncService.Trigger.SCHEDULED);
            next = summary.aborted() ? afterAbortedRun(startedAt, summary.abort()) : afterCompletedRun();
        } catch (Exception e) {
            monitor.severe("Policy Hub sync failed", e);
            next = interval;
        }
        schedule(next);
    }

    private Duration afterAbortedRun(Instant startedAt, PolicyHubSyncService.Abort abort) {
        if (executor.isShutdown()) {
            return outageInterval;
        }
        consecutiveAbortedRuns++;
        if (abortedSince == null) {
            abortedSince = startedAt;
        }
        var cause = abort.cause().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        monitor.warning("Next Policy Hub sync in %d s (%d aborted %s in a row since %s, last cause: %s (%s))"
                .formatted(outageInterval.toSeconds(), consecutiveAbortedRuns, consecutiveAbortedRuns == 1 ? "run" : "runs",
                        abortedSince, cause, abort.reason()));
        return outageInterval;
    }

    private Duration afterCompletedRun() {
        if (consecutiveAbortedRuns > 0) {
            monitor.info("Policy Hub sync completed again after %d aborted %s, back to the regular interval of %d s"
                    .formatted(consecutiveAbortedRuns, consecutiveAbortedRuns == 1 ? "run" : "runs", interval.toSeconds()));
            consecutiveAbortedRuns = 0;
            abortedSince = null;
        }
        return interval;
    }

    private void schedule(Duration delay) {
        try {
            executor.schedule(this::run, delay.toSeconds(), TimeUnit.SECONDS);
        } catch (RejectedExecutionException e) {
            // executor shut down together with the runtime: no further runs
        }
    }
}
