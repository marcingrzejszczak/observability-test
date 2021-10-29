package com.example.observabilitytest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.web.client.MetricsRestTemplateCustomizer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

@RestController
public class ObservabilityTestController {

	private final ObjectProvider<MyService> myService;

	private final RestTemplate restTemplate;

	private final WireMockServer wireMockServer;

	@Autowired
	MetricsRestTemplateCustomizer customizer;

	public ObservabilityTestController(ObjectProvider<MyService> myService, RestTemplate restTemplate,
			WireMockServer wireMockServer) {
		this.myService = myService;
		this.restTemplate = restTemplate;
		this.wireMockServer = wireMockServer;
	}

	@GetMapping("/foo")
	String foo() {
		return myService.getIfUnique().bar();
	}

	@GetMapping("/test")
	String test() {
		this.customizer.customize(restTemplate); // WTF
		String object = restTemplate.getForObject(wireMockServer.baseUrl() + "/", String.class);
		wireMockServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/"))
				.withHeader("X-B3-TraceId", WireMock.matching(".*")));
		return object;
	}
}


@Component
@Lazy
class MyService {

	MyService() {
		System.out.println("BOOOOOM");
	}

	String bar() {
		return "alsdjasd";
	}
}