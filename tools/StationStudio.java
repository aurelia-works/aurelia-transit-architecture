import com.aureliatransit.architecture.station.studio.BlockModels;
import com.aureliatransit.architecture.station.studio.StudioServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Station Studio outside the game: the same page and planner the station wrench opens, with textures read from this repo's
 * assets, the Minecraft client jar and the MTR jar in the Gradle cache. Planning and building in a world needs the game; this
 * is for designing and checking stations without it.
 *
 * <pre>
 * ./gradlew compileJava processResources --offline
 * java -cp build/classes/java/main:build/resources/main:&lt;slf4j-api jar&gt; tools/StationStudio.java
 * </pre>
 * The slf4j API jar (the server's logger; the game supplies it) is in the Gradle cache under {@code org.slf4j/slf4j-api}.
 * (or {@code tools/station-studio.sh}, which does both and opens the browser).
 */
public final class StationStudio {

	public static void main(String[] args) throws Exception {
		final List<Path> folders = new ArrayList<>();
		folders.add(Path.of("build/resources/main/assets"));
		folders.add(Path.of("src/main/resources/assets"));
		final List<ZipFile> jars = new ArrayList<>();
		final Path home = Path.of(System.getProperty("user.home"), ".gradle", "caches");
		final Path client = home.resolve("fabric-loom/1.20.1/minecraft-client.jar");
		if (Files.exists(client)) {
			jars.add(new ZipFile(client.toFile()));
		} else {
			System.out.println("Minecraft's client jar is not in the Gradle cache; vanilla blocks show without textures.");
		}
		final Path mtr = home.resolve("modules-2/files-2.1/maven.modrinth/minecraft-transit-railway");
		if (Files.isDirectory(mtr)) {
			try (Stream<Path> files = Files.walk(mtr)) {
				files.filter(p -> p.toString().contains("FABRIC") && p.toString().endsWith(".jar") && !p.toString().endsWith("-sources.jar"))
						.findFirst().ifPresent(p -> {
							try {
								jars.add(new ZipFile(p.toFile()));
							} catch (IOException e) {
								System.out.println("Could not open " + p + ": " + e.getMessage());
							}
						});
			}
		}
		final BlockModels.AssetSource assets = (namespace, path) -> {
			final String name = namespace + "/" + path;
			for (final Path folder : folders) {
				final Path file = folder.resolve(name);
				if (Files.isRegularFile(file)) {
					return Files.readAllBytes(file);
				}
			}
			for (final ZipFile jar : jars) {
				final ZipEntry entry = jar.getEntry("assets/" + name);
				if (entry != null) {
					try (InputStream in = jar.getInputStream(entry)) {
						return in.readAllBytes();
					}
				}
			}
			return null;
		};
		final StudioServer studio = StudioServer.start(assets, null);
		System.out.println("Station Studio: " + studio.url() + "  (Ctrl+C to stop)");
		Thread.currentThread().join();
	}
}
