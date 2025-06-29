import org.junit.jupiter.api.Test;
import work.CustomList;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomListTest {

    @Test
    void sizeListTest () {
        CustomList<String> list = new CustomList<>();
        assertThat(list.size()).isEqualTo(0);
        for (int i = 0; i < 10; i++) {
            list.add("Element " + i);
        }
        for (int i = 0; i < 10; i++) {
            assertThat(list.get(i)).isEqualTo("Element " + i);
        }
        list.add("test11");
        assertThat(list.size()).isEqualTo(11);

        ArrayList<String> baseList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            baseList.add("Element " + i);
        }
        for (int i = 0; i < 10; i++) {
            assertThat(baseList.get(i)).isEqualTo("Element " + i);
        }
        baseList.add("test11");
        assertThat(baseList.size()).isEqualTo(11);
    }

    @Test
    void isEmptyListTest () {
        CustomList<Integer> list = new CustomList<>();
        ArrayList<Integer> baseList = new ArrayList<>();
        assertThat(list.size()).isEqualTo(0);
        assertThat(list.isEmpty()).isTrue();
        assertThat(baseList.size()).isEqualTo(0);
        assertThat(baseList.isEmpty()).isTrue();
        list.add(1);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.isEmpty()).isFalse();
        baseList.add(1);
        assertThat(baseList.size()).isEqualTo(1);
        assertThat(baseList.isEmpty()).isFalse();
    }

    @Test
    void addTest () {
        CustomList<Integer> list = new CustomList<>();
        list.add(1);
        ArrayList<Integer> baseList = new ArrayList<>();
        baseList.add(1);
        assertThat(list.size()).isEqualTo(1);
        assertThat(baseList.size()).isEqualTo(1);
        assertThat(list.get(0)).isEqualTo(1);
        assertThat(baseList.get(0)).isEqualTo(1);
    }

    @Test
    void clearTest () {
        CustomList<Integer> list = new CustomList<>();
        ArrayList<Integer> baseList = new ArrayList<>();
        list.add(1);
        assertThat(list.size()).isEqualTo(1);
        baseList.add(1);
        assertThat(list.size()).isEqualTo(1);
        list.clear();
        baseList.clear();
        assertThat(list.size()).isEqualTo(0);
        assertThat(baseList.size()).isEqualTo(0);
    }

    @Test
    void setTest () {
        CustomList<String> list = new CustomList<>();
        list.add("test");
        assertThat(list.get(0)).isEqualTo("test");
        list.set(0, "new test");
        assertThat(list.get(0)).isEqualTo("new test");

        ArrayList<String> baseList = new ArrayList<>();
        baseList.add("test");
        assertThat(baseList.get(0)).isEqualTo("test");
        baseList.set(0, "new test");
        assertThat(baseList.get(0)).isEqualTo("new test");
    }

    @Test
    void removeTest () {
        CustomList<String> list = new CustomList<>();
        list.add("test");
        assertThat(list.size()).isEqualTo(1);
        list.remove(0);
        assertThat(list.size()).isEqualTo(0);

        ArrayList<String> baseList = new ArrayList<>();
        baseList.add("test");
        assertThat(baseList.size()).isEqualTo(1);
        baseList.remove(0);
        assertThat(baseList.size()).isEqualTo(0);
    }

    @Test
    void invalidIndexTest () {
        CustomList<Integer> list = new CustomList<>();
        list.add(1);
        ArrayList<Integer> baseList = new ArrayList<>();
        baseList.add(1);
        IndexOutOfBoundsException exception = assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThat(exception.getMessage()).isEqualTo("Index -1 out of bounds for length 1");
        IndexOutOfBoundsException exception2 = assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
        assertThat(exception2.getMessage()).isEqualTo("Index 2 out of bounds for length 1");

        IndexOutOfBoundsException exceptionBase = assertThrows(IndexOutOfBoundsException.class, () -> baseList.get(-1));
        assertThat(exceptionBase.getMessage()).isEqualTo("Index -1 out of bounds for length 1");
        IndexOutOfBoundsException exceptionBase2 = assertThrows(IndexOutOfBoundsException.class, () -> baseList.get(2));
        assertThat(exceptionBase2.getMessage()).isEqualTo("Index 2 out of bounds for length 1");
    }

    @Test
    void removeTest2 () {
        CustomList<Integer> list = new CustomList<>();
        list.add(1);
        list.add(2);
        list.add(3);
        list.add(4);

        int removed = list.remove(1);
        assertThat(removed).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo(1);
        assertThat(list.get(1)).isEqualTo(3);
        assertThat(list.get(2)).isEqualTo(4);
    }
}
