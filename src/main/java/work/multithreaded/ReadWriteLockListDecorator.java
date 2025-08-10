package work.multithreaded;

import work.collectionclasses.CustomList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.UnaryOperator;

/**
 * Потокобезопасный декоратор над CustomList на основе ReentrantReadWriteLock.
 * - Чтения (size, isEmpty, get, iterator и т.п.) идут под readLock
 * - Записи (add, set, remove и т.п.) — под writeLock
 * - Итераторы и subList возвращают снепшоты (копии), чтобы не держать блокировки во время обхода.
 */
public final class ReadWriteLockListDecorator<T> implements List<T> {
    private final CustomList<T> delegate;
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock r = rw.readLock();
    private final ReentrantReadWriteLock.WriteLock w = rw.writeLock();

    public ReadWriteLockListDecorator(CustomList<T> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public int size() { r.lock(); try { return delegate.size(); } finally { r.unlock(); } }

    @Override
    public boolean isEmpty() { r.lock(); try { return delegate.isEmpty(); } finally { r.unlock(); } }

    @Override
    public boolean contains(Object o) { r.lock(); try { return delegate.contains(o); } finally { r.unlock(); } }

    @Override
    public Iterator<T> iterator() {
        r.lock();
        try {
            return new ArrayList<>(delegate).iterator();
        } finally {
            r.unlock();
        }
    }

    @Override
    public Object[] toArray() { r.lock(); try { return delegate.toArray(); } finally { r.unlock(); } }

    @Override
    public <E> E[] toArray(E[] a) { r.lock(); try { return delegate.toArray(a); } finally { r.unlock(); } }

    @Override
    public boolean add(T t) { w.lock(); try { return delegate.add(t); } finally { w.unlock(); } }

    @Override
    public boolean remove(Object o) { w.lock(); try { return delegate.remove(o); } finally { w.unlock(); } }

    @Override
    public boolean containsAll(Collection<?> c) { r.lock(); try { return delegate.containsAll(c); } finally { r.unlock(); } }

    @Override
    public boolean addAll(Collection<? extends T> c) { w.lock(); try { return delegate.addAll(c); } finally { w.unlock(); } }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) { w.lock(); try { return delegate.addAll(index, c); } finally { w.unlock(); } }

    @Override
    public boolean removeAll(Collection<?> c) { w.lock(); try { return delegate.removeAll(c); } finally { w.unlock(); } }

    @Override
    public boolean retainAll(Collection<?> c) { w.lock(); try { return delegate.retainAll(c); } finally { w.unlock(); } }

    @Override
    public void replaceAll(UnaryOperator<T> operator) { w.lock(); try { delegate.replaceAll(operator); } finally { w.unlock(); } }

    @Override
    public void sort(Comparator<? super T> c) { w.lock(); try { delegate.sort(c); } finally { w.unlock(); } }

    @Override
    public void clear() { w.lock(); try { delegate.clear(); } finally { w.unlock(); } }

    @Override
    public T get(int index) { r.lock(); try { return delegate.get(index); } finally { r.unlock(); } }

    @Override
    public T set(int index, T element) { w.lock(); try { return delegate.set(index, element); } finally { w.unlock(); } }

    @Override
    public void add(int index, T element) { w.lock(); try { delegate.add(index, element); } finally { w.unlock(); } }

    @Override
    public T remove(int index) { w.lock(); try { return delegate.remove(index); } finally { w.unlock(); } }

    @Override
    public int indexOf(Object o) { r.lock(); try { return delegate.indexOf(o); } finally { r.unlock(); } }

    @Override
    public int lastIndexOf(Object o) { r.lock(); try { return delegate.lastIndexOf(o); } finally { r.unlock(); } }

    @Override
    public ListIterator<T> listIterator() {
        r.lock();
        try {
            return new ArrayList<>(delegate).listIterator();
        } finally {
            r.unlock();
        }
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        r.lock();
        try {
            return new ArrayList<>(delegate).listIterator(index);
        } finally {
            r.unlock();
        }
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        r.lock();
        try {
            return new ArrayList<>(delegate.subList(fromIndex, toIndex));
        } finally {
            r.unlock();
        }
    }
}
