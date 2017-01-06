package com.paluno.clemens.scheduler;

import hu.mta.sztaki.lpds.cloud.simulator.iaas.IaaSService;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.constraints.ConstantConstraints;
import hu.mta.sztaki.lpds.cloud.simulator.iaas.vmscheduling.Scheduler;

public class Guazzone extends Scheduler {

	public Guazzone(IaaSService parent) {
		super(parent);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected ConstantConstraints scheduleQueued() {
		// TODO Auto-generated method stub
		return ConstantConstraints.noResources;
	}

}
