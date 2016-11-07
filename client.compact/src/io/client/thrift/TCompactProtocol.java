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

package io.client.thrift;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;

/**
 * TCompactProtocol2 is the Java implementation of the compact protocol
 * specified in THRIFT-110. The fundamental approach to reducing the overhead of
 * structures is a) use variable-length integers all over the place and b) make
 * use of unused bits wherever possible. Your savings will obviously vary based
 * on the specific makeup of your structs, but in general, the more fields,
 * nested structures, short strings and collections, and low-value i32 and i64
 * fields you have, the more benefit you'll see.
 */
final class TCompactProtocol {

	private final static byte[] ttypeToCompactType = new byte[16];

	// private final static class Types {
	public static final byte BOOLEAN_TRUE = 0x01;
	public static final byte BOOLEAN_FALSE = 0x02;
	// public static final byte BYTE = 0x03;
	// public static final byte I16 = 0x04;
	// public static final byte I32 = 0x05;
	// public static final byte I64 = 0x06;
	// public static final byte DOUBLE = 0x07;
	// public static final byte BINARY = 0x08;
	// public static final byte LIST = 0x09;
	// public static final byte SET = 0x0A;
	// public static final byte MAP = 0x0B;
	// public static final byte STRUCT = 0x0C;
	// }

	static {
		ttypeToCompactType[0] = 0;
		ttypeToCompactType[2] = 1;
		ttypeToCompactType[3] = 3;
		ttypeToCompactType[6] = 4;
		ttypeToCompactType[8] = 5;
		ttypeToCompactType[10] = 6;
		ttypeToCompactType[4] = 7;
		ttypeToCompactType[11] = 8;
		ttypeToCompactType[15] = 9;
		ttypeToCompactType[14] = 10;
		ttypeToCompactType[13] = 11;
		ttypeToCompactType[12] = 12;
	}
	private final static long NO_LENGTH_LIMIT = -1;
	private static final byte PROTOCOL_ID = (byte) 0x82;
	private static final byte VERSION = 1;
	private static final byte VERSION_MASK = 0x1f; // 0001 1111
	private static final byte TYPE_MASK = (byte) 0xE0; // 1110 0000
	private static final int TYPE_SHIFT_AMOUNT = 5;

	/**
	 * Used to keep track of the last field for the current and previous
	 * structs, so we can do the delta stuff.
	 */
	private ShortStack lastField_ = new ShortStack(15);

	private short lastFieldId_ = 0;

	/**
	 * If we encounter a boolean field begin, save the int here so it can have
	 * the value incorporated.
	 */
	private int booleanField_ = 0;
	InputStream transIn;
	private OutputStream transOut;
	private Boolean boolValue_ = null;

	/**
	 * The maximum number of bytes to read from the transport for
	 * variable-length fields (such as strings or binary) or
	 * {@link #NO_LENGTH_LIMIT} for unlimited.
	 */
	private final long stringLengthLimit_;

	/**
	 * The maximum number of elements to read from the network for containers
	 * (maps, sets, lists), or {@link #NO_LENGTH_LIMIT} for unlimited.
	 */
	private final long containerLengthLimit_;

	/**
	 * Create a CompactProtocol.
	 *
	 * @param ttransOut
	 *            the TTransport object write to.
	 * @param ttransIn
	 *            the TTransport object to read from.
	 */
	public TCompactProtocol(OutputStream ttransOut, InputStream ttransIn) {
		this.transOut = ttransOut;
		this.transIn = ttransIn;
		this.stringLengthLimit_ = NO_LENGTH_LIMIT;
		this.containerLengthLimit_ = NO_LENGTH_LIMIT;
	}

	/**
	 * Create a CompactProtocol.
	 *
	 * @param ttransOut
	 *            the TTransport object write to.
	 * @param ttransIn
	 *            the TTransport object to read from.
	 * @param stringLengthLimit
	 *            the maximum number of bytes to read for variable-length
	 *            fields.
	 * @param containerLengthLimit
	 *            the maximum number of elements to read for containers.
	 */
	public TCompactProtocol(OutputStream ttransOut, InputStream ttransIn, long stringLengthLimit,
			long containerLengthLimit) {
		this.transOut = ttransOut;
		this.transIn = ttransIn;
		this.stringLengthLimit_ = stringLengthLimit;
		this.containerLengthLimit_ = containerLengthLimit;
	}

	public void reset() {
		lastField_.clear();
		lastFieldId_ = 0;
	}

	/**
	 * Read a message header.
	 */
	public Object[] readMessageBegin() throws Exception {
		byte protocolId = readByte();
		if (protocolId == 0) {// 异步方式，首字节为0
			protocolId = readByte();
		}
		if (protocolId != PROTOCOL_ID) {
			throw new ProtocolException("Expected protocol id " + Integer.toHexString(PROTOCOL_ID) + " but got "
					+ Integer.toHexString(protocolId));
		}
		byte versionAndType = readByte();
		byte version = (byte) (versionAndType & VERSION_MASK);
		if (version != VERSION) {
			throw new ProtocolException("Expected version " + VERSION + " but got " + version);
		}
		// byte TYPE_BITS = 0x07; // 0000 0111
		byte type = (byte) ((versionAndType >> TYPE_SHIFT_AMOUNT) & 0x07);
		int seqid = readVarint32();
		String messageName = readString();
		// return new TMessage(messageName, type, seqid);
		return new Object[] { messageName, type, seqid };
	}

	/**
	 * Read a struct begin. There's nothing on the wire for this, but it is our
	 * opportunity to push a new struct begin marker onto the field stack.
	 */
	public void readStructBegin() throws Exception {
		lastField_.push(lastFieldId_);
		lastFieldId_ = 0;
	}

	/**
	 * Doesn't actually consume any wire data, just removes the last field for
	 * this struct from the field stack.
	 */
	public void readStructEnd() throws Exception {
		// consume the last field we read off the wire.
		lastFieldId_ = lastField_.pop();
	}

	/**
	 * Read a field header off the wire.
	 */
	public int readFieldBegin() throws Exception {
		byte type = readByte();

		// if it's a stop, then we can return immediately, as the struct is
		// over.
		if (type == 0) {
			return 0;
		}

		short fieldId;

		// mask off the 4 MSB of the type header. it could contain a field id
		// delta.
		short modifier = (short) ((type & 0xf0) >> 4);
		if (modifier == 0) {
			// not a delta. look ahead for the zigzag varint field id.
			fieldId = readI16();
		} else {
			// has a delta. add the delta to the last read field id.
			fieldId = (short) (lastFieldId_ + modifier);
		}

		// int field: [0][type][idPart1][idPart2]
		// int field: [0][type][idPart1][idPart2]
		int field = ((getTType((byte) (type & 0x0f))) << 16) | (fieldId & 0x0ffff);

		// if this happens to be a boolean field, the value is encoded in the
		// type
		if (isBoolType(type)) {
			// save the boolean value in a special instance variable.
			boolValue_ = (byte) (type & 0x0f) == BOOLEAN_TRUE ? Boolean.TRUE : Boolean.FALSE;
		}

		// push the new field onto the field stack so we can keep the deltas
		// going.
		lastFieldId_ = fieldId;
		return field;
	}

	/**
	 * Read a map header off the wire. If the size is zero, skip reading the key
	 * and value type. This means that 0-length maps will yield TMaps without
	 * the "correct" types.
	 */
	public long readMapBegin() throws Exception {
		int size = readVarint32();
		checkContainerReadLength(size);
		byte keyAndValueType = size == 0 ? 0 : readByte();
		long kt = getTType((byte) (keyAndValueType >> 4));
		long vt = getTType((byte) (keyAndValueType & 0xf));
		return (kt << 40) | (vt << 32) | size;
	}

	/**
	 * Read a list header off the wire. If the list size is 0-14, the size will
	 * be packed into the element type header. If it's a longer list, the 4 MSB
	 * of the element type header will be 0xF, and a varint will follow with the
	 * true size.
	 */
	public long readListBegin() throws Exception {
		byte size_and_type = readByte();
		int size = (size_and_type >> 4) & 0x0f;
		if (size == 15) {
			size = readVarint32();
		}
		checkContainerReadLength(size);
		byte type = getTType(size_and_type);
		return (((long) type) << 32) | size;
	}

	/**
	 * Read a set header off the wire. If the set size is 0-14, the size will be
	 * packed into the element type header. If it's a longer set, the 4 MSB of
	 * the element type header will be 0xF, and a varint will follow with the
	 * true size.
	 */
	public long readSetBegin() throws Exception {
		return readListBegin();
	}

	/**
	 * Read a boolean off the wire. If this is a boolean field, the value should
	 * already have been read during readFieldBegin, so we'll just consume the
	 * pre-stored value. Otherwise, read a byte.
	 */
	public boolean readBool() throws Exception {
		if (boolValue_ != null) {
			boolean result = boolValue_.booleanValue();
			boolValue_ = null;
			return result;
		}
		return readByte() == BOOLEAN_TRUE;
	}

	private byte[] byteRawBuf = new byte[1];

	/**
	 * Read a single byte off the wire. Nothing interesting here.
	 */
	public byte readByte() throws Exception {
		byte b;
		transIn.read(byteRawBuf, 0, 1);
		b = byteRawBuf[0];
		return b;
	}

	/**
	 * Read an i16 from the wire as a zigzag varint.
	 */
	public short readI16() throws Exception {
		return (short) zigzagToInt(readVarint32());
	}

	/**
	 * Read an i32 from the wire as a zigzag varint.
	 */
	public int readI32() throws Exception {
		return zigzagToInt(readVarint32());
	}

	/**
	 * Read an i64 from the wire as a zigzag varint.
	 */
	public long readI64() throws Exception {
		return zigzagToLong(readVarint64());
	}

	/**
	 * No magic here - just read a double off the wire.
	 */
	public double readDouble() throws Exception {
		byte[] longBits = new byte[8];
		transIn.read(longBits, 0, 8);
		return Double.longBitsToDouble(bytesToLong(longBits));
	}

	/**
	 * Reads a byte[] (via readBinary), and then UTF-8 decodes it.
	 */
	public String readString() throws Exception {
		int length = readVarint32();
		checkStringReadLength(length);

		if (length == 0) {
			return "";
		}

		try {
			return new String(readBinary(length), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Exception("UTF-8 not supported!");
		}
	}

	/**
	 * Read a byte[] from the wire.
	 */
	public ByteBuffer readBinary() throws Exception {
		int length = readVarint32();
		checkStringReadLength(length);
		if (length == 0)
			return ByteBuffer.wrap(new byte[0]);

		byte[] buf = new byte[length];
		transIn.read(buf, 0, length);
		return ByteBuffer.wrap(buf);
	}

	/**
	 * Read a byte[] of a known length from the wire.
	 */
	private byte[] readBinary(int length) throws Exception {
		if (length == 0)
			return new byte[0];

		byte[] buf = new byte[length];
		transIn.read(buf, 0, length);
		return buf;
	}

	private void checkStringReadLength(int length) throws ProtocolException {
		if (length < 0) {
			throw new ProtocolException("Negative length: " + length);
		}
		if (stringLengthLimit_ != NO_LENGTH_LIMIT && length > stringLengthLimit_) {
			throw new ProtocolException("Length exceeded max allowed: " + length);
		}
	}

	private void checkContainerReadLength(int length) throws ProtocolException {
		if (length < 0) {
			throw new ProtocolException("Negative length: " + length);
		}
		if (containerLengthLimit_ != NO_LENGTH_LIMIT && length > containerLengthLimit_) {
			throw new ProtocolException("Length exceeded max allowed: " + length);
		}
	}

	//
	// These methods are here for the struct to call, but don't have any wire
	// encoding.
	//
	public void readMessageEnd() throws Exception {
	}

	public void readFieldEnd() throws Exception {
	}

	public void readMapEnd() throws Exception {
	}

	public void readListEnd() throws Exception {
	}

	public void readSetEnd() throws Exception {
	}

	//
	// Internal reading methods
	//

	/**
	 * Read an i32 from the wire as a varint. The MSB of each byte is set if
	 * there is another byte to follow. This can read up to 5 bytes.
	 */
	private int readVarint32() throws Exception {
		int result = 0;
		int shift = 0;
		while (true) {
			byte b = readByte();
			result |= (int) (b & 0x7f) << shift;
			if ((b & 0x80) != 0x80)
				break;
			shift += 7;
		}
		return result;
	}

	/**
	 * Read an i64 from the wire as a proper varint. The MSB of each byte is set
	 * if there is another byte to follow. This can read up to 10 bytes.
	 */
	private long readVarint64() throws Exception {
		int shift = 0;
		long result = 0;
		while (true) {
			byte b = readByte();
			result |= (long) (b & 0x7f) << shift;
			if ((b & 0x80) != 0x80)
				break;
			shift += 7;
		}
		return result;
	}

	//
	// encoding helpers
	//

	/**
	 * Convert from zigzag int to int.
	 */
	private int zigzagToInt(int n) {
		return (n >>> 1) ^ -(n & 1);
	}

	/**
	 * Convert from zigzag long to long.
	 */
	private long zigzagToLong(long n) {
		return (n >>> 1) ^ -(n & 1);
	}

	/**
	 * Note that it's important that the mask bytes are long literals, otherwise
	 * they'll default to ints, and when you shift an int left 56 bits, you just
	 * get a messed up int.
	 */
	private long bytesToLong(byte[] bytes) {
		return ((bytes[7] & 0xffL) << 56) | ((bytes[6] & 0xffL) << 48) | ((bytes[5] & 0xffL) << 40)
				| ((bytes[4] & 0xffL) << 32) | ((bytes[3] & 0xffL) << 24) | ((bytes[2] & 0xffL) << 16)
				| ((bytes[1] & 0xffL) << 8) | ((bytes[0] & 0xffL));
	}

	//
	// type testing and converting
	//

	private boolean isBoolType(byte b) {
		int lowerNibble = b & 0x0f;
		return lowerNibble == BOOLEAN_TRUE || lowerNibble == BOOLEAN_FALSE;
	}

	private byte[] typeTable = new byte[] { 0, 2, 2, 3, 6, 8, 10, 4, 11, 15, 14, 13, 12 };

	/**
	 * Given a TCompactProtocol.Types constant, convert it to its corresponding
	 * TType value.
	 */
	private byte getTType(byte type) throws ProtocolException {
		int index = type & 0x0f;
		if (index < typeTable.length) {
			return typeTable[index];
		} else {
			throw new ProtocolException("don't know what type: " + index);
		}
	}
	//
	// Public Writing methods.
	//

	/**
	 * Write a message header to the wire. Compact Protocol messages contain the
	 * protocol version so we can migrate forwards in the future if need be.
	 */
	public void writeMessageBegin(String name, byte type, int seqid) throws Exception {
		writeByteDirect(PROTOCOL_ID);
		writeByteDirect((VERSION & VERSION_MASK) | ((type << TYPE_SHIFT_AMOUNT) & TYPE_MASK));
		writeVarint32(seqid);
		writeString(name);
	}

	/**
	 * Write a struct begin. This doesn't actually put anything on the wire. We
	 * use it as an opportunity to put special placeholder markers on the field
	 * stack so we can get the field id deltas correct.
	 */
	public void writeStructBegin() throws Exception {
		lastField_.push(lastFieldId_);
		lastFieldId_ = 0;
	}

	/**
	 * Write a struct end. This doesn't actually put anything on the wire. We
	 * use this as an opportunity to pop the last field from the current struct
	 * off of the field stack.
	 */
	public void writeStructEnd() throws Exception {
		lastFieldId_ = lastField_.pop();
	}

	/**
	 * Write a field header containing the field id and field type. If the
	 * difference between the current field id and the last one is small (< 15),
	 * then the field id will be encoded in the 4 MSB as a delta. Otherwise, the
	 * field id will follow the type header as a zigzag varint.
	 */
	public void writeFieldBegin(int field) throws Exception {
		byte fieldType = (byte) ((field >> 16) & 0x0000ffff);
		short fieldId = (short) (field & 0x0000ffff);
		if (fieldType == 2/* TType.BOOL */) {
			// we want to possibly include the value, so we'll wait.
			booleanField_ = field;
		} else {
			writeFieldBeginInternal(fieldId, fieldType, (byte) -1);
		}
	}

	/**
	 * The workhorse of writeFieldBegin. It has the option of doing a 'type
	 * override' of the type header. This is used specifically in the boolean
	 * field case.
	 */
	private void writeFieldBeginInternal(short fieldId, byte fieldType, byte typeOverride) throws Exception {
		// short lastField = lastField_.pop();

		// if there's a type override, use that.
		byte typeToWrite = typeOverride == -1 ? getCompactType(fieldType) : typeOverride;

		// check if we can use delta encoding for the field id
		if (fieldId > lastFieldId_ && fieldId - lastFieldId_ <= 15) {
			// write them together
			writeByteDirect((fieldId - lastFieldId_) << 4 | typeToWrite);
		} else {
			// write them separate
			writeByteDirect(typeToWrite);
			writeI16(fieldId);
		}

		lastFieldId_ = fieldId;
		// lastField_.push(field.id);
	}

	/**
	 * Write the STOP symbol so we know there are no more fields in this struct.
	 */
	public void writeFieldStop() throws Exception {
		writeByteDirect(0);
	}

	/**
	 * Write a map header. If the map is empty, omit the key and value type
	 * headers, as we don't need any additional information to skip it.
	 */
	public void writeMapBegin(long map) throws Exception {
		int mapSize = (int) (map & 0xFFFFFFFF);
		if (mapSize == 0) {
			writeByteDirect(0);
		} else {
			byte keyType = (byte) (((byte) (map >> 40) & 0xFF));
			byte valueType = (byte) (((byte) (map >> 32) & 0xFF));
			writeVarint32(mapSize);
			writeByteDirect(getCompactType(keyType) << 4 | getCompactType(valueType));
		}
	}

	/**
	 * Write a list header.
	 */
	public void writeListBegin(long list) throws Exception {
		byte elemType = (byte) (((byte) (list >> 32)) & 0xFF);
		int size = (int) (list & 0xFFFFFFFF);
		writeCollectionBegin(elemType, size);
	}

	/**
	 * Write a set header.
	 */
	public void writeSetBegin(long set) throws Exception {
		writeListBegin(set);
	}

	/**
	 * Write a boolean value. Potentially, this could be a boolean field, in
	 * which case the field header info isn't written yet. If so, decide what
	 * the right type header is for the value and then write the field header.
	 * Otherwise, write a single byte.
	 */
	public void writeBool(boolean b) throws Exception {
		if (booleanField_ != 0) {
			byte fieldType = (byte) ((booleanField_ >> 16) & 0x0000ffff);
			short fieldId = (short) (booleanField_ & 0x0000ffff);
			// we haven't written the field header yet
			writeFieldBeginInternal(fieldId, fieldType, b ? BOOLEAN_TRUE : BOOLEAN_FALSE);
			booleanField_ = 0;
		} else {
			// we're not part of a field, so just write the value.
			writeByteDirect(b ? BOOLEAN_TRUE : BOOLEAN_FALSE);
		}
	}

	/**
	 * Write a byte. Nothing to see here!
	 */
	public void writeByte(byte b) throws Exception {
		writeByteDirect(b);
	}

	/**
	 * Write an I16 as a zigzag varint.
	 */
	public void writeI16(short i16) throws Exception {
		writeVarint32(intToZigZag(i16));
	}

	/**
	 * Write an i32 as a zigzag varint.
	 */
	public void writeI32(int i32) throws Exception {
		writeVarint32(intToZigZag(i32));
	}

	/**
	 * Write an i64 as a zigzag varint.
	 */
	public void writeI64(long i64) throws Exception {
		writeVarint64(longToZigzag(i64));
	}

	/**
	 * Write a double to the wire as 8 bytes.
	 */
	public void writeDouble(double dub) throws Exception {
		byte[] data = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		fixedLongToBytes(Double.doubleToLongBits(dub), data, 0);
		transOut.write(data);
	}

	/**
	 * Write a string to the wire with a varint size preceding.
	 */
	public void writeString(String str) throws Exception {
		try {
			byte[] bytes = str.getBytes("UTF-8");
			writeBinary(bytes, 0, bytes.length);
		} catch (UnsupportedEncodingException e) {
			throw new Exception("UTF-8 not supported!");
		}
	}

	/**
	 * Write a byte array, using a varint for the size.
	 */
	public void writeBinary(ByteBuffer bin) throws Exception {
		int length = bin.limit() - bin.position();
		writeBinary(bin.array(), bin.position() + bin.arrayOffset(), length);
	}

	private void writeBinary(byte[] buf, int offset, int length) throws Exception {
		writeVarint32(length);
		transOut.write(buf, offset, length);
	}

	//
	// These methods are called by structs, but don't actually have any wire
	// output or purpose.
	//

	public void writeMessageEnd() throws Exception {
	}

	public void writeMapEnd() throws Exception {
	}

	public void writeListEnd() throws Exception {
	}

	public void writeSetEnd() throws Exception {
	}

	public void writeFieldEnd() throws Exception {
	}

	//
	// Internal writing methods
	//

	/**
	 * Abstract method for writing the start of lists and sets. List and sets on
	 * the wire differ only by the type indicator.
	 */
	protected void writeCollectionBegin(byte elemType, int size) throws Exception {
		if (size <= 14) {
			writeByteDirect(size << 4 | getCompactType(elemType));
		} else {
			writeByteDirect(0xf0 | getCompactType(elemType));
			writeVarint32(size);
		}
	}

	/**
	 * Write an i32 as a varint. Results in 1-5 bytes on the wire. TODO: make a
	 * permanent buffer like writeVarint64?
	 */
	private byte[] i32buf = new byte[5];

	private void writeVarint32(int n) throws Exception {
		int idx = 0;
		while (true) {
			if ((n & ~0x7F) == 0) {
				i32buf[idx++] = (byte) n;
				// writeByteDirect((byte)n);
				break;
				// return;
			} else {
				i32buf[idx++] = (byte) ((n & 0x7F) | 0x80);
				// writeByteDirect((byte)((n & 0x7F) | 0x80));
				n >>>= 7;
			}
		}
		transOut.write(i32buf, 0, idx);
	}

	/**
	 * Write an i64 as a varint. Results in 1-10 bytes on the wire.
	 */
	private byte[] varint64out = new byte[10];

	private void writeVarint64(long n) throws Exception {
		int idx = 0;
		while (true) {
			if ((n & ~0x7FL) == 0) {
				varint64out[idx++] = (byte) n;
				break;
			} else {
				varint64out[idx++] = ((byte) ((n & 0x7F) | 0x80));
				n >>>= 7;
			}
		}
		transOut.write(varint64out, 0, idx);
	}

	/**
	 * Convert l into a zigzag long. This allows negative numbers to be
	 * represented compactly as a varint.
	 */
	private long longToZigzag(long l) {
		return (l << 1) ^ (l >> 63);
	}

	/**
	 * Convert n into a zigzag int. This allows negative numbers to be
	 * represented compactly as a varint.
	 */
	private int intToZigZag(int n) {
		return (n << 1) ^ (n >> 31);
	}

	/**
	 * Convert a long into little-endian bytes in buf starting at off and going
	 * until off+7.
	 */
	private void fixedLongToBytes(long n, byte[] buf, int off) {
		buf[off + 0] = (byte) (n & 0xff);
		buf[off + 1] = (byte) ((n >> 8) & 0xff);
		buf[off + 2] = (byte) ((n >> 16) & 0xff);
		buf[off + 3] = (byte) ((n >> 24) & 0xff);
		buf[off + 4] = (byte) ((n >> 32) & 0xff);
		buf[off + 5] = (byte) ((n >> 40) & 0xff);
		buf[off + 6] = (byte) ((n >> 48) & 0xff);
		buf[off + 7] = (byte) ((n >> 56) & 0xff);
	}

	/**
	 * Writes a byte without any possibility of all that field header nonsense.
	 * Used internally by other writing methods that know they need to write a
	 * byte.
	 */
	private byte[] byteDirectBuffer = new byte[1];

	private void writeByteDirect(byte b) throws Exception {
		byteDirectBuffer[0] = b;
		transOut.write(byteDirectBuffer);
	}

	/**
	 * Writes a byte without any possibility of all that field header nonsense.
	 */
	private void writeByteDirect(int n) throws Exception {
		writeByteDirect((byte) n);
	}

	/**
	 * Given a TType value, find the appropriate TCompactProtocol.Types
	 * constant.
	 */
	private byte getCompactType(byte ttype) {
		return ttypeToCompactType[ttype];
	}

	public OutputStream getTransport() {
		return transOut;
	}

}
class ShortStack {

	  private short[] vector;
	  private int top = -1;

	  public ShortStack(int initialCapacity) {
	    vector = new short[initialCapacity];
	  }

	  public short pop() {
	    return vector[top--];
	  }

	  public void push(short pushed) {
	    if (vector.length == top + 1) {
	      grow();
	    }
	    vector[++top] = pushed;
	  }

	  private void grow() {
	    short[] newVector = new short[vector.length * 2];
	    System.arraycopy(vector, 0, newVector, 0, vector.length);
	    vector = newVector;
	  }

	  public short peek() {
	    return vector[top];
	  }

	  public void clear() {
	    top = -1;
	  }

	//  @Override
	//  public String toString() {
//	    StringBuilder sb = new StringBuilder();
//	    sb.append("<ShortStack vector:[");
//	    for (int i = 0; i < vector.length; i++) {
//	      if (i != 0) {
//	        sb.append(" ");
//	      }
	//
//	      if (i == top) {
//	        sb.append(">>");
//	      }
	//
//	      sb.append(vector[i]);
	//
//	      if (i == top) {
//	        sb.append("<<");
//	      }
//	    }
//	    sb.append("]>");
//	    return sb.toString();
	//  }
	}