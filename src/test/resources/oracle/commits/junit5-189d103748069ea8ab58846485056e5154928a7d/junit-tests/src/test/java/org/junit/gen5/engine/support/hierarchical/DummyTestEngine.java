/*
 * Copyright 2015-2016 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.support.hierarchical;

import org.junit.gen5.engine.EngineDiscoveryRequest;
import org.junit.gen5.engine.ExecutionRequest;
import org.junit.gen5.engine.TestDescriptor;
import org.junit.gen5.engine.UniqueId;

public final class DummyTestEngine extends HierarchicalTestEngine<DummyEngineExecutionContext> {

	private final String engineId;
	private final DummyEngineDescriptor engineDescriptor;

	public DummyTestEngine() {
		this("dummy");
	}

	public DummyTestEngine(String engineId) {
		this.engineId = engineId;
		this.engineDescriptor = new DummyEngineDescriptor(engineId);
	}

	@Override
	public String getId() {
		return engineId;
	}

	public DummyEngineDescriptor getEngineDescriptor() {
		return engineDescriptor;
	}

	public DummyTestDescriptor addTest(String uniqueName, Runnable runnable) {
		UniqueId uniqueId = engineDescriptor.getUniqueIdObject().append("test", uniqueName);
		DummyTestDescriptor child = new DummyTestDescriptor(uniqueId, uniqueName, runnable);
		engineDescriptor.addChild(child);
		return child;
	}

	@Override
	public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest) {
		return engineDescriptor;
	}

	@Override
	protected DummyEngineExecutionContext createExecutionContext(ExecutionRequest request) {
		return new DummyEngineExecutionContext();
	}
}
