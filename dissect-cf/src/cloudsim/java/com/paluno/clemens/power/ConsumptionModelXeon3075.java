package com.paluno.clemens.power;

import hu.mta.sztaki.lpds.cloud.simulator.energy.powermodelling.PowerState;

public class ConsumptionModelXeon3075 extends PowerState.ConsumptionModel implements PowerConsuming{

	private final double[] power = { 93.7, 97, 101, 105, 110, 116, 121, 125, 129, 133, 135 };

	@Override
	protected double evaluateConsumption(double load) {

		return power[(int) (load * 10)];
	}

	public double idlePower() {
		return evaluateConsumption(0);
	}

	public double maxPower() {
		return evaluateConsumption(1);
	}
}
