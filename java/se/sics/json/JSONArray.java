/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * $Id: JSONArray.java 95 2011-04-19 13:29:41Z nfi $
 *
 * -----------------------------------------------------------------
 *
 * JSONArray
 *
 * Authors : Joakim Eriksson, Niclas Finne
 * Created : 18 apr 2011
 * Updated : $Date: 2011-04-19 15:29:41 +0200 (Tue, 19 Apr 2011) $
 *           $Revision: 95 $
 */

package se.sics.json;

import com.github.cliftonlabs.json_simple.Jsonable;
import com.github.cliftonlabs.json_simple.Jsoner;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;

/**
 *
 */
public class JSONArray extends ArrayList<Object> implements Jsonable {
    private void checkForCycles(Object value) {
        if (this == value) {
            throw new IllegalArgumentException("cycle detected");
        }
        if (value instanceof JSONObject object) {
            for (Object v : object.values()) {
                checkForCycles(v);
            }
        } else if (value instanceof JSONArray list) {
            for (Object v : list) {
                checkForCycles(v);
            }
        }
    }

    private void checkAllForCycles(Collection<?> c) {
        for(Object v : c) {
            checkForCycles(v);
        }
    }

    @Override
    public boolean add(Object e) {
        checkForCycles(e);
        return super.add(e);
    }

    @Override
    public boolean remove(Object o) {
        return super.remove(o);
    }

    @Override
    public boolean addAll(Collection<?> c) {
        checkAllForCycles(c);
        return super.addAll(c);
    }

    @Override
    public boolean addAll(int index, Collection<?> c) {
        checkAllForCycles(c);
        return super.addAll(index, c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return super.retainAll(c);
    }

    @Override
    public void clear() {
        super.clear();
    }

    @Override
    public Object set(int index, Object element) {
        checkForCycles(element);
        return super.set(index, element);
    }

    @Override
    public void add(int index, Object element) {
        checkForCycles(element);
        super.add(index, element);
    }

    @Override
    public Object remove(int index) {
        return super.remove(index);
    }

    public String getAsString(int index) {
        Object v = get(index);
        return v != null ? v.toString() : null;
    }

    public int getAsInt(int index, int defaultValue) {
      return get(index) instanceof Number number ? number.intValue() : defaultValue;
    }

    public long getAsLong(int index, long defaultValue) {
      return get(index) instanceof Number number ? number.longValue() : defaultValue;
    }

    public float getAsFloat(int index, float defaultValue) {
      return get(index) instanceof Number number ? number.floatValue() : defaultValue;
    }

    public double getAsDouble(int index, double defaultValue) {
      return get(index) instanceof Number number ? number.doubleValue() : defaultValue;
    }

    public boolean getAsBoolean(int index, boolean defaultValue) {
      return get(index) instanceof Boolean b ? b : defaultValue;
    }

    public JSONObject getJSONObject(int index) {
      return get(index) instanceof JSONObject jsonObject ? jsonObject : null;
    }

    public JSONArray getJSONArray(int index) {
      return get(index) instanceof JSONArray objects ? objects : null;
    }

    public void update(JSONArray v) {
        int count = v.size();
        if (size() < count) {
            count = size();
        }
        for(int i = 0; i < count; i++) {
            Object target = get(i);
            Object source = v.get(i);
            if (source instanceof JSONObject sourceObject) {
                if (target instanceof JSONObject jsonObject) {
                    jsonObject.update(sourceObject);
                }
            } else if (source instanceof JSONArray sourceArray) {
                if (target instanceof JSONArray objects) {
                    objects.update(sourceArray);
                }
            } else if (target instanceof JSONObject || target instanceof JSONArray) {
                // Compound values can not be replaced by primitive values
            } else {
                set(i, source);
            }
        }
    }

    public void merge(JSONArray v) {
        int count = v.size();
        if (size() < count) {
            count = size();
        }
        for(int i = 0; i < count; i++) {
            Object target = get(i);
            Object source = v.get(i);
            if (source instanceof JSONObject sourceObject) {
                if (target instanceof JSONObject jsonObject) {
                    jsonObject.merge(sourceObject);
                }
            } else if (source instanceof JSONArray sourceArray) {
                if (target instanceof JSONArray objects) {
                    objects.merge(sourceArray);
                }
            } else if (target instanceof JSONObject || target instanceof JSONArray) {
                // Compound values can not be replaced by primitive values
            } else {
                set(i, source);
            }
        }
        if (v.size() > size()) {
            for(int i = size(), n = v.size(); i < n; i++) {
                Object source = v.get(i);
                if (source instanceof JSONObject jsonObject) {
                    add(jsonObject.clone());
                } else if (source instanceof JSONArray objects) {
                    add(objects.clone());
                } else {
                    add(source);
                }
            }
        }
    }

    @Override
    public JSONArray clone() {
        JSONArray clone = (JSONArray) super.clone();
        // Create deep copy
        for(int i = 0, n = clone.size(); i < n; i++) {
            Object value = clone.get(i);
            if (value instanceof JSONObject jsonObject) {
                clone.set(i, jsonObject.clone());
            } else if (value instanceof JSONArray objects) {
                clone.set(i, objects.clone());
            }
        }
        return clone;
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public String toJson() {
        return Jsoner.serialize(this);
    }

    @Override
    public void toJson(Writer out) throws IOException {
        Jsoner.serialize(this, out);
    }
}
