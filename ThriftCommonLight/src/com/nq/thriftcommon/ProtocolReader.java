/**
 * 
 */
package com.nq.thriftcommon;

import java.lang.reflect.Type;

import com.nq.thriftcommon.ProtocolWriter.H;

/**
 * 
 * @author HouKangxi
 *
 */
class ProtocolReader {
	public static <T> T read(TProtocol reader, Type resultBeanClass, Class<?>[] exceptionsTypes, int seqid_)
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
					methodName + " failed: out of sequence response");
		}
		// -- read Struct_result START --
		reader.readStructBegin();
		int field = reader.readFieldBegin();
		int fieldType = ((field >> 16) & 0x0000ffff);
		short fieldId = (short) (field & 0x0000ffff);
		if (fieldId == 0) {
			H h = ProtocolWriter.getH(fieldType);
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
				H h = ProtocolWriter.getH(fieldType);
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
}
