/**
 * 
 */
package alab.thriftLabService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.thrift.protocol.TSimpleJSONProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import org.junit.Test;

/**
 * @author houkangxi
 *
 */
public class SeeSimpleJson {
   @Test
	public void testSee_jsonOnSend() throws Exception {
		TMemoryBuffer trans = new TMemoryBuffer(1024);
		TSimpleJSONProtocol oprot = new TSimpleJSONProtocol(trans);
		TUser user = new TUser();
		user.setAge(18);
		user.setLength(1.68);
		user.setUid(System.currentTimeMillis());
		user.setName("慧子");
		user.setSupportTypes(Arrays.asList(1, 6, 8, 13, 14));
		Map<String, String> otherDescs = new HashMap<String, String>();
		otherDescs.put("city", "北京");
		otherDescs.put("country", "中国");
		user.setOtherDescs(otherDescs);

		// user.write(oprot);
		TGetBalanceRequest req = new TGetBalanceRequest();
		req.setCardNumber(System.nanoTime());
		req.setCity("BeiJIng");
		// req.write(oprot);
//		TBankService.getBalance_args args = new TBankService.getBalance_args(user, req);
//		args.write(oprot);
		TBankService.Client client = new TBankService.Client(oprot);
		client.send_getBalance(user, req);
		
		System.out.println(trans.toString("UTF-8"));
	}

}
