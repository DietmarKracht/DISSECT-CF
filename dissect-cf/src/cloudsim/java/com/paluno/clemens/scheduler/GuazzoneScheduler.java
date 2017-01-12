package com.paluno.clemens.scheduler;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.paluno.clemens.util.Helper;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.QueueingData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;

public class GuazzoneScheduler extends FirstFitScheduler {

	public GuazzoneScheduler(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		List<PhysicalMachine> pms = Helper.getList(currIterator);
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		// sort the pms
		Collections.sort(pms, pmComp);
		QueueingData request = queue.get(0);
		boolean processable = false;
		for (VirtualMachine vm : request.queuedVMs) {
			for (PhysicalMachine pm : pms) {
				if (pm.localDisk.getFreeStorageCapacity() > vm.getVa().size) {

				} else
					continue;
			}
		}

		return returner;
	}

	/**
	 * Comparator for sorting like guazzone
	 * 
	 */
	public static final Comparator<PhysicalMachine> pmComp = new Comparator<PhysicalMachine>() {

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
	public static final Comparator<VirtualMachine> vmComp = new Comparator<VirtualMachine>() {
		@Override
		public int compare(VirtualMachine a, VirtualMachine b) throws ClassCastException {
			Double aUtilization = a.getPerTickProcessingPower();
			Double bUtilization = b.getPerTickProcessingPower();
			return bUtilization.compareTo(aUtilization);
		}
	};
}
