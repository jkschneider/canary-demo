package com.panera.canary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class CanaryDemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(CanaryDemoApplication.class, args);
	}
}

@RestController
class DemoController {
	private final AtomicBoolean stable = new AtomicBoolean(true);

	@GetMapping("/destabilize")
	public String destabilize() {
		stable.set(false);
		return "OK, I'll start failing requests to this instance";
	}

	@GetMapping("/stabilize")
	public String stabilize() {
		return "OK, I'll start returning successful responses";
	}

	// The value of the first triangle number to have over N divisors
	// https://projecteuler.net/problem=12
	@GetMapping("/triangular")
	public Long triangularNumber(@RequestParam(value = "divisors", required = false) Integer divisors) {
		if(divisors == null)
			divisors = Math.max((int) Math.floor(Math.random() * 10), 1);

		int[] totalDivisors = new int[1000];

		int thousand = 11;
		while(true) {
			System.out.println("Checking " + thousand + ",000" + "-" + (thousand+1) + ",000");

			for(int i = 0; i < totalDivisors.length; i++)
				totalDivisors[i] = 1;

			List<Long> triangles = TriangleGenerator.triangles(thousand*1000, (thousand+1)*1000);
			int firstTriInPlay = 0;

			for(long n = 2L; n <= triangles.get(triangles.size() - 1)/2; n++) {
				for(int i = firstTriInPlay; i < triangles.size(); i++) {
					long triangle = triangles.get(i);
					if(n > triangle/2) {
						firstTriInPlay++;
						continue;
					}

					if(triangle % n == 0) {
						totalDivisors[i]++;

						if(totalDivisors[i] >= divisors) {
							if(stable.get())
								return triangle;
							throw new IllegalStateException("I'm in an unstable mood.");
						}
					}
				}
			}
			thousand++;
		}
	}
}