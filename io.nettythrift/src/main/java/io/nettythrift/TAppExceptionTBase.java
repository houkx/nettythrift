package io.nettythrift;

import org.apache.thrift.TApplicationException;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.protocol.TProtocol;

@SuppressWarnings({ "serial", "rawtypes" })
final class TAppExceptionTBase implements TBase {
	final TApplicationException x;

	public TAppExceptionTBase(TApplicationException x) {
		this.x = x;
	}

	@Override
	public void write(TProtocol oprot) throws TException {
		x.write(oprot);
	}

	@Override
	public int compareTo(Object arg0) {
		return 0;
	}

	@Override
	public void read(TProtocol iprot) throws TException {
	}

	@Override
	public TFieldIdEnum fieldForId(int fieldId) {
		return null;
	}

	@Override
	public boolean isSet(TFieldIdEnum field) {
		return false;
	}

	@Override
	public Object getFieldValue(TFieldIdEnum field) {
		return null;
	}

	@Override
	public void setFieldValue(TFieldIdEnum field, Object value) {

	}

	@Override
	public TBase deepCopy() {
		return null;
	}

	@Override
	public void clear() {
	}

}