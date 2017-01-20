package io.nettythrift.core;

import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TMessage;

public interface WriterHandler {

	void beforeWrite(TMessage msg,TBase args,TBase result);

	void afterWrite(TMessage msg, Throwable cause, int code,TBase args,TBase result);
}
