package custom.tests;

import work.customannotatition.BeforeEach;
import work.customannotatition.Test;
import work.handmadelinkedlist.HandMadeLinkedList;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HandMadeLinkedListTest {
    private HandMadeLinkedList<Integer> list;

    @BeforeEach
    public void setUp() {
        list = new HandMadeLinkedList<>();
    }

    @Test
    public void testAddFirstAndGetFirst() {
        list.addFirst(10);
        assertThat(list.getFirst()).isEqualTo(10);
        assertThat(list.size()).isEqualTo(1);

        list.addFirst(20);
        assertThat(list.getFirst()).isEqualTo(20);
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    public void testAddLastAndGetLast() {
        list.addLast(30);
        assertThat(list.getLast()).isEqualTo(30);
        assertThat(list.size()).isEqualTo(1);

        list.addLast(40);
        assertThat(list.getLast()).isEqualTo(40);
        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    public void testAddFirstAndAddLastCombined() {
        list.addFirst(10);
        list.addLast(20);
        list.addFirst(5);

        assertThat(list.getFirst()).isEqualTo(5);
        assertThat(list.getLast()).isEqualTo(20);
        assertThat(list.size()).isEqualTo(3);
    }

    @Test
    public void testGetFirstThrowsExceptionWhenEmpty() {
        assertThatThrownBy(() -> list.getFirst())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void testGetLastThrowsExceptionWhenEmpty() {
        assertThatThrownBy(() -> list.getLast())
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void testSizeInitiallyZero() {
        assertThat(list.size()).isEqualTo(0);
    }
}
