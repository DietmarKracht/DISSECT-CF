package com.paluno.clemens.power;

import java.util.EnumMap;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.ConstantConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.LinearConsumptionModel;
import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;

public class CustomPowerTransitionGenerator {

	/**
	 * The generator function that derives the power transition and power state
	 * definitions from a few simple parameters. The generated power states will
	 * all be based on the linear consumption model (except during power off
	 * state).
	 * 
	 * @param minpower
	 *            the power (in W) to be drawn by the PM while it is completely
	 *            switched off (but plugged into the wall socket)
	 * @param idlepower
	 *            the power (in W) to be drawn by the PM's CPU while it is
	 *            running but not doing any useful tasks.
	 * @param maxpower
	 *            the power (in W) to be drawn by the PM's CPU if it's CPU is
	 *            completely utilized
	 * @param diskDivider
	 *            the ratio of the PM's disk power draw values compared to the
	 *            it's CPU's power draw values
	 * @param netDivider
	 *            the ratio of the PM's network power draw values compared to
	 *            the it's CPU's power draw values
	 * @return a power state setup useful for instantiating PMs
	 * @throws SecurityException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws InstantiationException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws IllegalAccessException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 * @throws NoSuchFieldException
	 *             if the power state to be created failed to instantiate
	 *             properly
	 */
	public static EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>> generateTransitions(
			double minpower, double idlepower, double maxpower, double diskDivider, double netDivider,
			Class<? extends PowerState.ConsumptionModel> consumptionmodel)
			throws SecurityException, InstantiationException, IllegalAccessException, NoSuchFieldException {
		EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>> returner = new EnumMap<PhysicalMachine.PowerStateKind, EnumMap<PhysicalMachine.State, PowerState>>(
				PhysicalMachine.PowerStateKind.class);
		EnumMap<PhysicalMachine.State, PowerState> hostStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.host, hostStates);
		EnumMap<PhysicalMachine.State, PowerState> diskStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.storage, diskStates);
		EnumMap<PhysicalMachine.State, PowerState> netStates = new EnumMap<PhysicalMachine.State, PowerState>(
				PhysicalMachine.State.class);
		returner.put(PhysicalMachine.PowerStateKind.network, netStates);
		//Custom Consumptionmodel
		PowerState hostDefault = new PowerState(idlepower, maxpower - idlepower, consumptionmodel);
		
		PowerState diskDefault = new PowerState(idlepower / diskDivider / 2, (maxpower - idlepower) / diskDivider / 2,
				LinearConsumptionModel.class);
		PowerState netDefault = new PowerState(idlepower / netDivider / 2, (maxpower - idlepower) / netDivider / 2,
				LinearConsumptionModel.class);
		for (PhysicalMachine.State aState : PhysicalMachine.StatesOfHighEnergyConsumption) {
			hostStates.put(aState, hostDefault);
			diskStates.put(aState, diskDefault);
			netStates.put(aState, netDefault);
		}

		hostStates.put(PhysicalMachine.State.OFF, new PowerState(minpower, 0, ConstantConsumptionModel.class));
		diskStates.put(PhysicalMachine.State.OFF, new PowerState(0, 0, ConstantConsumptionModel.class));
		netStates.put(PhysicalMachine.State.OFF, new PowerState(0, 0, ConstantConsumptionModel.class));
		return returner;
	}
}
