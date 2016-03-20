package test.log4j;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.helpers.PatternParser;

public class ExtpatternLayout extends PatternLayout {

	public ExtpatternLayout() {
		super();
		// TODO Auto-generated constructor stub
	}

	public ExtpatternLayout(String pattern) {
		super(pattern);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected PatternParser createPatternParser(String pattern) {
		return new ExtPatternParse(pattern);
	}


	

}
