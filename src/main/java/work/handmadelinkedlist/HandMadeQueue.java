package work.handmadelinkedlist;

import java.util.NoSuchElementException;

public class HandMadeQueue<T> {
    private final HandMadeLinkedList<T> list = new HandMadeLinkedList<>();

    // Add an item to the back of the queue
    public void enqueue(T element) {
        list.addLast(element);
    }

    // Extract an item from the head of the queue
    public T dequeue() {
        if (list.size() == 0) {
            throw new NoSuchElementException("Queue is empty");
        }
        T front = list.getFirst();
        removeFirst();
        return front;
    }

    // View an item at the head of the queue without deleting it
    public T peek() {
        if (list.size() == 0) {
            throw new NoSuchElementException("Queue is empty");
        }
        return list.getFirst();
    }

    // Queue size
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
