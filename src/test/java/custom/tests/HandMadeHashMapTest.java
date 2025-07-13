package custom.tests;

import work.customannotatition.Test;
import work.handmadehashmap.HandMadeHashMap;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class HandMadeHashMapTest {

    @Test
    public void testPutAndGet() {
        HandMadeHashMap<String, Integer> map = new HandMadeHashMap<>();
        map.put("one", 1);
        assertThat(map.get("one")).isEqualTo(1);
        assertThat(map.get("two")).isNull();

        map.put("one", 11);
        assertThat(map.get("one")).isEqualTo(11);
    }

    @Test
    public void testRemove() {
        HandMadeHashMap<String, String> map = new HandMadeHashMap<>();
        map.put("a", "one");
        map.put("b", "two");

        assertThat(map.remove("a")).isEqualTo("one");
        assertThat(map.get("a")).isNull();
        assertThat(map.size()).isEqualTo(1);

        assertThat(map.remove("c")).isNull();
    }

    @Test
    public void testContainsKey() {
        HandMadeHashMap<Integer, String> map = new HandMadeHashMap<>();
        map.put(1, "one");
        map.put(2, "two");

        assertThat(map.containsKey(1)).isTrue();
        assertThat(map.containsKey(3)).isFalse();
    }

    @Test
    public void testSizeAndIsEmpty() {
        HandMadeHashMap<String, String> map = new HandMadeHashMap<>();
        assertThat(map.isEmpty()).isTrue();
        map.put("x", "ex");
        assertThat(map.isEmpty()).isFalse();
        assertThat(map.size()).isEqualTo(1);
    }

    @Test
    public void testKeySetValuesEntrySet() {
        HandMadeHashMap<String, Integer> map = new HandMadeHashMap<>();
        map.put("a", 1);
        map.put("b", 2);

        Set<String> keys = map.keySet();
        assertThat(keys.contains("a")).isTrue();
        assertThat(keys.contains("b")).isTrue();
        assertThat(keys.size()).isEqualTo(2);

        Collection<Integer> values = map.values();
        assertThat(values.contains(1)).isTrue();
        assertThat(values.contains(2)).isTrue();
        assertThat(values.size()).isEqualTo(2);

        Set<Map.Entry<String, Integer>> entries = map.entrySet();
        assertThat(entries.size()).isEqualTo(2);
        for (Map.Entry<String, Integer> e : entries) {
            assertThat(map.get(e.getKey())).isEqualTo(e.getValue());
        }
    }

    @Test
    public void testClear() {
        HandMadeHashMap<String, String> map = new HandMadeHashMap<>();
        map.put("key", "value");
        map.clear();
        assertThat(map.size()).isEqualTo(0);
    }
}
