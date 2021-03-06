package com.paluno.clemens;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;

import com.paluno.clemens.scheduler.BeloglazovScheduler;

import at.ac.uibk.dps.cloud.simulator.test.IaaSRelatedFoundation;
import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.PhysicalMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.VirtualMachine.State;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.AlterableResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ResourceConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.AlwaysOnMachines;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.pmscheduling.PhysicalMachineController;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ConsumptionEventAdapter;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.resourcemodel.ResourceConsumption.ConsumptionEvent;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;
import hu.mta.sztaki.lpds.cloud.simulator.io.Repository;
import hu.mta.sztaki.lpds.cloud.simulator.io.VirtualAppliance;
import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

public class PMTest extends IaaSRelatedFoundation {
	private File[] inputFiles;
	public IaaSService basic;
	public Repository repo;
	public VirtualAppliance va;
	public ResourceConstraints baseRC;
	public static List<DataVM> dataVMs = new LinkedList<DataVM>();
	static final int hostCount = 40;
	static final int vmCount = 1052;
	static final int maxTaskCount = 5;
	static final double maxTaskLen = 50;
	public int runningCounter = 0;
	public int destroyCounter = 0;

	public class VMHandler implements VirtualMachine.StateChange, ConsumptionEvent {
		private final VirtualMachine vm;
		private int myTaskCount;

		public VMHandler() throws Exception {
			AlterableResourceConstraints mRC = new AlterableResourceConstraints(baseRC);
			mRC.multiply(SeedSyncer.centralRnd.nextDouble());
			vm = basic.requestVM(va, mRC, repo, 1)[0];
			vm.subscribeStateChange(this);

			Timed.simulateUntil(Timed.getFireCount() + SeedSyncer.centralRnd.nextInt((int) maxTaskLen));
		}

		@Override
		public void stateChanged(VirtualMachine vm, final State oldState, final State newState) {
			switch (newState) {
			case RUNNING:
				runningCounter++;
				myTaskCount = 1 + SeedSyncer.centralRnd.nextInt(maxTaskCount - 1);
				try {
					for (int j = 0; j < myTaskCount; j++) {
						vm.newComputeTask(SeedSyncer.centralRnd.nextDouble() * maxTaskLen,
								ResourceConsumption.unlimitedProcessing, this);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				break;
			case DESTROYED:
				destroyCounter++;
			default:
			}
		}

		@Override
		public void conComplete() {
			if (--myTaskCount == 0) {
				try {
					vm.destroy(false);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public void conCancelled(final ResourceConsumption problematic) {
			throw new RuntimeException("No cancellations should happen");
		}

		public VirtualMachine getVM() {
			return vm;
		}
	}

	private void genericPerformanceCheck(Class<? extends Scheduler> vmsch,
			Class<? extends PhysicalMachineController> pmsch) throws Exception {
		basic = setupIaaS(vmsch, pmsch, hostCount, 1);
		baseRC = basic.machines.get(0).getCapacities();
		repo = basic.repositories.get(0);
		va = (VirtualAppliance) repo.contents().iterator().next();
		FireCounter f = new FireCounter(basic, 5 * 60);
		for (int i = 0; i < vmCount; i++) {
			VMHandler handler = new VMHandler();
			dataVMs.add(new DataVM(handler.getVM(), loadFiles(Constants.inputfolder)[i].getAbsolutePath()));
		}
		long time = Timed.getFireCount();
		for (PhysicalMachine pm : basic.machines) {
			ResourceConsumption cons = new ResourceConsumption(75 * 5 * 60, 75, pm.directConsumer, pm,
					new ConsumptionEventAdapter());
			cons.registerConsumption();
			Timed.simulateUntilLastEvent();
		}
		System.out.println("Simulation time = " + (Timed.getFireCount() - time));
		Timed.simulateUntilLastEvent();
		Assert.assertEquals("Not all VMs ran", vmCount, runningCounter);
		Assert.assertEquals("Not all VMs terminated", vmCount, destroyCounter);
		for (PhysicalMachine pm : basic.runningMachines) {
			Assert.assertFalse("Should not have any running VMs registered", pm.isHostingVMs());
		}
	}

	public static void main(String[] args) {
		PMTest test = new PMTest();
		try {
			test.genericPerformanceCheck(BeloglazovScheduler.class, AlwaysOnMachines.class);
			System.out.println("Total ticks: " + Timed.getFireCount());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private class FireCounter extends Timed {
		private long freq;
		private IaaSService toObserve;
		private BeloglazovScheduler sAdv;
		private int count = 0;

		public FireCounter(IaaSService toObserve, int intervall) {
			this.freq = intervall;
			this.toObserve = toObserve;
			if (toObserve.sched instanceof BeloglazovScheduler) {
				sAdv = (BeloglazovScheduler) toObserve.sched;
			}
			subscribe(freq);
		}

		public void cancel() {
			unsubscribe();
		}

		@Override
		public void tick(long fires) {
			sAdv.optimizeAllocation(count++);
			System.out.println("Allocation for line " + count);
			if (count >= 289)
				cancel();
		}
	}

	public static DataVM getDataFromVM(VirtualMachine vm) {
		for (DataVM data : dataVMs) {
			if (data.getVM() == vm)
				return data;
		}
		return null;
	}

	public File[] loadFiles(String inputPath) {
		return inputFiles != null ? inputFiles : (inputFiles = new File(inputPath).listFiles());

	}

}
