package work.handmadehashmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

//Collisions in the HandMadeHashMapDoubleHash are resolved using double hashing
public class HandMadeHashMapDoubleHash<K, V> implements Map<K, V> {
    private static final int INITIAL_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;

    private Entry<K, V>[] table;
    private int size = 0;
    private int threshold;

    @SuppressWarnings("unchecked")
    public HandMadeHashMapDoubleHash() {
        table = (Entry<K, V>[]) new Entry[INITIAL_CAPACITY];
        threshold = (int) (INITIAL_CAPACITY * LOAD_FACTOR);
    }

    private static class Entry<K, V> implements Map.Entry<K, V> {
        final K key;
        V value;
        boolean deleted; // to mark deleted items (lazy deletion)

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
            this.deleted = false;
        }

        @Override
        public K getKey() { return key; }

        @Override
        public V getValue() { return value; }

        @Override
        public V setValue(V newValue) {
            V old = value;
            value = newValue;
            return old;
        }
    }

    // The first hash function: hash1(key) = hashCode(key) mod table.length
    private int hash1(Object key) {
        int h = key == null ? 0 : key.hashCode();
        return Math.floorMod(h, table.length);
    }

    // The second hash function: hash2(key) = PRIME - (hashCode(key) mod PRIME)
    // PRIME — a prime number is less than table.length, here we fix 7 for the initial size of 16
    private int hash2(Object key) {
        int h = key == null ? 0 : key.hashCode();
        int prime = 7;
        return prime - Math.floorMod(h, prime);
    }

    @Override
    public V put(K key, V value) {
        if (size >= threshold) {
            resize(table.length * 2);
        }

        int hash1 = hash1(key);
        int hash2 = hash2(key);
        int index = hash1;
        int firstDeletedIndex = -1;

        for (int i = 0; i < table.length; i++) {
            Entry<K, V> entry = table[index];

            if (entry == null) {
                if (firstDeletedIndex != -1) {
                    table[firstDeletedIndex] = new Entry<>(key, value);
                } else {
                    table[index] = new Entry<>(key, value);
                }
                size++;
                return null;
            } else if (entry.deleted) {
                if (firstDeletedIndex == -1) {
                    firstDeletedIndex = index;
                }
            } else if (Objects.equals(entry.key, key)) {
                V oldValue = entry.value;
                entry.value = value;
                return oldValue;
            }

            index = (index + hash2) % table.length;
        }

        throw new IllegalStateException("HandMadeHashMapDoubleHash full, cannot insert");
    }

    @Override
    public V get(Object key) {
        int hash1 = hash1(key);
        int hash2 = hash2(key);
        int index = hash1;

        for (int i = 0; i < table.length; i++) {
            Entry<K, V> entry = table[index];
            if (entry == null) return null;
            if (!entry.deleted && Objects.equals(entry.key, key)) {
                return entry.value;
            }
            index = (index + hash2) % table.length;
        }
        return null;
    }

    @Override
    public V remove(Object key) {
        int hash1 = hash1(key);
        int hash2 = hash2(key);
        int index = hash1;

        for (int i = 0; i < table.length; i++) {
            Entry<K, V> entry = table[index];
            if (entry == null) return null;
            if (!entry.deleted && Objects.equals(entry.key, key)) {
                entry.deleted = true;
                size--;
                return entry.value;
            }
            index = (index + hash2) % table.length;
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(key) != null;
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Set<Map.Entry<K, V>> entries = new HashSet<>();
        for (Entry<K, V> entry : table) {
            if (entry != null && !entry.deleted) {
                entries.add(entry);
            }
        }
        return entries;
    }

    @Override
    public Set<K> keySet() {
        Set<K> keys = new HashSet<>();
        for (Entry<K, V> entry : table) {
            if (entry != null && !entry.deleted) {
                keys.add(entry.key);
            }
        }
        return keys;
    }

    @Override
    public Collection<V> values() {
        List<V> values = new ArrayList<>();
        for (Entry<K, V> entry : table) {
            if (entry != null && !entry.deleted) {
                values.add(entry.value);
            }
        }
        return values;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        for (Entry<K, V> entry : table) {
            if (entry != null && !entry.deleted && Objects.equals(entry.value, value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clear() {
        Arrays.fill(table, null);
        size = 0;
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCapacity) {
        Entry<K, V>[] oldTable = table;
        table = (Entry<K, V>[]) new Entry[newCapacity];
        size = 0;
        threshold = (int) (newCapacity * LOAD_FACTOR);

        for (Entry<K, V> entry : oldTable) {
            if (entry != null && !entry.deleted) {
                put(entry.key, entry.value);
            }
        }
    }
}
