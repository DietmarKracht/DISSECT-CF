package com.paluno.clemens;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import com.paluno.clemens.power.CustomPowerTransitionGenerator;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class PMTest {
	private static int repoID = 0;
	private VirtualAppliance va = new VirtualAppliance("va", 0, 0);
	private static ArrayList<PhysicalMachine> pms = new ArrayList<PhysicalMachine>();

	private static PhysicalMachine createPM(double perCorePocessing, double[] turnonOperations,
			double[] switchoffOperations, EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		Repository repo = createNewDisk(1000L, 1000L, 1024l * 1024 * 1024, null);
		return new PhysicalMachine(Constants.PMCores, perCorePocessing, Constants.PMram, repo, switchoffOperations,
				switchoffOperations, powerTransitions);

	}

	private static Repository createNewDisk(long maxInBW, long maxOutBW, long diskBW, Map<String, Integer> latencyMap) {
		return new Repository(Constants.PMStorage, "" + repoID++ + "-repo", maxInBW, maxOutBW, diskBW, latencyMap);

	}

	

	private static IaaSService createCloud()
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
		final IaaSService s = new IaaSService(FirstFitScheduler.class, AlwaysOnMachines.class);
		for (int i = 0; i < Constants.PMcount; i++) {
			PhysicalMachine pm = createPM(Constants.PMMips[i % 2], new double[] {}, new double[] {}, CustomPowerTransitionGenerator
					.generateTransitions(0, 0, 500, 1000000000, 1000000000, Constants.models[i % 2]));
			s.registerHost(pm);
			pms.add(pm);
		}
		return s;
	}

	public static void main(String[] args)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {

		final IaaSService iaas = createCloud();
		for (PhysicalMachine pm : pms) {
			
			pm.turnon();
			
			

		}
		
		Timed.simulateUntilLastEvent();

	}

}
