package com.paluno.clemens.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.paluno.clemens.Constants;
import com.paluno.clemens.util.Helper;

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

	@Override
	protected ConstantConstraints scheduleQueued() {
		super.scheduleQueued();
		// System.out.println("Entered Scheduler");
		final PMIterator currIterator = getPMIterator();
		List<PhysicalMachine> pms = Helper.getList(currIterator);

		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		// overutilized pms
		List<PhysicalMachine> pmsToFree = getOverutilizedHosts(pms);
		// vms to migrate from overutilized hosts
		List<VirtualMachine> vmsToMigrate = getVMsToMigrate(pmsToFree);

		getNewVMPlacement(vmsToMigrate, pmsToFree, pms);

		getNewPlacementForUnderutilizedHosts(pmsToFree, vmsToMigrate);

		return returner;
	}

	protected List<PhysicalMachine> getPmList() {
		return this.parent.machines;
	}

	protected void getNewPlacementForUnderutilizedHosts(List<PhysicalMachine> pmsToFree,
			List<VirtualMachine> vmsToMigrate) {
		List<PhysicalMachine> switchedOffPMs = getSwitchedOfPMs();
		List<PhysicalMachine> excluded = new LinkedList<PhysicalMachine>();
		excluded.addAll(switchedOffPMs);
		excluded.addAll(pmsToFree);
		excluded.addAll(pmsFromVMs(vmsToMigrate));

		List<PhysicalMachine> excludedPlacement = new LinkedList<PhysicalMachine>();
		excludedPlacement.addAll(pmsToFree);
		excludedPlacement.addAll(switchedOffPMs);

		int numberOfHosts = parent.machines.size();

		while (true) {

			if (numberOfHosts == excluded.size())
				break;
			PhysicalMachine underUtilizedHost = getUnderUtilizedHost(excluded);
			if (underUtilizedHost == null)
				break;

			excluded.add(underUtilizedHost);
			excludedPlacement.add(underUtilizedHost);

			List<VirtualMachine> vmsToMigrateFromUnderUtilized = getVmsFromUnderUtilized(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilized.isEmpty())
				continue;
			excluded.addAll(getNewVmPlacementFromUnderUtilized(vmsToMigrateFromUnderUtilized, excludedPlacement));
		}
	}

	protected Collection<? extends PhysicalMachine> getNewVmPlacementFromUnderUtilized(
			List<VirtualMachine> vmsToMigrate, List<PhysicalMachine> excludedPlacement) {
		List<PhysicalMachine> out = new LinkedList<PhysicalMachine>();
		Collections.sort(vmsToMigrate, vmCPUComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, getPmList(), excludedPlacement);
			if (allocatedHost != null) {
				try {
					vm.getResourceAllocation().getHost().migrateVM(vm, allocatedHost);
				} catch (VMManagementException | NetworkException e) {
					System.out.println("Unable to migrate VM: " + vm + " to PM " + allocatedHost);
					e.printStackTrace();
					break;
				}
				out.add(allocatedHost);

			} else {
				System.out.println("Couldn't reallocated all VMs - Reallocation cancelled");
				break;
			}
		}

		return out;
	}

	protected List<VirtualMachine> getVmsFromUnderUtilized(PhysicalMachine underUtilizedHost) {
		List<VirtualMachine> out = new LinkedList<VirtualMachine>();
		for (VirtualMachine vm : underUtilizedHost.listVMs()) {
			if (vm.getState() != VirtualMachine.State.MIGRATING) {
				out.add(vm);
			}
		}
		return out;
	}

	protected PhysicalMachine getUnderUtilizedHost(List<PhysicalMachine> excluded) {
		double minUtilization = 1;
		PhysicalMachine underUtilized = null;
		for (PhysicalMachine pm : getPmList()) {
			if (excluded.contains(pm))
				continue;
			double utilization = getUtilization(pm);
			if (utilization > 0 && utilization < minUtilization) {
				minUtilization = utilization;
				underUtilized = pm;
			}
		}
		return underUtilized;
	}

	protected double getUtilization(PhysicalMachine pm) {
		double utilization = pm.getPerTickProcessingPower() / pm.getCapacities().getTotalProcessingPower();
		if (utilization > 1 && utilization < 1.01) {
			utilization = 1;
		}
		return utilization;
	}

	protected List<? extends PhysicalMachine> pmsFromVMs(List<VirtualMachine> vmsToMigrate) {
		List<PhysicalMachine> out = new LinkedList<>();
		for (VirtualMachine vm : vmsToMigrate) {
			out.add(vm.getResourceAllocation().getHost());
		}
		return out;
	}

	/**
	 * Get the switched off hosts
	 * 
	 * @return switched off hosts
	 */
	protected List<PhysicalMachine> getSwitchedOfPMs() {
		List<PhysicalMachine> out = new LinkedList<>();
		for (PhysicalMachine pm : this.parent.machines) {
			if (pm.getState() == PhysicalMachine.State.OFF)
				out.add(pm);
		}
		return out;
	}

	protected void getNewVMPlacement(List<VirtualMachine> vmsToMigrate, List<PhysicalMachine> pmsToFree,
			List<PhysicalMachine> totalPms) {
		Collections.sort(vmsToMigrate, vmCPUComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, totalPms, pmsToFree);
			if (allocatedHost != null) {
				try {
					vm.getResourceAllocation().getHost().migrateVM(vm, allocatedHost);
				} catch (VMManagementException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (NetworkException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	protected PhysicalMachine findHostForVM(VirtualMachine vm, List<PhysicalMachine> totalPms,
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
			double powerAfterAllocation = getPowerAfterAllocation(pm, vm);
			if (powerAfterAllocation != -1) {
				double powerDiff = powerAfterAllocation - pm.getCurrentPowerBehavior().getCurrentPower(getPower(pm));
				if (powerDiff < minPower) {
					minPower = powerDiff;
					allocatedHost = pm;
				}
			}

		}
		return allocatedHost;
	}

	protected double getPower(PhysicalMachine pm) {
		double utilization = pm.getPerTickProcessingPower() / pm.getCapacities().getTotalProcessingPower();
		if (utilization > 1 && utilization < 1.01) {
			utilization = 1;
		}
		return utilization;
	}

	protected double getPowerAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
		double power = 0;
		power = pm.getCurrentPowerBehavior().getCurrentPower(getMaxUtilizationAfterAllocation(pm, vm));

		return power;
	}

	protected double getMaxUtilizationAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
		double requestedTotalMips = vm.getPerTickProcessingPower();
		double hostUtilizationMips = pm.getPerTickProcessingPower();
		double hostPotentialUtilizationMips = hostUtilizationMips + requestedTotalMips;
		double pePotentialUtilization = hostPotentialUtilizationMips / pm.getCapacities().getTotalProcessingPower();
		return pePotentialUtilization;
	}

	protected boolean isHostOverUtilizedAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
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
		return overUtilized;
	}

	/**
	 * Checks if a pm is overutilized with the current threshold
	 * 
	 * @param pm
	 *            the pm
	 * @return true, if the pm is overutilized; false otherwise
	 */
	protected boolean isHostOverUtilized(PhysicalMachine pm) {
		double totalRequestedMips = 0;
		for (VirtualMachine vm : pm.listVMs()) {
			totalRequestedMips += vm.getPerTickProcessingPower();
		}
		double utilization = totalRequestedMips / pm.getCapacities().getTotalProcessingPower();
		return utilization > Constants.staticThreshold;
	}

	protected List<PhysicalMachine> getOverutilizedHosts(List<PhysicalMachine> pms) {
		List<PhysicalMachine> overutilHosts = new ArrayList<PhysicalMachine>();
		for (PhysicalMachine pm : pms) {
			if (isHostOverUtilized(pm))
				overutilHosts.add(pm);
		}

		return overutilHosts;
	}

	/**
	 * gets the VMs to migrate from hosts
	 * 
	 * @param pms
	 *            the overutilized hosts
	 * @return the VMs to migrate from hosts
	 */
	protected List<VirtualMachine> getVMsToMigrate(List<PhysicalMachine> pms) {
		List<VirtualMachine> out = new LinkedList<VirtualMachine>();
		for (PhysicalMachine pm : pms) {
			VirtualMachine vm = vmToMigrate(pm);
			if (vm == null)
				break;
			out.add(vm);

			if (!isHostStillOverUtilized(pm, out))
				break;
		}
		return out;

	}

	private boolean isHostStillOverUtilized(PhysicalMachine pm, List<VirtualMachine> out) {
		double totalRequestedMips = 0;
		for (VirtualMachine vm : pm.listVMs()) {
			if (out.contains(vm))
				continue;
			totalRequestedMips += vm.getPerTickProcessingPower();
		}
		double utilization = totalRequestedMips / pm.getPerTickProcessingPower();
		return utilization > Constants.staticThreshold;
	}

	protected VirtualMachine vmToMigrate(PhysicalMachine pm) {
		List<VirtualMachine> migratable = migratableVms(pm);
		if (migratable.isEmpty())
			return null;
		VirtualMachine vmToMigrate = null;
		double minMetric = Double.MAX_VALUE;
		for (VirtualMachine vm : migratable) {
			if (vm.getState() == VirtualMachine.State.MIGRATING)
				continue;
			double metric = vm.getResourceAllocation().allocated.getRequiredMemory();
			if (metric < minMetric) {
				minMetric = metric;
				vmToMigrate = vm;
			}
		}

		return vmToMigrate;
	}

	protected List<VirtualMachine> migratableVms(PhysicalMachine pm) {
		List<VirtualMachine> migratableVMs = new ArrayList<VirtualMachine>();
		for (VirtualMachine vm : pm.listVMs()) {
			if (vm.getState() != VirtualMachine.State.MIGRATING)
				migratableVMs.add(vm);
		}
		return migratableVMs;
	}

	protected Comparator<VirtualMachine> vmCPUComp = new Comparator<VirtualMachine>() {

		@Override
		public int compare(VirtualMachine a, VirtualMachine b) {
			Double aCPU = a.getPerTickProcessingPower();
			Double bCPU = b.getPerTickProcessingPower();
			return bCPU.compareTo(aCPU);
		}

	};

	protected Comparator<VirtualMachine> vmComp = new Comparator<VirtualMachine>() {

		@Override
		public int compare(VirtualMachine a, VirtualMachine b) {
			ResourceConstraints aCons = a.getResourceAllocation().allocated;
			ResourceConstraints bCons = b.getResourceAllocation().allocated;

			return aCons.compareTo(bCons);
		}

	};

}
