package work.handmadelinkedlist;

import java.util.NoSuchElementException;

public class HandMadeStack<T> {
    private final HandMadeLinkedList<T> list = new HandMadeLinkedList<>();

    // Add an item to the top of the stack
    public void push(T element) {
        list.addFirst(element);
    }

    // Extract an element from the top of the stack
    public T pop() {
        if (list.size() == 0) {
            throw new NoSuchElementException("Stack is empty");
        }
        T top = list.getFirst();
        removeFirst();
        return top;
    }

    // View an item at the top of the stack without deleting it
    public T peek() {
        if (list.size() == 0) {
            throw new NoSuchElementException("Stack is empty");
        }
        return list.getFirst();
    }

    // Stack size
    public int size() {
        return list.size();
    }

    // Auxiliary method for deleting the first element
    private void removeFirst() {
        if (list.head == null) {
            throw new NoSuchElementException();
        }
        list.head = list.head.next;
        if (list.head == null) {
            list.tail = null;
        } else {
            list.head.prev = null;
        }
        list.size--;
    }
}
