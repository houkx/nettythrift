/**
 * 
 */
package org.apache.thrift.protocol;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import org.junit.Test;

import aLab_commonDefines.CalculatorService;
import junit.framework.Assert;

/**
 * @author houkangxi
 *
 */
public class TCmpactProtocol_TEST {

	@Test
	public void testWrite_ping() throws Exception {
		CalculatorService.ping_args args = new CalculatorService.ping_args();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		TTransport transport = new TIOStreamTransport(os);
		TProtocol oprot = new TCompactProtocol(transport);
		args.write(oprot);
		byte[] bs = os.toByteArray();
		System.out.println(bs.length);
		System.out.println(Arrays.toString(bs));
	}

	private int intToZigZag(int n) {
		int rs = (n << 1) ^ (n >> 31);
		System.out.printf("intToZigZag(%d) = %d\n", n, rs);
		return rs;
	}

	@Test
	public void test_BitMove() throws Exception {
		int n = -2;
		int a = n << 1;
		int b = n >> 31;
		System.out.printf("%s :  %d\n", Integer.toBinaryString(2), 2);
		System.out.printf("%s :  %d\n", Integer.toBinaryString(n), n);
		System.out.printf("%s :  %d\n", Integer.toBinaryString(a), a);
		System.out.printf("%s :  %d\n", Integer.toBinaryString(b), b);
	}

	@Test
	public void testWrite_add() throws Exception {
		CalculatorService.add_args args = new CalculatorService.add_args();
		args.num1 = -2;
//		args.num2 = 15;
		 args.num2 = Integer.MAX_VALUE / 2;
		intToZigZag(args.num2);
		intToZigZag(64);

		int MAX = Byte.MAX_VALUE;
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		TTransport transport = new TIOStreamTransport(os);
		TProtocol oprot = new TCompactProtocol(transport);
		args.write(oprot);
		byte[] bs = os.toByteArray();
		System.out.println(bs.length);
		System.out.println(Arrays.toString(bs));
	}
}
