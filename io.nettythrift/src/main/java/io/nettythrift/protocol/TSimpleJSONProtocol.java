/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.nettythrift.protocol;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.meta_data.FieldMetaData;
import org.apache.thrift.meta_data.ListMetaData;
import org.apache.thrift.meta_data.MapMetaData;
import org.apache.thrift.meta_data.SetMetaData;
import org.apache.thrift.meta_data.StructMetaData;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TMessageType;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolException;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TSet;
import org.apache.thrift.protocol.TStruct;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TTransport;

import io.nettythrift.utils.json.ArrayJson;
import io.nettythrift.utils.json.BaseArray;
import io.nettythrift.utils.json.JSONArray;

/**
 * JSON protocol implementation for thrift.
 *
 * This protocol is read-write. It should not be confused with the
 * TJSONProtocol.
 * <p>
 *
 * Changes: <br/>
 * 只写改为可读写 - by Houkx
 */
@SuppressWarnings("rawtypes")
public class TSimpleJSONProtocol extends TProtocol {

	/**
	 * Factory
	 */
	@SuppressWarnings("serial")
	public static class Factory implements TProtocolFactory {
		private final Class<?> ifaceClass;
		private final boolean isServer;

		public Factory() {
			this(null, true);
		}

		public Factory(Class<?> ifaceClass) {
			this(ifaceClass, true);
		}

		public Factory(Class<?> ifaceClass, boolean isServer) {
			this.ifaceClass = ifaceClass;
			this.isServer = isServer;
		}

		public TProtocol getProtocol(TTransport trans) {
			return new TSimpleJSONProtocol(trans, ifaceClass, isServer);
		}
	}

	private static final byte[] COMMA = new byte[] { ',' };
	private static final byte[] COLON = new byte[] { ':' };
	private static final byte[] LBRACE = new byte[] { '{' };
	private static final byte[] RBRACE = new byte[] { '}' };
	private static final byte[] LBRACKET = new byte[] { '[' };
	private static final byte[] RBRACKET = new byte[] { ']' };
	private static final char QUOTE = '"';

	private static final TStruct ANONYMOUS_STRUCT = new TStruct();
	private static final TField ANONYMOUS_FIELD = new TField();
	// private static final TMessage EMPTY_MESSAGE = new TMessage();
	// private static final TSet EMPTY_SET = new TSet();
	// private static final TList EMPTY_LIST = new TList();
	// private static final TMap EMPTY_MAP = new TMap();
	private static final String LIST = "list";
	private static final String SET = "set";
	private static final String MAP = "map";

	protected class Context {
		protected void write() throws TException {
		}

		/**
		 * Returns whether the current value is a key in a map
		 */
		protected boolean isMapKey() {
			return false;
		}
	}

	protected class ListContext extends Context {
		protected boolean first_ = true;

		protected void write() throws TException {
			if (first_) {
				first_ = false;
			} else {
				trans_.write(COMMA);
			}
		}
	}

	protected class StructContext extends Context {
		protected boolean first_ = true;
		protected boolean colon_ = true;

		protected void write() throws TException {
			if (first_) {
				first_ = false;
				colon_ = true;
			} else {
				trans_.write(colon_ ? COLON : COMMA);
				colon_ = !colon_;
			}
		}
	}

	protected class MapContext extends StructContext {
		protected boolean isKey = true;

		@Override
		protected void write() throws TException {
			super.write();
			isKey = !isKey;
		}

		protected boolean isMapKey() {
			// we want to coerce map keys to json strings regardless
			// of their type
			return isKey;
		}
	}

	protected final Context BASE_CONTEXT = new Context();

	/**
	 * Stack of nested contexts that we may be in.
	 */
	protected Stack<Context> writeContextStack_ = new Stack<Context>();

	/**
	 * Current context that we are in
	 */
	protected Context writeContext_ = BASE_CONTEXT;

	/**
	 * Push a new write context onto the stack.
	 */
	protected void pushWriteContext(Context c) {
		writeContextStack_.push(writeContext_);
		writeContext_ = c;
	}

	/**
	 * Pop the last write context off the stack
	 */
	protected void popWriteContext() {
		writeContext_ = writeContextStack_.pop();
	}

	/**
	 * Used to make sure that we are not encountering a map whose keys are
	 * containers
	 */
	protected void assertContextIsNotMapKey(String invalidKeyType) throws CollectionMapKeyException {
		if (writeContext_.isMapKey()) {
			throw new CollectionMapKeyException("Cannot serialize a map with keys that are of type " + invalidKeyType);
		}
	}

	private Class argsTBaseClass;

	private final Class<?> ifaceClass;
	private final boolean isServer;

	/**
	 * Constructor
	 */
	public TSimpleJSONProtocol(TTransport trans) {
		this(trans, null, true);
	}

	/**
	 * Constructor
	 */
	public TSimpleJSONProtocol(TTransport trans, Class<?> ifaceClass, boolean isServer) {
		super(trans);
		this.isServer = isServer;
		this.ifaceClass = ifaceClass;
	}

	public Class getArgsTBaseClass() {
		return argsTBaseClass;
	}

	public void setArgsTBaseClass(Class argsTBaseClass) {
		this.argsTBaseClass = argsTBaseClass;
	}

	public void writeMessageBegin(TMessage message) throws TException {
		trans_.write(LBRACKET);
		pushWriteContext(new ListContext());
		writeString(message.name);
		writeByte(message.type);
		writeI32(message.seqid);
	}

	public void writeMessageEnd() throws TException {
		popWriteContext();
		trans_.write(RBRACKET);
	}

	public void writeStructBegin(TStruct struct) throws TException {
		writeContext_.write();
		trans_.write(LBRACE);
		pushWriteContext(new StructContext());
	}

	public void writeStructEnd() throws TException {
		popWriteContext();
		trans_.write(RBRACE);
	}

	public void writeFieldBegin(TField field) throws TException {
		// Note that extra type information is omitted in JSON!
		writeString(useFieldId ? String.valueOf(field.id) : field.name);
	}

	public void writeFieldEnd() {
	}

	public void writeFieldStop() {
	}

	public void writeMapBegin(TMap map) throws TException {
		assertContextIsNotMapKey(MAP);
		writeContext_.write();
		trans_.write(LBRACE);
		pushWriteContext(new MapContext());
		// No metadata!
	}

	public void writeMapEnd() throws TException {
		popWriteContext();
		trans_.write(RBRACE);
	}

	public void writeListBegin(TList list) throws TException {
		assertContextIsNotMapKey(LIST);
		writeContext_.write();
		trans_.write(LBRACKET);
		pushWriteContext(new ListContext());
		// No metadata!
	}

	public void writeListEnd() throws TException {
		popWriteContext();
		trans_.write(RBRACKET);
	}

	public void writeSetBegin(TSet set) throws TException {
		assertContextIsNotMapKey(SET);
		writeContext_.write();
		trans_.write(LBRACKET);
		pushWriteContext(new ListContext());
		// No metadata!
	}

	public void writeSetEnd() throws TException {
		popWriteContext();
		trans_.write(RBRACKET);
	}

	public void writeBool(boolean b) throws TException {
		writeByte(b ? (byte) 1 : (byte) 0);
	}

	public void writeByte(byte b) throws TException {
		writeI32(b);
	}

	public void writeI16(short i16) throws TException {
		writeI32(i16);
	}

	public void writeI32(int i32) throws TException {
		if (writeContext_.isMapKey()) {
			writeString(Integer.toString(i32));
		} else {
			writeContext_.write();
			_writeStringData(Integer.toString(i32));
		}
	}

	public void _writeStringData(String s) throws TException {
		try {
			byte[] b = s.getBytes("UTF-8");
			trans_.write(b);
		} catch (UnsupportedEncodingException uex) {
			throw new TException("JVM DOES NOT SUPPORT UTF-8");
		}
	}

	public void writeI64(long i64) throws TException {
		if (writeContext_.isMapKey()) {
			writeString(Long.toString(i64));
		} else {
			writeContext_.write();
			_writeStringData(Long.toString(i64));
		}
	}

	public void writeDouble(double dub) throws TException {
		if (writeContext_.isMapKey()) {
			writeString(Double.toString(dub));
		} else {
			writeContext_.write();
			_writeStringData(Double.toString(dub));
		}
	}

	public void writeString(String str) throws TException {
		writeContext_.write();
		int length = str.length();
		StringBuffer escape = new StringBuffer(length + 16);
		escape.append(QUOTE);
		for (int i = 0; i < length; ++i) {
			char c = str.charAt(i);
			switch (c) {
			case '"':
			case '\\':
				escape.append('\\');
				escape.append(c);
				break;
			case '\b':
				escape.append('\\');
				escape.append('b');
				break;
			case '\f':
				escape.append('\\');
				escape.append('f');
				break;
			case '\n':
				escape.append('\\');
				escape.append('n');
				break;
			case '\r':
				escape.append('\\');
				escape.append('r');
				break;
			case '\t':
				escape.append('\\');
				escape.append('t');
				break;
			default:
				// Control characters! According to JSON RFC u0020 (space)
				if (c < ' ') {
					String hex = Integer.toHexString(c);
					escape.append('\\');
					escape.append('u');
					for (int j = 4; j > hex.length(); --j) {
						escape.append('0');
					}
					escape.append(hex);
				} else {
					escape.append(c);
				}
				break;
			}
		}
		escape.append(QUOTE);
		_writeStringData(escape.toString());
	}

	public void writeBinary(ByteBuffer bin) throws TException {
		try {
			// TODO(mcslee): Fix this
			writeString(new String(bin.array(), bin.position() + bin.arrayOffset(),
					bin.limit() - bin.position() - bin.arrayOffset(), "UTF-8"));
		} catch (UnsupportedEncodingException uex) {
			throw new TException("JVM DOES NOT SUPPORT UTF-8");
		}
	}

	private BaseArray msgStruct;

	/**
	 * Reading methods.
	 */
	public TMessage readMessageBegin() throws TException {
		byte[] buf = new byte[256];
		ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
		while (true) {
			int readLen = trans_.read(buf, 0, buf.length);
			if (readLen == 0) {
				break;
			}
			out.write(buf, 0, readLen);
			if (readLen < buf.length) {
				break;
			}
		}
		String sb = null;
		try {
			buf = out.toByteArray();
			sb = new String(buf, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		}
		// System.out.println("读取完毕： sb=" + sb);
		// TODO JSON 格式的检查
		if (sb.charAt(0) != '[' || sb.charAt(sb.length() - 1) != ']') {
			throw new TProtocolException(TProtocolException.INVALID_DATA, "bad format!");
		}
		JSONArray jsonArray = new JSONArray(sb);
		TMessage msg = new TMessage(jsonArray.getString(0), (byte) jsonArray.getInt(1), jsonArray.getInt(2));
		// System.out.println(msg + ", jsonArray.len = " + jsonArray.length());

		if (jsonArray.length() > 3) {
			if (argsTBaseClass == null) {
				try {
					argsTBaseClass = guessTBaseClassByMethodName(msg.name);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (argsTBaseClass == null) {
				// throw new
				// TProtocolException(TApplicationException.UNKNOWN_METHOD,
				// "Invalid method name: '" + msg.name + "'");
				return new TMessage(msg.name, TMessageType.EXCEPTION, msg.seqid);
			}
			@SuppressWarnings("unchecked")
			StructMetaData meta = new StructMetaData(TType.STRUCT, argsTBaseClass);
			msgStruct = new BaseArray(meta, (ArrayJson) jsonArray.get(3));
		}
		return msg;
	}

	private static ConcurrentHashMap<String, Class<?>> tBaseclassCache = new ConcurrentHashMap<String, Class<?>>();

	private Class guessTBaseClassByMethodName(String name) throws Exception {
		String classSimpleName = String.format("%s_%s", name, isServer ? "args" : "result");
		Class<?> result = tBaseclassCache.get(classSimpleName);
		if (result != null) {
			return result;
		}
		String className = String.format("%s$%s", ifaceClass.getEnclosingClass().getName(), classSimpleName);
		if (ifaceClass != null) {
			try {
				result = Class.forName(className, false, ifaceClass.getClassLoader());
				tBaseclassCache.putIfAbsent(classSimpleName, result);
				return result;
			} catch (Exception e) {
				Class[] cls = ifaceClass.getInterfaces();
				if (cls != null) {
					for (Class c : cls) {
						String cname = String.format("%s$%s", c.getEnclosingClass().getName(), classSimpleName);
						try {
							result = Class.forName(cname);
							className = cname;
							tBaseclassCache.putIfAbsent(classSimpleName, result);
							return result;
						} catch (Exception ex) {
						}
					}
				}
			}
		}
		java.lang.reflect.Field f = FieldMetaData.class.getDeclaredField("structMap");
		f.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<Class<? extends TBase>, Map<? extends TFieldIdEnum, FieldMetaData>> structMap = (Map) f.get(null);
		for (Class c : structMap.keySet()) {
			if (c.getName().equals(className)) {
				tBaseclassCache.putIfAbsent(classSimpleName, c);
				return c;
			}
		}
		return null;
	}

	public void readMessageEnd() {
	}

	private LinkedList<BaseArray> structStack = new LinkedList<BaseArray>();

	public TField readFieldBegin() throws TException {
		BaseArray prevStruct = structStack.peek();
		TField field = prevStruct.newField();
		return field != null ? field : ANONYMOUS_FIELD;
	}

	public void readFieldEnd() {
	}

	public TStruct readStructBegin() {
		BaseArray prevStruct = structStack.peek();
		if (prevStruct != null) {
			BaseArray e = prevStruct.getArray();
			structStack.push(e);
		} else {
			structStack.push(msgStruct);
		}
		return ANONYMOUS_STRUCT;
	}

	public TList readListBegin() throws TException {
		BaseArray prevStruct = structStack.peek();
		BaseArray obj = prevStruct.getArray();
		structStack.push(obj);

		ListMetaData lm = (ListMetaData) obj.getMetaData();
		return new TList(lm.elemMetaData.type, obj.length());
	}

	public TSet readSetBegin() throws TException {
		BaseArray prevStruct = structStack.peek();
		BaseArray obj = prevStruct.getArray();
		structStack.push(obj);

		SetMetaData lm = (SetMetaData) obj.getMetaData();
		return new TSet(lm.elemMetaData.type, obj.length());
	}

	public TMap readMapBegin() throws TException {
		BaseArray prevStruct = structStack.peek();
		BaseArray obj = prevStruct.getArray();
		structStack.push(obj);

		MapMetaData mm = (MapMetaData) obj.getMetaData();
		return new TMap(mm.keyMetaData.type, mm.valueMetaData.type, obj.length());
	}

	private boolean useFieldId;

	public void readStructEnd() {
		BaseArray prevStruct = structStack.pop();
		if (!useFieldId && prevStruct.useId()) {
			useFieldId = true;
		}
	}

	public void readListEnd() {
		structStack.pop();
	}

	public void readMapEnd() {
		structStack.pop();
	}

	public void readSetEnd() {
		structStack.pop();
	}

	public int readI32() throws TException {
		BaseArray prevStruct = structStack.peek();
		return prevStruct.getInt();
	}

	public boolean readBool() throws TException {
		BaseArray prevStruct = structStack.peek();
		return prevStruct.getBoolean();
	}

	/**
	 * Read a single byte off the wire. Nothing interesting here.
	 */
	public byte readByte() throws TException {
		return (byte) readI32();
	}

	public short readI16() throws TException {
		return (short) readI32();
	}

	public long readI64() throws TException {
		BaseArray prevStruct = structStack.peek();
		return prevStruct.getLong();
	}

	public double readDouble() throws TException {
		BaseArray prevStruct = structStack.peek();
		return prevStruct.getDouble();
	}

	public String readString() throws TException {
		BaseArray prevStruct = structStack.peek();
		return prevStruct.getString();
	}

	// public String readStringBody(int size) throws TException {
	// // TODO(mcslee): implement
	// return "";
	// }

	public ByteBuffer readBinary() throws TException {
		return ByteBuffer.wrap(readString().getBytes());
	}

	@SuppressWarnings("serial")
	public static class CollectionMapKeyException extends TException {
		public CollectionMapKeyException(String message) {
			super(message);
		}
	}
}
