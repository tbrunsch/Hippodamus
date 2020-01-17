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

	CoordinatorConfiguration(Map<Integer, ExecutorServiceWrapper> executorServiceWrappersByTaskType, Logger logger, LogLevel minimumLogLevel, boolean verifyDependencies) {
		this.executorServiceWrappersByTaskType = ImmutableMap.copyOf(executorServiceWrappersByTaskType);
		this.logger = logger;
		this.minimumLogLevel = minimumLogLevel;
		this.verifyDependencies = verifyDependencies;
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
}
