package dd.kms.hippodamus.coordinator.configuration;

import com.google.common.collect.ImmutableMap;
import dd.kms.hippodamus.execution.ExecutorServiceWrapper;
import dd.kms.hippodamus.logging.LogLevel;
import dd.kms.hippodamus.logging.Logger;

import java.util.Map;

public class CoordinatorConfiguration
{
	private final Map<Integer, ExecutorServiceWrapper>	executorServiceWrappersByTaskType;
	private final Logger								logger;
	private final LogLevel								minimumLogLevel;
	private final boolean								verifyDependencies;
	private final WaitMode								waitMode;

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
