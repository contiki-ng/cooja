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
 * $Id: JSONObject.java 95 2011-04-19 13:29:41Z nfi $
 *
 * -----------------------------------------------------------------
 *
 * JSONObject
 *
 * Authors : Joakim Eriksson, Niclas Finne
 * Created : 18 apr 2011
 * Updated : $Date: 2011-04-19 15:29:41 +0200 (Tue, 19 Apr 2011) $
 *           $Revision: 95 $
 */

package se.sics.json;

import com.github.cliftonlabs.json_simple.JsonException;
import com.github.cliftonlabs.json_simple.Jsonable;
import com.github.cliftonlabs.json_simple.Jsoner;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class JSONObject extends HashMap<String,Object> implements Jsonable {
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

    @Override
    public Object put(String key, Object value) {
        checkForCycles(value);
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends String,?> map) {
        for (Object v : map.values()) {
            checkForCycles(v);
        }
        super.putAll(map);
    }

    @Override
    public Object remove(Object key) {
        return super.remove(key);
    }

    @Override
    public void clear() {
        super.clear();
    }

    public boolean has(String key) {
        return containsKey(key);
    }

    public JSONObject set(String key, Object value) {
        put(key, value);
        return this;
    }

    public String[] getKeys() {
        return keySet().toArray(new String[0]);
    }

    public String getAsString(String key) {
        return getAsString(key, null);
    }

    public String getAsString(String key, String defaultValue) {
        Object v = get(key);
        return v != null ? v.toString() : defaultValue;
    }

    public int getAsInt(String key, int defaultValue) {
        return get(key) instanceof Number number ? number.intValue() : defaultValue;
    }

    public long getAsLong(String key, long defaultValue) {
        return get(key) instanceof Number number ? number.longValue() : defaultValue;
    }

    public float getAsFloat(String key, float defaultValue) {
        return get(key) instanceof Number number ? number.floatValue() : defaultValue;
    }

    public double getAsDouble(String key, double defaultValue) {
        return get(key) instanceof Number number ? number.doubleValue() : defaultValue;
    }

    public boolean getAsBoolean(String key, boolean defaultValue) {
        return get(key) instanceof Boolean b ? b : defaultValue;
    }

    public JSONObject getJSONObject(String key) {
        return get(key) instanceof JSONObject jsonObject ? jsonObject : null;
    }

    public JSONArray getJSONArray(String key) {
        return get(key) instanceof JSONArray objects ? objects : null;
    }

    public void update(JSONObject source) {
        for(Map.Entry<String,Object> entry : source.entrySet()) {
            if (containsKey(entry.getKey())) {
                Object target = get(entry.getKey());
                Object v = entry.getValue();
                if (v instanceof JSONObject valueObject) {
                    if (target instanceof JSONObject jsonObject) {
                        jsonObject.update(valueObject);
                    }
                } else if (v instanceof JSONArray jsonArray) {
                    if (target instanceof JSONArray objects) {
                        objects.update(jsonArray);
                    }
                } else if (target instanceof JSONObject || target instanceof JSONArray) {
                    // Compound values can not be replaced by primitive values
                } else {
                    put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    public void merge(JSONObject source) {
        for(Map.Entry<String,Object> entry : source.entrySet()) {
            Object target = get(entry.getKey());
            Object v = entry.getValue();
            if (target != null) {
                if (v instanceof JSONObject valueObject) {
                    if (target instanceof JSONObject jsonObject) {
                        jsonObject.merge(valueObject);
                    }
                } else if (v instanceof JSONArray valueArray) {
                    if (target instanceof JSONArray objects) {
                        objects.merge(valueArray);
                    }
                } else if (target instanceof JSONObject || target instanceof JSONArray) {
                    // Compound values can not be replaced by primitive values
                } else {
                    put(entry.getKey(), entry.getValue());
                }
            } else {
                /* New value */
                if (v instanceof JSONObject jsonObject) {
                    v = jsonObject.clone();
                } else if (v instanceof JSONArray objects) {
                    v = objects.clone();
                }
                put(entry.getKey(), v);
            }
        }
    }

    @Override
    public JSONObject clone() {
        JSONObject clone = (JSONObject) super.clone();
        // Create deep copy
        for (String key : clone.getKeys()) {
            Object value = clone.get(key);
            if (value instanceof JSONObject jsonObject) {
                clone.put(key, jsonObject.clone());
            } else if (value instanceof JSONArray objects) {
                clone.put(key, objects.clone());
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

    public static Object parseJSON(String input) throws ParseException {
        try {
            return Jsoner.deserialize(input);
        } catch (JsonException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }

    public static Object parseJSON(Reader input) throws ParseException {
        try {
            return Jsoner.deserialize(input);
        } catch (JsonException e) {
            throw new ParseException(e.getMessage(), e);
        }
    }

    public static JSONObject parseJSONObject(String input) throws ParseException {
        if (parseJSON(input) instanceof JSONObject jsonObject) {
            return jsonObject;
        }
        throw new ParseException("not a JSON object: " + input);
    }

    public static JSONObject parseJSONObject(Reader input) throws ParseException {
        if (parseJSON(input) instanceof JSONObject jsonObject) {
            return jsonObject;
        }
        throw new ParseException("not a JSON object: " + input);
    }
}
