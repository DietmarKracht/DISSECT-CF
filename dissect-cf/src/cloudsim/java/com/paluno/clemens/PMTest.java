package com.paluno.clemens;

import java.util.Arrays;
import java.util.HashMap;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

public class PMTest {
	public final static int pmcount = 10;
	public static PhysicalMachine[] pms= new PhysicalMachine[pmcount];

	public static void main(String[] args)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		for (int i = 0; i < pmcount; i++) {
			pms[i] = new PhysicalMachine(2, 2500, 4000,
					new Repository(1000, "" + i, 10000, 10000, 1000, new HashMap<String, Integer>()), new double[] {},
					new double[] {}, PowerTransitionGenerator.generateTransitions(100, 110, 500, 50, 50));
		}
		
		for(int i=0;i<pms.length;i++){
			pms[i].turnon();
			System.out.println(Arrays.toString(pms));
		}
	}
}
