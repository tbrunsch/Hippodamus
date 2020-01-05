package dd.kms.hippodamus.handles;

import java.util.function.Supplier;

public interface ResultHandle<T> extends Handle, Supplier<T>
{
}
