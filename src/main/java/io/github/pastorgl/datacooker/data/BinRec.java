/**
 * Copyright (C) 2022 Data Cooker Team and Contributors
 * This project uses New BSD license with do no evil clause. For full text, check the LICENSE file in the root directory.
 */
package io.github.pastorgl.datacooker.data;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.flatbuffers.ArrayReadWriteBuf;
import com.google.flatbuffers.FlexBuffers;
import com.google.flatbuffers.FlexBuffersBuilder;
import org.apache.commons.collections4.map.ListOrderedMap;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BinRec implements KryoSerializable {
    protected ListOrderedMap<String, Object> payload;
    protected FlexBuffers.Map source;
    protected byte[] bytes;

    public BinRec() {
        this.payload = new ListOrderedMap<>();
    }

    public BinRec(byte[] columns) {
        this.payload = new ListOrderedMap<>();
        this.payload.put("", columns);
        regen();
    }

    public BinRec(List<String> columns, Object[] payload) {
        this.payload = new ListOrderedMap<>();
        for (int i = 0; i < columns.size(); i++) {
            this.payload.put(columns.get(i), payload[i]);
        }
        regen();
    }

    public BinRec(Map<String, Object> payload) {
        this.payload = new ListOrderedMap<>();
        this.payload.putAll(payload);
        regen();
    }

    @Override
    public void write(Kryo kryo, Output output) {
        try {
            output.writeInt(bytes.length);
            output.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void read(Kryo kryo, Input input) {
        int length = input.readInt();
        bytes = input.readBytes(length);
        source = FlexBuffers.getRoot(new ArrayReadWriteBuf(bytes, bytes.length)).asMap();
        payload = new ListOrderedMap<>();
    }

    @Override
    public BinRec clone() {
        return new BinRec(this.asIs());
    }

    public Object asIs(String attr) {
        Object p = payload.get(attr);
        if (p == null) {
            p = fromRef(attr);
            payload.put(attr, p);
        }

        return p;
    }

    public String asString(String attr) {
        Object p = payload.get(attr);
        String s = null;
        if (!(p instanceof String)) {
            FlexBuffers.Reference r = getRef(attr);
            if (r.isString()) {
                s = r.asString();
            }
            if (r.isInt()) {
                s = String.valueOf(r.asLong());
            }
            if (r.isFloat()) {
                s = String.valueOf(r.asFloat());
            }
            if (r.isBlob()) {
                s = new String(r.asBlob().getBytes());
            }
            payload.put(attr, s);
        } else {
            s = (String) p;
        }

        return s;
    }

    private Object fromRef(String attr) {
        FlexBuffers.Reference r = getRef(attr);
        Object p = null;
        if (r.isString()) {
            p = r.asString();
        }
        if (r.isInt()) {
            p = r.asLong();
        }
        if (r.isFloat()) {
            p = r.asFloat();
        }
        if (r.isBlob()) {
            p = r.asBlob().getBytes();
        }
        if (r.isBoolean()) {
            p = null;
        }
        return p;
    }

    private FlexBuffers.Reference getRef(String attr) {
        FlexBuffers.KeyVector keys = source.keys();
        int size = keys.size();
        for (int i = 0; i < size; i++) {
            String key = keys.get(i).toString();
            if (attr.equals(key)) {
                return source.get(i);
            }
        }
        return source.get(size);
    }

    public int length() {
        return payload.size();
    }

    public ListOrderedMap<String, Object> asIs() {
        ListOrderedMap<String, Object> ret = new ListOrderedMap<>();
        if (!payload.isEmpty()) {
            payload.keyList().forEach(k -> ret.put(k, asIs(k)));
        } else {
            FlexBuffers.KeyVector keys = source.keys();
            int size = keys.size();
            for (int i = 0; i < size; i++) {
                String key = keys.get(i).toString();
                ret.put(key, asIs(key));
            }
        }
        return ret;
    }

    public byte[] raw() {
        return bytes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinRec)) return false;
        BinRec columnar = (BinRec) o;
        if (bytes == null) {
            regen();
        }
        if (columnar.bytes == null) {
            columnar.regen();
        }
        return Arrays.equals(bytes, columnar.bytes);
    }

    @Override
    public int hashCode() {
        if (bytes == null) {
            regen();
        }
        return Arrays.hashCode(bytes);
    }

    protected BinRec regen() {
        FlexBuffersBuilder fbb = new FlexBuffersBuilder();
        int start = fbb.startMap();
        for (int index = 0, size = payload.size(); index < size; index++) {
            Object value = payload.getValue(index);
            String key = payload.get(index);
            if ((value == null) && (source != null)) {
                value = fromRef(key);
            }

            if (value == null) {
                fbb.putBoolean(key, false);
            } else if (value instanceof Integer) {
                fbb.putInt(key, (Integer) value);
            } else if (value instanceof Double) {
                fbb.putFloat(key, (Double) value);
            } else if (value instanceof Long) {
                fbb.putInt(key, (Long) value);
            } else if (value instanceof byte[]) {
                fbb.putBlob(key, (byte[]) value);
            } else {
                fbb.putString(key, String.valueOf(value));
            }
        }
        fbb.endMap(null, start);
        ByteBuffer buffer = fbb.finish();
        int length = buffer.remaining();
        bytes = new byte[length];
        System.arraycopy(buffer.array(), 0, bytes, 0, length);
        source = FlexBuffers.getRoot(new ArrayReadWriteBuf(bytes, length)).asMap();
        return this;
    }
}
