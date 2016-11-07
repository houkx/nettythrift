/**
 * 
 */
package io.client.thrift;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.client.thrift.annotaion.Index;

/**
 * Protocol读写工具类
 * 
 * @author HouKangxi
 *
 */
class ProtocolIOUtil {
	public static <T> T read(TCompactProtocol reader, Type resultBeanClass, Class<?>[] exceptionsTypes, int seqid_)
			throws Throwable {
		Object[] msg = reader.readMessageBegin();
		String methodName = (String) msg[0];
		int type = ((Number) msg[1]).intValue();
		int seqid = ((Number) msg[2]).intValue();
		if (type == 3) {
			TApplicationException x = TApplicationException.read(reader);
			reader.readMessageEnd();
			throw x;
		}
		// 验证seqId 与请求的是否相同
		if (seqid != seqid_) {
			throw new TApplicationException(TApplicationException.BAD_SEQUENCE_ID,
					methodName + " failed: out of sequence response: " + seqid + " != " + seqid_);
		}
		// -- read Struct_result START --
		reader.readStructBegin();
		int field = reader.readFieldBegin();
		int fieldType = ((field >> 16) & 0x0000ffff);
		short fieldId = (short) (field & 0x0000ffff);
		// class ${interfaceMethod}_result{id0:success id1: exception}
		if (fieldId == 0) {
			H h = ProtocolIOUtil.getH(fieldType);
			@SuppressWarnings("unchecked")
			T bean = (T) h.read(reader, resultBeanClass);
			reader.readFieldEnd();

			reader.readStructEnd();
			// -- read Struct_result END --

			reader.readMessageEnd();
			//
			return bean;
		} else if (exceptionsTypes != null && exceptionsTypes.length > 0) {
			Throwable ex = null;
			if (fieldType != 0 && fieldId < exceptionsTypes.length) {
				Class<?> exClass = exceptionsTypes[fieldId - 1];
				H h = ProtocolIOUtil.getH(fieldType);
				if (h != null) {
					Object oex = h.read(reader, exClass);
					if (oex instanceof Throwable) {
						ex = (Throwable) oex;
					}
				}
			}
			reader.readFieldEnd();
			reader.readStructEnd();
			reader.readMessageEnd();
			if (ex != null) {
				throw ex;
			}
		}
		return null;
	}

	public static void write(String methodName, int seqid, TCompactProtocol writer, Type[] parmTypes, Object[] args)
			throws Exception {
		writer.writeMessageBegin(methodName, (byte) 1, seqid);
		//
		writer.writeStructBegin();
		for (int i = 0, len = parmTypes.length; i < len; i++) {
			Object v = args[i];
			if (v != null) {
				Type t = parmTypes[i];
				H h = getH(t);
				writer.writeFieldBegin((h.type << 16) | (i + 1));
				h.write(writer, t, v);
				writer.writeFieldEnd();
			}
		}
		writer.writeFieldStop();
		writer.writeStructEnd();
		//
		writer.writeMessageEnd();
		writer.getTransport().flush();
	}

	private static final Map<Object, H> class2type = new HashMap<Object, H>(16);

	static class H {
		byte type;
		Type jType;

		H(int t) {
			type = (byte) t;
		}

		void write(TCompactProtocol writer, Type _t, Object bean) throws Exception {
			Class<?> beanClass = (Class<?>) _t;
			writer.writeStructBegin();
			if (bean != null) {
				Field[] fs = sortFields(beanClass);
				short fieldId = 0;
				for (Field f : fs) {
					fieldId++;
					Object v = f.get(bean);
					if (v != null) {
						Type t = f.getGenericType();
						H h = getH(t);
						int field = (h.type << 16) | fieldId;
						writer.writeFieldBegin(field);
						h.write(writer, t, v);
						writer.writeFieldEnd();
					}
				}
			}
			writer.writeFieldStop();
			writer.writeStructEnd();
		}

		@SuppressWarnings("rawtypes")
		Object read(TCompactProtocol reader, Type beanType) throws Exception {
			//
			Object bean = null;
			Class beanClass = (Class) beanType;
			try {
				bean = beanClass.newInstance();
			} catch (Exception e) {
				return null;
			}
			if (bean == null) {
				return null;
			}
			Field[] fs = ProtocolIOUtil.sortFields(beanClass);
			reader.readStructBegin();
			while (true) {
				int field = reader.readFieldBegin();
				int fieldType = ((field >> 16) & 0x0000ffff);
				short fieldId = (short) (field & 0x0000ffff);
				if (fieldType != 0 && fieldId <= fs.length) {
					// normal
					Field f = fs[fieldId - 1];
					H h = ProtocolIOUtil.getH(fieldType);
					Object v = h.read(reader, f.getGenericType());
					if (v != null) {
						try {
							f.set(bean, v);
						} catch (Exception e) {
						}
					}
					reader.readFieldEnd();
				} else if (fieldType == 0) {
					// Stop
					reader.readFieldEnd();
					break;
				} else if (fieldId > fs.length) {
					// skip
					skip(reader, (byte) fieldType);
				}
			}
			reader.readStructEnd();
			return bean;
		}
		//
		// @Override
		// public String toString() {
		// return "H [type=" + type + ", jType=" + jType + "]";
		// }
	}

	// static void skip(TCompactProtocol reader, int fieldType) {
	// H h = ProtocolIOUtil.getH(fieldType);
	// try {
	// h.read(reader, h.jType);
	// reader.readFieldEnd();
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// }

	static void skip(TCompactProtocol prot, byte type) throws Exception {
		skip(prot, type, 512);
	}

	static void skip(TCompactProtocol prot, byte type, int maxDepth) throws Exception {
		if (maxDepth <= 0) {
			throw new Exception("Maximum skip depth exceeded");
		}
		switch (type) {
		case 2/* TType.BOOL */:
			prot.readBool();
			break;

		case 3/* TType.BYTE */:
			prot.readByte();
			break;

		case 6/* TType.I16 */:
			prot.readI16();
			break;

		case 8/* TType.I32 */:
			prot.readI32();
			break;

		case 10/* TType.I64 */:
			prot.readI64();
			break;

		case 4/* TType.DOUBLE */:
			prot.readDouble();
			break;

		case 11/* TType.STRING */:
			prot.readBinary();
			break;

		case 12/* TType.STRUCT */:
			prot.readStructBegin();
			while (true) {
				int field = prot.readFieldBegin();
				byte fieldType = (byte) ((field >> 16) & 0xff);
				if (fieldType == 0/* TType.STOP */) {
					break;
				}
				skip(prot, fieldType, maxDepth - 1);
				prot.readFieldEnd();
			}
			prot.readStructEnd();
			break;

		case 13/* TType.MAP */: {
			long map = prot.readMapBegin();
			final int size = (int) (map & 0x7FFFFFFFL);
			map >>= 32;
			final byte keyType = (byte) ((map & 0xFF00) >> 8);
			final byte valueType = (byte) (map & 0x00FF);
			for (int i = 0; i < size; i++) {
				skip(prot, keyType, maxDepth - 1);
				skip(prot, valueType, maxDepth - 1);
			}
			prot.readMapEnd();
			break;
		}
		case 14/* TType.SET */: {
			final long set = prot.readSetBegin();
			final int size = (int) (set & 0x7FFFFFFFL);
			final byte elemType = (byte) ((set >> 32) & 0xFF);
			for (int i = 0; i < size; i++) {
				skip(prot, elemType, maxDepth - 1);
			}
			prot.readSetEnd();
			break;
		}
		case 15/* TType.LIST */: {
			final long list = prot.readListBegin();
			final int size = (int) (list & 0x7FFFFFFFL);
			final byte elemType = (byte) ((list >> 32) & 0xFF);
			for (int i = 0; i < size; i++) {
				skip(prot, elemType, maxDepth - 1);
			}
			prot.readListEnd();
			break;
		}
		default:
			break;
		}
	}

	// 解析字段的缓存
	private static ConcurrentHashMap<Class<?>, Field[]> fieldsCache = new ConcurrentHashMap<Class<?>, Field[]>();

	static Field[] sortFields(Class<?> beanClass) {
		Field[] fs = fieldsCache.get(beanClass);
		if (fs == null) {
			Field[] _fs = beanClass.getDeclaredFields();
			// Field[] _fs = beanClass.getFields();
			List<Field> flist = new ArrayList<Field>(_fs.length);
			for (Field f : _fs) {
				int md = f.getModifiers();
				if (Modifier.isStatic(md) || Modifier.isTransient(md)) {
					continue;
				}
				String name = f.getName();
				if (name.equals("class") || name.startsWith("__isset_")) {// 排除
																			// __isset_bitFields标记
					continue;
				}
				if (f.getType().isArray()) {// thrift 不支持数组，支持List
					// 排除 optionals 字段
					continue;
				}
				f.setAccessible(true);
				flist.add(f);
			}
			fs = flist.toArray(new Field[0]);
			Arrays.sort(fs, new BeanFieldCmp(beanClass));
			fieldsCache.putIfAbsent(beanClass, fs);
		}
		return fs;
	}

	private static final H structH = new H(12);

	/**
	 * 排序--优先按@Index注解值排序，并兼任旧的thrift接口包
	 * 
	 * @author HouKangxi
	 *
	 */
	private static final class BeanFieldCmp implements Comparator<Field> {
		final Class<?> beanClass;
		Method mfind;
		Method m_getThriftFieldId;
		boolean hasTryFind_Field;

		public BeanFieldCmp(Class<?> beanClass) {
			this.beanClass = beanClass;
		}

		int id4name(String name) {
			try {
				Number num = (Number) m_getThriftFieldId.invoke(mfind.invoke(null, name));
				return num.intValue();
			} catch (Exception e) {
			}
			return Integer.MAX_VALUE;
		}

		public int compare(Field o1, Field o2) {
			int ord1 = Integer.MAX_VALUE, ord2 = ord1;
			Index index1 = o1.getAnnotation(Index.class);
			Index index2 = o2.getAnnotation(Index.class);

			if (index1 != null) {
				ord1 = index1.value();
			} else if (mfind != null || !hasTryFind_Field) {
				if (!hasTryFind_Field) {
					hasTryFind_Field = true;
					Class<?>[] cs = beanClass.getClasses();
					for (Class<?> c : cs) {
						if (c.getSimpleName().equals("_Fields")) {
							try {
								Method mfind = c.getMethod("findByName", String.class);
								if (mfind.getReturnType() == c) {
									this.mfind = mfind;
									m_getThriftFieldId = c.getMethod("getThriftFieldId");
									ord1 = id4name(o1.getName());
								}
							} catch (Exception e) {
							}
							break;
						}
					}
				} else {
					ord1 = id4name(o1.getName());
				}
			}
			// -------- //
			if (index2 != null) {
				ord2 = index2.value();
			} else if (mfind != null) {
				ord2 = id4name(o2.getName());
			}
			return ord1 - ord2;
		}
	};

	static H getH(Object type) {
		Object key = type;
		if (type instanceof ParameterizedType) {
			key = ((ParameterizedType) type).getRawType();
		}
		H h = class2type.get(key);
		if (h != null) {
			return h;
		}
		return structH;
	}

	static {
		class2type.put(0, new H(0) {
			{
				class2type.put(void.class, this);
			}

			@Override
			void write(TCompactProtocol writer, Type _t, Object bean) throws Exception {
			}

			@Override
			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return null;
			}
		});
		put(boolean.class, new H(2) {
			{
				class2type.put(Boolean.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeBool((Boolean) v);
			}

			@Override
			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readBool());
			}
		});
		put(byte.class, new H(3) {
			{
				class2type.put(Byte.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeByte((Byte) v);
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readByte());
			}
		});
		put(double.class, new H(4) {
			{
				class2type.put(Double.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeDouble((Double) v);
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readDouble());
			}
		});
		put(short.class, new H(6) {
			{
				class2type.put(Short.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeI16((Short) v);
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readI16());
			}
		});
		put(int.class, new H(8) {
			{
				class2type.put(Integer.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeI32((Integer) v);
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readI32());
			}
		});
		put(long.class, new H(10) {
			{
				class2type.put(Long.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				writer.writeI64((Long) v);
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return (reader.readI64());
			}
		});
		put(String.class, new H(11) {
			{
				class2type.put(ByteBuffer.class, this);
			}

			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				if (v == null) {
					return;
				}
				if (t == String.class) {
					writer.writeString((String) v);
				} else {
					writer.writeBinary((ByteBuffer) v);
				}
			}

			Object read(TCompactProtocol reader, Type beanType) throws Exception {
				return beanType == String.class ? reader.readString() : reader.readBinary();
			}
		});
		//
		put(Map.class, new H(13) {
			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				H k_h = getH(argtypes[0]);
				H v_h = getH(argtypes[1]);
				@SuppressWarnings("rawtypes")
				Map<?, ?> _map = (Map) v;
				long typeLong = (((long) k_h.type) << 40) | (((long) v_h.type) << 32) | _map.size();
				writer.writeMapBegin(typeLong);
				for (Map.Entry<?, ?> entry : _map.entrySet()) {
					k_h.write(writer, argtypes[0], entry.getKey());
					v_h.write(writer, argtypes[1], entry.getValue());
				}
				writer.writeMapEnd();
			}

			@Override
			Object read(TCompactProtocol reader, Type t) throws Exception {
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				H kh = getH(argtypes[0]);
				H vh = getH(argtypes[1]);
				long rs = reader.readMapBegin();
				// int kType = rs[0];
				// int vType = rs[1];
				int size = (int) (rs & 0xFFFFFFFF);
				HashMap<Object, Object> map = new HashMap<Object, Object>(size);
				for (int i = 0; i < size; i++) {
					Object key = kh.read(reader, argtypes[0]);
					Object value = vh.read(reader, argtypes[1]);
					map.put(key, value);
				}
				reader.readMapEnd();
				return (map);
			}
		});
		put(Set.class, new H(14) {
			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				H et = getH(argtypes[0]);
				@SuppressWarnings("rawtypes")
				Set<?> set = (Set) v;
				long typeLong = (((long) et.type) << 32) | set.size();
				writer.writeSetBegin(typeLong);
				for (Object eo : set) {
					et.write(writer, argtypes[0], eo);
				}
				writer.writeSetEnd();
			}

			@Override
			Object read(TCompactProtocol reader, Type t) throws Exception {
				long rs = reader.readSetBegin();
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				int size = (int) (rs & 0xFFFFFFFF);
				HashSet<Object> set = new HashSet<Object>(size);
				H et = getH(argtypes[0]);
				for (int i = 0; i < size; i++) {
					set.add(et.read(reader, argtypes[0]));
				}
				reader.readSetEnd();
				return (set);
			}
		});
		put(List.class, new H(15) {
			void write(TCompactProtocol writer, Type t, Object v) throws Exception {
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				H et = getH(argtypes[0]);
				@SuppressWarnings("rawtypes")
				List<?> list = (List) v;
				long typeLong = (((long) et.type) << 32) | list.size();
				writer.writeListBegin(typeLong);
				for (Object eo : list) {
					et.write(writer, argtypes[0], eo);
				}
				writer.writeListEnd();
			}

			@Override
			Object read(TCompactProtocol reader, Type t) throws Exception {
				long rs = reader.readListBegin();
				ParameterizedType ptype = (ParameterizedType) t;
				Type[] argtypes = ptype.getActualTypeArguments();
				int size = (int) (rs & 0xFFFFFFFF);
				ArrayList<Object> list = new ArrayList<Object>(size);
				H et = getH(argtypes[0]);
				for (int i = 0; i < size; i++) {
					list.add(et.read(reader, argtypes[0]));
				}
				reader.readListEnd();
				return list;
			}
		});
		class2type.put(12, structH);
	}

	private static void put(Type jtype, H h) {
		h.jType = jtype;
		class2type.put(jtype, h);
		class2type.put(h.type & 0x0f, h);
	}
}
