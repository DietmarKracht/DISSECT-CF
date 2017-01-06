package com.paluno.clemens;

import java.io.File;

import com.paluno.clemens.power.ConsumptionModelXeon3040;
import com.paluno.clemens.power.ConsumptionModelXeon3075;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;

public class Constants {

	public static final int PMCores = 2;

	public static final long PMram = 4096l * 1024 * 1024;// 4Gb

	public static final int PMcount = 800;// 800

	public static final long PMStorage = 1024l * 1024 * 1024; // 1Gb

	public static final double[] PMMips = { 1.86, 2.66 };

	public final static int VMTYPES = 4;
	public final static double[] VMMIPS = { 2.500, 2.000, 1.000, 0.500 };
	public final static int[] VMCores = { 1, 1, 1, 1 };
	public final static long[] VMRAM = { 870l * 1024 * 1024, 1740l * 1024 * 1024, 1740l * 1024 * 1024,
			613l * 1024 * 1024 };
	public final static int VMBW = 100000; // 100 Mbit/s
	public final static long VMStorage = 2500l * 1024 * 1024; // 2.5 GB

	public static final String inputfolder = Simulation.class.getClassLoader().getResource("workload/planetlab")
			.getPath() + "/20110303";
	
	
	public static final int VMcount = vmCount(inputfolder);// vmCount(inputfolder)

	public static PowerState.ConsumptionModel[] models = { new ConsumptionModelXeon3040(),
			new ConsumptionModelXeon3075() };

	private static int vmCount(String inputFolderName) {
		File inputFolder = new File(inputFolderName);
		File[] files = inputFolder.listFiles();
		return files.length;
	}
}
