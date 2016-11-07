package io.client.thrift;

import static java.lang.reflect.Modifier.ABSTRACT;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * JSON 序列化和反序列化工具类
 * 
 * @author HouKangxi
 *
 */
public class Json {
	// 系统ClassLoader, 一般是null, 取决于vm 实现
	private static final ClassLoader ROOT_CLASSLOADER = Object.class.getClassLoader();

	/**
	 * JSON序列(和反序列)化策略
	 */
	public static class Strategy {
		int excludeModifiers = Modifier.TRANSIENT | Modifier.STATIC;

		/**
		 * 返回字段名，如果返回null,则表示忽略这个字段
		 * 
		 * @param field
		 * @return
		 */
		public String fieldName(Field field) {
			if (excludeClass(field.getType(), true))
				return null;
			return field.getName();
		}

		public Field field(Class<?> cls, String fieldName) throws NoSuchFieldException, SecurityException {
			if (excludeClass(cls, false))
				return null;
			Field f = cls.getDeclaredField(fieldName);
			if ((f.getModifiers() & excludeModifiers) != 0) {
				return null;
			}
			f.setAccessible(true);
			return f;
		}

		/**
		 * 是否忽略指定class
		 * 
		 * @param clazz
		 * @param serialize
		 *            - 是否在序列化过程中(Object to JSON)
		 * @return
		 */
		public boolean excludeClass(Class<?> clazz, boolean serialize) {
			return false;
		}

		public void excludeFieldsWithModifiers(int... modifiers) {
			excludeModifiers = 0;
			for (int modifier : modifiers) {
				excludeModifiers |= modifier;
			}
		}

		public void publicFieldsOnly() {
			excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.PROTECTED, Modifier.STATIC, Modifier.TRANSIENT);
		}
	}

	public static <T> T fromJson(String json, Type type) throws Exception {
		return fromJson(json, type, new Strategy());
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T fromJson(String json, Type type, Strategy strategy) throws Exception {
		if (type instanceof Class) {
			Class cls = (Class) type;
			if (strategy.excludeClass(cls, false))
				return null;
			if (cls.isArray()) {
				JSONArray arr = new JSONArray(json);
				Class et = cls.getComponentType();
				Object a = Array.newInstance(et, arr.length());
				for (int i = 0; i < arr.length(); i++) {
					Array.set(a, i, fromJson(toJson(et, arr.get(i), strategy), et, strategy));
				}
				return (T) a;
			} else if (cls.getClassLoader() == ROOT_CLASSLOADER) {
				JSONTokener tk = new JSONTokener(json);
				return (T) tk.nextValue();
			} else {// 自定义类型
				JSONObject jo = new JSONObject(json);
				Iterator keys = jo.keys();
				Object map = cls.newInstance();
				while (keys.hasNext()) {
					String k = (String) keys.next();
					try {
						Field f = strategy.field(cls, k);
						if (f != null) {
							Type vt = f.getGenericType();
							Object ov = fromJson(toJson(vt, jo.get(k), strategy), vt, strategy);
							f.set(map, ov);
						}
					} catch (NoSuchFieldException e) {
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				return (T) map;
			}
		} else if (type instanceof ParameterizedType) {
			ParameterizedType pt = (ParameterizedType) type;
			Class cls = (Class) pt.getRawType();
			if (List.class.isAssignableFrom(cls)) {
				JSONArray arr = new JSONArray(json);
				Type et = pt.getActualTypeArguments()[0];
				List list = (cls.getModifiers() & ABSTRACT) != 0 ? new ArrayList(arr.length())
						: ((List) cls.newInstance());
				for (int i = 0; i < arr.length(); i++) {
					Object ov = fromJson(toJson(et, arr.get(i), strategy), et, strategy);
					list.add(ov);
				}
				return (T) list;
			} else if (Set.class.isAssignableFrom(cls)) {
				JSONArray arr = new JSONArray(json);
				Type et = pt.getActualTypeArguments()[0];
				Set set = (cls.getModifiers() & ABSTRACT) != 0 ? new HashSet(arr.length()) : ((Set) cls.newInstance());
				for (int i = 0; i < arr.length(); i++) {
					Object ov = fromJson(toJson(et, arr.get(i), strategy), et, strategy);
					set.add(ov);
				}
				return (T) set;
			} else if (Map.class.isAssignableFrom(cls)) {
				JSONObject jo = new JSONObject(json);
				Map map = (cls.getModifiers() & ABSTRACT) != 0 ? new HashMap(jo.length()) : ((Map) cls.newInstance());
				// Type kt = pt.getActualTypeArguments()[0];
				Type vt = pt.getActualTypeArguments()[1];
				Iterator keys = jo.keys();
				while (keys.hasNext()) {
					Object k = keys.next();
					Object ov = fromJson(toJson(vt, jo.get(String.valueOf(k)), strategy), vt, strategy);
					map.put(k, ov);
				}
				return (T) map;
			}
		}
		return null;
	}

	public static String toJson(Object obj) {
		if (obj == null) {
			return "null";
		}
		return toJson(obj, new Strategy());
	}

	public static String toJson(Object obj, Strategy strategy) {
		if (obj == null) {
			return "null";
		}
		return toJson(obj.getClass(), obj, strategy);
	}

	public static String toJson(Type oType, Object obj, Strategy strategy) {
		if (obj == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder(256);
		toJson(oType, obj, sb, strategy);
		return sb.toString();
	}

	@SuppressWarnings("rawtypes")
	public static void toJson(Type oType, Object obj, StringBuilder sb, Strategy strategy) {

		if (obj == null) {
			sb.append(JSONObject.NULL);
			return;
		}
		if (obj instanceof JSONObject || obj instanceof JSONArray) {
			sb.append(obj);
			return;
		}
		Class clazz = obj.getClass();
		if (oType instanceof Class) {
			Class cls = (Class) oType;
			if (strategy.excludeClass(cls, true))
				return;
			if (obj instanceof CharSequence) {
				sb.append('"').append(obj).append('"');
			} else if (clazz.isArray()) {
				sb.append('[');
				Class et = clazz.getComponentType();
				for (int i = 0, len = Array.getLength(obj); i < len; i++) {
					Object oe = Array.get(obj, i);
					toJson(oe != null ? oe.getClass() : et, oe, sb, strategy);
					sb.append(',');
				}
				if (sb.charAt(sb.length() - 1) == ',')
					sb.deleteCharAt(sb.length() - 1);
				sb.append(']');
			} else if (cls.getClassLoader() == ROOT_CLASSLOADER) {
				sb.append(obj);
			} else {
				// 用户自定义的类型
				Field[] fs = clazz.getDeclaredFields();
				sb.append('{');
				for (Field f : fs) {
					if ((f.getModifiers() & strategy.excludeModifiers) != 0)
						continue;
					String fname = strategy.fieldName(f);
					if (fname == null)
						continue;
					try {
						f.setAccessible(true);
						Object v = f.get(obj);
						if (v != null) {
							sb.append('"').append(fname).append('"').append(':');
							toJson(f.getGenericType(), v, sb, strategy);
							sb.append(',');
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (sb.charAt(sb.length() - 1) == ',')
					sb.deleteCharAt(sb.length() - 1);
				sb.append('}');
			}
		} else {
			if (obj instanceof Collection) {
				sb.append('[');
				Collection col = (Collection) obj;
				Type eType = null;
				if (oType instanceof ParameterizedType) {
					ParameterizedType ptype = (ParameterizedType) oType;
					eType = ptype.getActualTypeArguments()[0];
				}
				for (Object o : col) {
					toJson(eType, o, sb, strategy);
					sb.append(',');
				}
				if (sb.charAt(sb.length() - 1) == ',')
					sb.deleteCharAt(sb.length() - 1);
				sb.append(']');
			} else if (obj instanceof Map) {
				sb.append('{');
				Map<?, ?> m = (Map) obj;
				Type vType = null;
				if (oType instanceof ParameterizedType) {
					ParameterizedType ptype = (ParameterizedType) oType;
					vType = ptype.getActualTypeArguments()[1];
				}
				for (Map.Entry e : m.entrySet()) {
					sb.append('"').append(e.getKey()).append('"').append(':');
					Object ev = e.getValue();
					toJson(vType, ev, sb, strategy);
					sb.append(',');
				}
				if (sb.charAt(sb.length() - 1) == ',')
					sb.deleteCharAt(sb.length() - 1);
				sb.append('}');
			} else {
				sb.append(obj);
			}
		}
	}
}