/**
 * 
 */
package mimeTypeTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import org.junit.Test;

/**
 * @author HouKangxi
 *
 */
public class MimeToMap {

//	@Test
	public void testMimeToMap() throws Exception {
		FileReader fr = new FileReader(new File("mimeTypes"));
		BufferedReader br = new BufferedReader(fr);
		String s;
		while ((s = br.readLine()) != null) {
             s = s.trim();
             String ss[] = s.split("[\\s]+");
             if(ss.length==1){
            	 System.out.printf("fileExt2Mimes.put(\"\",\"%s\");\n",ss[0]);
             }else if(ss.length==2){
            	 System.out.printf("fileExt2Mimes.put(\"%s\",\"%s\");\n",ss[0],ss[1]);
             }
		}
		br.close();
	}
}
