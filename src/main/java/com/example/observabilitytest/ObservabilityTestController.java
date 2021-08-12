package com.example.observabilitytest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@RestController
public class ObservabilityTestController {

	private final ObjectProvider<MyService> myService;

	private final RestTemplate restTemplate;

	public ObservabilityTestController(ObjectProvider<MyService> myService, RestTemplate restTemplate) {
		this.myService = myService;
		this.restTemplate = restTemplate;
	}

	@GetMapping("/foo")
	String foo() {
		return myService.getIfUnique().bar();
	}

	@GetMapping("/test")
	String test() {
		String object = restTemplate.getForObject("https://httpbin.org/headers", String.class);
		if (!object.contains("B3")) {
			throw new IllegalStateException("No B3 header propagated");
		}
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