package com.paluno.clemens.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.paluno.clemens.Constants;
import com.paluno.clemens.DataVM;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine.ResourceAllocation;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VMManager.VMManagementException;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.FirstFitScheduler;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.QueueingData;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.pmiterators.PMIterator;
import hu.mta.sztaki.lpds.cloud.simulator.io.NetworkNode.NetworkException;

public class BeloglazovScheduler extends FirstFitScheduler {
	/**
	 * The set of resource allocations made for the current VM request (this is
	 * important for multi VM requests)
	 */
	int total;
	ResourceAllocation[] ras = new ResourceAllocation[5];
	/**
	 * the largest allocation that was possible to collect from all running PMs
	 * in the infrastructure. this is important to determine the amount of
	 * resources that need to become free before the scheduler would be able to
	 * place the head of the queue to any of the PMs in the infrastructure.
	 */
	ResourceAllocation raBiggestNotSuitable = null;
	private List<DataVM> dataVMs;
	private int counter = 0;
	protected int migrationCounter = 0;

	public BeloglazovScheduler(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	public void optimizeAllocation(int i) {
		counter = i;
		try {
			this.optimizeAllocation();
		} catch (NetworkException | VMManagementException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void initializeData(List<DataVM> data) {
		this.dataVMs = data;
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		final PMIterator currIterator = getPMIterator();
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		if (currIterator.hasNext()) {
			QueueingData request;
			ResourceAllocation allocation;
			boolean processableRequest = true;
			int vmNum = 0;
			while (queue.size() > 0 && processableRequest) {
				request = queue.get(0);

				vmNum = 0;
				do {
					processableRequest = false;
					do {
						final PhysicalMachine pm = currIterator.next();
						if (pm.localDisk.getFreeStorageCapacity() >= request.queuedVMs[vmNum].getVa().size) {
							try {
								allocation = pm.allocateResources(request.queuedRC, false,
										PhysicalMachine.defaultAllocLen);
								if (allocation != null) {
									if (allocation.allocated.compareTo(request.queuedRC) >= 0) {
										// Successful allocation
										if (pm.freeCapacities.getRequiredCPUs() == 0 && currIterator.hasNext()) {
											currIterator.next();
										}
										currIterator.markLastCollected();
										if (vmNum == ras.length) {
											ResourceAllocation[] rasnew = new ResourceAllocation[vmNum * 2];
											System.arraycopy(ras, 0, rasnew, 0, vmNum);
											ras = rasnew;
										}
										ras[vmNum] = allocation;
										processableRequest = true;
										break;
									} else {
										if (raBiggestNotSuitable == null) {
											raBiggestNotSuitable = allocation;
										} else {
											if (allocation.allocated.compareTo(raBiggestNotSuitable.allocated) > 0) {
												raBiggestNotSuitable.cancel();
												raBiggestNotSuitable = allocation;
											} else {
												allocation.cancel();
											}
										}
									}
								}
							} catch (VMManagementException e) {
							}
						}
					} while (currIterator.hasNext());
					currIterator.restart(true);
				} while (++vmNum < request.queuedVMs.length && processableRequest);
				if (processableRequest) {
					try {
						for (int i = request.queuedVMs.length - 1; i >= 0; i--) {
							vmNum--;
							allocation = ras[i];
							allocation.getHost().deployVM(request.queuedVMs[i], allocation, request.queuedRepo);
							ras[i] = null;
						}
						manageQueueRemoval(request);
					} catch (VMManagementException e) {
						processableRequest = false;
					} catch (NetworkException e) {
						// Connectivity issues! Should not happen!
						System.err.println("WARNING: there are connectivity issues in the system." + e.getMessage());
						processableRequest = false;
					}
				} else {
					AlterableResourceConstraints arc = new AlterableResourceConstraints(request.queuedRC);
					arc.multiply(request.queuedVMs.length - vmNum + 1);
					if (raBiggestNotSuitable != null) {
						arc = new AlterableResourceConstraints(request.queuedRC);
						arc.subtract(raBiggestNotSuitable.allocated);
					}
					returner = new ConstantConstraints(arc);
				}
				if (raBiggestNotSuitable != null) {
					raBiggestNotSuitable.cancel();
					raBiggestNotSuitable = null;
				}
			}
			vmNum--;
			for (int i = 0; i < vmNum; i++) {
				ras[i].cancel();
				ras[i] = null;
			}
		}
		return returner;
	}

	protected PhysicalMachine findHostForVM(VirtualMachine vm, ResourceConstraints rc) {
		for (PhysicalMachine pm : getPmList()) {
			if ((pm.isRunning()) && pm.isHostableRequest(rc)) {
				return pm;
			}
		}
		return null;
	}

	@SuppressWarnings("unused")
	@Deprecated
	public void random() {
		// System.out.println("Entered Scheduler");
		ConstantConstraints returner = new ConstantConstraints(getTotalQueued());
		QueueingData request;
		ResourceConstraints constraints;
		ResourceAllocation allocation = null;
		boolean processableRequest = true;
		int vmNum = 0;
		while (queue.size() > 0 && processableRequest) {
			request = queue.get(0);
			constraints = request.queuedRC;
			for (VirtualMachine vm : request.queuedVMs) {
				PhysicalMachine allocatedHost = findHostForVM(vm, constraints);
				if (allocatedHost == null) {
					System.out.println("Couldn't find Host for VM: " + vm);
					continue;
				}
				try {
					allocation = allocatedHost.allocateResources(constraints, false, PhysicalMachine.defaultAllocLen);
				} catch (VMManagementException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();

					if (allocation != null) {
						try {
							allocation.getHost().deployVM(vm, allocation, parent.repositories.get(0));
						} catch (VMManagementException | NetworkException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			manageQueueRemoval(request);
		}

	}

	public synchronized void optimizeAllocation() throws NetworkException, VMManagementException {
		List<PhysicalMachine> pms = getPmList();

		// overutilized pms

		List<PhysicalMachine> overutilizedHosts = getOverutilizedHosts();// -->

		// ok
		// vms to migrate from overutilized hosts

		List<VirtualMachine> vmsToMigrate = getVMsToMigrateFromHosts(overutilizedHosts);// -->
		// ok
		List<PhysicalMachine> targets = getNewVMPlacement(vmsToMigrate, overutilizedHosts);
		
		getNewPlacementForUnderutilizedHosts(overutilizedHosts, targets);

	}

	protected List<PhysicalMachine> getPmList() {
		return parent.machines;
	}

	protected boolean isRunning(PhysicalMachine pm) {
		return pm.isHostingVMs();
	}

	protected void getNewPlacementForUnderutilizedHosts(List<PhysicalMachine> overUtilizedHosts,
			List<PhysicalMachine> targets) throws NetworkException, VMManagementException {
		List<PhysicalMachine> switchedOffPMs = getSwitchedOfPMs();

		// Overutilized hosts + hosts that are targets of migration from
		// overutilized hosts
		List<PhysicalMachine> excludedHostForFinding = new LinkedList<PhysicalMachine>();
		excludedHostForFinding.addAll(switchedOffPMs);
		excludedHostForFinding.addAll(overUtilizedHosts);
		excludedHostForFinding.addAll(targets);

		List<PhysicalMachine> excludedHostForPlacement = new LinkedList<PhysicalMachine>();
		excludedHostForPlacement.addAll(overUtilizedHosts);
		excludedHostForPlacement.addAll(switchedOffPMs);

		int numberOfHosts = getPmList().size();

		while (true) {

			if (numberOfHosts == excludedHostForFinding.size()) {
				System.out.println("Number of hosts = excluded");
				break;
			}
			PhysicalMachine underUtilizedHost = getUnderUtilizedHost(excludedHostForFinding);
			if (underUtilizedHost == null) {

				break;
			} else {
				System.out.println("under");
			}
			excludedHostForFinding.add(underUtilizedHost);
			excludedHostForPlacement.add(underUtilizedHost);

			List<VirtualMachine> vmsToMigrateFromUnderUtilized = getVmsFromUnderUtilized(underUtilizedHost);
			if (vmsToMigrateFromUnderUtilized.isEmpty())
				continue;

			getNewVmPlacementFromUnderUtilized(vmsToMigrateFromUnderUtilized, excludedHostForPlacement);

		}

	}

	protected void getNewVmPlacementFromUnderUtilized(List<? extends VirtualMachine> vmsToMigrate,
			List<PhysicalMachine> excludedPlacement) throws NetworkException, VMManagementException {

		Collections.sort(vmsToMigrate, vmCPUComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, excludedPlacement);
			if (allocatedHost != null) {
				ResourceAllocation alloc = allocatedHost.allocateResources(vm.getResourceAllocation().allocated, false,
						PhysicalMachine.migrationAllocLen);
				if (alloc != null) {
					vm.migrate(alloc);
					Timed.simulateUntil(Timed.getFireCount()+10);
					migrationCounter++;
				}

			} else {
				System.out.println("Couln't allocate all VMs");

				break;
			}
		}

	}

	protected List<VirtualMachine> getVmsFromUnderUtilized(PhysicalMachine underUtilizedHost) {
		List<VirtualMachine> out = new LinkedList<VirtualMachine>();
		for (VirtualMachine vm : underUtilizedHost.listVMs()) {
			if (!vm.getState().equals(VirtualMachine.State.MIGRATING)) {
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
			double utilization = getUtilizationOfCpu(pm);
			if (utilization > 0 && utilization < minUtilization && noMigration(pm)) {
				minUtilization = utilization;
				underUtilized = pm;
			}
		}
		return underUtilized;
	}

	protected boolean noMigration(PhysicalMachine pm) {
		List<VirtualMachine> migrating = new LinkedList<VirtualMachine>();
		for (VirtualMachine vm : pm.listVMs()) {
			if (vm.getState().equals(VirtualMachine.State.MIGRATING))
				migrating.add(vm);
		}
		return migrating.isEmpty();
	}

	protected double getUtilization(PhysicalMachine pm) {
		if (dataVMs.isEmpty()) {
			System.out.println("data VM empty");
			double totalRequestedMips = 0;
			for (VirtualMachine vm : pm.listVMs()) {
				totalRequestedMips += vm.getPerTickProcessingPower();
			}
			double utilization = totalRequestedMips / pm.getCapacities().getTotalProcessingPower();
			return utilization;
		} else {
			double totalUtilization = 0;
			for (VirtualMachine vm : pm.listVMs()) {
				totalUtilization += (getDataFromVM(vm).getUtilization(counter)
						* vm.getResourceAllocation().allocated.getTotalProcessingPower());

			}
			double utilization = totalUtilization / pm.getCapacities().getTotalProcessingPower();

			return utilization;
		}
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
		for (PhysicalMachine pm : getPmList()) {
			if (isRunning(pm))
				out.add(pm);
		}
		return out;
	}

	protected List<PhysicalMachine> getNewVMPlacement(List<? extends VirtualMachine> vmsToMigrate,
			List<PhysicalMachine> pmsToFree) throws NetworkException, VMManagementException {
		List<PhysicalMachine> targets = new LinkedList<PhysicalMachine>();
		Collections.sort(vmsToMigrate, vmCPUComp);
		for (VirtualMachine vm : vmsToMigrate) {
			PhysicalMachine allocatedHost = findHostForVM(vm, pmsToFree);

			if (allocatedHost != null) {
				targets.add(allocatedHost);
				// if (!allocatedHost.isRunning()) {
				// allocatedHost.turnon();
				// Timed.simulateUntilLastEvent();
				// }
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

	protected PhysicalMachine findHostForVM(VirtualMachine vm, List<PhysicalMachine> pmsToFree)
			throws NetworkException {
		double minPower = Double.MAX_VALUE;
		PhysicalMachine allocatedHost = null;

		for (PhysicalMachine pm : getPmList()) {

			if (pmsToFree.contains(pm))
				continue;

			if (pm.isHostableRequest(vm.getResourceAllocation().allocated)) {
				if (getUtilizationOfCpuMips(pm) != 0 && isHostOverUtilizedAfterAllocation(pm, vm)) {
					continue;
				}
				double powerAfterAllocation = getPowerAfterAllocation(pm, vm);
				if (powerAfterAllocation != -1) {
					double powerDiff = powerAfterAllocation
							- pm.getCurrentPowerBehavior().getCurrentPower(getCurrentUtilization(pm));
					if (powerDiff < minPower) {
						minPower = powerDiff;
						allocatedHost = pm;
					}
				}
			}

		}

		return allocatedHost;
	}

	public int getMigrationCount() {
		return migrationCounter;
	}

	protected double getPowerAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
		double power = 0;
		power = pm.getCurrentPowerBehavior().getCurrentPower(getMaxUtilizationAfterAllocation(pm, vm));

		return power;
	}

	protected double getMaxUtilizationAfterAllocation(PhysicalMachine pm, VirtualMachine vm) {
		if (dataVMs.isEmpty()) {
			double additional = vm.getResourceAllocation().allocated.getTotalProcessingPower();
			double hostUtilizationMips = getCurrentUtilization(pm);
			double hostPotentialUtilizationMips = hostUtilizationMips + additional;
			return hostPotentialUtilizationMips;
		} else {
			// (getDataFromVM(vm).getUtilization(counter)*vm.getResourceAllocation().allocated.getTotalProcessingPower())
			double additional = vm.getResourceAllocation().allocated.getTotalProcessingPower();
			double hostUtilizationMips = getUtilizationOfCpuMips(pm);
			double hostPotentialUtilizationMips = hostUtilizationMips + additional;
			return hostPotentialUtilizationMips / pm.getCapacities().getTotalProcessingPower();
		}
	}

	protected boolean isHostOverUtilizedAfterAllocation(PhysicalMachine pm, VirtualMachine vm) throws NetworkException {
		double util = getMaxUtilizationAfterAllocation(pm, vm);

		return util > Constants.staticThreshold;
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
			totalRequestedMips += vm.getResourceAllocation().allocated.getTotalProcessingPower();
		}
		double utilization = totalRequestedMips / pm.getCapacities().getTotalProcessingPower();
		// was getCurrentUtilization(pm)> Constants.staticThreshold
		if (utilization > 1)
			System.out.println("Utilization > 1 --> Error");
		return utilization > Constants.staticThreshold;
	}

	protected double getUtilizationOfCpu(PhysicalMachine pm) {
		double utilization = 0, vmMips = 0;
		if (dataVMs.isEmpty())
			;
		else

			for (VirtualMachine vm : pm.listVMs()) {
				vmMips += (getDataFromVM(vm).getUtilization(counter)
						* vm.getResourceAllocation().allocated.getTotalProcessingPower());
			}
		utilization = vmMips / pm.getCapacities().getTotalProcessingPower();
		if (utilization > 1 && utilization < 1.01) {
			utilization = 1;
		}
		return utilization;
	}

	protected double getCurrentUtilization(PhysicalMachine pm) {
		double vmRequest = 0;
		double utilization = 0;
		if (dataVMs.isEmpty()) {
			for (VirtualMachine vm : pm.listVMs()) {
				vmRequest += vm.getPerTickProcessingPower();
			}
			utilization = vmRequest / pm.getCapacities().getTotalProcessingPower();
		} else {
			for (VirtualMachine vm : pm.listVMs()) {
				vmRequest += (getDataFromVM(vm).getUtilization(counter)
						* vm.getResourceAllocation().allocated.getTotalProcessingPower());
			}
			utilization = vmRequest / pm.getCapacities().getTotalProcessingPower();
		}

		return utilization;

	}

	protected double getUtilizationOfCpuMips(PhysicalMachine pm) {
		double hostUtilization = 0;
		for (VirtualMachine vm : pm.listVMs()) {
			if (vm.getState().equals(VirtualMachine.State.MIGRATING)) {
				hostUtilization += (getDataFromVM(vm).getUtilization(counter)
						* vm.getResourceAllocation().allocated.getTotalProcessingPower()) * 0.9 / 0.1;
			}
			hostUtilization += (getDataFromVM(vm).getUtilization(counter)
					* vm.getResourceAllocation().allocated.getTotalProcessingPower());
		}
		return hostUtilization;
	}

	public DataVM getDataFromVM(VirtualMachine vm) {
		for (DataVM data : dataVMs) {
			if (data.getVM() == vm)
				return data;
		}
		return null;
	}

	/**
	 * Gets the over utilized hosts.
	 * 
	 * @param pms
	 *            the list to look for hosts
	 * @return the over utilized hosts
	 */
	protected List<PhysicalMachine> getOverutilizedHosts() {
		List<PhysicalMachine> overutilHosts = new ArrayList<PhysicalMachine>();
		for (PhysicalMachine pm : getPmList()) {
			if (isHostOverUtilized(pm)) {
				overutilHosts.add(pm);
				// System.out.println("Found overutilized Host");
			}
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
	protected List<VirtualMachine> getVMsToMigrateFromHosts(List<PhysicalMachine> pms) {
		List<VirtualMachine> vmsToMigrate = new LinkedList<VirtualMachine>();
		for (PhysicalMachine pm : pms) {
			while (true) {
				VirtualMachine vm = vmToMigrate(pm, vmsToMigrate);

				if (vm == null)
					break;
				vmsToMigrate.add(vm);

				if (!isHostStillOverUtilized(pm, vmsToMigrate))
					break;

			}
		}
		return vmsToMigrate;

	}

	/**
	 * checks if the host is still overutilized if we would take the vms off it
	 * 
	 * @param pm
	 *            the host that is/was overutilized
	 * @param takenVMs
	 *            the vms that we will migrate from this host
	 * @return true if another vm should be migrated, false otherwise
	 */
	private boolean isHostStillOverUtilized(PhysicalMachine pm, List<VirtualMachine> takenVMs) {
		// if (dataVMs.isEmpty()) {
		// System.out.println("data VM empty");
		// double totalRequestedMips = 0;
		// for (VirtualMachine vm : pm.listVMs()) {
		// if (takenVMs.contains(vm))
		// continue;
		// totalRequestedMips += vm.getPerTickProcessingPower();
		// }
		// double utilization = totalRequestedMips /
		// pm.getCapacities().getTotalProcessingPower();
		// return utilization > Constants.staticThreshold;
		// } else {
		// double totalUtilization = 0;
		// for (VirtualMachine vm : pm.listVMs()) {
		// if (takenVMs.contains(vm))
		// continue;
		// totalUtilization += (getDataFromVM(vm).getUtilization(counter)
		// * vm.getResourceAllocation().allocated.getTotalProcessingPower());
		//
		// }
		// double utilization = totalUtilization /
		// pm.getCapacities().getTotalProcessingPower();
		// boolean compare = utilization > Constants.staticThreshold;
		// return compare;
		// }
		double totalRequestedMips = 0;
		for (VirtualMachine vm : pm.listVMs()) {
			if (takenVMs.contains(vm))
				continue;
			totalRequestedMips += vm.getResourceAllocation().allocated.getTotalProcessingPower();
		}
		double utilization = totalRequestedMips / pm.getCapacities().getTotalProcessingPower();
		// was getCurrentUtilization(pm)> Constants.staticThreshold
		if (utilization > 1)
			System.out.println("Utilization > 1 --> Error");
		return utilization > Constants.staticThreshold;
	}

	/**
	 * Find a vm to migrate from given pm
	 * 
	 * @param pm
	 * @param vmsToMigrate
	 * @return
	 */
	protected VirtualMachine vmToMigrate(PhysicalMachine pm, List<VirtualMachine> vmsToMigrate) {
		List<VirtualMachine> migratable = migratableVms(pm);
		if (migratable.isEmpty())
			return null;
		VirtualMachine vmToMigrate = null;
		double minMetric = Double.MAX_VALUE;
		for (VirtualMachine vm : migratable) {
			if (vm.getState().equals(VirtualMachine.State.MIGRATING))
				continue;
			if (vmsToMigrate.contains(vm))
				continue;
			double metric = vm.getResourceAllocation().allocated.getRequiredMemory();
			if (metric < minMetric) {
				minMetric = metric;
				vmToMigrate = vm;
			}
		}

		return vmToMigrate;
	}

	/**
	 * gets the vms that can be migrated
	 * 
	 * @param pm
	 * @return
	 */
	protected List<VirtualMachine> migratableVms(PhysicalMachine pm) {
		List<VirtualMachine> migratableVMs = new ArrayList<VirtualMachine>();
		for (VirtualMachine vm : pm.listVMs()) {
			if (!vm.getState().equals(VirtualMachine.State.MIGRATING))
				migratableVMs.add(vm);
		}
		return migratableVMs;
	}

	protected Comparator<VirtualMachine> vmCPUComp = new Comparator<VirtualMachine>() {

		@Override
		public int compare(VirtualMachine a, VirtualMachine b) {
			if (dataVMs.isEmpty()) {
				Double aCPU = a.getPerTickProcessingPower();
				Double bCPU = b.getPerTickProcessingPower();
				return bCPU.compareTo(aCPU);
			} else {
				Double aCPU = (getDataFromVM(a).getUtilization(counter)
						* a.getResourceAllocation().allocated.getTotalProcessingPower());
				Double bCPU = (getDataFromVM(b).getUtilization(counter)
						* b.getResourceAllocation().allocated.getTotalProcessingPower());
				return bCPU.compareTo(aCPU);
			}

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
