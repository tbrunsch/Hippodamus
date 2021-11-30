package dd.kms.hippodamus.impl.coordinator.configuration;

import com.google.common.collect.ImmutableMap;
import dd.kms.hippodamus.api.coordinator.configuration.WaitMode;
import dd.kms.hippodamus.api.logging.LogLevel;
import dd.kms.hippodamus.api.logging.Logger;
import dd.kms.hippodamus.impl.execution.ExecutorServiceWrapper;

import java.util.Map;

public class CoordinatorConfiguration
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private final Logger								logger;
	private final LogLevel								minimumLogLevel;
	private final boolean								verifyDependencies;
	private final WaitMode waitMode;

	CoordinatorConfiguration(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies, WaitMode waitMode) {
		this.executorServiceWrappersByTaskType = ImmutableMap.copyOf(executorServiceWrappersByTaskType);
		this.logger = logger;
		this.minimumLogLevel = minimumLogLevel;
		this.verifyDependencies = verifyDependencies;
		this.waitMode = waitMode;
	}

	public Map<Integer, ExecutorServiceWrapper> getExecutorServiceWrappersByTaskType() {
		return executorServiceWrappersByTaskType;
	}

	public Logger getLogger() {
		return logger;
	}

	public LogLevel getMinimumLogLevel() {
		return minimumLogLevel;
	}

	public boolean isVerifyDependencies() {
		return verifyDependencies;
	}

	public WaitMode getWaitMode() {
		return waitMode;
	}
}
