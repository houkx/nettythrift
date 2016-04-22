package aLab_commonDefines;

import java.nio.ByteBuffer;
import org.apache.thrift.TException;

public class BeanExampleServiceImpl implements BeanExampleService.Iface {
	@Override
	public BeanExample getBean(int anArg, String anOther) throws TException {
		System.out.printf("ServiceExampleImpl::getBean(): anArg=%d, anOther=%s\n", anArg,
				anOther);
		return new BeanExample(true, (byte) 2, (short) 3, anArg, 5, 6.0, anOther
				+ "halou", ByteBuffer.wrap(new byte[] { 3, 1, 4 }));
	}
}