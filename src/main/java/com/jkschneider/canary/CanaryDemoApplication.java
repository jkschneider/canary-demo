package com.jkschneider.canary;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.metrics.web.servlet.DefaultWebMvcTagsProvider;
import org.springframework.boot.actuate.metrics.web.servlet.WebMvcTagsProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.sleuth.zipkin2.ZipkinRestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
public class CanaryDemoApplication {
	public static void main(String[] args) {
		SpringApplication.run(CanaryDemoApplication.class, args);
	}
}

@Configuration
class MetricsConfiguration {
	@Bean
	public ZipkinRestTemplateCustomizer zipkinCustomizer(MeterRegistry registry) {
		return restTemplate -> {
			restTemplate.getInterceptors().add((request, body, execution) -> {
				Timer.Sample sample = Timer.start(registry);
				ClientHttpResponse response = execution.execute(request, body);
				sample.stop(registry.timer("zipkin.send", "status", Integer.toString(response.getRawStatusCode())));
				return response;
			});
		};
	}

	@Bean
	public WebMvcTagsProvider organizationalCommonTags() {
		return new DefaultWebMvcTagsProvider() {
			@SuppressWarnings("unchecked")
			@Override
			public Iterable<Tag> getTags(HttpServletRequest request, HttpServletResponse response, Object handler, Throwable exception) {
				Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
				Tags tags = Tags.empty();
				if(pathVariables.containsKey("store")) {
					tags = tags.and("store", pathVariables.get("store"));
				}

				return Tags.concat(super.getTags(request, response, handler, exception), tags);
			}

			@Override
			public Iterable<Tag> getLongRequestTags(HttpServletRequest request, Object handler) {
				return super.getLongRequestTags(request, handler);
			}
		};
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

	@GetMapping("/param/{store}")
	public String store(@PathVariable("store") String store) {
		return store;
	}

	@GetMapping("/quick")
	public String quick() {
		return "Hello world!";
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
