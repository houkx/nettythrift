/**
 * 
 */
package com.nq.thriftcommon;

import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * <p>
 * 简化的序列化协议，去除了对thrift 类型(如 TList,TMap,TField,TMessage等)的依赖；<br/>
 * 原来的 TTransport 用 OutputStream 和 InputStream 代替.
 * <p/>
 * 原来的TField 有两个字段：byte type,short id; 这两个字段合并为一个int表示 (减少了一个对象创建)<br/>
 * TList,TSet,TMap 则改用int[]表示； TMessage 改为Object[]表示;
 * 
 * @author HouKangxi
 *
 */
public abstract class TProtocol {
	// --------- readerMethods -------------------------------
	public abstract Object[] readMessageBegin() throws Exception;

	public abstract void readMessageEnd() throws Exception;

	public abstract void readStructBegin() throws Exception;

	public abstract void readStructEnd() throws Exception;

	public abstract int readFieldBegin() throws Exception;

	public abstract void readFieldEnd() throws Exception;

	public abstract int[] readMapBegin() throws Exception;

	public abstract void readMapEnd() throws Exception;

	public abstract int[] readListBegin() throws Exception;

	public abstract void readListEnd() throws Exception;

	public abstract int[] readSetBegin() throws Exception;

	public abstract void readSetEnd() throws Exception;

	public abstract boolean readBool() throws Exception;

	public abstract byte readByte() throws Exception;

	public abstract short readI16() throws Exception;

	public abstract int readI32() throws Exception;

	public abstract long readI64() throws Exception;

	public abstract double readDouble() throws Exception;

	public abstract String readString() throws Exception;

	public abstract ByteBuffer readBinary() throws Exception;

	// ------- writer methods -------------------------
	public abstract void reset();

	public abstract void writeMessageBegin(String name, byte type, int seqid) throws Exception;

	public abstract void writeMessageEnd() throws Exception;

	public abstract void writeStructBegin() throws Exception;

	public abstract void writeStructEnd() throws Exception;

	public abstract void writeFieldBegin(int field) throws Exception;

	public abstract void writeFieldEnd() throws Exception;

	public abstract void writeFieldStop() throws Exception;

	public abstract void writeMapBegin(int[] map) throws Exception;

	public abstract void writeMapEnd() throws Exception;

	public abstract void writeListBegin(int[] list) throws Exception;

	public abstract void writeListEnd() throws Exception;

	public abstract void writeSetBegin(int[] set) throws Exception;

	public abstract void writeSetEnd() throws Exception;

	public abstract void writeBool(boolean b) throws Exception;

	public abstract void writeByte(byte b) throws Exception;

	public abstract void writeI16(short i16) throws Exception;

	public abstract void writeI32(int i32) throws Exception;

	public abstract void writeI64(long i64) throws Exception;

	public abstract void writeDouble(double dub) throws Exception;

	public abstract void writeString(String str) throws Exception;

	public abstract void writeBinary(ByteBuffer buf) throws Exception;

	public abstract OutputStream getTransport();
}
