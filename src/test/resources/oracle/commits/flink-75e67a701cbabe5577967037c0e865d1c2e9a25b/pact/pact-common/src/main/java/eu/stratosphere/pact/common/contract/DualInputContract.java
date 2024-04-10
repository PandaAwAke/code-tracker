/***********************************************************************************************************************
 *
 * Copyright (C) 2010 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 **********************************************************************************************************************/

package eu.stratosphere.pact.common.contract;

import java.lang.annotation.Annotation;

import eu.stratosphere.pact.common.plan.Visitor;
import eu.stratosphere.pact.common.stub.DualInputStub;
import eu.stratosphere.pact.common.type.Key;
import eu.stratosphere.pact.common.type.Value;
import eu.stratosphere.pact.common.util.ReflectionUtil;

/**
 * Contract for all tasks that have two inputs like "reduce" or "cross".
 * 
 * @author DIMA
 */
public abstract class DualInputContract<IK1 extends Key, IV1 extends Value, IK2 extends Key, IV2 extends Value, OK extends Key, OV extends Value>
		extends Contract implements OutputContractConfigurable {
	protected final Class<? extends DualInputStub<IK1, IV1, IK2, IV2, OK, OV>> clazz;

	protected Contract firstInput;

	protected Contract secondInput;

	protected Class<? extends Annotation> outputContract;

	/**
	 * Creates a new contract using the given stub and the given name
	 * 
	 * @param clazz
	 *        the stub class that is represented by this contract
	 * @param name
	 *        name for the task represented by this contract
	 */
	public DualInputContract(Class<? extends DualInputStub<IK1, IV1, IK2, IV2, OK, OV>> clazz, String name) {
		super(name);
		this.clazz = clazz;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<? extends DualInputStub<IK1, IV1, IK2, IV2, OK, OV>> getStubClass() {
		return clazz;
	}

	/**
	 * Returns the class type of the first input key
	 * 
	 * @return
	 */
	public Class<? extends Key> getFirstInputKeyClass() {
		return ReflectionUtil.getTemplateType1(this.getClass());
	}

	/**
	 * Returns the class type of the first input value
	 * 
	 * @return
	 */
	public Class<? extends Value> getFirstInputValueClass() {
		return ReflectionUtil.getTemplateType2(this.getClass());
	}

	/**
	 * Returns the class type of the second input key
	 * 
	 * @return
	 */
	public Class<? extends Key> getSecondInputKeyClass() {
		return ReflectionUtil.getTemplateType3(this.getClass());
	}

	/**
	 * Returns the class type of the second input value
	 * 
	 * @return
	 */
	public Class<? extends Value> getSecondInputValueClass() {
		return ReflectionUtil.getTemplateType4(this.getClass());
	}

	/**
	 * Returns the class type of the output key
	 * 
	 * @return
	 */
	public Class<? extends Key> getOutputKeyClass() {
		return ReflectionUtil.getTemplateType5(this.getClass());
	}

	/**
	 * Returns the class type of the output value
	 * 
	 * @return
	 */
	public Class<? extends Value> getOutputValueClass() {
		return ReflectionUtil.getTemplateType6(this.getClass());
	}

	/**
	 * Returns the first input or null if none is set
	 * 
	 * @return
	 */
	public Contract getFirstInput() {
		return firstInput;
	}

	/**
	 * Returns the second input or null if none is set
	 * 
	 * @return
	 */
	public Contract getSecondInput() {
		return secondInput;
	}

	/**
	 * Connects the second input to the task wrapped in this contract
	 * 
	 * @param firstInput
	 */
	public void setFirstInput(Contract firstInput) {
		this.firstInput = firstInput;
	}

	/**
	 * Connects the second input to the task wrapped in this contract
	 * 
	 * @param secondInput
	 */
	public void setSecondInput(Contract secondInput) {
		this.secondInput = secondInput;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setOutputContract(Class<? extends Annotation> oc) {
		if (!oc.getEnclosingClass().equals(OutputContract.class)) {
			throw new IllegalArgumentException("The given annotation does not describe an output contract.");
		}

		this.outputContract = oc;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Class<? extends Annotation> getOutputContract() {
		return this.outputContract;
	}

	@Override
	public void accept(Visitor<Contract> visitor) {
		visitor.preVisit(this);
		if (firstInput != null)
			firstInput.accept(visitor);
		if (secondInput != null)
			secondInput.accept(visitor);
		visitor.postVisit(this);
	}

}
