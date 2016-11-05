/**
 * 
 */
package io.nettythrift.utils.json;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Super interface for JSONObject And JSONArray to implement.
 * <p>
 * JSONObject is used as array: &lt;k1,v2&gt;,&lt;k2,v2&gt; ---&gt;[k1,v1,k2,v2]
 * 
 * @author houkx
 *
 */
public interface ArrayJson {
	//size for array used,for map, arraySize is map.size*2
	public int arraySize();

	public int length();// orig length

	// getters for getXX By index
	public Object get(int index);

	public boolean getBoolean(int index);

	public double getDouble(int index);

	public <E extends Enum<E>> E getEnum(Class<E> clazz, int index);

	public BigDecimal getBigDecimal(int index);

	public BigInteger getBigInteger(int index);

	public int getInt(int index);

	public JSONArray getJSONArray(int index);

	public JSONObject getJSONObject(int index);

	public long getLong(int index);

	public String getString(int index);
}
