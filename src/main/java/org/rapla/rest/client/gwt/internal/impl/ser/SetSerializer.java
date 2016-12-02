// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.rapla.rest.client.gwt.internal.impl.ser;

import java.util.HashSet;
import java.util.LinkedHashSet;

import javax.inject.Provider;

import org.rapla.rest.client.gwt.internal.impl.JsonSerializer;
import org.rapla.rest.client.gwt.internal.impl.ResultDeserializer;

/**
 * Serialization for a {@link java.util.Set}.
 * <p>
 * When deserialized from JSON the Set implementation is always a
 * {@link HashSet}. When serializing to JSON any Set is permitted.
 */
public class SetSerializer<T> extends JsonSerializer<java.util.Set<T>>
    implements ResultDeserializer<java.util.Set<T>> {
  private final Provider<JsonSerializer<T>> serializer;
  
  public SetSerializer(final JsonSerializer<T> s) {
      serializer = new SimpleGwtProvider<JsonSerializer<T>>(s);
  }

  public SetSerializer(final Provider<JsonSerializer<T>> s) {
    serializer = s;
  }

  @Override
  public void printJson(final StringBuilder sb, final java.util.Set<T> o) {
    sb.append('[');
    boolean first = true;
    for (final T item : o) {
      if (first) {
        first = false;
      } else {
        sb.append(',');
      }
      if (item != null) {
        serializer.get().printJson(sb, item);
      } else {
        sb.append(JS_NULL);
      }
    }
    sb.append(']');
  }

  @Override
  public java.util.Set<T> fromJson(final Object o) {
    if (o == null) {
      return null;
    }
    final int n = size(o);
    final LinkedHashSet<T> r = new LinkedHashSet<T>(n);
    for (int i = 0; i < n; i++) {
      r.add(serializer.get().fromJson(get(o, i)));
    }
    return r;
  }
}
