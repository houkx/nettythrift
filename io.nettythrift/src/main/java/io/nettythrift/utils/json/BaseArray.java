package io.nettythrift.utils.json;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
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

/**
 * Common class for List,Set,Map, and Struct.(Struct is similar with Map)
 * 
 * @author houkx
 *
 */
public class BaseArray {
	private int fieldIndex;
	private final ArrayJson obj;
	private FieldValueMetaData metaData;
	private final int addStep;
	private final int ARRAY_SIZE;
	// ----- fields for Struct --------------
	private Map<String, Map.Entry<TFieldIdEnum, FieldMetaData>> elementMetas;
	private int createIndex = 0;
	// field for JsonArray
	private Map.Entry<TFieldIdEnum, FieldMetaData>[] elementMetaArr;

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

			if (obj instanceof JSONObject) {
				this.fieldIndex = 1;
				addStep = 2;
				if (map != null && map.size() > 0) {
					elementMetas = new HashMap<String, Map.Entry<TFieldIdEnum, FieldMetaData>>(map.size());
					for (Map.Entry<TFieldIdEnum, FieldMetaData> m : map.entrySet()) {
						TFieldIdEnum k = m.getKey();
						// fieldName <-> metaData
						elementMetas.put(k.getFieldName(), m);
						// id <-> metaData
						elementMetas.put(String.valueOf(k.getThriftFieldId()), m);
					}
				}
				String fieldName = obj.getString(0);
				if (!useId && fieldName.length() > 0) {
					char c0 = fieldName.charAt(0);
					useId = c0 >= '0' && c0 <= '9';
				}
			} else {
				elementMetaArr = map.entrySet().toArray(new Map.Entry[0]);
				Arrays.sort(elementMetaArr, new Comparator<Map.Entry<TFieldIdEnum, FieldMetaData>>() {
					@Override
					public int compare(Entry<TFieldIdEnum, FieldMetaData> o1, Entry<TFieldIdEnum, FieldMetaData> o2) {
						return o1.getKey().getThriftFieldId() - o2.getKey().getThriftFieldId();
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
			FieldMetaData fm = prevFieldMetaData;
			return new BaseArray(fm.valueMetaData, o);
		}
		default:
			throw new RuntimeException("ILLegal access:getArray():type=" + m.type);
		}

	}

	public FieldValueMetaData getMetaData() {
		return metaData;
	}

	private FieldMetaData prevFieldMetaData;
	private boolean useId;

	public boolean useId() {
		return useId;
	}

	/**
	 * Struct use only.
	 * 
	 * @return
	 */
	public TField newField() {
		if (createIndex < obj.length()) {
			Map.Entry<TFieldIdEnum, FieldMetaData> entry = null;
			if (addStep == 2) {
				String fieldName = obj.getString(createIndex << 1);
				entry = elementMetas.get(fieldName);
				createIndex++;
			} else {
				int i = createIndex;
				Object o;
				while (i < obj.length() && ((o = obj.get(i)) == null || o == JSONObject.NULL)) {
					currentIndex();// array index: +1
					i++;
				}
				entry = elementMetaArr[i];
				createIndex = i + 1;
			}
			FieldMetaData fm = entry.getValue();
			prevFieldMetaData = fm;
			return new TField(fm.fieldName, fm.valueMetaData.type, entry.getKey().getThriftFieldId());
		}
		return null;
	}
}
