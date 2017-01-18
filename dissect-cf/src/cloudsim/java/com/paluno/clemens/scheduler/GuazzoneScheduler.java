package com.paluno.clemens.scheduler;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class GuazzoneScheduler extends BeloglazovScheduler {

	public GuazzoneScheduler(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected List<PhysicalMachine> getNewVMPlacement(List<? extends VirtualMachine> vmsToMigrate,
			List<PhysicalMachine> pmsToFree) throws NetworkException, VMManagementException {
		List<PhysicalMachine> targets = new LinkedList<PhysicalMachine>();
		Collections.sort(vmsToMigrate, vmComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, pmsToFree);
			if (allocatedHost != null) {
				targets.add(allocatedHost);
				ResourceAllocation alloc = allocatedHost.allocateResources(vm.getResourceAllocation().allocated, false,
						PhysicalMachine.migrationAllocLen);
				if (alloc != null) {
					vm.migrate(alloc);
					migrationCounter++;
				}
			}
		}
		return targets;
	}

	@Override
	protected PhysicalMachine findHostForVM(VirtualMachine vm, List<PhysicalMachine> pmsToFree)
			throws NetworkException {
		List<PhysicalMachine> lph = new LinkedList<PhysicalMachine>(getPmList());

		Collections.sort(lph, pmComp);
		for (PhysicalMachine pm : lph) {
			if (pmsToFree.contains(pm))
				continue;
			if (pm.isHostableRequest(vm.getResourceAllocation().allocated)) {
				if (isHostOverUtilizedAfterAllocation(pm, vm))
					continue;
				return pm;
			}
		}
		return null;
	}

	/**
	 * Comparator for sorting like guazzone
	 * 
	 */
	public final Comparator<PhysicalMachine> pmComp = new Comparator<PhysicalMachine>() {

		@Override
		public int compare(PhysicalMachine a, PhysicalMachine b) {

			Integer aUtilization = (a.getState() == PhysicalMachine.State.OFF) ? 0 : 1;
			Integer bUtilization = (b.getState() == PhysicalMachine.State.OFF) ? 0 : 1;
			int cUtilization = bUtilization.compareTo(aUtilization); // descending

			if (cUtilization != 0)
				return cUtilization;

			Double aTotal = a.getPerTickProcessingPower();
			Double bTotal = b.getPerTickProcessingPower();
			int cTotal = bTotal.compareTo(aTotal); // descending

			if (cTotal != 0)
				return cTotal;

			Double aIdle = a.getCurrentPowerBehavior().getMinConsumption(); // idle
																			// power
																			// consumption
			Double bIdle = b.getCurrentPowerBehavior().getMinConsumption();
			int cIdle = aIdle.compareTo(bIdle); // ascending

			return cIdle;
		}
	};
	/**
	 * sorting the VMs by their processing power
	 * 
	 */
	public final Comparator<VirtualMachine> vmComp = new Comparator<VirtualMachine>() {
		@Override
		public int compare(VirtualMachine a, VirtualMachine b) throws ClassCastException {
			Double aUtilization = a.getPerTickProcessingPower();
			Double bUtilization = b.getPerTickProcessingPower();
			return bUtilization.compareTo(aUtilization);
		}
	};
}
