/*
 * MIT License
 *
 * Copyright (c) 2018-2020 Falkreon (Isaac Ellingson)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package blue.endless.jankson;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import blue.endless.jankson.api.JanksonOps;
import blue.endless.jankson.api.Marshaller;
import blue.endless.jankson.impl.serializer.CommentSerializer;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObject extends JsonElement implements Map<String, JsonElement> {
	/** This pattern matches JsonObject keys that are permitted to appear unquoted */
	private static final Predicate<String> CAN_BE_UNQUOTED = Pattern.compile("^[a-zA-Z0-9]+$").asPredicate();
	@SuppressWarnings("deprecation")
	protected Marshaller marshaller = blue.endless.jankson.impl.MarshallerImpl.getFallback();
	private List<Entry> entries = new ArrayList<>();

	public void dataFix(@NotNull DataFixer dataFixer, int newVersion) {
		boolean shouldAddVersion = true;
		String versionKey = "jankson:schema_version";

		for (Entry entry : entries) {
			if (versionKey.equals(entry.key)) {
				shouldAddVersion = false;
				break;
			}
		}
		if (shouldAddVersion) {
			entries.add(
				0,
				new Entry(
					"The version of this JSON file\nDon't modify!",
					versionKey,
					JanksonOps.INSTANCE.createNumeric(newVersion)
				)
			);
		}

		Entry versionEntry = null;
		for (Entry entry : entries) {
			if (versionKey.equals(entry.key)) {
				versionEntry = entry;
				break;
			}
		}
        assert versionEntry != null;
        int version = ((JsonPrimitive) versionEntry.value).asInt(newVersion);

		for (Entry entry : entries) {
			Dynamic<JsonElement> dynamic = new Dynamic<>(JanksonOps.INSTANCE, entry.value);
            entry.value = dataFixer.update(JanksonOps.TYPE, dynamic, version, newVersion).getValue();
		}
	}
	
	/**
	 * If there is an entry at this key, and that entry is a json object, return it. Otherwise returns null.
	 */
	@Nullable
	public JsonObject getObject(@NotNull String name) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(name)) {
				if (entry.value instanceof JsonObject object) {
					return object;
				} else {
					return null;
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Replaces a key-value mapping in this object if it exists, or adds the mapping to the end of the object if it
	 * doesn't. Returns the old value mapped to this key if there was one.
	 */
	public JsonElement put(@NotNull String key, @NotNull JsonElement elem, @Nullable String comment) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(key)) {
				JsonElement result = entry.value;
				entry.value = elem;
				entry.setComment(comment);
				return result;
			}
		}
		
		//If we reached here, there's no existing mapping, so make one.
		Entry entry = new Entry();
		if (elem instanceof JsonObject) ((JsonObject)elem).marshaller = marshaller;
		if (elem instanceof JsonArray) ((JsonArray)elem).marshaller = marshaller;
		entry.key = key;
		entry.value = elem;
		entry.setComment(comment);
		entries.add(entry);
		return null;
	}
	
	@NotNull
	public JsonElement putDefault(@NotNull String key, @NotNull JsonElement elem, @Nullable String comment) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(key)) {
				return entry.value;
			}
		}
		
		//If we reached here, there's no existing mapping, so make one.
		Entry entry = new Entry();
		entry.key = key;
		entry.value = elem;
		entry.setComment(comment);
		entries.add(entry);
		return elem;
	}
	
	/** May return null if the existing object can't be marshalled to elem's class */
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T putDefault(@NotNull String key, @NotNull T elem, @Nullable String comment) {
		return (T) putDefault(key, elem, elem.getClass(), comment);
	}
	
	/** May return null if the existing object can't be marshalled to the target class */
	@Nullable
	public <T> T putDefault(@NotNull String key, @NotNull T elem, Class<? extends T> clazz, @Nullable String comment) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(key)) {
				return marshaller.marshall(clazz, entry.value);
			}
		}
		
		//If we reached here, there's no existing mapping, so make one.
		Entry entry = new Entry();
		entry.key = key;
		entry.value = marshaller.serialize(elem);
		if (entry.value==null) entry.value = JsonNull.INSTANCE;
		entry.setComment(comment);
		entries.add(entry);
		return elem;
	}
	
	/**
	 * Gets a minimal set of key-value-comment settings which, if added to the supplied JsonObject, would produce this
	 * JsonObject. See BasicTests::testDiffAgainstDefaults() for more details on this comparison.
	 * 
	 * <ul>
	 *   <li>If a key is present in the default and not in the object, it's skipped
	 *   <li>If a key is an object, a deep (recursive) comparison occurs. Comments are ignored in this comparison.
	 *   <li>All other types, including lists, receive a shallow comparison of its value. The comment is ignored in this comparison.
	 *   <li>Whether deep or shallow, if the key is found to be identical in value to its default, it is skipped.
	 *   <li>If the key is found to be different than its default, the key, value, and comment are represented in the
	 *       output.
	 * </ul>
	 */
	@NotNull
	public JsonObject getDelta(@NotNull JsonObject defaults) {
		JsonObject result = new JsonObject();
		for (Entry entry : entries) {
			String key = entry.key;
			JsonElement defaultValue = defaults.get(key);
			if (defaultValue==null) {
				result.put(entry.key, entry.value, entry.getComment());
				continue;
			}
			
			if (entry.value instanceof JsonObject object && defaultValue instanceof JsonObject defaultObject) {
				JsonObject subDelta = object.getDelta(defaultObject);
				if (!subDelta.isEmpty())
					result.put(entry.key, subDelta, entry.getComment());
				continue;
			}
			
			if (entry.value.equals(defaultValue)) continue;
			
			result.put(entry.key, entry.value, entry.getComment());
		}
		
		return result;
	}
	
	/**
	 * Returns the comment "attached to" a given key-value mapping, which is to say, the comment appearing immediately
	 * before it or the single-line comment to the right of it.
	 */
	@Nullable
	public String getComment(@NotNull String name) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(name)) {
				return entry.getComment();
			}
		}
		
		return null;
	}
	
	public void setComment(@NotNull String name, @Nullable String comment) {
		for(Entry entry : entries) {
			if (entry.key.equalsIgnoreCase(name)) {
				entry.setComment(comment);
				return;
			}
		}
	}
	
	@Override
	public String toJson(boolean comments, boolean newlines, int depth) {
		JsonGrammar grammar = JsonGrammar.builder().withComments(comments).printWhitespace(newlines).build();
		return toJson(grammar, depth);
	}
	
	@Override
	public void toJson(Writer w, JsonGrammar grammar, int depth) throws IOException {
		//StringBuilder builder = new StringBuilder();
		boolean skipBraces = depth==0 && grammar.bareRootObject;
		int effectiveDepth = (grammar.bareRootObject) ? depth-1 : depth;
		int nextDepth = (grammar.bareRootObject) ? depth : depth+1; 
		
		if (!skipBraces) {
			w.append("{");
			
			if (grammar.printWhitespace && entries.size()>0) {
				w.append('\n');
			} else {
				w.append(' ');
			}
		}
		
		for(int i=0; i<entries.size(); i++) {
			Entry entry = entries.get(i);
			
			if (grammar.printWhitespace) {
				for(int j=0; j<nextDepth; j++) {
					w.append("\t");
				}
			}
			
			CommentSerializer.print(w, entry.getComment(), effectiveDepth, grammar);
			
			boolean quoted = !grammar.printUnquotedKeys;
			
			//If it can't be unquoted, quote it anyway
			if (!CAN_BE_UNQUOTED.test(entry.key)) {
				quoted = true;
			}
			
			if (quoted) w.append("\"");
			w.append(entry.key);
			if (quoted) w.append("\""); 
			w.append(": ");
			w.append(entry.value.toJson(grammar, depth+1));
			
			if (grammar.printCommas) { 
				if (i<entries.size()-1 || grammar.printTrailingCommas) {
					w.append(",");
					if (i<entries.size()-1 && !grammar.printWhitespace) w.append(' ');
				}
			} else if (!grammar.printWhitespace) {
				w.append(" ");
			}
			
			if (grammar.printWhitespace) {
				w.append('\n');
			}
		}
		
		if (!skipBraces) {
			if (!entries.isEmpty()) {
				if (grammar.printWhitespace) {
					for(int j=0; j<effectiveDepth; j++) {
						w.append("\t");
					}
				} else {
					w.append(' ');
				}
			}
			
			w.append("}");
		}
	}
	
	@Override
	public String toString() {
		return toJson(true, false, 0);
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof JsonObject otherObject)) return false;
		if (entries.size() != otherObject.entries.size()) return false;
		
		//Lists are identical sizes, but if the contents, comments, or ordering are at all different, fail them
		for(int i=0; i < entries.size(); i++) {
			Entry a = entries.get(i);
			Entry b = otherObject.entries.get(i);
			
			if (!a.equals(b)) return false;
		}
		
		return true;
	}
	
	@Override
	public int hashCode() {
		return entries.hashCode();
	}
	
	public void setMarshaller(Marshaller marshaller) {
		this.marshaller = marshaller;
	}
	
	public Marshaller getMarshaller() {
		return this.marshaller;
	}
	
	@Nullable
	public <E> E get(@NotNull Class<E> clazz, @NotNull String key) {
		if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");
		
		JsonElement elem = get(key);
		return marshaller.marshall(clazz, elem);
	}
	
	//Convenience getters
	
	public boolean getBoolean(@NotNull String key, boolean defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asBoolean(defaultValue);
		}
		return defaultValue;
	}
	
	public byte getByte(@NotNull String key, byte defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return ((JsonPrimitive)elem).asByte(defaultValue);
		}
		return defaultValue;
	}
	
	public char getChar(@NotNull String key, char defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asChar(defaultValue);
		}
		return defaultValue;
	}
	
	public short getShort(@NotNull String key, short defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asShort(defaultValue);
		}
		return defaultValue;
	}
	
	public int getInt(@NotNull String key, int defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asInt(defaultValue);
		}
		return defaultValue;
	}
	
	public long getLong(@NotNull String key, long defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asLong(defaultValue);
		}
		return defaultValue;
	}
	
	public float getFloat(@NotNull String key, float defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asFloat(defaultValue);
		}
		return defaultValue;
	}
	
	public double getDouble(@NotNull String key, double defaultValue) {
		JsonElement elem = get(key);
		if (elem instanceof JsonPrimitive primitive) {
			return primitive.asDouble(defaultValue);
		}
		return defaultValue;
	}
	
	/**
	 * Gets a (potentially nested) element from this object if it exists.
	 * @param clazz The expected class of the element
	 * @param key   The keys of the nested elements, separated by periods, such as "foo.bar.baz"
	 * @return The element at that location, if it exists and is of the proper type, otherwise null.
	 */
	@Nullable
	public <E> E recursiveGet(@NotNull Class<E> clazz, @NotNull String key) {
		if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");
		String[] parts = key.split("\\.");
		JsonObject cur = this;
		for(int i=0; i<parts.length; i++) {
			String s = parts[i];
			if (s.isEmpty()) throw new IllegalArgumentException("Cannot get from broken key '"+key+"'");
			JsonElement elem = cur.get(s);
			if (i<parts.length-1) {
				//elem must be a JsonObject or we're sunk
				if (elem instanceof JsonObject object) {
					cur = object;
				} else {
					return null;
				}
			} else {
				return marshaller.marshall(clazz, elem);
				
				/*
				if (clazz.isAssignableFrom(elem.getClass())) {
					return (E) elem;
				} else {
					return null;
				}*/
			}
		}
		throw new IllegalArgumentException("Cannot get from broken key '"+key+"'");
	}
	
	/**
	 * Gets a (potentially nested) element from this object if it exists, or creates it and any intermediate objects
	 * needed to put it at the indicated location in the hierarchy.
	 * @param clazz The expected class of the element
	 * @param key   The keys of the nested elements, separated by periods, such as "foo.bar.baz"
	 * @return The element at that location if it exists, or the newly-created element if it did not previously exist.
	 */
	@SuppressWarnings("unchecked")
	public <E extends JsonElement> E recursiveGetOrCreate(@NotNull Class<E> clazz, @NotNull String key, @NotNull E fallback, @Nullable String comment) {
		if (key.isEmpty()) throw new IllegalArgumentException("Cannot get from empty key");
		String[] parts = key.split("\\.");
		JsonObject cur = this;
		for(int i=0; i<parts.length; i++) {
			String s = parts[i];
			if (s.isEmpty()) throw new IllegalArgumentException("Cannot get from broken key '"+key+"'");
			JsonElement elem = cur.get(s);
			if (i<parts.length-1) {
				//elem must be a JsonObject or we're sunk
				if (elem instanceof JsonObject object) {
					cur = object;
				} else {
					JsonObject replacement = new JsonObject();
					cur.put(s, replacement);
					cur = replacement;
				}
			} else {
				if (elem != null && clazz.isAssignableFrom(elem.getClass())) {
					return (E) elem;
				} else {
					E result = (E) fallback.clone();
					cur.put(s, result, comment);
					return result;
				}
			}
		}
		
		throw new IllegalArgumentException("Cannot get from broken key '"+key+"'");
	}
	
	
	private static final class Entry {
		private String comment;
		private String key;
		private JsonElement value;

		private Entry(String comment, String key, JsonElement value) {
			this.comment = comment;
			this.key = key;
			this.value = value;
		}

		private Entry() {
			this(null, null, null);
		}
		
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Entry o)) return false;
			if (!comment.equals(o.comment)) return false;
			if (!key.equals(o.key)) return false;
			if (!value.equals(o.value)) return false;
			
			return true;
		}
		
		@Override
		public int hashCode() {
			return Objects.hash(comment, key, value);
		}
		
		public String getComment() {
			return this.comment;
		}
		
		public void setComment(String comment) {
			if (comment!=null && !comment.trim().isEmpty()) {
				this.comment = comment;
			} else {
				this.comment = null;
			}
		}
	}

	//implements Cloneable {
	
		@Override
		public JsonObject clone() {
			JsonObject result = new JsonObject();
			for(Entry entry : entries) {
				result.put(entry.key, entry.value.clone(), entry.comment);
			}
			result.marshaller = marshaller;
			return result;
		}
	
	//}
	
	//implements Map<JsonElement> {
		
		/**
		 * Replaces a key-value mapping in this object if it exists, or adds the mapping to the end of the object if it
		 * doesn't. Returns the old value mapped to this key if there was one.
		 */
		@Override
		@Nullable
		public JsonElement put(@NotNull String key, @NotNull JsonElement elem) {
			for(Entry entry : entries) {
				if (entry.key.equalsIgnoreCase(key)) {
					JsonElement result = entry.value;
					entry.value = elem;
					return result;
				}
			}
			
			//If we reached here, there's no existing mapping, so make one.
			Entry entry = new Entry();
			entry.key = key;
			entry.value = elem;
			entries.add(entry);
			return null;
		}
		
		@Override
		public void clear() {
			entries.clear();
		}
	
		@Override
		public boolean containsKey(@Nullable Object key) {
			if (key==null) return false;
			if (!(key instanceof String)) return false;
			
			for(Entry entry : entries) {
				if (entry.key.equalsIgnoreCase((String)key)) {
					return true;
				}
			}
			
			return false;
		}
	
		@Override
		public boolean containsValue(@Nullable Object val) {
			if (val==null) return false;
			if (!(val instanceof JsonElement)) return false;
			
			for(Entry entry : entries) {
				if (entry.value.equals(val)) return true;
			}
			
			return false;
		}
		
		/**
		 * Creates a semi-live shallow copy instead of a live view
		 */
		@Override
		public Set<Map.Entry<String, JsonElement>> entrySet() {
			Set<Map.Entry<String, JsonElement>> result = new LinkedHashSet<>();
			for(Entry entry : entries) {
				result.add(new Map.Entry<String, JsonElement>(){
					@Override
					public String getKey() {
						return entry.key;
					}
	
					@Override
					public JsonElement getValue() {
						return entry.value;
					}
	
					@Override
					public JsonElement setValue(JsonElement value) {
						JsonElement oldValue = entry.value;
						entry.value = value;
						return oldValue;
					}
					
				});
			}
			
			return result;
		}
	
		@Override
		@Nullable
		public JsonElement get(@Nullable Object key) {
			if (!(key instanceof String str)) return null;
			
			for(Entry entry : entries) {
				if (entry.key.equalsIgnoreCase(str)) {
					return entry.value;
				}
			}
			return null;
		}
	
		@Override
		public boolean isEmpty() {
			return entries.isEmpty();
		}
	
		/** Returns a defensive copy instead of a live view */
		@Override
		@NotNull
		public Set<String> keySet() {
			Set<String> keys = new HashSet<>();
			for(Entry entry : entries) {
				keys.add(entry.key);
			}
			return keys;
		}
	
		@Override
		public void putAll(Map<? extends String, ? extends JsonElement> map) {
			for(Map.Entry<? extends String, ? extends JsonElement> entry : map.entrySet()) {
				put(entry.getKey(), entry.getValue());
			}
		}

		@Override
		@Nullable
		public JsonElement remove(@Nullable Object key) {
			if (!(key instanceof String str)) return null;

			for(int i=0; i<entries.size(); i++) {
				Entry entry = entries.get(i);
				if (entry.key.equalsIgnoreCase(str)) {
					return entries.remove(i).value;
				}
			}
			return null;
		}
	
		@Override
		public int size() {
			return entries.size();
		}
	
		@Override
		public Collection<JsonElement> values() {
			List<JsonElement> values = new ArrayList<>();
			for(Entry entry : entries) {
				values.add(entry.value);
			}
			return values;
		}
	//}
}
