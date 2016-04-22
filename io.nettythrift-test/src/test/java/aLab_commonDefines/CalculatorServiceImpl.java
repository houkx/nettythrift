package aLab_commonDefines;

import java.io.Serializable;
import java.util.Map;
import java.util.Random;

import org.apache.thrift.TException;

public class CalculatorServiceImpl implements CalculatorService.Iface,Serializable,Map.Entry<String, Integer> {

	@Override
	public String ping() throws TException {
		System.out.printf("%s Call: ping()\n", Thread.currentThread().getName());
//		Random r = new Random();
//		if (r.nextInt(100) % 2 == 1) {
//			throw new TException("server random throw exception for TEST");
//		}
		return "PONG";
	}

	@Override
	public int add(int num1, int num2) throws TException {
		if (num1 == 1) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		System.out.printf("%s Call: add(%d, %d)\n", Thread.currentThread().getName(), num1, num2);
		if (num1 == -1) {
			throw new TException("num1 not alow: -1");
		}
		return num1 + num2;
	}

	@Override
	public void zip(String content, int flag) throws TException {
		System.out.printf("zip: %s, flag=%d\n", content, flag);
	}

	@Override
	public String getKey() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer getValue() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer setValue(Integer value) {
		// TODO Auto-generated method stub
		return null;
	}

}
