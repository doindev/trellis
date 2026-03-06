package io.cwc.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import java.util.EnumSet;

public class NanoIdGenerator implements BeforeExecutionGenerator {
	private static final long serialVersionUID = 1L;

	@Override
	public Object generate(
		SharedSessionContractImplementor session, 
		Object owner,
		Object currentValue, 
		EventType eventType
	) {
		return NanoIdUtils.randomNanoId();
	}

	@Override
	public EnumSet<EventType> getEventTypes() {
		return EnumSet.of(EventType.INSERT);
	}
}
