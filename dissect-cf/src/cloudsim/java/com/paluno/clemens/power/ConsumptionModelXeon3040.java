package com.paluno.clemens.power;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;

public class ConsumptionModelXeon3040 extends PowerState.ConsumptionModel {
	private final double[] power = { 86, 89.4, 92.6, 96, 99.5, 102, 106, 108, 112, 114, 117 };

	@Override
	protected double evaluateConsumption(double load) {

		return power[(int) (load * 10)];
	}

}
