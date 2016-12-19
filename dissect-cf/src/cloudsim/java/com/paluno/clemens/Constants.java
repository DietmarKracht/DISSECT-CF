package com.paluno.clemens;

import com.paluno.clemens.power.ConsumptionModelXeon3040;
import com.paluno.clemens.power.ConsumptionModelXeon3075;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;

public class Constants {

	public static final int PMCores = 2;
	
	public static final int VMCores = 1;
	
	public static final long PMram = 4096l * 1024 * 1024;// 4Gb
	
	public static final long VMram = 870l * 1024 * 1024;// 870 Mb
	
	public static final int PMcount = 800;
	
	public static final int VMcount = 1052;
	
	public static final long PMStorage = 1024l * 1024 * 1024; // 1Gb
	
	public static final double[] PMMips = { 1.86, 2.66 };
	
	public static final double VMMips = 2.5;
	
	public static final String inputfolder = PMTest.class.getClassLoader().getResource("workload/planetlab").getPath()+"/20110303";
	
	public static PowerState.ConsumptionModel[] models = { new ConsumptionModelXeon3040(),
			new ConsumptionModelXeon3075() };
}
