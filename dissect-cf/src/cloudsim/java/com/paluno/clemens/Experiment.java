package com.paluno.clemens;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.paluno.clemens.power.CustomPowerTransitionGenerator;
import com.paluno.clemens.power.PowerConsuming;
import com.paluno.clemens.scheduler.BeloglazovScheduler;
import com.paluno.clemens.scheduler.GuazzoneScheduler;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.PowerStateKind;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.SchedulingDependentMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

@SuppressWarnings("unused")
public class Experiment {
	/**
	 * indicates the scheduler to use
	 * (true:BeloglazovScheduler,false:GuazzoneScheduler)
	 **/
	private boolean scheduler;
	/** The created iaasservice **/
	private IaaSService iaas;
	/** List of all created pms **/
	private List<PhysicalMachine> pms = new LinkedList<PhysicalMachine>();
	/** List of all created vms **/
	private List<VirtualMachine> vms = new LinkedList<VirtualMachine>();
	/** The Data stored for the allocation optimization **/
	private List<DataVM> data = new LinkedList<DataVM>();
	/** Latency map for the repositories **/
	private final static HashMap<String, Integer> globalLatencyMapInternal = new HashMap<String, Integer>(10000);
	/** The virtual appliance for every vm **/
	private VirtualAppliance va;
	/** The current scheduler **/
	private BeloglazovScheduler scd;
	/** The files for every vm **/
	private File[] inputFiles;
	/**
	 * The repository for the iaas that stores the virtual appliance for the vms
	 **/
	private Repository repo;
	/** the resourceconstraints array for the virtualmachines (max 4 types) **/
	private ResourceConstraints rc[];

	/**
	 * starts the simulation
	 * 
	 * @throws SecurityException
	 * @throws NoSuchMethodException
	 * @throws InvocationTargetException
	 * @throws IllegalArgumentException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws NoSuchFieldException
	 * @throws NetworkException
	 * @throws VMManagementException
	 * @throws IOException
	 **/
	public void startSimulation() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException,
			VMManagementException, NetworkException, IOException {
		iaas = setupIaaS();
		if (iaas.sched instanceof BeloglazovScheduler) {
			scd = (BeloglazovScheduler) iaas.sched;

		}

		pms = multiplePMs(Constants.PMcount);
		iaas.bulkHostRegistration(pms);
		iaas.registerRepository(iaasRepo());
		repo = iaas.repositories.get(0);
		va = (VirtualAppliance) repo.contents().iterator().next();
		rc = createConstraints(Constants.VMTYPES);
		Timed.simulateUntilLastEvent();
		multipleVMs(Constants.VMcount);
		scd.initializeData(data);
		AllocationOptimizer opt = new AllocationOptimizer(iaas);
		Timed.simulateUntilLastEvent();
		printInformation();
	}

	public Experiment(boolean flag) {
		this.scheduler = flag;
	}

	/** Creates a new iaasservice with the defined scheduler **/
	public IaaSService setupIaaS() throws InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException {
		return new IaaSService((scheduler ? BeloglazovScheduler.class : GuazzoneScheduler.class),
				SchedulingDependentMachines.class);
	}

	/** returns a new pm with constant number of cores and constant ram **/
	public PhysicalMachine newPM(double mips, Repository disk,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> transitions) {
		return new PhysicalMachine(Constants.PMCores, mips, Constants.PMram, disk, 0, 0, transitions);
	}

	/**
	 * create multiple pms
	 * 
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws SecurityException
	 **/
	public List<PhysicalMachine> multiplePMs(int count)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		List<PhysicalMachine> out = new LinkedList<PhysicalMachine>();
		String[] names = generateNames(count, "PM", 1);
		for (int i = 0; i < count; i++) {
			out.add(newPM(Constants.PMMips[i % 2], newRepository(names[i]),
					CustomPowerTransitionGenerator.generateTransitions(0,
							((PowerConsuming) Constants.models[i % 2]).idlePower(),
							((PowerConsuming) Constants.models[i % 2]).maxPower(), Double.MAX_VALUE, Double.MAX_VALUE,
							Constants.models[i % 2].getClass())));
		}
		return out;
	}

	/** returns a new repository for pms only with constant storage **/
	public Repository newRepository(String id) {
		return new Repository(Constants.PMStorage, id, 1, 1, 1, globalLatencyMapInternal);
	}

	/** creates a repo for the iaas with virtual appliance **/
	private Repository iaasRepo() {
		VirtualAppliance va = new VirtualAppliance("VA", 0, 0, false, Constants.VMStorage);
		Repository repo = new Repository(Constants.VMStorage * 1500, "IaaS", 1, 1, 1, globalLatencyMapInternal);
		repo.registerObject(va);
		return repo;
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
		ResourceConstraints[] out = new ResourceConstraints[vmTypes];
		for (int i = 0; i < out.length; i++) {
			out[i] = new ConstantConstraints(Constants.VMCores[i], Constants.VMMIPS[i], Constants.VMRAM[i]);
		}
		return out;
	}

	public VirtualMachine[] multipleVMs(int count)
			throws VMManagementException, NetworkException, NumberFormatException, IOException {
		VirtualMachine[] out = new VirtualMachine[count];
		for (int i = 0; i < out.length; i++) {
			out[i] = iaas.requestVM(va, rc[i % Constants.VMTYPES], repo, 1)[0];
			data.add(new DataVM(out[i], loadFiles(Constants.inputfolder)[i].getAbsolutePath()));
			Timed.simulateUntilLastEvent();
		}
		vms = Arrays.asList(out);
		return out;

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

	/** Generates a single name for the latency map **/
	public String generateName(final String prefix, final int latency) {
		return generateNames(1, prefix, latency)[0];
	}

	private void printInformation() {
		int rPM = 0, rVM = 0, totalPM = 0, totalVM = 0;
		for (PhysicalMachine pm : iaas.machines) {
			totalPM++;
			for (VirtualMachine vm : pm.publicVms) {
				totalVM++;
			}
			if (pm.isHostingVMs())
				rVM++;
			if (pm.isRunning())
				rPM++;
		}

		System.out.println("Total PMs: " + totalPM);
		System.out.println("Running PMs: " + rPM);
		System.out.println("PMs with VMs running on them: " + rVM);
		System.out.println("VMs: " + totalVM);
		System.out.println("Total Migrations: " + ((BeloglazovScheduler) iaas.sched).getMigrationCount());

	}

	public File[] loadFiles(String inputPath) {
		return inputFiles != null ? inputFiles : (inputFiles = new File(inputPath).listFiles());

	}

	public static void main(String[] args) {
		try {
			new Experiment(true).startSimulation();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | NoSuchFieldException | VMManagementException
				| NetworkException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private class AllocationOptimizer extends Timed {
		long setFreq = 5 * 60;// 1 tick = 1 second
		IaaSService observe;
		int count = 0;

		@Override
		public void tick(long fires) {
			if (observe.sched instanceof BeloglazovScheduler) {
				System.out.println("Currently allocating at " + count);
				((BeloglazovScheduler) observe.sched).optimizeAllocation(count++);
				if (count >= 289)
					cancel();
			}
		}

		public void cancel() {
			System.out.println(count);
			unsubscribe();
		}

		public AllocationOptimizer(IaaSService iaas) {
			if (subscribe(1))
				System.out.println("Successfully registered subscribtion");
			;
			this.observe = iaas;

		}
	}
}
