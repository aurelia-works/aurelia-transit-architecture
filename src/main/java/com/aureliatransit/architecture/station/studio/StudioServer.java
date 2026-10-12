package com.aureliatransit.architecture.station.studio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Station Studio's local web server: the page, its scripts, the planner ({@link StudioApi}) and block textures, on
 * {@code 127.0.0.1} only. Started by the game client (which can also take a design back into the game) or by
 * {@code tools/StationStudio.java} outside the game.
 *
 * <p>Only this machine can connect. Requests must name localhost as their host (so a web page elsewhere cannot reach it through
 * a renamed DNS entry), and sending a design into the game also needs the token the page was served with, which other web
 * pages cannot read.
 */
public final class StudioServer {

	/** Takes a design into the game: an action ("plan", "build", "undo", "cancel"), the full description and the encoded controls. */
	@FunctionalInterface
	public interface Sender {
		/** A short answer for the page, e.g. "Planned in front of you". */
		String send(String action, String description, String encoded);
	}

	public static final int FIRST_PORT = 25680;
	private static final int PORTS = 10;
	private static final int MAX_BODY = 16 * 1024;

	private final HttpServer server;
	private final ExecutorService executor;
	private final StudioApi api;
	private final BlockModels.AssetSource assets;
	private final Sender sender;
	private final String token;

	private StudioServer(HttpServer server, StudioApi api, BlockModels.AssetSource assets, Sender sender) {
		this.server = server;
		this.api = api;
		this.assets = assets;
		this.sender = sender;
		final byte[] random = new byte[16];
		new SecureRandom().nextBytes(random);
		this.token = HexFormat.of().formatHex(random);
		this.executor = Executors.newFixedThreadPool(2, runnable -> {
			final Thread thread = new Thread(runnable, "ATA Station Studio");
			thread.setDaemon(true);
			return thread;
		});
		server.setExecutor(executor);
		server.createContext("/", this::handle);
	}

	/** Starts on the first free port from {@link #FIRST_PORT}; {@code sender} is null outside the game. */
	public static StudioServer start(BlockModels.AssetSource assets, Sender sender) throws IOException {
		IOException last = null;
		for (int port = FIRST_PORT; port < FIRST_PORT + PORTS; port++) {
			try {
				final HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 16);
				final StudioServer studio = new StudioServer(http, new StudioApi(new BlockModels(assets)), assets, sender);
				http.start();
				return studio;
			} catch (BindException e) {
				last = e;
			}
		}
		throw last == null ? new IOException("no free port") : last;
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}

	public void stop() {
		server.stop(0);
		executor.shutdownNow();
	}

	private void handle(HttpExchange exchange) throws IOException {
		try (exchange) {
			final String host = exchange.getRequestHeaders().getFirst("Host");
			if (host == null || !(host.startsWith("127.0.0.1:") || host.startsWith("localhost:"))) {
				reply(exchange, 403, "text/plain", "Station Studio only answers on this computer.");
				return;
			}
			final String path = exchange.getRequestURI().getPath();
			final String method = exchange.getRequestMethod();
			if (path.equals("/") || path.equals("/index.html")) {
				final String page = resource("index.html").replace("{{TOKEN}}", token).replace("{{INGAME}}", Boolean.toString(sender != null));
				reply(exchange, 200, "text/html; charset=utf-8", page);
			} else if (path.equals("/studio.js") || path.equals("/silk.js") || path.equals("/three.min.js") || path.equals("/studio.css")) {
				reply(exchange, 200, path.endsWith(".css") ? "text/css; charset=utf-8" : "text/javascript; charset=utf-8", resource(path.substring(1)));
			} else if (path.equals("/api/options")) {
				reply(exchange, 200, "application/json", api.options());
			} else if (path.equals("/api/plan") && method.equals("POST")) {
				reply(exchange, 200, "application/json", api.plan(form(body(exchange))));
			} else if (path.equals("/api/send") && method.equals("POST")) {
				send(exchange);
			} else if (path.startsWith("/tex/") && path.endsWith(".png")) {
				texture(exchange, path.substring("/tex/".length(), path.length() - 4));
			} else {
				reply(exchange, 404, "text/plain", "Not found");
			}
		} catch (RuntimeException e) {
			reply(exchange, 500, "text/plain", "Station Studio could not do that: " + e);
		}
	}

	private void send(HttpExchange exchange) throws IOException {
		if (!token.equals(exchange.getRequestHeaders().getFirst("X-Studio-Token"))) {
			reply(exchange, 403, "text/plain", "Reload the studio page from the game.");
			return;
		}
		if (sender == null) {
			reply(exchange, 409, "text/plain", "Open the studio from the game (station wrench) to plan or build there.");
			return;
		}
		final Map<String, String> q = form(body(exchange));
		final String action = q.getOrDefault("action", "");
		final String answer = sender.send(action, StudioApi.recipe(q).text(StudioApi.typed(q)), StudioApi.recipe(q).encode());
		reply(exchange, 200, "text/plain; charset=utf-8", answer);
	}

	/** A block texture, by its id written as a path ({@code namespace/block/name}). */
	private void texture(HttpExchange exchange, String id) throws IOException {
		final int slash = id.indexOf('/');
		if (slash <= 0 || id.contains("..")) {
			reply(exchange, 404, "text/plain", "Not found");
			return;
		}
		final byte[] png = assets.read(id.substring(0, slash), "textures/" + id.substring(slash + 1) + ".png");
		if (png == null) {
			reply(exchange, 404, "text/plain", "Not found");
			return;
		}
		exchange.getResponseHeaders().set("Cache-Control", "max-age=3600");
		reply(exchange, 200, "image/png", png);
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = StudioServer.class.getResourceAsStream("/studio/" + name)) {
			if (in == null) {
				throw new IOException("missing studio file " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String body(HttpExchange exchange) throws IOException {
		try (InputStream in = exchange.getRequestBody()) {
			final byte[] bytes = in.readNBytes(MAX_BODY + 1);
			if (bytes.length > MAX_BODY) {
				throw new IllegalArgumentException("request too large");
			}
			return new String(bytes, StandardCharsets.UTF_8);
		}
	}

	/** {@code a=1&b=two} into a map. */
	static Map<String, String> form(String body) {
		final Map<String, String> q = new HashMap<>();
		for (final String pair : body.split("&")) {
			final int eq = pair.indexOf('=');
			if (eq > 0) {
				q.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT),
						URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
			}
		}
		return q;
	}

	private static void reply(HttpExchange exchange, int status, String type, String text) throws IOException {
		reply(exchange, status, type, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void reply(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", type);
		exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}
}
