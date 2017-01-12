package com.paluno.clemens.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Helper {

	/**
	 * Returns a List view of the given iterator
	 * 
	 * @param iterator
	 * @return
	 */
	public static <T> List<T> getList(Iterator<T> iterator) {
		List<T> out = new ArrayList<T>();
		while (iterator.hasNext())
			out.add(iterator.next());
		return out;
	}
}
