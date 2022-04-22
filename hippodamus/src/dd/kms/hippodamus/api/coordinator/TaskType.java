package dd.kms.hippodamus.api.coordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class contains 2 predefined task types.
 */
public class TaskType
{
	private static final List<TaskType> REGISTERED_TASK_TYPES	= new ArrayList<>();

	public static final TaskType		COMPUTATIONAL	= new TaskType(-1);
	public static final TaskType		BLOCKING		= new TaskType(-2);

	private final int	id;

	TaskType(int id) {
		this.id = id;
		REGISTERED_TASK_TYPES.add(this);
	}

	/**
	 * @return A {@link TaskType} with the given id. Note that the specified id must be non-negative. When calling
	 * the method twice with the same id, then the same instance will be returned.
	 * @throws IllegalArgumentException if the specified id is negative
	 */
	public static synchronized TaskType create(int id) {
		if (id < 0) {
			throw new IllegalArgumentException("The id must be non-negative");
		}
		for (TaskType registeredTaskType : REGISTERED_TASK_TYPES) {
			if (registeredTaskType.id == id) {
				return registeredTaskType;
			}
		}
		return new TaskType(id);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TaskType taskType = (TaskType) o;
		return id == taskType.id;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		return "TaskType{" + "id=" + id + '}';
	}
}
