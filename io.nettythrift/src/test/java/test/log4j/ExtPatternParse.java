package test.log4j;

import java.text.SimpleDateFormat;

import org.apache.log4j.helpers.FormattingInfo;
import org.apache.log4j.helpers.PatternConverter;
import org.apache.log4j.helpers.PatternParser;
import org.apache.log4j.spi.LoggingEvent;
/**
 * 自定义log4j日志中需要特殊处理的输出项
 *
 */
public class ExtPatternParse extends PatternParser {
	public ExtPatternParse(String pattern) {
		super(pattern);
	}

	/**
	 * 重写finalizeConverter，对特定的占位符进行处理
	 */
	@Override
	protected void finalizeConverter(char flag) {
		if (flag == 'T') {
			this.addConverter(new ExtPatternConverter(this.formattingInfo));
		} else if (flag == 'Z') {
			this.addConverter(new ExtPatternConverterTime(this.formattingInfo,this.extractOption()));
		} else {
			super.finalizeConverter(flag);
		}

	}

	private static class ExtPatternConverter extends PatternConverter {
		public ExtPatternConverter(FormattingInfo fi) {
			super(fi);
		}

		/**
		 * 返回当前线程的ID
		 */
		@Override
		protected String convert(LoggingEvent arg0) {
			return String.valueOf(Thread.currentThread().getId());
		}

	}

	private static class ExtPatternConverterTime extends PatternConverter {
		private String pattern;

		public ExtPatternConverterTime(FormattingInfo fi, String pattern) {
			super(fi);
			this.pattern = pattern;
			// TODO Auto-generated constructor stub
		}

		/**
		 * 返回当前日志的时间处理，为满足时区个性化需求，在此实现
		 */
		@Override
		protected String convert(LoggingEvent event) {
			if (this.pattern != null) {
				if(pattern.contains("|")){
					String[] patternArray = pattern.split("\\|");
					SimpleDateFormat sdf = new SimpleDateFormat(patternArray[0]);
					long currTime = System.currentTimeMillis();
					int timeStamp = 1;
					String timeStr = patternArray[1];
					if(timeStr.endsWith("h") || timeStr.endsWith("H")){
						timeStamp = 60*60*1000;
					}else if(timeStr.endsWith("m") || timeStr.endsWith("m")){
						timeStamp = 60*1000;
					}else if(timeStr.endsWith("s") || timeStr.endsWith("s")){
						timeStamp = 1000;
					}
					int timeNum =  Integer.valueOf(String.valueOf(timeStr.substring(0, timeStr.length()-1)));
					currTime = currTime + timeNum * timeStamp;
					return sdf.format(currTime);
				}else{
					SimpleDateFormat sdf = new SimpleDateFormat(this.pattern);
					return sdf.format(System.currentTimeMillis());
				}
				
			}
			return String.valueOf(System.currentTimeMillis());
		}

	}

}
