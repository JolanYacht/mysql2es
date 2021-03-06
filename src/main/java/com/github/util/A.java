package com.github.util;

import java.util.*;

/** util with array, list, map */
public final class A {

    public static boolean isArray(Object obj) {
        return (obj != null && obj.getClass().isArray());
    }
    public static <T> boolean isEmpty(T[] array) {
        return array == null || array.length == 0;
    }
    public static <T> boolean isNotEmpty(T[] array) {
        return !isEmpty(array);
    }

    public static <T> boolean isEmpty(Collection<T> collection) {
        return collection == null || collection.size() == 0;
    }
    public static <T> boolean isNotEmpty(Collection<T> collection) {
        return !isEmpty(collection);
    }

    public static boolean isEmpty(Map map) {
        return map == null || map.isEmpty();
    }
    public static boolean isNotEmpty(Map map) {
        return !isEmpty(map);
    }

    public static <T> String toStr(Collection<T> collection) {
        return toStr(collection, ",");
    }
    public static <T> String toStr(Collection<T> collection, String split) {
        return toStr(collection.toArray(), split);
    }
    public static String toStr(Object[] array) {
        return toStr(array, ",");
    }
    public static String toStr(Object[] array, String split) {
        if (isEmpty(array)) {
            return U.EMPTY;
        }

        StringBuilder sbd = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            sbd.append(array[i]);
            if (i + 1 != array.length) {
                sbd.append(split);
            }
        }
        return sbd.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> lists(T... values) {
        return new ArrayList<T>(java.util.Arrays.asList(values));
    }
    @SuppressWarnings("unchecked")
    public static <T> List<T> linkedLists(T... values) {
        return new LinkedList<T>(java.util.Arrays.asList(values));
    }

    @SuppressWarnings("unchecked")
    public static <T> Set<T> sets(T... sets) {
        return new HashSet<T>(java.util.Arrays.asList(sets));
    }
    @SuppressWarnings("unchecked")
    public static <T> Set<T> linkedSets(T... sets) {
        return new LinkedHashSet<T>(java.util.Arrays.asList(sets));
    }

    public static <K, V> HashMap<K, V> newHashMap() {
        return new HashMap<K, V>();
    }
    public static <K, V> HashMap<K, V> newLinkedHashMap() {
        return new LinkedHashMap<K, V>();
    }

    @SuppressWarnings("unchecked")
    public static <K, V> HashMap<K, V> maps(Object... keysAndValues) {
        return (HashMap<K, V>) maps(newHashMap(), keysAndValues);
    }
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> maps(Map<K, V> result, Object... keysAndValues) {
        if (isNotEmpty(keysAndValues)) {
            for (int i = 0; i < keysAndValues.length; i += 2) {
                if (keysAndValues.length > (i + 1)) {
                    result.put((K) keysAndValues[i], (V) keysAndValues[i + 1]);
                }
            }
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    public static <K, V> LinkedHashMap<K, V> linkedMaps(Object... keysAndValues) {
        return (LinkedHashMap<K, V>) maps(newLinkedHashMap(), keysAndValues);
    }

    public static <T> T first(Collection<T> collection) {
        return isEmpty(collection) ? null : collection.iterator().next();
    }
    public static <T> T last(Collection<T> collection) {
        if (isEmpty(collection)) {
            return null;
        }

        if (collection instanceof List) {
            List<T> list = (List<T>) collection;
            return list.get(list.size() - 1);
        }
        Iterator<T> iterator = collection.iterator();
        while (true) {
            T current = iterator.next();
            if (!iterator.hasNext()) {
                return current;
            }
        }
    }
}
