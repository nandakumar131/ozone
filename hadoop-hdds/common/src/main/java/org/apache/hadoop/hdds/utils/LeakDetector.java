/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.utils;

import java.lang.ref.ReferenceQueue;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ratis.util.UncheckedAutoCloseable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple general resource leak detector using {@link ReferenceQueue} and {@link java.lang.ref.WeakReference} to
 * observe resource object life-cycle and assert proper resource closure before they are GCed.
 * <p>
 * Example usage:
 *
 * <pre> {@code
 * class MyResource implements AutoClosable {
 *   static final LeakDetector LEAK_DETECTOR = new LeakDetector("MyResource");
 *
 *   private final UncheckedAutoCloseable leakTracker = LEAK_DETECTOR.track(this, () -> {
 *      // report leaks, don't refer to the original object (MyResource) here.
 *      System.out.println("MyResource is not closed before being discarded.");
 *   });
 * }
 * }
 * </pre>
 * <pre>
 *   {@code @Override
 *   public void close() {
 *     // proper resources cleanup...
 *     // inform tracker that this object is closed properly.
 *     leakTracker.close();
 *   }
 *  }
 * </pre>
 */
public class LeakDetector {
  private static final Logger LOG = LoggerFactory.getLogger(LeakDetector.class);
  private static final AtomicLong COUNTER = new AtomicLong();
  private final ReferenceQueue<Object> queue = new ReferenceQueue<>();
  private final Set<LeakTracker> allLeaks = Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final String name;

  public LeakDetector(String name) {
    this.name = name + COUNTER.getAndIncrement();
    start();
  }

  private void start() {
    Thread t = new Thread(this::run);
    t.setName(LeakDetector.class.getSimpleName() + "-" + name);
    t.setDaemon(true);
    LOG.debug("Starting leak detector thread {}.", name);
    t.start();
  }

  private void run() {
    while (true) {
      try {
        LeakTracker tracker = (LeakTracker) queue.remove();
        // Original resource already been GCed, if tracker is not closed yet,
        // report a leak.
        if (allLeaks.remove(tracker)) {
          tracker.reportLeak();
        }
      } catch (InterruptedException e) {
        LOG.warn("Thread interrupted, exiting.", e);
        break;
      }
    }

    LOG.warn("Exiting leak detector {}.", name);
  }

  public UncheckedAutoCloseable track(Object leakable, Runnable reportLeak) {
    // A rate filter can be put here to only track a subset of all objects, e.g. 5%, 10%,
    // if we have proofs that leak tracking impacts performance, or a single LeakDetector
    // thread can't keep up with the pace of object allocation.
    // For now, it looks effective enough and let keep it simple.
    LeakTracker tracker = new LeakTracker(leakable, queue, allLeaks, reportLeak);
    allLeaks.add(tracker);
    return tracker;
  }
}
