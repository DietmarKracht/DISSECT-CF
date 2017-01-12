package com.paluno.clemens;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
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
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.MultiPMController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;

public class Simulation {
	private Logger log = Logger.getLogger("Simulation");
	private final int vmTypes = Constants.VMTYPES;

	public static void main(String[] args) {
		Simulation s = new Simulation();
		try {
			s.startSimulation();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException | NoSuchFieldException | VMManagementException
				| NetworkException e) {
			// TODO Auto-generated catch block
			System.out.println("Something went wrong");
			e.printStackTrace();
			System.exit(0);
		}
	}

	private int repoID = 0;

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
	 */
	public void startSimulation()
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, NoSuchFieldException, VMManagementException, NetworkException {

		reset();
		IaaSService s = createIaaS(true);
		EnergyMeter em = new IaaSEnergyMeter(s);
		// em.startMeter(5, false);
		ArrayList<PhysicalMachine> pms = createMultiplePMs(Constants.PMcount);
		s.bulkHostRegistration(pms);
		s.registerRepository(createRepo(true));
		Repository r = s.repositories.get(0);
		VirtualAppliance va = (VirtualAppliance) r.contents().iterator().next();
		ResourceConstraints[] rc = createConstraints(vmTypes);
		VirtualMachine[] vms = requestVMs(va, rc, r, s, Constants.VMcount);
		ResourceConsumption cons = new ResourceConsumption(10, 1000, pms.get(0).directConsumer, pms.get(0),
				new ConsumptionEventAdapter());
		cons.registerConsumption();

		for (VirtualMachine vm : vms) {
			vm.newComputeTask(1000, vm.getPerTickProcessingPower(), new ConsumptionEventAdapter());
		}
		Timed.simulateUntilLastEvent();
		for (VirtualMachine vm : vms) {
			vm.destroy(true);
		}
		Timed.simulateUntilLastEvent();
		log.info("Simulation finished in " + Timed.getFireCount() + " ticks");

	}

	/**
	 * Requests multiple vms for the iaas with different types
	 * 
	 * @param count
	 *            The amount of vms to request
	 * @return
	 * @throws VMManagementException
	 * @throws NetworkException
	 */
	public VirtualMachine[] requestVMs(VirtualAppliance virtualAppliance, ResourceConstraints[] resourceConstraints,
			Repository repository, IaaSService iaas, int count) throws VMManagementException, NetworkException {
		VirtualMachine[] out = new VirtualMachine[count];
		for (int i = 0; i < out.length; i++) {
			// System.out.println("Currently requesting VM No. "+i);
			out[i] = iaas.requestVM(virtualAppliance, resourceConstraints[i % vmTypes], repository, 1)[0];
		}
		return out;
	}

	private List<VirtualMachine> createInitialVMs(int count, ResourceConstraints[] rc, Repository repo,
			VirtualAppliance va) {
		List out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			out.add(new VirtualMachine(va));
		}
		return null;
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
				MultiPMController.class);
	}

	public void initialSetup(List<PhysicalMachine> pms, List<VirtualMachine> vms) {

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
	public PhysicalMachine createPM(double perCoreProcessing,
			EnumMap<PowerStateKind, EnumMap<State, PowerState>> powerTransitions) {
		// log.info("Creates PM");
		return new PhysicalMachine(Constants.PMCores, perCoreProcessing, Constants.PMram, createRepo(false), 0, 0,
				powerTransitions);
	}

	/**
	 * Creates a new Repository
	 * 
	 * @param withVA
	 *            if an Virtual Appliance is needed
	 * @return
	 */
	public Repository createRepo(boolean withVA) {
		// log.info("Creates Repository " + (withVA == true ? "with Virtual
		// Appliance" : "without Virtual Appliance"));
		Repository out = new Repository(Constants.PMStorage, "R-" + repoID++, 1L * 1024, 1L * 1024, 1024L * 1024 * 1024,
				null);
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
		for (int i = 0; i < count; i++) {
			pms.add(createPM(Constants.PMMips[i % 2],
					CustomPowerTransitionGenerator.generateTransitions(0,
							((PowerConsuming) Constants.models[i % 2]).idlePower(),
							((PowerConsuming) Constants.models[i % 2]).maxPower(), Double.MAX_VALUE, Double.MAX_VALUE,
							Constants.models[i % 2].getClass())));
		}
		return pms;
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
}
