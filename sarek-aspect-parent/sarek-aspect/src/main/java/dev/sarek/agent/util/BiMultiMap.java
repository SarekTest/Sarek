package dev.sarek.agent.util;

import java.util.*;

public class BiMultiMap<K, V> {
  private final Map<K, List<V>> keyToValue = new IdentityHashMap<>();
  private final Map<V, List<K>> valueToKey = new IdentityHashMap<>();

  public synchronized BiMultiMap<K, V> add(K key, V value) {
    initLists(key, value);
    List<V> values = keyToValue.get(key);
    List<K> keys = valueToKey.get(value);
    boolean keyHasValue = values.contains(value);
    boolean valueHasKey = keys.contains(key);
    assert keyHasValue == valueHasKey : "inconsistent key/value association (should never happen)";
    if (keyHasValue)
      throw new KeyValueCollisionException(key, value);
    values.add(value);
    keys.add(key);
    return this;
  }

  public synchronized BiMultiMap<K, V> addAll(K key, Collection<V> values) {
    for (V value : values)
      add(key, value);
    return this;
  }

  public synchronized BiMultiMap<K, V> remove(K key, V value) {
    initLists(key, value);
    keyToValue.get(key).remove(value);
    valueToKey.get(value).remove(key);
    return this;
  }

  public synchronized BiMultiMap<K, V> removeAll(K key, Collection<V> values) {
    for (V value : values)
      remove(key, value);
    return this;
  }

  // TODO: Warn user in Javadoc that backing list can change
  public List<V> getValues(K key) {
    List<V> values = keyToValue.get(key);
    return Collections.unmodifiableList(values == null ? Collections.emptyList() : values);
  }

  // TODO: Warn user in Javadoc that backing list can change
  public List<K> getKeys(V value) {
    List<K> keys = valueToKey.get(value);
    return Collections.unmodifiableList(keys == null ? Collections.emptyList() : keys);
  }

  private void initLists(K key, V value) {
    keyToValue.computeIfAbsent(key, k -> new IdentityArrayList<>());
    valueToKey.computeIfAbsent(value, v -> new IdentityArrayList<>());
  }

  public static class KeyValueCollisionException extends RuntimeException {
    public KeyValueCollisionException(Object key, Object value) {
      super("key/value pair (" + key + "/" + value + ") already exists");
    }
  }

}
