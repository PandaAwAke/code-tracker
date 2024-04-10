package eu.stratosphere.example.record.incremental.pagerank;

import eu.stratosphere.api.record.io.DelimitedInputFormat;
import eu.stratosphere.types.PactRecord;
import eu.stratosphere.types.PactDouble;
import eu.stratosphere.types.PactLong;

public class InitialRankInputFormat extends DelimitedInputFormat {
	
	private static final long serialVersionUID = 1L;
	
	private final PactLong vId = new PactLong();
	private final PactDouble initialRank = new PactDouble();
	
	@Override
	public boolean readRecord(PactRecord target, byte[] bytes, int offset, int numBytes) {
		String str = new String(bytes, offset, numBytes);
		String[] parts = str.split("\\s+");

		this.vId.setValue(Long.parseLong(parts[0]));
		this.initialRank.setValue(Double.parseDouble(parts[1]));
		
		target.setField(0, this.vId);
		target.setField(1, this.initialRank);
		return true;
	}

}