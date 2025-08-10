// java
package work.multithreaded;

import work.collectionclasses.CustomList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Потокобезопасный декоратор над CustomList.
 * - Все операции синхронизированы на приватном lock.
 * - Итераторы и subList возвращают копии (snapshot), чтобы обход не требовал удержания lock.
 */
public final class SynchronizedListDecorator<T> implements List<T> {
    private final CustomList<T> delegate;
    private final Object lock = new Object();

    public SynchronizedListDecorator(CustomList<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override public int size() { synchronized (lock) { return delegate.size(); } }
    @Override public boolean isEmpty() { synchronized (lock) { return delegate.isEmpty(); } }
    @Override public boolean contains(Object o) { synchronized (lock) { return delegate.contains(o); } }

    @Override
    public Iterator<T> iterator() {
        synchronized (lock) {
            return new ArrayList<>(delegate).iterator();
        }
    }

    @Override public Object[] toArray() { synchronized (lock) { return delegate.toArray(); } }

    @Override
    public <E> E[] toArray(E[] a) {
        synchronized (lock) {
            return delegate.toArray(a);
        }
    }

    @Override public boolean add(T t) { synchronized (lock) { return delegate.add(t); } }
    @Override public boolean remove(Object o) { synchronized (lock) { return delegate.remove(o); } }
    @Override public boolean containsAll(Collection<?> c) { synchronized (lock) { return delegate.containsAll(c); } }
    @Override public boolean addAll(Collection<? extends T> c) { synchronized (lock) { return delegate.addAll(c); } }
    @Override public boolean addAll(int index, Collection<? extends T> c) { synchronized (lock) { return delegate.addAll(index, c); } }
    @Override public boolean removeAll(Collection<?> c) { synchronized (lock) { return delegate.removeAll(c); } }
    @Override public boolean retainAll(Collection<?> c) { synchronized (lock) { return delegate.retainAll(c); } }

    @Override
    public void replaceAll(UnaryOperator<T> operator) {
        synchronized (lock) { delegate.replaceAll(operator); }
    }

    @Override
    public void sort(Comparator<? super T> c) {
        synchronized (lock) { delegate.sort(c); }
    }

    @Override public void clear() { synchronized (lock) { delegate.clear(); } }
    @Override public T get(int index) { synchronized (lock) { return delegate.get(index); } }
    @Override public T set(int index, T element) { synchronized (lock) { return delegate.set(index, element); } }
    @Override public void add(int index, T element) { synchronized (lock) { delegate.add(index, element); } }
    @Override public T remove(int index) { synchronized (lock) { return delegate.remove(index); } }
    @Override public int indexOf(Object o) { synchronized (lock) { return delegate.indexOf(o); } }
    @Override public int lastIndexOf(Object o) { synchronized (lock) { return delegate.lastIndexOf(o); } }

    @Override
    public ListIterator<T> listIterator() {
        synchronized (lock) {
            return new ArrayList<>(delegate).listIterator();
        }
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        synchronized (lock) {
            return new ArrayList<>(delegate).listIterator(index);
        }
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        synchronized (lock) {
            return new ArrayList<>(delegate.subList(fromIndex, toIndex));
        }
    }
}
