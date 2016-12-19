package com.paluno.clemens;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import com.paluno.clemens.power.ConsumptionModelXeon3040;
import com.paluno.clemens.power.CustomPowerTransitionGenerator;
import com.paluno.clemens.power.PowerConsuming;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.MonitorConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.PhysicalMachineEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.SimpleVMEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.PowerTransitionGenerator;

public class PMTest {
	// ID for every PMs Disk, increasing
	private static int repoID = 0;
	// Image for every VM
	private static VirtualAppliance va = new VirtualAppliance("va", 0, 0, false, 2500l * 1024 * 1024);
	// Global List of all PMs
	private static ArrayList<PhysicalMachine> pms = new ArrayList<PhysicalMachine>();
	private static ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();

	private static Repository globalStorage;

	static PhysicalMachine pm;

	private static PhysicalMachine createPM(double perCorePocessing,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		Repository repo = createNewDisk(1000L, 1000L, 1024l * 1024 * 1024, null);
		return new PhysicalMachine(Constants.PMCores, perCorePocessing, Constants.PMram, repo, 0, 0, powerTransitions);

	}

	private static Repository createNewDisk(long maxInBW, long maxOutBW, long diskBW, Map<String, Integer> latencyMap) {
		return new Repository(Constants.PMStorage, "" + repoID++ + "-repo", maxInBW, maxOutBW, diskBW, latencyMap);

	}

	private static IaaSService createCloud() throws SecurityException, InstantiationException, IllegalAccessException,
			NoSuchFieldException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
		// globalStorage.registerObject(va);
		final IaaSService s = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
		for (int i = 0; i < Constants.PMcount; i++) {
			PhysicalMachine pm = createPM(Constants.PMMips[i % 2],
					CustomPowerTransitionGenerator.generateTransitions(0,
							((PowerConsuming) Constants.models[i % 2]).idlePower(),
							((PowerConsuming) Constants.models[i % 2]).maxPower(), Double.MAX_VALUE, Double.MAX_VALUE,
							Constants.models[i % 2].getClass()));
			pms.add(pm);

		}
		s.bulkHostRegistration(pms);
		return s;
	}

	private static VirtualMachine newVM() {
		return new VirtualMachine((VirtualAppliance) globalStorage.lookup("va"));
	}

	public static void main(String[] args) throws SecurityException, InstantiationException, IllegalAccessException,
			NoSuchFieldException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException,
			VMManagementException, NetworkException {
		// globalStorage = new Repository(1l * 1024 * 1024, "Global", 1000L,
		// 1000L, 1024l * 1024 * 1024, null);
		// if (globalStorage.registerObject(va))
		// System.out.println("Successfully registered VA");
		// else
		// System.out.println("Couldn't register VA due to no free storage
		// capacity");
		// final IaaSService iaas = createCloud();
		//
		// //Wird nie aufgerufen
		//// for (PhysicalMachine pm : pms) {
		//// if (!pm.isRunning()) {
		//// pm.turnon();
		//// System.out.println("Pm angeschaltet");
		//// }
		////
		//// }
		// for (int i = 0; i < vmCount(Constants.inputfolder); i++) {
		// vms.add(newVM());
		//// System.out.println("Created VM");
		//
		// }
		//
		// Timed.simulateUntilLastEvent();
		long start = System.currentTimeMillis();
		ResourceConstraints vmreq = new ConstantConstraints(Constants.VMCores[0], Constants.VMMIPS[3],
				Constants.VMRAM[0]);
		Repository storage = new Repository(1l * 1024 * 1024 * 1024, "repo", 1000l, 1000l, 1l * 1024 * 1024, null);
		storage.registerObject(va);
		IaaSService s = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
		pm = new PhysicalMachine(Constants.PMCores, Constants.PMMips[1], Constants.PMram,
				new Repository(1l * 1024 * 1024, "disk", 1000L, 1000L, 1l * 1024 * 1024, null), 0, 0,
				CustomPowerTransitionGenerator.generateTransitions(0,
						((PowerConsuming) Constants.models[0]).idlePower(),
						((PowerConsuming) Constants.models[0]).maxPower(), Double.MAX_VALUE, Double.MAX_VALUE,
						Constants.models[0].getClass()));
		preparePM();

		s.registerHost(pm);

		VirtualMachine vm = pm.requestVM((VirtualAppliance) pm.localDisk.contents().iterator().next(), vmreq,
				pm.localDisk, 1)[0];
		EnergyMeter m = new SimpleVMEnergyMeter(vm);
		PhysicalMachineEnergyMeter pe = new PhysicalMachineEnergyMeter(pm);
		pe.startMeter(1, true);
		m.startMeter(1, true);

		// Timed.simulateUntilLastEvent();
		long task = Timed.getFireCount();
		vm.newComputeTask(10, ResourceConsumption.unlimitedProcessing, new ConsumptionEventAdapter());
		Timed.simulateUntilLastEvent();

		System.out.println("Took the simulator " + (Timed.getFireCount() - task) + " time units to finish task");
		System.out.println("Finished run in " + (System.currentTimeMillis() - start) + " ms");

		m.stopMeter();
		System.exit(0);

	}

	private static void registerVA(PhysicalMachine pm) {
		VirtualAppliance va = new VirtualAppliance("VAID", 1500, 0, false, pm.localDisk.getMaxStorageCapacity() / 10);
		pm.localDisk.registerObject(va);
	}

	private static void preparePM() {
		registerVA(pm);
		pm.turnon();
		// Timed.simulateUntilLastEvent();
	}

	private static int vmCount(String inputFolderName) {
		File inputFolder = new File(inputFolderName);
		File[] files = inputFolder.listFiles();
		return files.length;
	}

}
