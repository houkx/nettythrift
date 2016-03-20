package io.nettythrift.protocol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.FieldValueMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TType;

import io.nettythrift.json.ArrayJson;
import io.nettythrift.json.JSONObject;

/**
 * Common class for List,Set,Map, and Struct.(Struct is similar with Map)
 * 
 * @author houkx
 *
 */
class BaseArray {
	private int fieldIndex;
	private final ArrayJson obj;
	private FieldValueMetaData metaData;
	private final int addStep;
	private final int ARRAY_SIZE;
	// ----- fields for Struct --------------
	private Map.Entry<TFieldIdEnum, FieldMetaData>[] elementMetas;
	private int createIndex = 0;

	// ---------------------------------------
	@SuppressWarnings("unchecked")
	public BaseArray(FieldValueMetaData meta, ArrayJson obj) {
		this.obj = obj;
		this.metaData = meta;
		ARRAY_SIZE = obj.arraySize();
		int addStep = 1;
		if (meta.type == TType.STRUCT) {
			StructMetaData sm = (StructMetaData) meta;
			Map<TFieldIdEnum, FieldMetaData> map = (Map<TFieldIdEnum, FieldMetaData>) FieldMetaData
					.getStructMetaDataMap(sm.structClass);
			elementMetas = map.entrySet().toArray(new Map.Entry[0]);

			if (obj instanceof JSONObject) {

				this.fieldIndex = 1;
				addStep = 2;

				JSONObject jobj = (JSONObject) obj;
				final Map<String, Integer> ks = new HashMap<String, Integer>(elementMetas.length);
				int i = 0;
				for (Map.Entry<TFieldIdEnum, FieldMetaData> m : elementMetas) {
					ks.put(m.getValue().fieldName, i++);
				}
				jobj.sort(new Comparator<Map.Entry<String, Object>>() {
					@Override
					public int compare(Entry<String, Object> o1, Entry<String, Object> o2) {
						Integer i1 = ks.get(o1.getKey());
						Integer i2 = ks.get(o2.getKey());
						if (i1 == null) {
							i1 = Integer.MAX_VALUE;
						}
						if (i2 == null) {
							i2 = Integer.MAX_VALUE;
						}
						return i1 - i2;
					}
				});
			}
		}
		this.addStep = addStep;
	}

	public int length() {
		return obj.length();
	}

	protected int currentIndex() {
		int cur = fieldIndex;
		if (cur >= ARRAY_SIZE) {
			cur = ARRAY_SIZE - 1;
		} else {
			fieldIndex += addStep;
		}
		return cur;
	}

	public Object get() {
		return obj.get(currentIndex());
	}

	public boolean getBoolean() {
		return obj.getBoolean(currentIndex());
	}

	public double getDouble() {
		return obj.getDouble(currentIndex());
	}

	public <E extends Enum<E>> E getEnum(Class<E> clazz) {
		return obj.getEnum(clazz, currentIndex());
	}

	public BigDecimal getBigDecimal() {
		return obj.getBigDecimal(currentIndex());
	}

	public BigInteger getBigInteger() {
		return obj.getBigInteger(currentIndex());
	}

	public int getInt() {
		return obj.getInt(currentIndex());
	}

	public long getLong() {
		return obj.getLong(currentIndex());
	}

	public String getString() {
		return obj.getString(currentIndex());
	}

	// 元素类型还是数组（或map）
	public BaseArray getArray() {
		return arr(metaData);
	}

	protected BaseArray arr(FieldValueMetaData m) {
		switch (m.type) {
		case TType.LIST: {
			ArrayJson o = (ArrayJson) obj.get(currentIndex());
			ListMetaData lm = (ListMetaData) m;
			FieldValueMetaData em = lm.elemMetaData;
			return new BaseArray(em, o);
		}
		case TType.SET: {
			ArrayJson o = (ArrayJson) obj.get(currentIndex());
			SetMetaData sm = (SetMetaData) m;
			FieldValueMetaData em = sm.elemMetaData;
			return new BaseArray(em, o);
		}
		case TType.MAP: {
			int cur = currentIndex();
			ArrayJson o = (ArrayJson) obj.get(cur);
			MapMetaData mm = (MapMetaData) m;
			FieldValueMetaData em = cur % 2 == 0 ? mm.keyMetaData : mm.valueMetaData;
			return new BaseArray(em, o);
		}
		case TType.STRUCT: {
			int cur = currentIndex();
			ArrayJson o = (ArrayJson) obj.get(cur);
			FieldMetaData fm = elementMetas[cur / addStep].getValue();
			return new BaseArray(fm.valueMetaData, o);
		}
		default:
			throw new RuntimeException("ILLegal access:getArray():type=" + m.type);
		}

	}

	public FieldValueMetaData getMetaData() {
		return metaData;
	}

	/**
	 * Struct use only.
	 * 
	 * @return
	 */
	public TField newField() {
		if (createIndex < elementMetas.length) {
			Map.Entry<TFieldIdEnum, FieldMetaData> entry = elementMetas[createIndex++];
			FieldMetaData fm = entry.getValue();
			return new TField(fm.fieldName, fm.valueMetaData.type, entry.getKey().getThriftFieldId());
		}
		return null;
	}
}
