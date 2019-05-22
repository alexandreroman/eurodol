/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.eurodol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RequiredArgsConstructor
class CurrencyConverterController {
    private static final Currency EUR_SYMBOL = Currency.getInstance("EUR");
    private static final Currency USD_SYMBOL = Currency.getInstance("USD");
    private final CurrencyConverterService ccs;

    @GetMapping("/api/v1/convert")
    ResponseEntity<?> convert(@RequestParam(value = "amount", defaultValue = "1") BigDecimal amount,
                              @RequestParam(value = "symbol", defaultValue = "EUR") String symbol) throws IOException {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Amount must be positive: " + amount);
        }
        final Currency inputSymbol;
        try {
            inputSymbol = Currency.getInstance(symbol);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid symbol: " + symbol);
        }
        final Currency outputSymbol;
        if (EUR_SYMBOL.equals(inputSymbol)) {
            outputSymbol = USD_SYMBOL;
        } else if (USD_SYMBOL.equals(inputSymbol)) {
            outputSymbol = EUR_SYMBOL;
        } else {
            return ResponseEntity.badRequest().body("Unsupported symbol: " + symbol);
        }
        return ResponseEntity.ok(ccs.convert(new CurrencyAmount(amount, inputSymbol), outputSymbol));
    }
}

@Data
@NoArgsConstructor
@AllArgsConstructor
class CurrencyConversion {
    private CurrencyAmount input;
    private CurrencyAmount output;
    private BigDecimal exchangeRate;
}

@Component
@RequiredArgsConstructor
@Slf4j
class CurrencyConverterService {
    private final RestTemplate restTemplate;
    @Value("${app.exchangeRatesEndpoint}")
    private String exchangeRatesEndpoint;

    public CurrencyConversion convert(CurrencyAmount input, Currency outputCurrency) throws IOException {
        final String url = UriComponentsBuilder.fromHttpUrl(exchangeRatesEndpoint)
                .path("/latest")
                .queryParam("base", input.getSymbol().getCurrencyCode())
                .queryParam("symbols", outputCurrency.getCurrencyCode())
                .toUriString();

        log.debug("Getting exchange rate between {} and {}", input.getSymbol(), outputCurrency);
        final CurrencyResponse resp = restTemplate.getForObject(url, CurrencyResponse.class);
        final BigDecimal exchangeRate = resp.rates.get(outputCurrency.getCurrencyCode());
        if (exchangeRate == null) {
            throw new IOException("Failed to get exchange rate");
        }
        log.debug("Got exchange rate between {} and {}: {}", input.getSymbol(), outputCurrency, exchangeRate);

        final BigDecimal outputAmount = input.getAmount().multiply(exchangeRate);
        final CurrencyAmount output = new CurrencyAmount(outputAmount, outputCurrency);
        log.info("Converted using exchange rate {}: {} {} -> {} {}",
                exchangeRate, input.getAmount(), input.getSymbol(), output.getAmount(), output.getSymbol());
        return new CurrencyConversion(input, output, exchangeRate);
    }

    @Data
    private static class CurrencyResponse {
        private Map<String, BigDecimal> rates;
    }
}

@Data
@AllArgsConstructor
class CurrencyAmount {
    private final BigDecimal amount;
    private final Currency symbol;
}

@Configuration
class AppConfig {
    @Bean
    OkHttpClient httpClient() throws IOException {
        // Setup an HTTP cache to reduce API calls to external endpoints.
        final Cache cache = new Cache(Files.createTempDirectory("httpcache-").toFile(), 10 * 1024);
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .cache(cache)
                .build();
    }

    @Bean
    RestTemplate restTemplate(OkHttpClient httpClient) {
        // Create a shared RestTemplate instance to make HTTP calls.
        // We use OkHttp as the underlying transport library.
        return new RestTemplate(new OkHttp3ClientHttpRequestFactory(httpClient));
    }
}

@Component
class CorsFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Access-Control-Allow-Headers", "authorization, content-type, xsrf-token");
        response.addHeader("Access-Control-Expose-Headers", "xsrf-token");
        if ("OPTIONS".equals(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}

@RestController
class ConfigController {
    @Value("${app.apiEndpoint}")
    private String apiEndpoint = "";
    @Value("${app.apiEnv}")
    private String apiEnv = "";
    @Value("${app.apiKey}")
    private String apiKey = "";

    @GetMapping(value = "/js/config.js", produces = "application/javascript")
    ResponseEntity<CharSequence> getConfig() {
        final Map<String, String> values = new HashMap<>();
        final String apiEndpointUrl;
        if ("".equals(apiEndpoint)) {
            apiEndpointUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() + "/";
        } else if (apiEndpoint.endsWith("/")) {
            apiEndpointUrl = apiEndpoint;
        } else {
            apiEndpointUrl = apiEndpoint + "/";
        }
        values.put("apiEndpoint", apiEndpointUrl);

        values.put("apiEnv", apiEnv);
        values.put("apiKey", apiKey);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .body(generateJsValues(values));
    }

    private CharSequence generateJsValues(Map<String, String> values) {
        final StringBuilder buf = new StringBuilder(256);
        for (final Map.Entry<String, String> i : values.entrySet()) {
            buf.append("var ").append(i.getKey()).append(" = \"").append(i.getValue().trim()).append("\";\n");
        }
        return buf;
    }
}
