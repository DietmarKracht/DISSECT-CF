package com.paluno.clemens.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.paluno.clemens.Constants;
import com.paluno.clemens.util.Helper;

import hu.mta.sztaki.lpds.cloud.simulator.energy.MonitorConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class BeloglazovScheduler extends FirstFitScheduler {

	public BeloglazovScheduler(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	@SuppressWarnings("unused")
	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		List<PhysicalMachine> pms = Helper.getList(currIterator);
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		List<PhysicalMachine> pmsToFree = getOverutilizedHosts(pms);
		List<VirtualMachine> vmsToMigrate = getVMsToMigrate(pmsToFree);
		Collections.sort(vmsToMigrate, vmCPUComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, pms, pmsToFree);
			if (allocatedHost != null) {
				try {
					vm.getResourceAllocation().getHost().migrateVM(vm, allocatedHost);
				} catch (VMManagementException | NetworkException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				;
			}
		}
		return returner;
	}

	private PhysicalMachine findHostForVM(VirtualMachine vm, List<PhysicalMachine> totalPms,
			List<PhysicalMachine> pmsToFree) {
		double minPower = Double.MAX_VALUE;
		PhysicalMachine allocatedHost = null;
		for (PhysicalMachine pm : totalPms) {
			if (pmsToFree.contains(pm))
				continue;
			if (pm.isHostableRequest(vm.getResourceAllocation().allocated)) {
				if (isHostOverUtilizedAfterAllocation(pm, vm))
					continue;
			}
		}
		return null;
	}

	private boolean isHostOverUtilizedAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
		boolean overUtilized = true;
		try {
			if (pm.allocateResources(vm.getResourceAllocation().allocated, false,
					PhysicalMachine.defaultAllocLen) != null) {
				overUtilized = isHostOverUtilized(pm);
			}
		} catch (VMManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return false;
	}

	private boolean isHostOverUtilized(PhysicalMachine pm) {
		MonitorConsumption mc = new MonitorConsumption(pm, 1);
		if (mc.getSubSecondProcessing() < Constants.staticThreshold)
			return true;
		return false;
	}

	private List<PhysicalMachine> getOverutilizedHosts(List<PhysicalMachine> pms) {
		List<PhysicalMachine> overutilHosts = new ArrayList<PhysicalMachine>();
		for (PhysicalMachine pm : pms) {

		}

		return overutilHosts;
	}

	private List<VirtualMachine> getVMsToMigrate(List<PhysicalMachine> pms) {
		List<VirtualMachine> out = new ArrayList<VirtualMachine>();
		for (PhysicalMachine pm : pms) {
			List<VirtualMachine> deployed = new ArrayList<>(pm.listVMs());
			Collections.sort(deployed, vmComp);
			out.add(deployed.get(0));
		}
		return out;

	}

	private Comparator<VirtualMachine> vmCPUComp = new Comparator<VirtualMachine>() {

		@Override
		public int compare(VirtualMachine a, VirtualMachine b) {
			Double aCPU = a.getPerTickProcessingPower();
			Double bCPU = b.getPerTickProcessingPower();
			return aCPU.compareTo(bCPU);
		}

	};

	private Comparator<VirtualMachine> vmComp = new Comparator<VirtualMachine>() {

		@Override
		public int compare(VirtualMachine a, VirtualMachine b) {
			ResourceConstraints aCons = a.getResourceAllocation().allocated;
			ResourceConstraints bCons = b.getResourceAllocation().allocated;

			return aCons.compareTo(bCons);
		}

	};

}
