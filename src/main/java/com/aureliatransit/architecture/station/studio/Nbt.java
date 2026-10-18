package com.aureliatransit.architecture.station.studio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * A small NBT reader and writer for schematic files, without Minecraft classes (the studio runs outside the game).
 * Values are plain Java objects: {@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float}, {@link Double},
 * {@link String}, {@code byte[]}, {@code int[]}, {@code long[]}, {@link List} (a list tag; an empty one is written as a list of
 * end tags) and {@link Map} (a compound, written in iteration order, so use a {@link LinkedHashMap} for a stable file).
 * The file is gzipped and its root compound has a name, as Minecraft's and WorldEdit's files do.
 *
 * <p>The output is deterministic: the same values give the same bytes (the gzip header holds no time).
 */
public final class Nbt {

	private static final int END = 0;
	private static final int BYTE = 1;
	private static final int SHORT = 2;
	private static final int INT = 3;
	private static final int LONG = 4;
	private static final int FLOAT = 5;
	private static final int DOUBLE = 6;
	private static final int BYTE_ARRAY = 7;
	private static final int STRING = 8;
	private static final int LIST = 9;
	private static final int COMPOUND = 10;
	private static final int INT_ARRAY = 11;
	private static final int LONG_ARRAY = 12;
	/** Deepest nesting the reader follows. */
	private static final int MAX_DEPTH = 64;

	private Nbt() {
	}

	/** A root compound and its name. */
	public record Root(String name, Map<String, Object> value) {
	}

	/** Gzipped NBT: the root compound {@code name} holding {@code value}. */
	public static byte[] writeGzip(String name, Map<String, Object> value) {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
			out.writeByte(COMPOUND);
			writeString(out, name);
			writePayload(out, value);
		} catch (IOException e) {
			throw new IllegalStateException("writing to memory failed", e);
		}
		return bytes.toByteArray();
	}

	/** Reads gzipped NBT written by {@link #writeGzip} (or by WorldEdit); throws {@link IllegalArgumentException} for anything else. */
	public static Root readGzip(byte[] gzip) {
		try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
			final int type = in.readUnsignedByte();
			if (type != COMPOUND) {
				throw new IllegalArgumentException("the root is not a compound");
			}
			final String name = readString(in);
			@SuppressWarnings("unchecked")
			final Map<String, Object> value = (Map<String, Object>) readPayload(in, COMPOUND, 0);
			return new Root(name, value);
		} catch (IOException e) {
			throw new IllegalArgumentException("not gzipped NBT: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private static void writePayload(DataOutputStream out, Object value) throws IOException {
		if (value instanceof Byte b) {
			out.writeByte(b);
		} else if (value instanceof Short s) {
			out.writeShort(s);
		} else if (value instanceof Integer i) {
			out.writeInt(i);
		} else if (value instanceof Long l) {
			out.writeLong(l);
		} else if (value instanceof Float f) {
			out.writeFloat(f);
		} else if (value instanceof Double d) {
			out.writeDouble(d);
		} else if (value instanceof String s) {
			writeString(out, s);
		} else if (value instanceof byte[] bytes) {
			out.writeInt(bytes.length);
			out.write(bytes);
		} else if (value instanceof int[] ints) {
			out.writeInt(ints.length);
			for (final int i : ints) {
				out.writeInt(i);
			}
		} else if (value instanceof long[] longs) {
			out.writeInt(longs.length);
			for (final long l : longs) {
				out.writeLong(l);
			}
		} else if (value instanceof List<?> list) {
			final int type = list.isEmpty() ? END : typeOf(list.get(0));
			out.writeByte(type);
			out.writeInt(list.size());
			for (final Object element : list) {
				if (typeOf(element) != type) {
					throw new IllegalArgumentException("a list holds one kind of tag");
				}
				writePayload(out, element);
			}
		} else if (value instanceof Map<?, ?> map) {
			for (final Map.Entry<String, Object> entry : ((Map<String, Object>) map).entrySet()) {
				out.writeByte(typeOf(entry.getValue()));
				writeString(out, entry.getKey());
				writePayload(out, entry.getValue());
			}
			out.writeByte(END);
		} else {
			throw new IllegalArgumentException("no NBT tag for " + (value == null ? "null" : value.getClass().getName()));
		}
	}

	private static int typeOf(Object value) {
		if (value instanceof Byte) {
			return BYTE;
		} else if (value instanceof Short) {
			return SHORT;
		} else if (value instanceof Integer) {
			return INT;
		} else if (value instanceof Long) {
			return LONG;
		} else if (value instanceof Float) {
			return FLOAT;
		} else if (value instanceof Double) {
			return DOUBLE;
		} else if (value instanceof String) {
			return STRING;
		} else if (value instanceof byte[]) {
			return BYTE_ARRAY;
		} else if (value instanceof int[]) {
			return INT_ARRAY;
		} else if (value instanceof long[]) {
			return LONG_ARRAY;
		} else if (value instanceof List<?>) {
			return LIST;
		} else if (value instanceof Map<?, ?>) {
			return COMPOUND;
		}
		throw new IllegalArgumentException("no NBT tag for " + (value == null ? "null" : value.getClass().getName()));
	}

	private static void writeString(DataOutputStream out, String text) throws IOException {
		final byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 0xFFFF) {
			throw new IllegalArgumentException("a string longer than 65535 bytes");
		}
		out.writeShort(bytes.length);
		out.write(bytes);
	}

	private static String readString(DataInputStream in) throws IOException {
		final byte[] bytes = new byte[in.readUnsignedShort()];
		in.readFully(bytes);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("nested too deep");
		}
		switch (type) {
			case BYTE:
				return in.readByte();
			case SHORT:
				return in.readShort();
			case INT:
				return in.readInt();
			case LONG:
				return in.readLong();
			case FLOAT:
				return in.readFloat();
			case DOUBLE:
				return in.readDouble();
			case STRING:
				return readString(in);
			case BYTE_ARRAY: {
				final byte[] bytes = new byte[length(in)];
				in.readFully(bytes);
				return bytes;
			}
			case INT_ARRAY: {
				final int[] ints = new int[length(in)];
				for (int i = 0; i < ints.length; i++) {
					ints[i] = in.readInt();
				}
				return ints;
			}
			case LONG_ARRAY: {
				final long[] longs = new long[length(in)];
				for (int i = 0; i < longs.length; i++) {
					longs[i] = in.readLong();
				}
				return longs;
			}
			case LIST: {
				final int elementType = in.readUnsignedByte();
				final int size = length(in);
				final List<Object> list = new ArrayList<>();
				for (int i = 0; i < size; i++) {
					list.add(readPayload(in, elementType, depth + 1));
				}
				return list;
			}
			case COMPOUND: {
				final Map<String, Object> map = new LinkedHashMap<>();
				for (int tag = in.readUnsignedByte(); tag != END; tag = in.readUnsignedByte()) {
					final String key = readString(in);
					map.put(key, readPayload(in, tag, depth + 1));
				}
				return map;
			}
			default:
				throw new IllegalArgumentException("unknown NBT tag " + type);
		}
	}

	private static int length(DataInputStream in) throws IOException {
		final int length = in.readInt();
		if (length < 0) {
			throw new IllegalArgumentException("negative length");
		}
		return length;
	}
}
