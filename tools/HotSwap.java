import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Swaps changed classes into a running dev server or client without a restart (Jah 10-26). Start the game with
 * {@code ./gradlew runServer --offline --args=nogui -Pata.hotswap=true} (debug port 5005; runClient uses 5006), edit,
 * {@code ./gradlew compileJava --offline -q}, then:
 *
 * <pre>java tools/HotSwap.java [port] [runDir]</pre>
 *
 * It redefines every loaded class whose .class file in build/classes/java/main changed since the last swap (the first
 * time: since the run's latest.log was created). A class not loaded yet needs nothing: it loads the new file when first
 * used. The standard JVM swaps method bodies only; a change that adds or removes a method or field, or changes a record,
 * is refused and needs a restart. Plan the station again afterwards: the planner runs fresh on every plan.
 */
public class HotSwap {
	public static void main(String[] args) throws Exception {
		final int port = args.length > 0 ? Integer.parseInt(args[0]) : 5005;
		final Path runDir = Path.of(args.length > 1 ? args[1] : port == 5006 ? "run/client" : "run/server");
		final Path classes = Path.of("build/classes/java/main");
		final Path stamp = Path.of("build/hotswap-" + port + ".stamp");
		final FileTime since = Files.exists(stamp) ? Files.getLastModifiedTime(stamp)
				: (FileTime) Files.getAttribute(runDir.resolve("logs/latest.log"), "creationTime");
		final FileTime now = FileTime.fromMillis(System.currentTimeMillis());

		final Map<String, Path> changed = new HashMap<>();
		try (Stream<Path> files = Files.walk(classes)) {
			files.filter(f -> f.toString().endsWith(".class")).forEach(f -> {
				try {
					if (Files.getLastModifiedTime(f).compareTo(since) > 0) {
						final String rel = classes.relativize(f).toString();
						changed.put(rel.substring(0, rel.length() - ".class".length()).replace('/', '.'), f);
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
		}
		if (changed.isEmpty()) {
			System.out.println("nothing changed since " + since);
			return;
		}

		final AttachingConnector socket = Bootstrap.virtualMachineManager().attachingConnectors().stream()
				.filter(c -> c.transport().name().equals("dt_socket")).findFirst().orElseThrow();
		final Map<String, Connector.Argument> arguments = socket.defaultArguments();
		arguments.get("hostname").setValue("127.0.0.1");
		arguments.get("port").setValue(Integer.toString(port));
		final VirtualMachine vm = socket.attach(arguments);
		try {
			final Map<ReferenceType, byte[]> swap = new HashMap<>();
			int unloaded = 0;
			for (final Map.Entry<String, Path> entry : changed.entrySet()) {
				final List<ReferenceType> loaded = vm.classesByName(entry.getKey());
				if (loaded.isEmpty()) {
					unloaded++;
				}
				for (final ReferenceType type : loaded) {
					swap.put(type, Files.readAllBytes(entry.getValue()));
				}
			}
			if (!swap.isEmpty()) {
				vm.redefineClasses(swap);
			}
			Files.writeString(stamp, "");
			Files.setLastModifiedTime(stamp, now);
			System.out.println("swapped " + swap.size() + " classes on port " + port + (unloaded > 0 ? ", " + unloaded + " not loaded yet (they load the new code)" : ""));
		} catch (UnsupportedOperationException e) {
			System.out.println("refused: " + e.getMessage() + ". A method or field was added or removed: restart the run.");
			System.exit(1);
		} finally {
			vm.dispose();
		}
	}
}
