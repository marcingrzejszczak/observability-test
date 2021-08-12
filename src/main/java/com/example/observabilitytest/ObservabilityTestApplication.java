package com.example.observabilitytest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import brave.Tracing;
import brave.sampler.Sampler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
//import org.springframework.core.metrics.observability.ObservabilityApplicationStartup;
//import org.springframework.observability.event.SimpleRecorder;
//import org.springframework.observability.event.listener.composite.AllMatchingCompositeRecordingListener;
//import org.springframework.observability.micrometer.listener.MicrometerRecordingListener;
//import org.springframework.observability.time.Clock;
import org.springframework.observability.tracing.Tracer;
import org.springframework.observability.tracing.brave.bridge.BraveTracer;
//import org.springframework.observability.tracing.listener.DefaultTracingRecordingListener;
import org.springframework.observability.tracing.reporter.zipkin.RestTemplateSender;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ObservabilityTestApplication {

	public static void main(String[] args) throws IOException {
		new SpringApplicationBuilder(ObservabilityTestApplication.class)
				.applicationStartup(new BufferingApplicationStartup(10_000))
				.run(args);
	}

	@Bean
	CommandLineRunner myCommandLineRunner(RestTemplate restTemplate) {
		return args -> {
			String object = restTemplate.getForObject("https://httpbin.org/headers", String.class);
			if (!object.contains("B3")) {
				throw new IllegalStateException("No B3 header propagated");
			}
		};
	}

	public static void applicationStartup(String[] args) throws IOException {
		new SpringApplicationBuilder(ObservabilityTestApplication.class)
				.applicationStartup(new BufferingApplicationStartup(10_000))
				.run(args);
	}

	public static void manual(String[] args) throws IOException {
		AsyncReporter<Span> reporter = reporter();
		Tracer tracer = tracer(reporter);
		MeterRegistry meterRegistry = meterRegistry();
		/*ObservabilityApplicationStartup observabilityApplicationStartup = new ObservabilityApplicationStartup(
				new SimpleRecorder<>(
						new AllMatchingCompositeRecordingListener(
								new DefaultTracingRecordingListener(tracer), new MicrometerRecordingListener(meterRegistry))
						, Clock.SYSTEM));*/
		try {
			new SpringApplicationBuilder(ObservabilityTestApplication.class)
//					.applicationStartup(observabilityApplicationStartup)
					.run(args);
		}
		finally {
//			observabilityApplicationStartup.endRootRecording();
			reporter.flush();
			reporter.close();
		}
	}

	private static AsyncReporter<Span> reporter() {
		return AsyncReporter
				.builder(new RestTemplateSender(new RestTemplate(), "http://localhost:9411/", null, SpanBytesEncoder.JSON_V2))
				.build();
	}

	private static Tracer tracer(AsyncReporter<Span> reporter) {
		Tracing tracing = Tracing.newBuilder()
				.addSpanHandler(ZipkinSpanHandler.newBuilder(reporter).build())
				.sampler(Sampler.ALWAYS_SAMPLE)
				.build();
		return new BraveTracer(tracing.tracer());
	}

	private static MeterRegistry meterRegistry() {
		PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);
			server.createContext("/prometheus", httpExchange -> {
				String response = prometheusRegistry.scrape();
				httpExchange.sendResponseHeaders(200, response.getBytes().length);
				try (OutputStream os = httpExchange.getResponseBody()) {
					os.write(response.getBytes());
				}
			});

			new Thread(server::start).start();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return prometheusRegistry;
	}

}
