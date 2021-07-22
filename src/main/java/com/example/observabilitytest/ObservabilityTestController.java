package com.example.observabilitytest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservabilityTestController {

	private final ObjectProvider<MyService> myService;

	public ObservabilityTestController(ObjectProvider<MyService> myService) {
		this.myService = myService;
	}

	@GetMapping("/foo")
	String foo() {
		return myService.getIfUnique().bar();
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