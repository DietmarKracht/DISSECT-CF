package com.paluno.clemens;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.paluno.clemens.power.CustomPowerTransitionGenerator;
import com.paluno.clemens.power.PowerConsuming;
import com.paluno.clemens.scheduler.BeloglazovScheduler;
import com.paluno.clemens.scheduler.GuazzoneScheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.EnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.energy.specialized.IaaSEnergyMeter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

public class Simulation {
	private Logger log = Logger.getLogger("Simulation");
	private final int vmTypes = Constants.VMTYPES;
	public static List<DataVM> dataVMs = new LinkedList<DataVM>();
	private File[] inputFiles;

	private final static HashMap<String, Integer> globalLatencyMapInternal = new HashMap<String, Integer>(10000);
	public final static Map<String, Integer> globalLatencyMap = Collections.unmodifiableMap(globalLatencyMapInternal);

	public static void main(String[] args) {
		Simulation s = new Simulation();
		try {
			s.startSimulation();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | NoSuchFieldException | VMManagementException
				| NetworkException | IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Something went wrong");
			e.printStackTrace();
			System.exit(0);
		}
	}

	private int repoID = 0;
	public IaaSService iaas;
	public Repository repo;
	public VirtualAppliance va;
	public ResourceConstraints[] rc;

	/**
	 * Call this to start the simulation
	 * 
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws NetworkException
	 * @throws VMManagementException
	 * @throws IOException
	 */
	public void startSimulation() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException,
			VMManagementException, NetworkException, IOException {

		reset();
		iaas = createIaaS(true);
		EnergyMeter em = new IaaSEnergyMeter(iaas);
		// em.startMeter(5, false);
		ArrayList<PhysicalMachine> pms = createMultiplePMs(Constants.PMcount);
		iaas.bulkHostRegistration(pms);
		iaas.registerRepository(createRepo(true));
		Timed.simulateUntilLastEvent();
		repo = iaas.repositories.get(0);
		va = (VirtualAppliance) repo.contents().iterator().next();
		rc = createConstraints(vmTypes);
		VirtualMachine[] vms = requestVMs(va, rc, repo, iaas, Constants.VMcount);

		for (VirtualMachine vm : vms)
			System.out.println(vm);
		for (PhysicalMachine pm : iaas.machines)
			for (VirtualMachine vm : pm.listVMs()) {

				// vm.newComputeTask(75 * 5 * 60, 75, new
				// ConsumptionEventAdapter());

			}

		Timed.simulateUntilLastEvent();

		printResults(iaas);
		log.info("Simulation finished in " + Timed.getFireCount() + " ticks");

	}

	private void printResults(IaaSService s) {
		int pmCounter = 0, vmCounter = 0;
		for (PhysicalMachine pm : s.machines) {
			if (pm.isRunning())
				pmCounter++;
			for (VirtualMachine vm : pm.listVMs()) {
				if (vm.getState().equals(VirtualMachine.State.RUNNING))
					vmCounter++;
			}
		}
		System.out.println(pmCounter + " running pms with a total of " + vmCounter + " running vms on them");

	}

	/**
	 * Requests multiple vms for the iaas with different types
	 * 
	 * @param count
	 *            The amount of vms to request
	 * @return
	 * @throws VMManagementException
	 * @throws NetworkException
	 * @throws IOException
	 * @throws NumberFormatException
	 */
	public VirtualMachine[] requestVMs(VirtualAppliance virtualAppliance, ResourceConstraints[] resourceConstraints,
			Repository repository, IaaSService iaas, int count)
			throws VMManagementException, NetworkException, NumberFormatException, IOException {
		VirtualMachine[] out = new VirtualMachine[count];
		int compare = 0;
		for (int i = 0; i < out.length; i++) {
			// System.out.println("Currently requesting VM No. "+i);
			compare++;
			out[i] = iaas.requestVM(virtualAppliance, resourceConstraints[i % vmTypes], repository, 1)[0];
			dataVMs.add(new DataVM(out[i], loadFiles(Constants.inputfolder)[i].getAbsolutePath()));

		}
		System.out.println("Created " + compare + " VMs but should create " + count);
		return out;
	}

	/**
	 * Determine which scheduler to use for the VMs
	 * 
	 * @param flag
	 *            , true = beloglazov, false = guazzone
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	public IaaSService createIaaS(boolean flag) throws InstantiationException, IllegalAccessException,
			IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		log.info("Creates IaaS");
		return new IaaSService(flag == true ? BeloglazovScheduler.class : GuazzoneScheduler.class,
				AlwaysOnMachines.class);
	}

	/**
	 * Creates the ResourceConstraints for the VMs based on the number of
	 * different types
	 * 
	 * @param vmTypes
	 *            (Max 4)
	 * @return
	 * @throws Exception
	 */
	public ResourceConstraints[] createConstraints(int vmTypes) throws ArrayIndexOutOfBoundsException {

		if (vmTypes < 1 || vmTypes > 4)
			throw new ArrayIndexOutOfBoundsException("Impossible number of different VM types: " + vmTypes);
		log.info("Creates " + vmTypes + " different types of VM ResourceConstraints");
		ResourceConstraints[] out = new ResourceConstraints[vmTypes];
		for (int i = 0; i < out.length; i++) {
			out[i] = new ConstantConstraints(Constants.VMCores[i], Constants.VMMIPS[i], Constants.VMRAM[i]);
		}
		return out;
	}

	/**
	 * Creates a new PM
	 * 
	 * @param perCoreProcessing
	 * @param powerTransitions
	 * @return
	 */
	public PhysicalMachine createPM(String names, double perCoreProcessing,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		// log.info("Creates PM");
		return new PhysicalMachine(Constants.PMCores, perCoreProcessing, Constants.PMram,
				new Repository(1024l * 1024 * 1024 * 1024, names, 1, 1, 1, globalLatencyMapInternal), 0, 0,
				powerTransitions);
	}

	/**
	 * Creates a new Repository
	 * 
	 * @param withVA
	 *            if an Virtual Appliance is needed
	 * @param pm
	 * @return
	 */
	public Repository createRepo(boolean withVA) {
		// log.info("Creates Repository " + (withVA == true ? "with Virtual
		// Appliance" : "without Virtual Appliance"));

		Repository out = new Repository(Constants.PMStorage, "R" + repoID++, 1L * 1024, 1L * 1024, 1024L * 1024 * 1024,
				globalLatencyMapInternal);
		if (withVA) {
			VirtualAppliance va = new VirtualAppliance("VA", 0, 0, false, 1L * 1024);
			out.registerObject(va);
		}
		return out;
	}

	/**
	 * Creates an Arraylist of pms for the simulation
	 * 
	 * @param count
	 * @return
	 * @throws SecurityException
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws NoSuchFieldException
	 */
	private ArrayList<PhysicalMachine> createMultiplePMs(int count)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		ArrayList<PhysicalMachine> pms = new ArrayList<PhysicalMachine>();
		String names[] = generateNames(count, "PM", 0);
		for (int i = 0; i < count; i++) {
			pms.add(createPM(names[i], Constants.PMMips[i % 2],
					CustomPowerTransitionGenerator.generateTransitions(0,
							((PowerConsuming) Constants.models[i % 2]).idlePower(),
							((PowerConsuming) Constants.models[i % 2]).maxPower(), Double.MAX_VALUE, Double.MAX_VALUE,
							Constants.models[i % 2].getClass())));
		}
		return pms;
	}

	/**
	 * Generates names as identifiers for latency map
	 * 
	 * @param count
	 *            number of names to generate
	 * @param prefix
	 *            the type this names should be of i.e. PM-001
	 * @param latency
	 *            latency for the map
	 * @return Array with generated names
	 */
	public String[] generateNames(final int count, final String prefix, final int latency) {
		final byte[] seed = new byte[4 * count];
		SeedSyncer.centralRnd.nextBytes(seed);
		final String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			final int mult = i * 4;
			names[i] = prefix + seed[mult] + seed[mult + 1] + seed[mult + 2] + seed[mult + 3];
			globalLatencyMapInternal.put(names[i], latency);
		}
		return names;
	}

	public String generateName(final String prefix, final int latency) {
		return generateNames(1, prefix, latency)[0];
	}

	/**
	 * Resets the class to initial
	 * 
	 */
	public void reset() {
		log.warning("Resetted class");
		repoID = 0;
	}

	private void simulate() {
		Timed.simulateUntilLastEvent();
	}

	public File[] loadFiles(String inputPath) {
		return inputFiles != null ? inputFiles : (inputFiles = new File(inputPath).listFiles());

	}

}
