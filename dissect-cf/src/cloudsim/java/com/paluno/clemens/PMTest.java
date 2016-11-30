package com.paluno.clemens;

import java.util.EnumMap;
import java.util.Map;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;

public class PMTest {
	private int repoID = 0;

	private PhysicalMachine createPM(double perCorePocessing, double[] turnonOperations, double[] switchoffOperations,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		Repository repo = createNewDisk(1000L, 1000L, 1024l * 1024 * 1024, null);
		return new PhysicalMachine(Constants.PMCores, perCorePocessing, Constants.PMram, repo, switchoffOperations,
				switchoffOperations, powerTransitions);

	}

	private Repository createNewDisk(long maxInBW, long maxOutBW, long diskBW, Map<String, Integer> latencyMap) {
		return new Repository(Constants.PMStorage, "" + repoID++ + "-repo", maxInBW, maxOutBW, diskBW, latencyMap);

	}

}
