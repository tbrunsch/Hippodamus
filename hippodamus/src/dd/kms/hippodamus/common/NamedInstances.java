package dd.kms.hippodamus.common;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

// TODO: Nice, but where do we need it?
public class NamedInstances
{
	public static <T> T createNamedInstance(Class<T> instanceInterface, T unnamedInstance, String name) {
		InvocationHandler invocationHandler = (proxy, method, args) ->
			"toString".equals(method.getName()) && (args == null || args.length == 0) ? name : method.invoke(unnamedInstance, args);
		return (T) Proxy.newProxyInstance(instanceInterface.getClassLoader(), new Class[]{instanceInterface}, invocationHandler);
	}

	public static void main(String[] args) {
		List<String> unnamedList = new ArrayList<>(Arrays.asList("Ich", "bin", "eine", "Liste"));
		List<String> namedList = createNamedInstance(List.class, unnamedList, unnamedList.stream().collect(Collectors.joining(" ")));
		unnamedList.add("another element");
		System.out.println(namedList.toString());
		namedList.forEach(System.out::println);
	}
}
