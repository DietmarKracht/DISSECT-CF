package com.paluno.clemens;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;

public class DataVM {
	private final double[] data;
	/**
	 * The Vm to observe
	 * 
	 */
	private final VirtualMachine vm;

	public DataVM(VirtualMachine vm, String path) throws NumberFormatException, IOException {
		this.vm = vm;
		data = new double[289];
		if (path != null) {
			BufferedReader input = new BufferedReader(new FileReader(path));
			int n = data.length;
			for (int i = 0; i < n - 1; i++) {
				data[i] = Integer.valueOf(input.readLine()) / 100.0;
			}
			data[n - 1] = data[n - 2];
			input.close();
		}else{
			System.out.println("Couldn't create Data");
		}
	}

	public VirtualMachine getVM() {
		return this.vm;
	}

	public DataVM(VirtualMachine vm) throws NumberFormatException, IOException {
		this(vm, null);
	}

	public double getUtilization(int count) {
		if (count >= data.length)
			throw new ArrayIndexOutOfBoundsException();
		return data[count];
	}

	public static DataVM createVM(VirtualMachine vm) throws NumberFormatException, IOException {

		return new DataVM(vm);
	}

}
