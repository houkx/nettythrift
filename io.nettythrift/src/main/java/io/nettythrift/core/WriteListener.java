package io.nettythrift.core;

import org.apache.thrift.protocol.TMessage;

public interface WriteListener {

	void beforeWrite(TMessage msg);

	void afterWrite(TMessage msg, Throwable cause, int code);
}
