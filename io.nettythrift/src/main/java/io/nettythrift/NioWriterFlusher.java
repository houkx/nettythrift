/**
 * 
 */
package io.nettythrift;

import java.util.concurrent.ScheduledExecutorService;

import io.nettythrift.transport.ThriftTransportType;

/**
 * @author houkangxi
 *
 */
public interface NioWriterFlusher {
	ScheduledExecutorService handlerContextExecutor();

	void doFlush(int code, String message);
	
	ThriftTransportType transportType();
}
