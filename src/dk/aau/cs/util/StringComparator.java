package dk.aau.cs.util;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class which implements Compatator<Object> the compare method 
 * compares the results from "toString()" of the objects.
 * If the two objects bouth end in an integer and the first parts
 * are identical the method compares the two numbers and returns the result of this
 * 
 * Thus t2 is considered smaller than t10
 * 
 *
 */
public class StringComparator implements Comparator<Object>{
	
	@Override
	public int compare(Object o1, Object o2) {
		Pattern p = Pattern.compile("\\d\\d*$");
		String s1 = o1.toString().toLowerCase();
		String s2 = o2.toString().toLowerCase();
		Matcher m1 = p.matcher(s1);
		Matcher m2 = p.matcher(s2);
		if(m1.find() && m2.find()){
			if(s1.substring(0, m1.start()).equals(s2.substring(0, m2.start()))){
				int i1 = Integer.parseInt(m1.group());
				int i2 = Integer.parseInt(m2.group());
				
				if(i1 > i2){
					return 1;
				} else if (i1 < i2){
					return -1;
				} else {
					return 0;
				}
			}
		}
		return s1.compareTo(s2);
	}
	
}
