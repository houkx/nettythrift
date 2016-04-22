/**
 * 
 */
package alab.thriftLabService;

import java.util.HashMap;
import java.util.Map;

import org.apache.thrift.TException;

import alab.thriftLabService.TBankService.Iface;

/**
 * @author nq
 *
 */
public class BankServiceImpl implements Iface {

	/* (non-Javadoc)
	 * @see alab.thriftLabService.TBankService.Iface#getBalance(alab.thriftLabService.TUser, alab.thriftLabService.TGetBalanceRequest)
	 */
	@Override
	public TBalance getBalance(TUser user, TGetBalanceRequest req) throws TException {
		System.out.printf("Call: getBalance(%s, %s)\n",user,req);
		TBalance b = new TBalance();
		b.setLeftMoney(Math.random()*user.age) ;
		b.addToConsumeHistory("May");
		b.addToConsumeHistory("March");
		b.addToConsumeHistory("七月");
		b.addToConsumeHistory(user.getName()+"'s 消费记录");
		Map<Integer, Double> monthConsumes = new HashMap<Integer, Double>();
		monthConsumes.put(1, 1.2);
		monthConsumes.put(2, 2.3);
		monthConsumes.put(4, 2.5);
		b.setMonthConsumes(monthConsumes );
		return b;
	}

}
