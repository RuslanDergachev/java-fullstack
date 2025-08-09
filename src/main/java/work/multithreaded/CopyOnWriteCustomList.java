package work.multithreaded;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public final class CopyOnWriteCustomList<T> implements List<T> {
    @SuppressWarnings("rawtypes")
    private static final Object[] EMPTY = new Object[0];

    private volatile Object[] array = EMPTY;

    @Override
    public int size() { return array.length; }

    @Override
    public boolean isEmpty() { return size() == 0; }

    @Override
    public boolean add(T t) {
        synchronized (this) {
            Object[] src = array;
            Object[] dst = Arrays.copyOf(src, src.length + 1);
            dst[dst.length - 1] = t;
            array = dst;
            return true;
        }
    }

    @Override
    public void clear() { synchronized (this) { array = EMPTY; } }

    @SuppressWarnings("unchecked")
    @Override
    public T get(int index) {
        Object[] a = array;
        if (index < 0 || index >= a.length) throw new IndexOutOfBoundsException();
        return (T) a[index];
    }

    @SuppressWarnings("unchecked")
    @Override
    public T set(int index, T element) {
        synchronized (this) {
            Object[] src = array;
            if (index < 0 || index >= src.length) throw new IndexOutOfBoundsException();
            Object[] dst = Arrays.copyOf(src, src.length);
            T old = (T) dst[index];
            dst[index] = element;
            array = dst;
            return old;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T remove(int index) {
        synchronized (this) {
            Object[] src = array;
            if (index < 0 || index >= src.length) throw new IndexOutOfBoundsException();
            T old = (T) src[index];
            Object[] dst = new Object[src.length - 1];
            System.arraycopy(src, 0, dst, 0, index);
            System.arraycopy(src, index + 1, dst, index, src.length - index - 1);
            array = dst;
            return old;
        }
    }

    @Override public boolean contains(Object o) { return indexOf(o) >= 0; }
    @Override public Iterator<T> iterator() { return Collections.unmodifiableList(asListSnapshot()).iterator(); }
    @Override public Object[] toArray() { return Arrays.copyOf(array, array.length); }
    @Override public <E> E[] toArray(E[] a) { return asListSnapshot().toArray(a); }
    @Override public boolean remove(Object o) { int i = indexOf(o); if (i < 0) return false; remove(i); return true; }
    @Override public boolean containsAll(Collection<?> c) { return asListSnapshot().containsAll(c); }
    @Override public boolean addAll(Collection<? extends T> c) { synchronized (this) { Object[] src = array; Object[] dst = Arrays.copyOf(src, src.length + c.size()); int p = src.length; for (T e : c) dst[p++] = e; array = dst; return !c.isEmpty(); } }
    @Override public boolean addAll(int index, Collection<? extends T> c) { synchronized (this) { Object[] src = array; if (index < 0 || index > src.length) throw new IndexOutOfBoundsException(); Object[] dst = new Object[src.length + c.size()]; System.arraycopy(src, 0, dst, 0, index); int p = index; for (T e : c) dst[p++] = e; System.arraycopy(src, index, dst, p, src.length - index); array = dst; return !c.isEmpty(); } }
    @Override public boolean removeAll(Collection<?> c) { return batchRemove(c, false); }
    @Override public boolean retainAll(Collection<?> c) { return batchRemove(c, true); }
    @Override public void add(int index, T element) { synchronized (this) { Object[] src = array; if (index < 0 || index > src.length) throw new IndexOutOfBoundsException(); Object[] dst = new Object[src.length + 1]; System.arraycopy(src, 0, dst, 0, index); dst[index] = element; System.arraycopy(src, index, dst, index + 1, src.length - index); array = dst; } }
    @Override public int indexOf(Object o) { Object[] a = array; if (o == null) { for (int i = 0; i < a.length; i++) if (a[i] == null) return i; } else { for (int i = 0; i < a.length; i++) if (o.equals(a[i])) return i; } return -1; }
    @Override public int lastIndexOf(Object o) { Object[] a = array; if (o == null) { for (int i = a.length - 1; i >= 0; i--) if (a[i] == null) return i; } else { for (int i = a.length - 1; i >= 0; i--) if (o.equals(a[i])) return i; } return -1; }
    @Override public ListIterator<T> listIterator() { return Collections.unmodifiableList(asListSnapshot()).listIterator(); }
    @Override public ListIterator<T> listIterator(int index) { return Collections.unmodifiableList(asListSnapshot()).listIterator(index); }
    @Override public List<T> subList(int fromIndex, int toIndex) { return Collections.unmodifiableList(asListSnapshot().subList(fromIndex, toIndex)); }

    @SuppressWarnings("unchecked")
    private List<T> asListSnapshot() {
        Object[] a = array;
        List<T> list = new ArrayList<>(a.length);
        for (Object o : a) list.add((T) o);
        return list;
    }

    private boolean batchRemove(Collection<?> c, boolean complement) {
        synchronized (this) {
            Objects.requireNonNull(c);
            Object[] src = array;
            Object[] tmp = new Object[src.length];
            int p = 0;
            for (Object o : src) {
                boolean keep = complement == c.contains(o);
                if (!keep) tmp[p++] = o;
            }
            if (p == 0) { array = EMPTY; return src.length != 0; }
            if (p == src.length) return false; // ничего не изменилось
            Object[] dst = Arrays.copyOf(tmp, p);
            array = dst;
            return true;
        }
    }
}

