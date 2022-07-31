package com.github.benmanes.caffeine.cache.simulator.policy.dash;

import java.util.Objects;

public class Data {
    public final long key;

    public Data(long key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Data)) {
            return false;
        }
        Data data = (Data) o;
        return key == data.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}