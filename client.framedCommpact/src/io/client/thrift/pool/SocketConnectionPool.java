/**
 * 
 */
package io.client.thrift.pool;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.SocketFactory;

/**
 * 套接字连接池
 * 
 * @author HouKangxi
 *
 */
public class SocketConnectionPool extends SocketFactory {
	public static final int DEFAULT_MAX_IDLE = 5;
	public static final int DEFAULT_MAX_TOTAL = 5;
	public static final long DEFAULT_MAX_WAIT = 8000;
	public static final int DEFAULT_MAX_IDLE_TIME = 5000;
	public static final int DEFAULT_IDLE_CHECK_GAP = 2000;
	/**
	 * 真正负责创建连接的socketFactory--本类专注做连接池，把创建连接的细节抛给外部socketFactory
	 */
	private final SocketFactory socketFactory;
	/**
	 * 连接池
	 */
	private final LinkedBlockingDeque<SocketWrapper> idleObjects;
	/**
	 * 最大空闲连接数
	 */
	private final int maxIdle;
	/**
	 * 池中最大连接数量
	 */
	private final int maxTotal;
	/**
	 * 等待可用连接的最长时间(毫秒)
	 */
	private final long maxWaitMills;
	/**
	 * 等待连接时是否阻塞
	 */
	private boolean blockWhenExhausted;
	/**
	 * 是否后进先出--如果true,则归还的连接放在队列头部，否则放在末尾
	 */
	private volatile boolean lifo;
	/**
	 * 负责清理空闲连接的定时器---默认清理超过15秒没有使用的连接，可通过maxIdleTime设置
	 */
	private Timer idleCheckTimer = new Timer("SocketConnectionIdelCheckTimer", true);

	private final AtomicLong borrowedCount = new AtomicLong(0);
	private final AtomicLong createdCount = new AtomicLong(0);
	private final AtomicLong destroyedCount = new AtomicLong(0);

	public SocketConnectionPool(SocketFactory socketFactory) {
		this(socketFactory, DEFAULT_MAX_IDLE, DEFAULT_MAX_TOTAL, DEFAULT_MAX_WAIT, true, DEFAULT_MAX_IDLE_TIME);
	}

	/**
	 * 
	 * @param socketFactory
	 *            - 真正负责创建连接的socketFactory--本类专注做连接池，把创建连接的细节抛给外部socketFactory
	 * @param maxIdle
	 * @param maxTotal
	 * @param maxWaitMills
	 * @param blockWhenExhausted
	 * @param maxIdleTime
	 */
	public SocketConnectionPool(SocketFactory socketFactory, int maxIdle, int maxTotal, long maxWaitMills,
			boolean blockWhenExhausted, final long maxIdleTime) {
		if (socketFactory instanceof SocketConnectionPool) {
			throw new IllegalArgumentException("socketFactory must not a SocketConnectionPool!");
		}
		this.socketFactory = socketFactory;
		this.maxIdle = maxIdle;
		this.maxTotal = maxTotal;
		this.maxWaitMills = maxWaitMills;
		this.blockWhenExhausted = blockWhenExhausted;

		idleObjects = new LinkedBlockingDeque<>(maxTotal);
		idleCheckTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				int size = idleObjects.size();
				if (size < 1) {
					return;
				}
				// toArray() copy 一个副本，避免
				SocketWrapper[] scs = idleObjects.toArray(new SocketWrapper[size]);
				for (int i = 0; i < scs.length; i++) {
					SocketWrapper sc = scs[i];
					if (sc != null && !sc.isWorking && System.currentTimeMillis() - sc.lastUseTime >= maxIdleTime) {
						// System.out.println("try删除空闲太久的连接: " + sc);
						destroy(sc);
					}
				}
			}
		}, 3000, DEFAULT_IDLE_CHECK_GAP);// 检查的开始时间 和 时间间隔--按需调整
	}

	public long getCreatedCount() {
		return createdCount.get();
	}

	public LinkedBlockingDeque<SocketWrapper> getIdleObjects() {
		// TODO just for debug
		return idleObjects;
	}

	@Override
	public Socket createSocket() throws IOException {
		try {
			return borrow();
		} catch (IOException e) {
			throw e;
		} catch (RuntimeException e) {
			Throwable cause = e.getCause();
			if (cause instanceof IOException) {
				throw (IOException) cause;
			}
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	protected SocketWrapper borrow() throws Exception {
		SocketWrapper p = null;
		while (p == null) {
			if (blockWhenExhausted) {
				p = idleObjects.pollFirst();
				if (p == null) {
					if (borrowedCount.get() > 0 && maxWaitMills > 0) {
						p = idleObjects.pollFirst(maxWaitMills, TimeUnit.MILLISECONDS);
					} else {
						p = create();
					}
				}
				if (p == null) {
					if (maxWaitMills <= 0) {
						p = idleObjects.takeFirst();
					} else {
						p = idleObjects.pollFirst(maxWaitMills, TimeUnit.MILLISECONDS);
					}
				}
				if (p == null) {
					throw new NoSuchElementException("Timeout waiting for idle object");
				}
			} else {
				p = idleObjects.pollFirst();
				if (p == null) {
					if (borrowedCount.get() > 0 && maxWaitMills > 0) {
						p = idleObjects.pollFirst(maxWaitMills, TimeUnit.MILLISECONDS);
					} else {
						p = create();
					}
				}
				if (p == null) {
					throw new NoSuchElementException("Pool exhausted");
				}
			}

		}
		p.isWorking = true;
		p.lastUseTime = System.currentTimeMillis();
		// System.out.println("borrow socket: " + p);
		borrowedCount.incrementAndGet();
		return p;
	}
	
	protected SocketWrapper create() throws IOException {
		long created = createdCount.incrementAndGet();
		if ((maxTotal > -1 && created > maxTotal) || created > Integer.MAX_VALUE) {
			createdCount.decrementAndGet();
			return null;
		}
		Socket socket = null;
		try {
			socket = socketFactory.createSocket();
		} catch (IOException e) {
			createdCount.decrementAndGet();
			throw e;
		} catch (Exception ex) {
			createdCount.decrementAndGet();
			throw new RuntimeException(ex);
		} catch (Throwable ex) {
			createdCount.decrementAndGet();
			throw new IOException(ex);
		}
		return new SocketWrapper(socket, this);
	}

	/**
	 * 归还到连接池
	 * 
	 * @param socketWrapper
	 */
	public void returnToPool(SocketWrapper p) {
		// System.out.println("try returnToPool:" + p);
		synchronized (p) {
			if (!p.isWorking) {
				throw new IllegalStateException("Object has already been returned to this pool or is invalid");
			} else {
				borrowedCount.decrementAndGet();
			}
		}
		if (maxIdle > -1 && maxIdle <= idleObjects.size()) {
			destroy(p);
		} else {
			p.isWorking = false;
			if (lifo) {
				idleObjects.addFirst(p);
			} else {
				idleObjects.addLast(p);
			}
		}
	}

	/**
	 * 销毁连接
	 * 
	 * @param socketWrapper
	 */
	protected void destroy(SocketWrapper socketWrapper) {
		if (socketWrapper == null || socketWrapper.target == null) {
			return;
		}
		synchronized (socketWrapper) {
			// System.out.println("try destroy :" + socketWrapper);
			if (!socketWrapper.target.isClosed()) {
				socketWrapper.isWorking = false;
				try {
					socketWrapper.target.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				destroyedCount.incrementAndGet();
				createdCount.decrementAndGet();
				idleObjects.remove(socketWrapper);
			}
		}
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress host, int port) throws IOException {
		return null;
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
			throws IOException, UnknownHostException {
		return null;
	}

	@Override
	public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
			throws IOException {
		return null;
	}

}
