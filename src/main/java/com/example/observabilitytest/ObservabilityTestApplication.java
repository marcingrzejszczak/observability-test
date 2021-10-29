package com.example.observabilitytest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.observability.AllMatchingCompositeRecordingListener;
import org.springframework.boot.autoconfigure.observability.tracing.brave.bridge.BraveBaggageManager;
import org.springframework.boot.autoconfigure.observability.tracing.brave.bridge.BraveTracer;
import org.springframework.boot.autoconfigure.observability.tracing.listener.DefaultTracingRecordingListener;
import org.springframework.boot.autoconfigure.observability.tracing.reporter.zipkin.core.RestTemplateSender;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.annotation.Bean;
import org.springframework.core.metrics.observability.ObservabilityApplicationStartup;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.sun.net.httpserver.HttpServer;

import brave.Tracing;
import brave.sampler.Sampler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.tracing.Tracer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

@SpringBootApplication
public class ObservabilityTestApplication {

	public static void main(String[] args) throws IOException {
//		manual(args);
		bufferingStartup(args);
	}

	@Bean(destroyMethod = "stop")
	WireMockServer wireMockServer() {
		WireMockServer wireMockServer =  new WireMockServer(0);
		wireMockServer.start();
		wireMockServer.givenThat(WireMock.get(WireMock.anyUrl()).willReturn(WireMock.aResponse().withStatus(200)));
		return wireMockServer;
	}

	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}

	private static void bufferingStartup(String[] args) {
		new SpringApplicationBuilder(ObservabilityTestApplication.class)
				.applicationStartup(new BufferingApplicationStartup(10_000))
				.run(args);
	}

	public static void manual(String[] args) throws IOException {
		AsyncReporter<Span> reporter = reporter();
		Tracer tracer = tracer(reporter);
		MeterRegistry meterRegistry = meterRegistry();
		meterRegistry.config().timerRecordingListener(
				new AllMatchingCompositeRecordingListener(new DefaultTracingRecordingListener(tracer)));
		ObservabilityApplicationStartup observabilityApplicationStartup = new ObservabilityApplicationStartup(
				meterRegistry);
		try {
			new SpringApplicationBuilder(ObservabilityTestApplication.class)
					.applicationStartup(observabilityApplicationStartup)
					.run(args);
		}
		finally {
			observabilityApplicationStartup.endRootRecording();
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
		return new BraveTracer(tracing.tracer(), tracing.currentTraceContext(), new BraveBaggageManager());
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

	@Bean
	CommandLineRunner myCommandLineRunner() {
		return args -> {
			/*String object = restTemplate.getForObject("https://httpbin.org/headers", String.class);
			if (!object.contains("B3")) {
				throw new IllegalStateException("No B3 header propagated");
			}*/
		};
	}

}
