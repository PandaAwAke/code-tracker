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

package eu.stratosphere.nephele.services.memorymanager;

import java.io.DataInput;

public interface DataInputView extends DataInput {
	/**
	 * Gets the current write position.
	 * 
	 * @return The current write size.
	 */
	public int getPosition();

	/**
	 * Sets the current write position.
	 */
	public DataInputView setPosition(int position);

	/**
	 * Skips {@code size} memory.
	 */
	public DataInputView skip(int size);

	/**
	 * Resets the write position to zero.
	 */
	public DataInputView reset();
}
