package custom.tests;
import work.customannotatition.BeforeEach;
import work.customannotatition.Test;
import work.handmadehashmap.HandMadeHashMapDoubleHash;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class HandMadeHashMapDoubleHashTest {
    private HandMadeHashMapDoubleHash<String, Integer> map;

    @BeforeEach
    public void setUp() {
        map = new HandMadeHashMapDoubleHash<>();
    }

    @Test
    public void testPutAndGet() {
        assertThat(map.put("one", 1)).isNull();
        assertThat(map.put("two", 2)).isNull();
        assertThat(map.get("one")).isEqualTo(1);
        assertThat(map.get("two")).isEqualTo(2);
        assertThat(map.get("three")).isNull();
    }

    @Test
    public void testPutOverwrite() {
        map.put("key", 10);
        Integer oldValue = map.put("key", 20);
        assertThat(oldValue).isEqualTo(10);
        assertThat(map.get("key")).isEqualTo(20);
    }

    @Test
    public void testRemove() {
        map.put("a", 100);
        map.put("b", 200);
        assertThat(map.remove("a")).isEqualTo(100);
        assertThat(map.get("a")).isNull();
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.remove("nonexistent")).isNull();
    }

    @Test
    public void testContainsKey() {
        map.put("x", 42);
        assertThat(map.containsKey("x")).isTrue();
        assertThat(map.containsKey("y")).isFalse();
    }

    @Test
    public void testSizeAndIsEmpty() {
        assertThat(map.isEmpty()).isTrue();
        map.put("k1", 1);
        map.put("k2", 2);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.isEmpty()).isFalse();
        map.remove("k1");
        map.remove("k2");
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    public void testEntrySetKeySetValues() {
        map.put("alpha", 10);
        map.put("beta", 20);
        map.put("gamma", 30);

        Set<Map.Entry<String, Integer>> entries = map.entrySet();
        assertThat(entries).hasSize(3);
        assertThat(entries).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("alpha", "beta", "gamma");
        assertThat(entries).extracting(Map.Entry::getValue)
                .containsExactlyInAnyOrder(10, 20, 30);

        Set<String> keys = map.keySet();
        assertThat(keys).containsExactlyInAnyOrder("alpha", "beta", "gamma");

        Collection<Integer> values = map.values();
        assertThat(values).containsExactlyInAnyOrder(10, 20, 30);
    }

    @Test
    public void testClear() {
        map.put("one", 1);
        map.put("two", 2);
        map.clear();
        assertThat(map.size()).isZero();
        assertThat(map.get("one")).isNull();
        assertThat(map.isEmpty()).isTrue();
    }

    @Test
    public void testContainsValue() {
        map.put("a", 1);
        map.put("b", 2);
        assertThat(map.containsValue(1)).isTrue();
        assertThat(map.containsValue(3)).isFalse();
    }

    @Test
    public void testPutAll() {
        Map<String, Integer> other = Map.of("x", 100, "y", 200);
        map.putAll(other);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.get("x")).isEqualTo(100);
        assertThat(map.get("y")).isEqualTo(200);
    }

    @Test
    public void testCollisionHandling() {
        // Создадим ключи, которые намеренно вызывают коллизии
        // Для этого можно использовать кастомные объекты с одинаковым hashCode
        class Key {
            private final String val;
            Key(String val) { this.val = val; }
            @Override
            public int hashCode() { return 42; } // фиксированный хэш для коллизии
            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof Key)) return false;
                return val.equals(((Key) o).val);
            }
            @Override
            public String toString() { return val; }
        }

        HandMadeHashMapDoubleHash<Key, String> collisionMap = new HandMadeHashMapDoubleHash<>();
        Key k1 = new Key("key1");
        Key k2 = new Key("key2");
        Key k3 = new Key("key3");

        collisionMap.put(k1, "value1");
        collisionMap.put(k2, "value2");
        collisionMap.put(k3, "value3");

        assertThat(collisionMap.size()).isEqualTo(3);
        assertThat(collisionMap.get(k1)).isEqualTo("value1");
        assertThat(collisionMap.get(k2)).isEqualTo("value2");
        assertThat(collisionMap.get(k3)).isEqualTo("value3");

        collisionMap.remove(k2);
        assertThat(collisionMap.get(k2)).isNull();
        assertThat(collisionMap.size()).isEqualTo(2);
    }
}
