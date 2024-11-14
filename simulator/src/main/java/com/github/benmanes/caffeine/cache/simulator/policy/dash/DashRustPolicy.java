package com.github.benmanes.caffeine.cache.simulator.policy.dash;

import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.policy.Policy;
import com.github.benmanes.caffeine.cache.simulator.policy.Policy.PolicySpec;
import com.github.benmanes.caffeine.cache.simulator.policy.PolicyStats;

import com.google.common.base.Enums;

import com.typesafe.config.Config;
import java.util.*;
import static java.util.Locale.US;
import static java.lang.System.exit;

@PolicySpec(name = "dash.DashRust")
public class DashRustPolicy implements Policy.KeyOnlyPolicy {
  private final PolicyStats policyStats;
  private final boolean debugMode;

  static {
    System.loadLibrary("dash");
  }

  public DashRustPolicy() {
    this.policyStats = new PolicyStats(name());
    this.debugMode = false;

    initDefaultCache();
  }

  public DashRustPolicy(DashSettings settings, DashRustEvictionPolicy evictionPolicy) {
    this.policyStats = new PolicyStats(name());
    this.debugMode = settings.debugMode();

    long numOfSegments = settings.maximumSize() / (settings.numOfNormalBuckets() + settings.numOfStashBuckets())
        / settings.bucketSize();

    if (this.debugMode) {
      System.out.println("^^^^^^^^^^^^^^^^^^^^^^ Constructor ^^^^^^^^^^^^^^^^^^^^^^^^^^");
      System.out.println("maximumSize: " + settings.maximumSize());
      System.out.println("numOfSegments: " + numOfSegments);
      System.out.println("numOfNormalBuckets: " + settings.numOfNormalBuckets());
      System.out.println("numOfStashBuckets: " + settings.numOfStashBuckets());
      System.out.println("bucketSize: " + settings.bucketSize());
      System.out.println("evictionPolicy: " + evictionPolicy.toString());
    }

    initCache(numOfSegments, settings.numOfNormalBuckets(), settings.numOfStashBuckets(),
        settings.bucketSize(), evictionPolicy.ordinal());
  }

  public static Set<Policy> policies(Config config) {
    var settings = new DashSettings(config);
    Set<Policy> set = new HashSet<>();

    if (settings.evictionPolicies().isEmpty()) {
      set.add(new DashRustPolicy());
    } else {
      for (DashRustEvictionPolicy policy : settings.evictionPolicies()) {
        set.add(new DashRustPolicy(settings, policy));
      }
    }

    return set;
  }

  @Override
  public PolicyStats stats() {
    return this.policyStats;
  }

  @Override
  public void record(long key) {
    long value = getFromCacheIfPresent(key);
    if (value == -1) {
      putToCache(key, key);
      policyStats.recordMiss();
      if (this.debugMode) {
        System.out.println("key: " + key + " is a miss");
      }
    } else {
      policyStats.recordHit();
      if (this.debugMode) {
        System.out.println("key: " + key + " is a hit");
      }
      if (key != value) {
        System.out.println("key != value in Dash cache");
        exit(1);
      }
    }
  }

  public enum DashRustEvictionPolicy {
    MOVE_AHEAD,
    LRU,
    LIFO,
    LFU,
    LFU_DISP,
    FIFO;
  }

  public static final class DashSettings extends BasicSettings {
    public DashSettings(Config config) {
      super(config);
    }

    public long numOfNormalBuckets() {
      return this.config().getLong("dash.numOfNormalBuckets");
    }

    public long numOfStashBuckets() {
      return this.config().getLong("dash.numOfStashBuckets");
    }

    public long bucketSize() {
      return this.config().getLong("dash.bucketSize");
    }

    public boolean debugMode() {
      return this.config().getBoolean("dash.debugMode");
    }

    public Set<DashRustEvictionPolicy> evictionPolicies() {
      var policies = EnumSet.noneOf(DashRustEvictionPolicy.class);
      for (var policy : config().getStringList("dash.policy")) {
        var option = Enums.getIfPresent(DashRustEvictionPolicy.class, policy.toUpperCase(US)).toJavaUtil();
        option.ifPresentOrElse(policies::add, () -> {
          throw new IllegalArgumentException("Unknown policy: " + policy);
        });
      }
      return policies;
    }
  }

  /*
   * ---------------------------------------------------------------------------
   * Native (Rust) functions to create and drive Dash cache.
   * ---------------------------------------------------------------------------
   */

  /*
   * Creates the shared singleton instance of the Dash cache with default
   * settings.
   */
  private static native void initDefaultCache();

  /*
   * Creates the shared singleton instance of the Dash cache with given
   * parameters.
   */
  private static native void initCache(long num_of_segments, long num_of_normal_buckets, long num_of_stash_buckets,
      long bucket_size, long eviction_policy);

  /*
   * TODO: the value type
   * Returns the value of the given key if exists. Otherwise returns -1.
   *
   * @return The weight of the key if exists. Otherwise -1.
   */
  private static native long getFromCacheIfPresent(long key);

  /*
   * TODO: the value type
   * Stores the value for the given key.
   */
  private static native void putToCache(long key, long value);

}
